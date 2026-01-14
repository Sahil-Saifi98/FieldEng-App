package com.company.fieldapp.ui.admin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminAttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val _attendanceList = MutableStateFlow<List<AdminAttendanceData>>(emptyList())
    val attendanceList: StateFlow<List<AdminAttendanceData>> = _attendanceList

    private val _filteredList = MutableStateFlow<List<AdminAttendanceData>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _selectedDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val selectedDate: StateFlow<String> = _selectedDate

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadAttendance()
    }

    fun loadAttendance() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val startDate = _selectedDate.value
                val endDate = _selectedDate.value

                Log.d("AdminAttendanceVM", "Loading attendance: $startDate to $endDate")

                val response = RetrofitClient.adminApi.getAllAttendance(startDate, endDate)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        val attendanceData = body.data.map { serverData ->
                            AdminAttendanceData(
                                id = serverData._id,
                                employeeName = serverData.userId.name,
                                employeeId = serverData.userId.employeeId,
                                date = serverData.date,
                                checkInTime = convertUtcToIst(serverData.timestamp),
                                latitude = serverData.latitude,
                                longitude = serverData.longitude,
                                selfieUrl = serverData.selfieUrl ?: serverData.selfiePath,
                                isSynced = serverData.isSynced
                            )
                        }.sortedByDescending { it.date + it.checkInTime }

                        _attendanceList.value = attendanceData
                        _filteredList.value = attendanceData
                        applyFilters()

                        Log.d("AdminAttendanceVM", "Loaded ${attendanceData.size} records")
                    } else {
                        _errorMessage.value = body.message ?: "Failed to load attendance"
                    }
                } else {
                    _errorMessage.value = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
                Log.e("AdminAttendanceVM", "Exception: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun convertUtcToIst(utcTime: String): String {
        return try {
            val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            utcFormat.timeZone = TimeZone.getTimeZone("UTC")

            val date = utcFormat.parse(utcTime)

            val istFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            istFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")

            istFormat.format(date!!)
        } catch (e: Exception) {
            utcTime
        }
    }


    fun onDateSelected(date: String) {
        _selectedDate.value = date
        loadAttendance()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    private fun applyFilters() {
        val query = _searchQuery.value.lowercase()

        _attendanceList.value = if (query.isEmpty()) {
            _filteredList.value
        } else {
            _filteredList.value.filter { attendance ->
                attendance.employeeName.lowercase().contains(query) ||
                        attendance.employeeId.lowercase().contains(query) ||
                        attendance.latitude.toString().contains(query) ||
                        attendance.longitude.toString().contains(query)
            }
        }
    }
}