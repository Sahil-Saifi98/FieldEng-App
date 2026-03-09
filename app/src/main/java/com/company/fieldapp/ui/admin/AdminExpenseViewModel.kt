package com.company.fieldapp.ui.admin

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.remote.TripData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── UI models ─────────────────────────────────────────────────────

data class AdminTripItem(
    val id: String,
    val employeeId: String,
    val employeeName: String,
    val designation: String,
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double,
    val totalAmount: Double,
    val payableAmount: Double,
    val status: String,
    val expenseCount: Int,
    val createdAt: String,
    val expenses: List<AdminExpenseLineItem>,
    val adminNote: String?
)

data class AdminExpenseLineItem(
    val expenseType: String,
    val details: String,
    val travelFrom: String,
    val travelTo: String,
    val travelMode: String,
    val daysCount: Int,
    val ratePerDay: Double,
    val amount: Double,
    val receiptUrl: String?
)

data class AdminExpenseStats(
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
    val totalPendingAmount: Double = 0.0,
    val totalApprovedAmount: Double = 0.0,
    val totalAmount: Double = 0.0
)

data class AdminExpenseUiState(
    val trips: List<AdminTripItem> = emptyList(),
    val filteredTrips: List<AdminTripItem> = emptyList(),
    val stats: AdminExpenseStats = AdminExpenseStats(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val searchQuery: String = "",
    val statusFilter: String = "all",
    val selectedTrip: AdminTripItem? = null,
    val showDetailSheet: Boolean = false
)

class AdminExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AdminExpenseUiState())
    val uiState: StateFlow<AdminExpenseUiState> = _uiState

    init { loadTrips() }

    fun loadTrips() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = RetrofitClient.tripApi.getAdminAllTrips()
                if (response.isSuccessful && response.body()?.success == true) {
                    val trips = response.body()!!.data.map { it.toAdminItem() }
                        .sortedWith(compareBy<AdminTripItem> {
                            when (it.status) { "pending" -> 0; "approved" -> 1; else -> 2 }
                        }.thenByDescending { it.createdAt })
                    _uiState.update {
                        it.copy(trips = trips, stats = buildStats(trips), isLoading = false)
                    }
                    applyFilters()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Failed to load: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminExpenseVM", "Load: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Network error: ${e.message}") }
            }
        }
    }

    fun onSearchChange(q: String) { _uiState.update { it.copy(searchQuery = q) }; applyFilters() }
    fun onStatusFilterChange(s: String) { _uiState.update { it.copy(statusFilter = s) }; applyFilters() }

    private fun applyFilters() {
        val s = _uiState.value
        val q = s.searchQuery.trim().lowercase()
        _uiState.update {
            it.copy(filteredTrips = it.trips.filter { trip ->
                val matchStatus   = s.statusFilter == "all" || trip.status == s.statusFilter
                val matchSearch   = q.isEmpty() ||
                        trip.employeeName.lowercase().contains(q) ||
                        trip.employeeId.lowercase().contains(q) ||
                        trip.stationVisited.lowercase().contains(q) ||
                        trip.designation.lowercase().contains(q)
                matchStatus && matchSearch
            })
        }
    }

    fun openDetail(trip: AdminTripItem) =
        _uiState.update { it.copy(selectedTrip = trip, showDetailSheet = true) }

    fun closeDetail() =
        _uiState.update { it.copy(showDetailSheet = false, selectedTrip = null) }

    fun approveTrip(tripId: String) = updateStatus(tripId, "approved", "")
    fun rejectTrip(tripId: String, note: String) = updateStatus(tripId, "rejected", note)

    private fun updateStatus(tripId: String, status: String, note: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.tripApi.updateTripStatus(
                    tripId, mapOf("status" to status, "adminNote" to note)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            successMessage = if (status == "approved") "Trip approved ✓" else "Trip rejected",
                            showDetailSheet = false, selectedTrip = null
                        )
                    }
                    loadTrips()
                } else {
                    _uiState.update { it.copy(errorMessage = response.body()?.message ?: "Update failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
            }
        }
    }


    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }

    private fun buildStats(trips: List<AdminTripItem>) = AdminExpenseStats(
        pendingCount = trips.count { it.status == "pending" },
        approvedCount = trips.count { it.status == "approved" },
        rejectedCount = trips.count { it.status == "rejected" },
        totalPendingAmount = trips.filter { it.status == "pending" }.sumOf { it.payableAmount },
        totalApprovedAmount = trips.filter { it.status == "approved" }.sumOf { it.totalAmount },
        totalAmount = trips.sumOf { it.totalAmount }
    )

    private fun TripData.toAdminItem() = AdminTripItem(
        id = _id,
        employeeId = employeeId,
        employeeName = employeeName,
        designation = designation ?: "",
        stationVisited = stationVisited,
        periodFrom = periodFrom,
        periodTo = periodTo,
        advanceAmount = advanceAmount,
        totalAmount = totalAmount,
        payableAmount = payableAmount,
        status = status,
        expenseCount = expenses.size,
        createdAt = createdAt,
        adminNote = adminNote,
        expenses = expenses.map { e ->
            AdminExpenseLineItem(
                expenseType = e.expenseType,
                details = e.details,
                travelFrom = e.travelFrom,
                travelTo = e.travelTo,
                travelMode = e.travelMode,
                daysCount = e.daysCount,
                ratePerDay = e.ratePerDay,
                amount = e.amount,
                receiptUrl = e.receiptUrl
            )
        }
    )
}