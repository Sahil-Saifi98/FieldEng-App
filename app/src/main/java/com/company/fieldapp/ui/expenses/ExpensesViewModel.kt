package com.company.fieldapp.ui.expenses

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.remote.TripExpenseItemRequest
import com.company.fieldapp.data.remote.TripSubmitRequest
import com.company.fieldapp.data.session.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Currency ──────────────────────────────────────────────────────
enum class Currency(val symbol: String) { INR("₹"), USD("$") }
const val USD_TO_INR = 83.5

// ── Expense types ─────────────────────────────────────────────────
enum class ExpenseType(val label: String) {
    HOTEL("Hotel / Lodging"),
    TRAVEL("Air / Train / Bus"),
    DAILY_ALLOWANCE("Daily Allowance"),
    LOCAL_CONVEYANCE("Local Conveyance"),
    OTHER("Other Expenses")
}

// ── Form models ───────────────────────────────────────────────────
data class ExpenseLineItem(
    val id: String = UUID.randomUUID().toString(),
    val expenseType: ExpenseType = ExpenseType.HOTEL,
    val details: String = "",
    val amount: String = "",
    val travelFrom: String = "",
    val travelTo: String = "",
    val travelMode: String = "Train",
    val daysCount: String = "",
    val ratePerDay: String = "",
    val receiptImagePath: String? = null
)

data class TripForm(
    val stationVisited: String = "",
    val periodFrom: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
    val periodTo: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
    val advanceAmount: String = "",
    val items: List<ExpenseLineItem> = listOf(ExpenseLineItem())
)

// ── Display model ─────────────────────────────────────────────────
data class TripGroup(
    val tripId: String,
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double,
    val status: String,
    val items: List<ExpenseEntity>,
    val total: Double,
    val isSynced: Boolean
)

