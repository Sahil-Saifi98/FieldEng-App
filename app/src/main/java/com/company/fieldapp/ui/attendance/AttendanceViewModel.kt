package com.company.fieldapp.ui.attendance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.local.AttendanceEntity
import com.company.fieldapp.data.repository.AttendanceRepository
import com.company.fieldapp.data.remote.RetrofitClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AttendanceRepository

    private val _status = MutableStateFlow("Ready to check in")
    val status: StateFlow<String> = _status

    private val _todayAttendance = MutableStateFlow<List<AttendanceEntity>>(emptyList())
    val todayAttendance: StateFlow<List<AttendanceEntity>> = _todayAttendance

    private var capturedSelfiePath: String? = null
    private var capturedLatitude: Double? = null
    private var capturedLongitude: Double? = null

    private val sessionManager = com.company.fieldapp.data.session.SessionManager(application)

    init {
        val dao = AppDatabase.getDatabase(application).attendanceDao()
        repository = AttendanceRepository(dao, application)
        loadTodayAttendance()
    }

    fun onSelfieCaptured(file: File) {
        capturedSelfiePath = file.absolutePath
        _status.value = "Selfie captured, getting location..."
        Log.d("AttendanceVM", "Selfie saved at: ${file.absolutePath}")
    }

    fun onLocationReceived(lat: Double, lng: Double) {
        capturedLatitude = lat
        capturedLongitude = lng
        Log.d("AttendanceVM", "Location received: $lat, $lng")

        // Save attendance both locally and to backend
        saveAttendance()
    }

    fun onLocationError(error: String) {
        _status.value = "Location error: $error"
        Log.e("AttendanceVM", "Location error: $error")
    }

    fun onCameraError(error: String) {
        _status.value = "Camera error: $error"
        Log.e("AttendanceVM", "Camera error: $error")
    }

    private fun saveAttendance() {
        val selfiePath = capturedSelfiePath
        val lat = capturedLatitude
        val lng = capturedLongitude

        if (selfiePath != null && lat != null && lng != null) {
            viewModelScope.launch {
                try {
                    val timestamp = System.currentTimeMillis()

                    // Get current user info
                    val userId = sessionManager.getUserId() ?: ""
                    val employeeId = sessionManager.getEmployeeId() ?: ""

                    // Save to local database first with temporary address
                    val attendance = AttendanceEntity(
                        userId = userId,
                        employeeId = employeeId,
                        selfiePath = selfiePath,
                        latitude = lat,
                        longitude = lng,
                        address = "Location: $lat, $lng", // ✅ Temporary address placeholder
                        timestamp = timestamp,
                        isSynced = false
                    )
                    val localId = repository.saveAttendance(attendance)
                    Log.d("AttendanceVM", "Saved to local DB with ID: $localId")

                    // Send to backend immediately
                    _status.value = "Uploading to server..."

                    val selfieFile = File(selfiePath)
                    if (selfieFile.exists()) {
                        Log.d("AttendanceVM", "File exists: ${selfieFile.length()} bytes")

                        // Create multipart with proper MIME type
                        val selfiePart = MultipartBody.Part.createFormData(
                            "selfie",
                            "selfie_${timestamp}.jpg",
                            selfieFile.asRequestBody("image/jpeg".toMediaType())
                        )

                        Log.d("AttendanceVM", "Uploading: lat=$lat, lng=$lng, timestamp=$timestamp")

                        val response = RetrofitClient.attendanceApi.submitAttendance(
                            selfie = selfiePart,
                            latitude = lat.toString().toRequestBody("text/plain".toMediaType()),
                            longitude = lng.toString().toRequestBody("text/plain".toMediaType()),
                            timestamp = timestamp.toString().toRequestBody("text/plain".toMediaType())
                        )

                        if (response.isSuccessful && response.body()?.success == true) {
                            val serverData = response.body()!!.data

                            // ✅ Update local record with server's address
                            if (serverData != null) {
                                val updatedAttendance = attendance.copy(
                                    id = localId,
                                    address = serverData.address, // ✅ Update with actual address from server
                                    isSynced = true
                                )
                                repository.updateAttendance(updatedAttendance)
                                Log.d("AttendanceVM", "Updated local record with address: ${serverData.address}")
                            } else {
                                // Just mark as synced
                                repository.markAsSynced(localId)
                            }

                            _status.value = "✅ Attendance recorded successfully!"
                            Log.d("AttendanceVM", "Upload successful!")
                        } else {
                            val errorMsg = response.body()?.message ?: response.message()
                            _status.value = "⚠️ Server error: $errorMsg"
                            Log.e("AttendanceVM", "Upload failed: $errorMsg")
                        }
                    } else {
                        _status.value = "⚠️ File not found"
                        Log.e("AttendanceVM", "Selfie file not found")
                    }

                    // Reset
                    capturedSelfiePath = null
                    capturedLatitude = null
                    capturedLongitude = null

                    // Reload today's attendance
                    loadTodayAttendance()

                } catch (e: Exception) {
                    _status.value = "Error: ${e.message}"
                    Log.e("AttendanceVM", "Exception: ${e.message}", e)
                }
            }
        } else {
            _status.value = "Missing data for attendance"
            Log.e("AttendanceVM", "Missing data: selfie=$selfiePath, lat=$lat, lng=$lng")
        }
    }

    fun syncAttendanceToServer() {
        viewModelScope.launch {
            try {
                _status.value = "Syncing..."
                val result = repository.syncAttendanceToServer()

                result.onSuccess { message ->
                    _status.value = message
                    loadTodayAttendance()
                }

                result.onFailure { error ->
                    _status.value = "Sync error: ${error.message}"
                    Log.e("AttendanceVM", "Sync failed: ${error.message}")
                }
            } catch (e: Exception) {
                _status.value = "Sync error: ${e.message}"
                Log.e("AttendanceVM", "Sync exception: ${e.message}", e)
            }
        }
    }

    private fun loadTodayAttendance() {
        viewModelScope.launch {
            try {
                val attendance = repository.getTodayAttendance()
                _todayAttendance.value = attendance
                Log.d("AttendanceVM", "Loaded ${attendance.size} attendance records")
            } catch (e: Exception) {
                _status.value = "Error loading attendance: ${e.message}"
                Log.e("AttendanceVM", "Load error: ${e.message}", e)
            }
        }
    }
}