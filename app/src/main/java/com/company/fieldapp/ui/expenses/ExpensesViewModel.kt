package com.company.fieldapp.ui.expenses

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.repository.ExpenseRepository
import com.company.fieldapp.data.session.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val isSyncing: Boolean = false,
    val unsyncedCount: Int = 0,
    val showAddForm: Boolean = false,
    val errorMessage: String? = null,
    val currency: Currency = Currency.INR,
    val syncMessage: String? = null
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao            = AppDatabase.getDatabase(application).expenseDao()
    private val repository     = ExpenseRepository(dao)
    private val sessionManager = SessionManager(application)
    private val userId         = sessionManager.getUserId() ?: ""
    private val employeeId     = sessionManager.getEmployeeId() ?: ""
    private val appContext     = application.applicationContext

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState

    private val _form = MutableStateFlow(TripForm())
    val form: StateFlow<TripForm> = _form

    // Prevents two sync loops running simultaneously
    private var syncJob: Job? = null

    init {
        loadExpenses()
        // Same pattern as AttendanceViewModel — delay 600ms for token restore, then sync
        viewModelScope.launch {
            delay(600)
            refreshUnsyncedCount()
            syncUnsyncedTrips()
            refreshStatusFromServer()
        }
        // Retry loop — every 30s attempt sync if there are pending trips
        // No isOnline() check — just try it, same as attendance does
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (repository.getUnsyncedCount(userId) > 0) {
                    syncUnsyncedTrips()
                }
            }
        }
    }

    // ── Load & group from local DB ────────────────────────────────
    private fun loadExpenses() {
        viewModelScope.launch {
            dao.getAllExpenses(userId).collect { all ->
                val groups = all
                    .groupBy { it.tripId }
                    .map { (tripId, items) ->
                        val first = items.first()
                        TripGroup(
                            tripId         = tripId,
                            stationVisited = first.stationVisited,
                            periodFrom     = first.periodFrom,
                            periodTo       = first.periodTo,
                            advanceAmount  = first.advanceAmount,
                            status         = first.status,
                            items          = items,
                            total          = items.sumOf { it.amount },
                            isSynced       = items.all { it.isSynced }
                        )
                    }
                    .sortedByDescending { it.items.first().timestamp }

                val totalPending = groups
                    .filter { it.status == "pending" }
                    .sumOf { t -> (t.total - t.advanceAmount).coerceAtLeast(0.0) }

                val totalApproved = groups
                    .filter { it.status == "approved" }
                    .sumOf { it.total }

                _uiState.update {
                    it.copy(
                        trips         = groups,
                        totalPending  = totalPending,
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

    fun onStationChange(v: String)    = _form.update { it.copy(stationVisited = v) }
    fun onPeriodFromChange(v: String) = _form.update { it.copy(periodFrom = v) }
    fun onPeriodToChange(v: String)   = _form.update { it.copy(periodTo = v) }
    fun onAdvanceChange(v: String)    = _form.update { it.copy(advanceAmount = v) }

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

    // ── Submit trip (offline-first) ───────────────────────────────
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
                val tripId  = UUID.randomUUID().toString()
                val advance = f.advanceAmount.toDoubleOrNull() ?: 0.0

                // 1. Save locally first — trip is never lost even if offline
                f.items.forEach { item ->
                    val amtInInr = (item.amount.toDoubleOrNull() ?: 0.0).let { amt ->
                        if (_uiState.value.currency == Currency.USD) amt * USD_TO_INR else amt
                    }
                    dao.insert(
                        ExpenseEntity(
                            userId           = userId,
                            employeeId       = employeeId,
                            tripId           = tripId,
                            stationVisited   = f.stationVisited,
                            periodFrom       = f.periodFrom,
                            periodTo         = f.periodTo,
                            advanceAmount    = advance,
                            expenseType      = item.expenseType.label,
                            details          = item.details,
                            travelFrom       = item.travelFrom,
                            travelTo         = item.travelTo,
                            travelMode       = item.travelMode,
                            daysCount        = item.daysCount.toIntOrNull() ?: 0,
                            ratePerDay       = item.ratePerDay.toDoubleOrNull() ?: 0.0,
                            amount           = amtInInr,
                            receiptImagePath = item.receiptImagePath,
                            isSynced         = false
                        )
                    )
                }

                _uiState.update { it.copy(showAddForm = false) }

                // 2. Attempt sync immediately (picks up this + any older pending trips)
                syncUnsyncedTrips()

            } catch (e: Exception) {
                Log.e("ExpensesVM", "Submit error: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "Failed to save: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── Sync all pending trips — called on startup, manual retry, after submit ──
    fun syncUnsyncedTrips() {
        if (syncJob?.isActive == true) return   // don't start a second loop

        syncJob = viewModelScope.launch {
            try {
                val count = repository.getUnsyncedCount(userId)
                _uiState.update { it.copy(unsyncedCount = count) }
                if (count == 0) return@launch

                Log.d("ExpensesVM", "Syncing $count unsynced trip(s)...")
                _uiState.update { it.copy(isSyncing = true) }

                val result    = repository.syncAllPendingToServer(userId)
                val remaining = repository.getUnsyncedCount(userId)

                val message = when {
                    result.total == 0                                  -> null
                    result.failed == 0 && result.missingReceipts == 0 ->
                        "✓ All trips synced successfully"
                    result.failed == 0 && result.missingReceipts > 0  ->
                        "✓ ${result.success} trip(s) synced — ${result.missingReceipts} receipt image(s) missing from device"
                    result.success > 0 ->
                        "${result.success} synced, $remaining still pending"
                    else -> null
                }

                _uiState.update {
                    it.copy(isSyncing = false, unsyncedCount = remaining, syncMessage = message)
                }

            } catch (e: Exception) {
                Log.e("ExpensesVM", "Sync error: ${e.message}")
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun refreshUnsyncedCount() {
        try {
            _uiState.update { it.copy(unsyncedCount = repository.getUnsyncedCount(userId)) }
        } catch (e: Exception) {
            Log.e("ExpensesVM", "refreshUnsyncedCount: ${e.message}")
        }
    }

    // ── Pull latest trip statuses from server ─────────────────────
    private fun refreshStatusFromServer() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.tripApi.getMyTrips()
                if (!response.isSuccessful || response.body()?.success != true) return@launch

                var updated = 0
                for (serverTrip in response.body()!!.data) {
                    val localItems = dao.getItemsByServerId(serverTrip._id)
                    if (localItems.isEmpty()) continue
                    if (localItems.first().status != serverTrip.status) {
                        dao.updateTripStatus(localItems.first().tripId, serverTrip.status)
                        updated++
                    }
                }
                if (updated > 0) Log.d("ExpensesVM", "$updated trip status(es) refreshed")

            } catch (e: Exception) {
                Log.w("ExpensesVM", "Status refresh (non-critical): ${e.message}")
            }
        }
    }

    fun clearError()       = _uiState.update { it.copy(errorMessage = null) }
    fun clearSyncMessage() = _uiState.update { it.copy(syncMessage = null) }
}