data class ExpenseUiState(
    val trips: List<TripGroup> = emptyList(),
    val totalPending: Double = 0.0,
    val totalApproved: Double = 0.0,
    val isLoading: Boolean = false,
    val showAddForm: Boolean = false,
    val errorMessage: String? = null,
    val currency: Currency = Currency.INR,
    val syncMessage: String? = null
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).expenseDao()
    private val sessionManager = SessionManager(application)
    private val userId = sessionManager.getUserId() ?: ""
    private val employeeId = sessionManager.getEmployeeId() ?: ""
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState

    private val _form = MutableStateFlow(TripForm())
    val form: StateFlow<TripForm> = _form

    init {
        loadExpenses()
        syncUnsyncedTrips()
    }

    // ── Load & group from local DB ────────────────────────────────
    private fun loadExpenses() {
        viewModelScope.launch {
            dao.getAllExpenses(userId).collect { all ->
                val groups = all
                    .groupBy { it.tripId }
                    .map { (tripId, items) ->
                        val first = items.first()
                        val total = items.sumOf { it.amount }
                        TripGroup(
                            tripId = tripId,
                            stationVisited = first.stationVisited,
                            periodFrom = first.periodFrom,
                            periodTo = first.periodTo,
                            advanceAmount = first.advanceAmount,
                            status = first.status,
                            items = items,
                            total = total,
                            isSynced = items.all { it.isSynced }
                        )
                    }
                    .sortedByDescending { it.items.first().timestamp }

                val totalPending = groups
                    .filter { it.status == "pending" }
                    .sumOf { trip ->
                        val payable = trip.total - trip.advanceAmount
                        if (payable > 0) payable else 0.0
                    }

                val totalApproved = groups
                    .filter { it.status == "approved" }
                    .sumOf { it.total }

                _uiState.update {
                    it.copy(
                        trips = groups,
                        totalPending = totalPending,
                        totalApproved = totalApproved
                    )
                }
            }
        }
    }

    // ── Currency ──────────────────────────────────────────────────
    fun toggleCurrency() {
        _uiState.update {
            it.copy(currency = if (it.currency == Currency.INR) Currency.USD else Currency.INR)
        }
    }

    fun convertAmount(amountInInr: Double): Double =
        if (_uiState.value.currency == Currency.USD) amountInInr / USD_TO_INR else amountInInr

    // ── Form controls ─────────────────────────────────────────────
    fun showAddForm() {
        _form.value = TripForm()
        _uiState.update { it.copy(showAddForm = true, errorMessage = null) }
    }

    fun hideAddForm() = _uiState.update { it.copy(showAddForm = false) }

    fun onStationChange(v: String) = _form.update { it.copy(stationVisited = v) }
    fun onPeriodFromChange(v: String) = _form.update { it.copy(periodFrom = v) }
    fun onPeriodToChange(v: String) = _form.update { it.copy(periodTo = v) }
    fun onAdvanceChange(v: String) = _form.update { it.copy(advanceAmount = v) }

    fun addLineItem() = _form.update { it.copy(items = it.items + ExpenseLineItem()) }

    fun removeLineItem(id: String) {
        if (_form.value.items.size <= 1) return
        _form.update { it.copy(items = it.items.filter { i -> i.id != id }) }
    }

    fun updateLineItem(id: String, updated: ExpenseLineItem) {
        _form.update { it.copy(items = it.items.map { i -> if (i.id == id) updated else i }) }
    }

    fun onReceiptCaptured(itemId: String, path: String) {
        val item = _form.value.items.first { it.id == itemId }
        updateLineItem(itemId, item.copy(receiptImagePath = path))
    }

    fun clearReceipt(itemId: String) {
        val item = _form.value.items.first { it.id == itemId }
        updateLineItem(itemId, item.copy(receiptImagePath = null))
    }

    // ── Submit trip ───────────────────────────────────────────────
    fun submitTrip() {
        val f = _form.value

        if (f.stationVisited.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter the station / place visited") }
            return
        }

        f.items.forEachIndexed { index, item ->
            if ((item.amount.toDoubleOrNull() ?: 0.0) <= 0.0) {
                _uiState.update { it.copy(errorMessage = "Item ${index + 1}: Enter a valid amount") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tripId = UUID.randomUUID().toString()
                val advance = f.advanceAmount.toDoubleOrNull() ?: 0.0

                // 1. Save all items locally first (offline-first)
                val savedItems = mutableListOf<ExpenseEntity>()
                f.items.forEach { item ->
                    val amtInInr = (item.amount.toDoubleOrNull() ?: 0.0).let {
                        if (_uiState.value.currency == Currency.USD) it * USD_TO_INR else it
                    }
                    val entity = ExpenseEntity(
                        userId = userId,
                        employeeId = employeeId,
                        tripId = tripId,
                        stationVisited = f.stationVisited,
                        periodFrom = f.periodFrom,
                        periodTo = f.periodTo,
                        advanceAmount = advance,
                        expenseType = item.expenseType.label,
                        details = item.details,
                        travelFrom = item.travelFrom,
                        travelTo = item.travelTo,
                        travelMode = item.travelMode,
                        daysCount = item.daysCount.toIntOrNull() ?: 0,
                        ratePerDay = item.ratePerDay.toDoubleOrNull() ?: 0.0,
                        amount = amtInInr,
                        receiptImagePath = item.receiptImagePath,
                        isSynced = false
                    )
                    val localId = dao.insert(entity)
                    savedItems.add(entity.copy(id = localId))
                }

                _uiState.update { it.copy(showAddForm = false) }

                // 2. Try server immediately
                submitTripToServer(tripId, f, savedItems)

            } catch (e: Exception) {
                Log.e("ExpensesVM", "Submit error: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "Failed to save: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── Server submission ─────────────────────────────────────────
    private suspend fun submitTripToServer(
        tripId: String,
        form: TripForm,
        items: List<ExpenseEntity>
    ) {
        try {
            val advance = form.advanceAmount.toDoubleOrNull() ?: 0.0
            val hasReceipts = items.any { it.receiptImagePath != null }

            val serverId = if (hasReceipts) {
                submitWithReceipts(form, items, advance)
            } else {
                submitJsonOnly(form, items, advance)
            }

            if (serverId != null) {
                dao.markTripSynced(tripId, serverId)
                Log.d("ExpensesVM", "Trip synced: $serverId")
                _uiState.update { it.copy(syncMessage = "Trip submitted successfully") }
            }
        } catch (e: Exception) {
            Log.e("ExpensesVM", "Server sync failed (will retry on restart): ${e.message}")
        }
    }

    private suspend fun submitJsonOnly(
        form: TripForm,
        items: List<ExpenseEntity>,
        advance: Double
    ): String? {
        val response = RetrofitClient.tripApi.submitTrip(
            TripSubmitRequest(
                stationVisited = form.stationVisited,
                periodFrom = form.periodFrom,
                periodTo = form.periodTo,
                advanceAmount = advance,
                expenses = items.map {
                    TripExpenseItemRequest(
                        expenseType = it.expenseType,
                        details = it.details,
                        travelFrom = it.travelFrom,
                        travelTo = it.travelTo,
                        travelMode = it.travelMode,
                        daysCount = it.daysCount,
                        ratePerDay = it.ratePerDay,
                        amount = it.amount
                    )
                }
            )
        )
        return if (response.isSuccessful && response.body()?.success == true)
            response.body()?.data?._id
        else {
            Log.e("ExpensesVM", "Server error: ${response.body()?.message}")
            null
        }
    }

    private suspend fun submitWithReceipts(
        form: TripForm,
        items: List<ExpenseEntity>,
        advance: Double
    ): String? {
        val textType = "text/plain".toMediaType()
        val jsonType  = "application/json".toMediaType()

        val expensesJson = gson.toJson(items.map {
            TripExpenseItemRequest(
                expenseType = it.expenseType,
                details     = it.details,
                travelFrom  = it.travelFrom,
                travelTo    = it.travelTo,
                travelMode  = it.travelMode,
                daysCount   = it.daysCount,
                ratePerDay  = it.ratePerDay,
                amount      = it.amount
            )
        })

        // Build receipt parts.
        //
        // HOW THE INDEX ENCODING WORKS (prevents cross-user / sparse-receipt mixups):
        //
        // Problem: if a trip has 3 expenses and only expense #2 has a receipt, a naive
        // sequential approach would assign the file to expense #1 on the server.
        // With concurrent multi-user requests each request is independent on the server,
        // but within one request we still need to know WHICH expense each file belongs to.
        //
        // Solution: encode the expense array index into the filename as a prefix:
        //   "expIdx_{index}_{timestamp}.jpg"
        //   e.g. "expIdx_1_1709040123456.jpg"  ← belongs to expense[1]
        //
        // The server reads the expIdx_ prefix from originalname to map each file to
        // the correct expense slot, regardless of how many expenses lack receipts.
        // The field name stays "receipts" to satisfy Multer's .array('receipts', 10).
        val receiptParts = mutableListOf<MultipartBody.Part>()
        items.forEachIndexed { index, item ->
            item.receiptImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    receiptParts.add(
                        MultipartBody.Part.createFormData(
                            name     = "receipts",                                    // Multer field name
                            filename = "expIdx_${index}_${System.currentTimeMillis()}.jpg", // index encoded
                            body     = file.asRequestBody("image/jpeg".toMediaType())
                        )
                    )
                }
            }
        }

        val response = RetrofitClient.tripApi.submitTripWithReceipts(
            stationVisited = form.stationVisited.toRequestBody(textType),
            periodFrom     = form.periodFrom.toRequestBody(textType),
            periodTo       = form.periodTo.toRequestBody(textType),
            advanceAmount  = advance.toString().toRequestBody(textType),
            expenses       = expensesJson.toRequestBody(jsonType),
            receipts       = receiptParts
        )

        return if (response.isSuccessful && response.body()?.success == true)
            response.body()?.data?._id
        else {
            Log.e("ExpensesVM", "Server error (multipart): ${response.body()?.message}")
            null
        }
    }

    // ── Auto-sync unsynced trips on startup ───────────────────────
    private fun syncUnsyncedTrips() {
        viewModelScope.launch {
            try {
                val unsyncedIds = dao.getUnsyncedTripIds(userId)
                if (unsyncedIds.isEmpty()) return@launch

                Log.d("ExpensesVM", "Found ${unsyncedIds.size} unsynced trips, retrying...")

                for (tripId in unsyncedIds) {
                    val items = dao.getItemsByTripId(tripId)
                    if (items.isEmpty()) continue

                    val first = items.first()
                    val mockForm = TripForm(
                        stationVisited = first.stationVisited,
                        periodFrom     = first.periodFrom,
                        periodTo       = first.periodTo,
                        advanceAmount  = first.advanceAmount.toString()
                    )
                    submitTripToServer(tripId, mockForm, items)
                }
            } catch (e: Exception) {
                Log.e("ExpensesVM", "Startup sync error: ${e.message}")
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun clearSyncMessage() = _uiState.update { it.copy(syncMessage = null) }
}