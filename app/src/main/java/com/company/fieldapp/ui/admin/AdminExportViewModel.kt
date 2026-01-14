package com.company.fieldapp.ui.admin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.remote.AdminUser
import com.company.fieldapp.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _selectedUser = MutableStateFlow<AdminUser?>(null)
    val selectedUser: StateFlow<AdminUser?> = _selectedUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _exportStatus = MutableStateFlow<ExportStatus?>(null)
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus

    private val _startDate = MutableStateFlow<String?>(null)
    val startDate: StateFlow<String?> = _startDate

    private val _endDate = MutableStateFlow<String?>(null)
    val endDate: StateFlow<String?> = _endDate

    private val context = application.applicationContext

    init {
        loadUsers()
        // Set default date range (last 30 days)
        val calendar = Calendar.getInstance()
        val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        _startDate.value = startDateStr
        _endDate.value = endDateStr
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminApi.getAllUsers()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _users.value = body.data
                        Log.d("AdminExportVM", "Loaded ${body.data.size} users")
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminExportVM", "Error loading users: ${e.message}", e)
            }
        }
    }

    fun selectUser(user: AdminUser) {
        _selectedUser.value = user
        _exportStatus.value = null
    }

    fun setStartDate(date: String) {
        _startDate.value = date
        _exportStatus.value = null
    }

    fun setEndDate(date: String) {
        _endDate.value = date
        _exportStatus.value = null
    }

    fun exportUserData() {
        val user = _selectedUser.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                // Get user's attendance data with date range
                val response = RetrofitClient.adminApi.getUserAttendance(user._id)

                if (response.isSuccessful && response.body() != null) {
                    var attendanceData = response.body()!!.data

                    // Filter by date range if specified
                    if (_startDate.value != null && _endDate.value != null) {
                        attendanceData = attendanceData.filter { attendance ->
                            attendance.date >= _startDate.value!! && attendance.date <= _endDate.value!!
                        }
                    }

                    // Create JSON file
                    val jsonData = JSONObject().apply {
                        put("user", JSONObject().apply {
                            put("employeeId", user.employeeId)
                            put("name", user.name)
                            put("email", user.email)
                            put("department", user.department)
                            put("designation", user.designation)
                        })
                        put("dateRange", JSONObject().apply {
                            put("startDate", _startDate.value ?: "All")
                            put("endDate", _endDate.value ?: "All")
                        })
                        put("totalRecords", attendanceData.size)
                        put("attendance", JSONArray().apply {
                            attendanceData.forEach { attendance ->
                                put(JSONObject().apply {
                                    put("date", attendance.date)
                                    put("checkInTime", attendance.checkInTime)
                                    put("latitude", attendance.latitude)
                                    put("longitude", attendance.longitude)
                                    put("selfieUrl", attendance.selfieUrl ?: attendance.selfiePath)
                                })
                            }
                        })
                        put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    }

                    // Save to file
                    val fileName = "${user.employeeId}_${System.currentTimeMillis()}.json"
                    val file = saveToDownloads(fileName, jsonData.toString())

                    _exportStatus.value = ExportStatus(
                        isSuccess = true,
                        message = "✓ Exported ${attendanceData.size} records to ${file.name}"
                    )
                    Log.d("AdminExportVM", "Exported data for user ${user.employeeId}")
                } else {
                    _exportStatus.value = ExportStatus(
                        isSuccess = false,
                        message = "Failed to fetch user data"
                    )
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = "Error: ${e.message}"
                )
                Log.e("AdminExportVM", "Export error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                val response = RetrofitClient.adminApi.getAllAttendance(
                    _startDate.value,
                    _endDate.value
                )

                if (response.isSuccessful && response.body() != null) {
                    val allData = response.body()!!.data

                    val jsonData = JSONObject().apply {
                        put("dateRange", JSONObject().apply {
                            put("startDate", _startDate.value ?: "All")
                            put("endDate", _endDate.value ?: "All")
                        })
                        put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        put("totalRecords", allData.size)
                        put("attendance", JSONArray().apply {
                            allData.forEach { attendance ->
                                put(JSONObject().apply {
                                    put("employeeId", attendance.employeeId)
                                    put("employeeName", attendance.userName)
                                    put("date", attendance.date)
                                    put("checkInTime", attendance.checkInTime)
                                    put("latitude", attendance.latitude)
                                    put("longitude", attendance.longitude)
                                    put("selfieUrl", attendance.selfieUrl ?: attendance.selfiePath)
                                })
                            }
                        })
                    }

                    val fileName = "all_data_${System.currentTimeMillis()}.json"
                    val file = saveToDownloads(fileName, jsonData.toString())

                    _exportStatus.value = ExportStatus(
                        isSuccess = true,
                        message = "✓ Exported ${allData.size} records to ${file.name}"
                    )
                } else {
                    _exportStatus.value = ExportStatus(
                        isSuccess = false,
                        message = "Failed to fetch data"
                    )
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = "Error: ${e.message}"
                )
                Log.e("AdminExportVM", "Export all error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportAttendance(format: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportStatus.value = null

            try {
                val response = RetrofitClient.adminApi.getAllAttendance(
                    _startDate.value,
                    _endDate.value
                )

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data

                    val content = when (format) {
                        "json" -> convertToJson(data)
                        "csv" -> convertAttendanceToCSV(data)
                        "pdf" -> {
                            _exportStatus.value = ExportStatus(
                                isSuccess = false,
                                message = "PDF export will be implemented soon"
                            )
                            _isLoading.value = false
                            return@launch
                        }
                        else -> convertToJson(data)
                    }

                    val fileName = "attendance_${System.currentTimeMillis()}.$format"
                    val file = saveToDownloads(fileName, content)

                    _exportStatus.value = ExportStatus(
                        isSuccess = true,
                        message = "✓ Exported ${data.size} attendance records as $format"
                    )
                }
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus(
                    isSuccess = false,
                    message = "Error: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportExpenses(format: String) {
        _exportStatus.value = ExportStatus(
            isSuccess = false,
            message = "Expenses module not yet implemented"
        )
    }

    fun exportTasks(format: String) {
        _exportStatus.value = ExportStatus(
            isSuccess = false,
            message = "Tasks module not yet implemented"
        )
    }

    private fun convertToJson(data: List<com.company.fieldapp.data.remote.AdminAttendanceItem>): String {
        val jsonArray = JSONArray()
        data.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("employeeId", item.employeeId)
                put("employeeName", item.userName)
                put("date", item.date)
                put("checkInTime", item.checkInTime)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("selfieUrl", item.selfieUrl ?: item.selfiePath)
            })
        }
        return jsonArray.toString(2)
    }

    private fun convertAttendanceToCSV(data: List<com.company.fieldapp.data.remote.AdminAttendanceItem>): String {
        val csv = StringBuilder()
        csv.append("Employee ID,Employee Name,Date,Check-in Time,Latitude,Longitude,Selfie URL\n")
        data.forEach { item ->
            csv.append("\"${item.employeeId}\",")
            csv.append("\"${item.userName ?: ""}\",")
            csv.append("\"${item.date}\",")
            csv.append("\"${item.checkInTime}\",")
            csv.append("${item.latitude},")
            csv.append("${item.longitude},")
            csv.append("\"${item.selfieUrl ?: item.selfiePath}\"\n")
        }
        return csv.toString()
    }

    private fun saveToDownloads(fileName: String, content: String): File {
        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray())
        }

        return file
    }
}