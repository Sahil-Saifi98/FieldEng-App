package com.company.fieldapp.data.repository

import com.company.fieldapp.data.local.AttendanceDao
import com.company.fieldapp.data.local.AttendanceEntity
import com.company.fieldapp.data.remote.RetrofitClient
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

class AttendanceRepository(
    private val dao: AttendanceDao
) {

    suspend fun saveAttendance(attendance: AttendanceEntity): Long {
        return dao.insert(attendance)
    }

    suspend fun getTodayAttendance(): List<AttendanceEntity> {
        return dao.getTodayAttendance()
    }

    suspend fun syncAttendanceToServer(): Result<String> {
        return try {
            val unsyncedList = dao.getUnsyncedAttendance()

            if (unsyncedList.isEmpty()) {
                return Result.success("No attendance to sync")
            }

            var successCount = 0
            var failCount = 0

            for (attendance in unsyncedList) {
                try {
                    val selfieFile = File(attendance.selfiePath)
                    if (!selfieFile.exists()) {
                        failCount++
                        continue
                    }

                    val selfiePart = MultipartBody.Part.createFormData(
                        "selfie",
                        selfieFile.name,
                        selfieFile.asRequestBody("image/*".toMediaType())
                    )

                    val response = RetrofitClient.attendanceApi.submitAttendance(
                        selfie = selfiePart,
                        latitude = attendance.latitude.toString().toRequestBody(),
                        longitude = attendance.longitude.toString().toRequestBody(),
                        timestamp = attendance.timestamp.toString().toRequestBody()
                    )

                    if (response.isSuccessful) {
                        dao.markAsSynced(attendance.id)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            if (successCount > 0) {
                Result.success("Synced $successCount attendance records")
            } else {
                Result.failure(Exception("Failed to sync $failCount records"))
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
                        id = 0, // Local ID will be auto-generated
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