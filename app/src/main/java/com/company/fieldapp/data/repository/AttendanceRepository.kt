package com.company.fieldapp.data.repository

import android.content.Context
import android.util.Log
import com.company.fieldapp.data.local.AttendanceDao
import com.company.fieldapp.data.local.AttendanceEntity
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.session.SessionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AttendanceRepository(
    private val dao: AttendanceDao,
    private val context: Context
) {
    private val sessionManager = SessionManager(context)

    suspend fun saveAttendance(attendance: AttendanceEntity): Long {
        return dao.insert(attendance)
    }

    suspend fun updateAttendance(attendance: AttendanceEntity) {
        dao.update(attendance)
    }

    suspend fun markAsSynced(id: Long) {
        dao.markAsSynced(id)
    }

    suspend fun getTodayAttendance(): List<AttendanceEntity> {
        val userId = sessionManager.getUserId() ?: return emptyList()
        return dao.getTodayAttendance(userId)
    }

    suspend fun getAllAttendance(): List<AttendanceEntity> {
        val userId = sessionManager.getUserId() ?: return emptyList()
        return dao.getAllAttendance(userId)
    }

    suspend fun getUnsyncedAttendance(userId: String): List<AttendanceEntity> {
        return dao.getUnsyncedAttendance(userId)
    }

    suspend fun getUnsyncedCount(userId: String): Int {
        return dao.getUnsyncedCount(userId)
    }

    /**
     * Syncs ALL pending records regardless of date (today, yesterday, older).
     * Returns SyncResult with counts so the ViewModel can show precise feedback.
     */
    suspend fun syncAllPendingToServer(): SyncResult {
        val userId = sessionManager.getUserId()
            ?: return SyncResult(total = 0, success = 0, failed = 0, errorMessage = "User not logged in")

        val unsyncedList = dao.getUnsyncedAttendance(userId)

        if (unsyncedList.isEmpty()) {
            return SyncResult(total = 0, success = 0, failed = 0)
        }

        var successCount = 0
        var failCount = 0
        var missingFileCount = 0

        for (record in unsyncedList) {
            try {
                val selfieFile = File(record.selfiePath)

                if (!selfieFile.exists()) {
                    // File deleted by OS (cache cleared / reinstall).
                    // Mark synced with a note so it never blocks the queue again.
                    Log.w("AttendanceRepo", "Selfie missing for record ${record.id} (${record.timestamp}) — clearing from queue")
                    dao.markSyncedWithAddress(
                        record.id,
                        record.address.ifBlank { "Check-in at ${record.latitude}, ${record.longitude}" }
                    )
                    missingFileCount++
                    continue
                }

                val selfiePart = MultipartBody.Part.createFormData(
                    "selfie",
                    "selfie_${record.timestamp}.jpg",
                    selfieFile.asRequestBody("image/jpeg".toMediaType())
                )

                val response = RetrofitClient.attendanceApi.submitAttendance(
                    selfie = selfiePart,
                    latitude = record.latitude.toString().toRequestBody("text/plain".toMediaType()),
                    longitude = record.longitude.toString().toRequestBody("text/plain".toMediaType()),
                    timestamp = record.timestamp.toString().toRequestBody("text/plain".toMediaType())
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val address = response.body()!!.data?.address
                    if (!address.isNullOrBlank()) {
                        dao.markSyncedWithAddress(record.id, address)
                    } else {
                        dao.markAsSynced(record.id)
                    }
                    successCount++
                    Log.d("AttendanceRepo", "Synced record ${record.id} (timestamp: ${record.timestamp})")
                } else {
                    val code = response.code()
                    // 401 = token not ready yet — leave pending, do NOT count as failure
                    if (code != 401) failCount++
                    Log.w("AttendanceRepo", "Failed record ${record.id}: HTTP $code — ${response.body()?.message}")
                }

            } catch (e: Exception) {
                failCount++
                Log.e("AttendanceRepo", "Exception syncing record ${record.id}: ${e.message}")
            }
        }

        return SyncResult(
            total = unsyncedList.size,
            success = successCount,
            failed = failCount,
            missingFiles = missingFileCount
        )
    }

    // Kept for backward compat with the manual sync button
    suspend fun syncAttendanceToServer(): Result<String> {
        return try {
            val result = syncAllPendingToServer()
            when {
                result.total == 0 -> Result.success("No attendance to sync")
                result.success > 0 -> Result.success("✅ Synced ${result.success} record(s)")
                else -> Result.failure(Exception("Sync failed — check connection and try again"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTodayAttendanceFromServer(): Result<List<AttendanceEntity>> {
        return try {
            val response = RetrofitClient.attendanceApi.getTodayAttendance()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.data.map { serverData ->
                    AttendanceEntity(
                        id = 0,
                        userId = serverData.userId,
                        employeeId = serverData.employeeId,
                        selfiePath = serverData.selfiePath,
                        latitude = serverData.latitude,
                        longitude = serverData.longitude,
                        address = serverData.address,
                        timestamp = serverData.timestamp.toLongOrNull() ?: System.currentTimeMillis(),
                        isSynced = true
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch attendance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class SyncResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val missingFiles: Int = 0,
    val errorMessage: String? = null
)