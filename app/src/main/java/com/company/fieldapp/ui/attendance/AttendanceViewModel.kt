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
import kotlinx.coroutines.delay
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

    // How many records are still pending — shown as a badge in the UI if > 0
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private var capturedSelfiePath: String? = null
    private var capturedLatitude: Double? = null
    private var capturedLongitude: Double? = null

    private val sessionManager = com.company.fieldapp.data.session.SessionManager(application)

    init {
        val dao = AppDatabase.getDatabase(application).attendanceDao()
        repository = AttendanceRepository(dao, application)
        loadTodayAttendance()
        // Delay startup sync slightly to allow SessionManager to fully restore token
        // before any network calls fire (avoids 401 race on cold start)
        syncUnsyncedOnStartup(delayMs = 600)
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

        if (selfiePath == null || lat == null || lng == null) {
            _status.value = "Missing data for attendance"
            Log.e("AttendanceVM", "Missing: selfie=$selfiePath lat=$lat lng=$lng")
            return
        }

        // Reset immediately so a second trigger can't reuse stale data
        capturedSelfiePath = null
        capturedLatitude = null
        capturedLongitude = null

        viewModelScope.launch {
            var localId = -1L
            try {
                val timestamp = System.currentTimeMillis()
                val userId = sessionManager.getUserId() ?: ""
                val employeeId = sessionManager.getEmployeeId() ?: ""

                // Always save locally first — attendance is never lost even if network fails
                val attendance = AttendanceEntity(
                    userId = userId,
                    employeeId = employeeId,
                    selfiePath = selfiePath,
                    latitude = lat,
                    longitude = lng,
                    address = "Location: $lat, $lng",
                    timestamp = timestamp,
                    isSynced = false
                )
                localId = repository.saveAttendance(attendance)
                Log.d("AttendanceVM", "Saved locally — id=$localId")

                // Now attempt to upload
                _status.value = "Uploading to server..."

                val selfieFile = File(selfiePath)
                if (!selfieFile.exists()) {
                    // File was already moved/deleted — keep as pending for manual sync
                    _status.value = "⚠️ Selfie file missing — saved locally, tap Sync to retry"
                    Log.e("AttendanceVM", "File not found: $selfiePath")
                    refreshPendingCount()
                    loadTodayAttendance()
                    return@launch
                }

                val selfiePart = MultipartBody.Part.createFormData(
                    "selfie",
                    "selfie_${timestamp}.jpg",
                    selfieFile.asRequestBody("image/jpeg".toMediaType())
                )

                val response = RetrofitClient.attendanceApi.submitAttendance(
                    selfie = selfiePart,
                    latitude = lat.toString().toRequestBody("text/plain".toMediaType()),
                    longitude = lng.toString().toRequestBody("text/plain".toMediaType()),
                    timestamp = timestamp.toString().toRequestBody("text/plain".toMediaType())
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val serverAddress = response.body()!!.data?.address
                    val updated = attendance.copy(
                        id = localId,
                        address = serverAddress ?: attendance.address,
                        isSynced = true
                    )
                    repository.updateAttendance(updated)
                    _status.value = "✅ Attendance recorded successfully!"
                    Log.d("AttendanceVM", "Upload success — address: $serverAddress")
                } else {
                    // Server error — record stays pending, sync will retry
                    val code = response.code()
                    val msg = response.body()?.message ?: "HTTP $code"
                    _status.value = "⚠️ Upload failed ($msg) — saved locally, tap Sync to retry"
                    Log.e("AttendanceVM", "Upload failed: $msg")
                }

            } catch (e: Exception) {
                // Network exception — local record already saved, sync will retry
                _status.value = if (localId > 0)
                    "⚠️ Network error — saved locally, tap Sync to retry"
                else
                    "⚠️ Error: ${e.message}"
                Log.e("AttendanceVM", "saveAttendance exception: ${e.message}", e)
            } finally {
                refreshPendingCount()
                loadTodayAttendance()
            }
        }
    }

    /**
     * Manual sync triggered by the Sync button.
     * Syncs ALL pending records — today, yesterday, and any older ones.
     */
    fun syncAttendanceToServer() {
        viewModelScope.launch {
            _status.value = "Syncing..."
            try {
                val result = repository.syncAllPendingToServer()
                _status.value = when {
                    result.total == 0 -> "✅ All attendance is up to date"
                    result.success == result.total -> "✅ Synced all ${result.success} record(s)"
                    result.success > 0 -> "✅ Synced ${result.success} / ${result.total} — ${result.failed} failed"
                    else -> "⚠️ Sync failed — check connection and try again"
                }
            } catch (e: Exception) {
                _status.value = "Sync error: ${e.message}"
                Log.e("AttendanceVM", "syncAttendanceToServer exception: ${e.message}", e)
            } finally {
                refreshPendingCount()
                loadTodayAttendance()
            }
        }
    }

    /**
     * Auto-sync on startup — handles:
     *  • Records from the current session that failed due to network issues
     *  • Records from yesterday or older sessions (e.g. app was offline all day)
     *  • 401 race condition: waits [delayMs] before firing so token is ready
     */
    private fun syncUnsyncedOnStartup(delayMs: Long = 600) {
        viewModelScope.launch {
            try {
                delay(delayMs)

                val userId = sessionManager.getUserId() ?: return@launch
                val count = repository.getUnsyncedCount(userId)

                if (count == 0) {
                    Log.d("AttendanceVM", "Startup sync: nothing pending")
                    return@launch
                }

                Log.d("AttendanceVM", "Startup sync: $count pending record(s) — syncing all dates")
                _status.value = "Syncing $count pending record(s)..."

                val result = repository.syncAllPendingToServer()

                _status.value = when {
                    result.success == 0 && result.failed == 0 ->
                        // All were missing-file entries — queue cleared silently
                        "Ready to check in"
                    result.failed == 0 ->
                        "✅ Synced ${result.success} pending record(s)"
                    result.success > 0 ->
                        "⚠️ Synced ${result.success}, ${result.failed} still pending — tap Sync to retry"
                    else ->
                        // Nothing synced but don't alarm user on startup — they'll see pending badge
                        "Ready to check in"
                }

                refreshPendingCount()
                loadTodayAttendance()

            } catch (e: Exception) {
                // Silent fail on startup — pending badge will still show
                Log.e("AttendanceVM", "Startup sync error: ${e.message}")
            }
        }
    }

    private fun loadTodayAttendance() {
        viewModelScope.launch {
            try {
                val attendance = repository.getTodayAttendance()
                _todayAttendance.value = attendance
                Log.d("AttendanceVM", "Loaded ${attendance.size} today's records")
            } catch (e: Exception) {
                Log.e("AttendanceVM", "loadTodayAttendance error: ${e.message}", e)
            }
        }
    }

    private fun refreshPendingCount() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: return@launch
                _pendingCount.value = repository.getUnsyncedCount(userId)
            } catch (e: Exception) {
                Log.e("AttendanceVM", "refreshPendingCount error: ${e.message}")
            }
        }
    }
}