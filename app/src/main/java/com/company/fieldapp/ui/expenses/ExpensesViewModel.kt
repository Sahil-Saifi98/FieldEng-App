package com.company.fieldapp.ui.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.data.session.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class Currency(val symbol: String) { INR("₹"), USD("$") }
const val USD_TO_INR = 83.5

// The 5 expense types from the tour form
enum class ExpenseType(val label: String) {
    HOTEL("Hotel / Lodging"),
    TRAVEL("Air / Train / Bus"),
    DAILY_ALLOWANCE("Daily Allowance"),
    LOCAL_CONVEYANCE("Local Conveyance"),
    OTHER("Other Expenses")
}

// One line item in the form
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

// Trip header — shared across all line items submitted together
data class TripForm(
    val stationVisited: String = "",
    val periodFrom: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
    val periodTo: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
    val advanceAmount: String = "",
    val items: List<ExpenseLineItem> = listOf(ExpenseLineItem())
)

// A "trip" as shown in the list — one header + its expense items
data class TripGroup(
    val tripId: String,
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double,
    val status: String,
    val items: List<ExpenseEntity>,
    val total: Double
)

data class ExpenseUiState(
    val trips: List<TripGroup> = emptyList(),
    val totalPending: Double = 0.0,
    val totalApproved: Double = 0.0,
    val isLoading: Boolean = false,
    val showAddForm: Boolean = false,
    val errorMessage: String? = null,
    val currency: Currency = Currency.INR
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).expenseDao()
    private val sessionManager = SessionManager(application)
    private val userId = sessionManager.getUserId() ?: ""
    private val employeeId = sessionManager.getEmployeeId() ?: ""

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState

    private val _form = MutableStateFlow(TripForm())
    val form: StateFlow<TripForm> = _form

    init { loadExpenses() }

    private fun loadExpenses() {
        viewModelScope.launch {
            dao.getAllExpenses(userId).collect { all ->
                // Group by tripId
                val groups = all
                    .groupBy { it.tripId }
                    .map { (tripId, items) ->
                        val first = items.first()
                        TripGroup(
                            tripId = tripId,
                            stationVisited = first.stationVisited,
                            periodFrom = first.periodFrom,
                            periodTo = first.periodTo,
                            advanceAmount = first.advanceAmount,
                            status = first.status,
                            items = items,
                            total = items.sumOf { it.amount }
                        )
                    }
                    .sortedByDescending { it.items.first().timestamp }

                val pending = all.filter { it.status == "pending" }.sumOf { it.amount }
                val approved = all.filter { it.status == "approved" }.sumOf { it.amount }

                _uiState.update {
                    it.copy(trips = groups, totalPending = pending, totalApproved = approved)
                }
            }
        }
    }

    fun toggleCurrency() {
        _uiState.update {
            it.copy(currency = if (it.currency == Currency.INR) Currency.USD else Currency.INR)
        }
    }

    fun convertAmount(amountInInr: Double): Double =
        if (_uiState.value.currency == Currency.USD) amountInInr / USD_TO_INR else amountInInr

    fun showAddForm() {
        _form.value = TripForm()
        _uiState.update { it.copy(showAddForm = true, errorMessage = null) }
    }

    fun hideAddForm() = _uiState.update { it.copy(showAddForm = false) }

    // Trip header updates
    fun onStationChange(v: String) = _form.update { it.copy(stationVisited = v) }
    fun onPeriodFromChange(v: String) = _form.update { it.copy(periodFrom = v) }
    fun onPeriodToChange(v: String) = _form.update { it.copy(periodTo = v) }
    fun onAdvanceChange(v: String) = _form.update { it.copy(advanceAmount = v) }

    // Line item operations
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

    fun submitTrip() {
        val f = _form.value

        if (f.stationVisited.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter the station / place visited") }
            return
        }

        for ((index, item) in f.items.withIndex()) {
            val amt = when (item.expenseType) {
                ExpenseType.DAILY_ALLOWANCE -> {
                    val d = item.daysCount.toDoubleOrNull() ?: 0.0
                    val r = item.ratePerDay.toDoubleOrNull() ?: 0.0
                    if (d <= 0 || r <= 0) {
                        _uiState.update { it.copy(errorMessage = "Item ${index + 1}: Enter valid days and rate") }
                        return
                    }
                    d * r
                }
                else -> {
                    val a = item.amount.toDoubleOrNull()
                    if (a == null || a <= 0) {
                        _uiState.update { it.copy(errorMessage = "Item ${index + 1}: Enter a valid amount") }
                        return
                    }
                    a
                }
            }
            // Store back computed amount for daily allowance
            updateLineItem(item.id, item.copy(amount = amt.toString()))
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tripId = UUID.randomUUID().toString()
                val advance = f.advanceAmount.toDoubleOrNull() ?: 0.0

                _form.value.items.forEach { item ->
                    val amtInInr = run {
                        val raw = when (item.expenseType) {
                            ExpenseType.DAILY_ALLOWANCE ->
                                (item.daysCount.toDoubleOrNull() ?: 0.0) *
                                        (item.ratePerDay.toDoubleOrNull() ?: 0.0)
                            else -> item.amount.toDoubleOrNull() ?: 0.0
                        }
                        if (_uiState.value.currency == Currency.USD) raw * USD_TO_INR else raw
                    }
                    dao.insert(
                        ExpenseEntity(
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
                            receiptImagePath = item.receiptImagePath
                        )
                    )
                }
                _uiState.update { it.copy(showAddForm = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}