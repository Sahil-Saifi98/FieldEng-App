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

    // Standard client for normal API calls
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(AuthInterceptor { authToken })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Extended timeout client specifically for file downloads/exports
    private val downloadClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Less verbose for large downloads
        })
        .addInterceptor(AuthInterceptor { authToken })
        .connectTimeout(60, TimeUnit.SECONDS)      // 1 minute to establish connection
        .readTimeout(10, TimeUnit.MINUTES)         // 10 minutes to read response data
        .writeTimeout(5, TimeUnit.MINUTES)         // 5 minutes to write request data
        .callTimeout(15, TimeUnit.MINUTES)         // 15 minutes total call timeout
        .retryOnConnectionFailure(true)            // Retry on connection failures
        .build()

    // Standard Retrofit instance
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Download Retrofit instance with extended timeouts
    private val downloadRetrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(downloadClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val attendanceApi: AttendanceApi = retrofit.create(AttendanceApi::class.java)

    // Use download client for admin API (handles large file exports)
    val adminApi: AdminApi = downloadRetrofit.create(AdminApi::class.java)
}