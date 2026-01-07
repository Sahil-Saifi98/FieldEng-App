package com.company.fieldapp.ui.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.local.AttendanceEntity
import com.company.fieldapp.data.repository.AttendanceRepository
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

    init {
        val dao = AppDatabase.getDatabase(application).attendanceDao()
        repository = AttendanceRepository(dao)
        loadTodayAttendance()
    }

    fun onSelfieCaptured(file: File) {
        capturedSelfiePath = file.absolutePath
        _status.value = "Selfie captured, getting location..."
    }

    fun onLocationReceived(lat: Double, lng: Double) {
        capturedLatitude = lat
        capturedLongitude = lng

        // Save attendance
        saveAttendance()
    }

    fun onLocationError(error: String) {
        _status.value = "Location error: $error"
    }

    fun onCameraError(error: String) {
        _status.value = "Camera error: $error"
    }

    private fun saveAttendance() {
        val selfiePath = capturedSelfiePath
        val lat = capturedLatitude
        val lng = capturedLongitude

        if (selfiePath != null && lat != null && lng != null) {
            viewModelScope.launch {
                try {
                    val attendance = AttendanceEntity(
                        selfiePath = selfiePath,
                        latitude = lat,
                        longitude = lng,
                        timestamp = System.currentTimeMillis(),
                        isSynced = false
                    )

                    repository.saveAttendance(attendance)
                    _status.value = "Attendance recorded successfully!"

                    // Reset
                    capturedSelfiePath = null
                    capturedLatitude = null
                    capturedLongitude = null

                    // Reload today's attendance
                    loadTodayAttendance()

                } catch (e: Exception) {
                    _status.value = "Error saving: ${e.message}"
                }
            }
        } else {
            _status.value = "Missing data for attendance"
        }
    }

    fun syncAttendanceToServer() {
        viewModelScope.launch {
            try {
                _status.value = "Syncing..."
                repository.syncAttendanceToServer()
                _status.value = "Sync completed!"
                loadTodayAttendance()
            } catch (e: Exception) {
                _status.value = "Sync error: ${e.message}"
            }
        }
    }

    private fun loadTodayAttendance() {
        viewModelScope.launch {
            try {
                val attendance = repository.getTodayAttendance()
                _todayAttendance.value = attendance
            } catch (e: Exception) {
                _status.value = "Error loading attendance: ${e.message}"
            }
        }
    }
}