package com.company.fieldapp.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Backend URL - Deployed on Render
    private const val BASE_URL = "https://asap-kc7n.onrender.com/api/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Auth interceptor to add token to requests
    private class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val token = tokenProvider()

            return if (token != null) {
                val newRequest = request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
    }

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(AuthInterceptor { authToken })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val attendanceApi: AttendanceApi = retrofit.create(AttendanceApi::class.java)
    val adminApi: AdminApi = retrofit.create(AdminApi::class.java)
}