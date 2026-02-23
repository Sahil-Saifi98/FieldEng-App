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

enum class Currency(val symbol: String, val code: String) {
    INR("₹", "INR"),
    USD("$", "USD")
}

const val USD_TO_INR = 90.92  // Update as needed

data class ExpenseUiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val totalPending: Double = 0.0,
    val totalApproved: Double = 0.0,
    val isLoading: Boolean = false,
    val showAddForm: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val currency: Currency = Currency.INR
)

data class NewExpenseForm(
    val category: String = "",
    val amount: String = "",
    val date: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
    val description: String = "",
    val receiptImagePath: String? = null
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).expenseDao()
    private val sessionManager = SessionManager(application)
    private val userId = sessionManager.getUserId() ?: ""
    private val employeeId = sessionManager.getEmployeeId() ?: ""

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState

    private val _form = MutableStateFlow(NewExpenseForm())
    val form: StateFlow<NewExpenseForm> = _form

    init {
        loadExpenses()
    }

    private fun loadExpenses() {
        viewModelScope.launch {
            dao.getAllExpenses(userId).collect { list ->
                val pending = list.filter { it.status == "pending" }.sumOf { it.amount }
                val approved = list.filter { it.status == "approved" }.sumOf { it.amount }
                _uiState.update {
                    it.copy(expenses = list, totalPending = pending, totalApproved = approved)
                }
            }
        }
    }

    fun toggleCurrency() {
        _uiState.update {
            it.copy(
                currency = if (it.currency == Currency.INR) Currency.USD else Currency.INR
            )
        }
    }

    // Convert stored INR amount to display currency
    fun convertAmount(amountInInr: Double): Double {
        return if (_uiState.value.currency == Currency.USD) amountInInr / USD_TO_INR
        else amountInInr
    }

    fun showAddForm() {
        _form.value = NewExpenseForm()
        _uiState.update { it.copy(showAddForm = true, submitSuccess = false) }
    }

    fun hideAddForm() {
        _uiState.update { it.copy(showAddForm = false) }
    }

    fun onCategoryChange(cat: String) = _form.update { it.copy(category = cat) }
    fun onAmountChange(amt: String) = _form.update { it.copy(amount = amt) }
    fun onDateChange(d: String) = _form.update { it.copy(date = d) }
    fun onDescriptionChange(desc: String) = _form.update { it.copy(description = desc) }
    fun onReceiptCaptured(path: String) = _form.update { it.copy(receiptImagePath = path) }
    fun clearReceipt() = _form.update { it.copy(receiptImagePath = null) }

    fun submitExpense() {
        val f = _form.value
        if (f.category.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select a category") }
            return
        }
        val amountInput = f.amount.toDoubleOrNull()
        if (amountInput == null || amountInput <= 0) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount") }
            return
        }
        // Always store in INR internally
        val amountInInr = if (_uiState.value.currency == Currency.USD)
            amountInput * USD_TO_INR else amountInput

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                dao.insert(
                    ExpenseEntity(
                        userId = userId,
                        employeeId = employeeId,
                        category = f.category,
                        amount = amountInInr,
                        date = f.date,
                        description = f.description,
                        receiptImagePath = f.receiptImagePath
                    )
                )
                _uiState.update { it.copy(showAddForm = false, submitSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}