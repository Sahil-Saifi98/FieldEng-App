package com.company.fieldapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

data class AttendanceResponse(
    val success: Boolean,
    val message: String,
    val data: AttendanceData?
)

data class AttendanceListResponse(
    val success: Boolean,
    val count: Int,
    val data: List<AttendanceData>
)

data class AttendanceData(
    val _id: String,
    val userId: String,
    val employeeId: String,
    val selfiePath: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val date: String,
    val checkInTime: String,
    val isSynced: Boolean,
    val createdAt: String
)

data class AttendanceStatsResponse(
    val success: Boolean,
    val data: AttendanceStats
)

data class AttendanceStats(
    val today: Int,
    val thisMonth: Int,
    val total: Int
)

interface AttendanceApi {

    @Multipart
    @POST("attendance/submit")
    suspend fun submitAttendance(
        @Part selfie: MultipartBody.Part,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("timestamp") timestamp: RequestBody
    ): Response<AttendanceResponse>

    @GET("attendance/today")
    suspend fun getTodayAttendance(): Response<AttendanceListResponse>

    @GET("attendance")
    suspend fun getAttendance(
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?
    ): Response<AttendanceListResponse>

    @GET("attendance/stats")
    suspend fun getAttendanceStats(): Response<AttendanceStatsResponse>

    @DELETE("attendance/{id}")
    suspend fun deleteAttendance(@Path("id") id: String): Response<AttendanceResponse>
}