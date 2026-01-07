package com.company.fieldapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class AttendanceResponse(
    val success: Boolean,
    val message: String
)

interface AttendanceApi {

    @Multipart
    @POST("attendance/submit")
    suspend fun submitAttendance(
        @Part selfie: MultipartBody.Part,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("userId") userId: RequestBody
    ): Response<AttendanceResponse>
}