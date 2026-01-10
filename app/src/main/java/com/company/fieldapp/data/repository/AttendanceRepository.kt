package com.company.fieldapp.data.repository

import com.company.fieldapp.data.local.AttendanceDao
import com.company.fieldapp.data.local.AttendanceEntity
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.session.SessionManager
import android.content.Context
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

class AttendanceRepository(
    private val dao: AttendanceDao,
    private val context: Context
) {
    private val sessionManager = SessionManager(context)

    suspend fun saveAttendance(attendance: AttendanceEntity): Long {
        return dao.insert(attendance)
    }

    suspend fun getTodayAttendance(): List<AttendanceEntity> {
        val userId = sessionManager.getUserId() ?: return emptyList()
        return dao.getTodayAttendance(userId)
    }

    suspend fun markAsSynced(id: Long) {
        dao.markAsSynced(id)
    }

    suspend fun syncAttendanceToServer(): Result<String> {
        return try {
            val userId = sessionManager.getUserId() ?: return Result.failure(Exception("User not logged in"))
            val unsyncedList = dao.getUnsyncedAttendance(userId)

            if (unsyncedList.isEmpty()) {
                return Result.success("No attendance to sync")
            }

            var successCount = 0
            var failCount = 0
            var errorMessage = ""

            for (attendance in unsyncedList) {
                try {
                    val selfieFile = File(attendance.selfiePath)
                    if (!selfieFile.exists()) {
                        failCount++
                        errorMessage = "Selfie file not found"
                        continue
                    }

                    val selfiePart = MultipartBody.Part.createFormData(
                        "selfie",
                        selfieFile.name,
                        selfieFile.asRequestBody("image/jpeg".toMediaType())
                    )

                    val response = RetrofitClient.attendanceApi.submitAttendance(
                        selfie = selfiePart,
                        latitude = attendance.latitude.toString().toRequestBody("text/plain".toMediaType()),
                        longitude = attendance.longitude.toString().toRequestBody("text/plain".toMediaType()),
                        timestamp = attendance.timestamp.toString().toRequestBody("text/plain".toMediaType())
                    )

                    if (response.isSuccessful && response.body()?.success == true) {
                        dao.markAsSynced(attendance.id)
                        successCount++
                    } else {
                        failCount++
                        errorMessage = response.body()?.message ?: "Server error"
                    }
                } catch (e: Exception) {
                    failCount++
                    errorMessage = e.message ?: "Unknown error"
                }
            }

            if (successCount > 0) {
                Result.success("✅ Synced $successCount records")
            } else {
                Result.failure(Exception("Failed: $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTodayAttendanceFromServer(): Result<List<AttendanceEntity>> {
        return try {
            val response = RetrofitClient.attendanceApi.getTodayAttendance()

            if (response.isSuccessful && response.body() != null) {
                val attendanceList = response.body()!!.data.map { serverData ->
                    AttendanceEntity(
                        id = 0,
                        userId = serverData.userId,
                        employeeId = serverData.employeeId,
                        selfiePath = serverData.selfiePath,
                        latitude = serverData.latitude,
                        longitude = serverData.longitude,
                        timestamp = serverData.timestamp.toLongOrNull() ?: System.currentTimeMillis(),
                        isSynced = true
                    )
                }
                Result.success(attendanceList)
            } else {
                Result.failure(Exception("Failed to fetch attendance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}