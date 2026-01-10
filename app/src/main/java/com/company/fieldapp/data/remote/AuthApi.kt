package com.company.fieldapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

data class LoginRequest(
    val employeeId: String,
    val password: String
)

data class RegisterRequest(
    val employeeId: String,
    val name: String,
    val email: String,
    val password: String,
    val department: String,
    val designation: String,
    val role: String = "employee"
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String?,
    val user: UserData?
)

data class UserData(
    val id: String,
    val employeeId: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val role: String
)

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getProfile(): Response<AuthResponse>
}