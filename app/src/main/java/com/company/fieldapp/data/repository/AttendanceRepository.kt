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

    suspend fun syncAttendanceToServer() {
        val unsyncedList = dao.getUnsyncedAttendance()

        for (attendance in unsyncedList) {
            try {
                val selfieFile = File(attendance.selfiePath)
                if (!selfieFile.exists()) continue

                val selfiePart = MultipartBody.Part.createFormData(
                    "selfie",
                    selfieFile.name,
                    selfieFile.asRequestBody("image/*".toMediaType())
                )

                val response = RetrofitClient.attendanceApi.submitAttendance(
                    selfie = selfiePart,
                    latitude = attendance.latitude.toString().toRequestBody(),
                    longitude = attendance.longitude.toString().toRequestBody(),
                    timestamp = attendance.timestamp.toString().toRequestBody(),
                    userId = "EMP-2024-001".toRequestBody()
                )

                if (response.isSuccessful) {
                    dao.markAsSynced(attendance.id)
                }
            } catch (e: Exception) {
                // retry later
            }
        }
    }
}