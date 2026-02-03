package com.company.fieldapp.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// Admin Attendance Response
data class AdminAttendanceResponse(
    val success: Boolean,
    val message: String? = null,
    val count: Int,
    val data: List<AdminAttendanceItem>
)

data class UserInfo(
    val _id: String,
    val name: String,
    val employeeId: String,
    val email: String,
    val department: String,
    val designation: String
)

data class AdminAttendanceItem(
    val _id: String,
    val userId: UserInfo,
    val employeeId: String,
    val userName: String? = null,
    val selfiePath: String,
    val selfieUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestamp: String,
    val date: String,
    val checkInTime: String,
    val isSynced: Boolean,
    val createdAt: String
)

// Admin Stats Response
data class AdminStatsResponse(
    val success: Boolean,
    val data: AdminStats
)

data class AdminStats(
    val totalUsers: Int,
    val activeUsers: Int,
    val todayAttendance: Int,
    val monthAttendance: Int,
    val topUsers: List<TopUser>
)

data class TopUser(
    val _id: String,
    val count: Int
)

// Admin Users Response
data class AdminUsersResponse(
    val success: Boolean,
    val count: Int,
    val data: List<AdminUser>
)

data class AdminUser(
    val _id: String,
    val employeeId: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: String
)

interface AdminApi {

    @GET("admin/attendance")
    suspend fun getAllAttendance(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("employeeId") employeeId: String? = null
    ): Response<AdminAttendanceResponse>

    @GET("admin/attendance/{userId}")
    suspend fun getUserAttendance(
        @Path("userId") userId: String
    ): Response<AdminAttendanceResponse>

    @GET("admin/stats")
    suspend fun getAdminStats(): Response<AdminStatsResponse>

    @GET("admin/users")
    suspend fun getAllUsers(): Response<AdminUsersResponse>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(
        @Path("id") userId: String
    ): Response<AuthResponse>

    @PUT("admin/users/{id}/toggle-active")
    suspend fun toggleUserActive(
        @Path("id") userId: String
    ): Response<AuthResponse>

    // Export endpoints that return files
    @Streaming
    @POST("admin/export/user/{userId}")
    suspend fun exportUserDataZip(
        @Path("userId") userId: String,
        @Body requestBody: RequestBody
    ): Response<ResponseBody>

    @Streaming
    @POST("admin/export/all")
    suspend fun exportAllDataZip(
        @Body requestBody: RequestBody
    ): Response<ResponseBody>

    @Streaming
    @GET("admin/export/attendance/csv")
    suspend fun exportAttendanceCSV(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ResponseBody>

    @Streaming
    @GET("admin/export/attendance/pdf")
    suspend fun exportAttendancePDF(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ResponseBody>

    @Streaming
    @GET("admin/export/attendance/json")
    suspend fun exportAttendanceJSON(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<ResponseBody>
}