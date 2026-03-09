package com.company.fieldapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// ── Request models ────────────────────────────────────────────────

data class TripExpenseItemRequest(
    val expenseType: String,
    val details: String = "",
    val travelFrom: String = "",
    val travelTo: String = "",
    val travelMode: String = "",
    val daysCount: Int = 0,
    val ratePerDay: Double = 0.0,
    val amount: Double
)

data class TripSubmitRequest(
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double,
    val expenses: List<TripExpenseItemRequest>
)

// ── Response models ───────────────────────────────────────────────

data class TripExpenseItemData(
    val expenseType: String,
    val details: String,
    val travelFrom: String,
    val travelTo: String,
    val travelMode: String,
    val daysCount: Int,
    val ratePerDay: Double,
    val amount: Double,
    val receiptUrl: String?
)

data class TripData(
    val _id: String,
    val userId: String,
    val employeeId: String,
    val employeeName: String,
    val designation: String?,
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double,
    val expenses: List<TripExpenseItemData>,
    val totalAmount: Double,
    val payableAmount: Double,
    val status: String,
    val adminNote: String?,
    val createdAt: String
)

data class TripResponse(
    val success: Boolean,
    val message: String,
    val data: TripData?
)

data class TripListResponse(
    val success: Boolean,
    val message: String = "",
    val count: Int = 0,
    val data: List<TripData> = emptyList()
)

data class TripStatsData(
    val totalPending: Double,
    val totalApproved: Double,
    val tripCount: Int
)

data class TripStatsResponse(
    val success: Boolean,
    val data: TripStatsData
)

// ── API interface ─────────────────────────────────────────────────

interface TripApi {

    // Employee routes
    @POST("trips/submit")
    suspend fun submitTrip(@Body request: TripSubmitRequest): Response<TripResponse>

    @Multipart
    @POST("trips/submit")
    suspend fun submitTripWithReceipts(
        @Part("stationVisited") stationVisited: RequestBody,
        @Part("periodFrom")     periodFrom: RequestBody,
        @Part("periodTo")       periodTo: RequestBody,
        @Part("advanceAmount")  advanceAmount: RequestBody,
        @Part("expenses")       expenses: RequestBody,
        @Part receipts: List<MultipartBody.Part>
    ): Response<TripResponse>

    @GET("trips")
    suspend fun getMyTrips(): Response<TripListResponse>

    @GET("trips/stats")
    suspend fun getTripStats(): Response<TripStatsResponse>

    @GET("trips/{id}")
    suspend fun getTrip(@Path("id") id: String): Response<TripResponse>

    // Admin routes
    @GET("trips/admin/all")
    suspend fun getAdminAllTrips(
        @Query("status")     status: String? = null,
        @Query("employeeId") employeeId: String? = null,
        @Query("page")       page: Int = 1,
        @Query("limit")      limit: Int = 100
    ): Response<TripListResponse>

    @PUT("trips/admin/{id}/status")
    suspend fun updateTripStatus(
        @Path("id") tripId: String,
        @Body body: Map<String, String>
    ): Response<TripResponse>
}