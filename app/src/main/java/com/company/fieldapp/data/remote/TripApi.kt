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
    val count: Int,
    val data: List<TripData>
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

    // JSON-only submit (no receipts)
    @POST("trips/submit")
    suspend fun submitTrip(
        @Body request: TripSubmitRequest
    ): Response<TripResponse>

    // Multipart submit (with receipt images)
    // FIX: @PartMap cannot hold MultipartBody.Part values.
    // Use @Part List<MultipartBody.Part> instead — each Part already carries
    // its own field name (set via MultipartBody.Part.createFormData) so Retrofit
    // will forward them correctly as receipt_0, receipt_1, etc.
    @Multipart
    @POST("trips/submit")
    suspend fun submitTripWithReceipts(
        @Part("stationVisited") stationVisited: RequestBody,
        @Part("periodFrom") periodFrom: RequestBody,
        @Part("periodTo") periodTo: RequestBody,
        @Part("advanceAmount") advanceAmount: RequestBody,
        @Part("expenses") expenses: RequestBody,
        @Part receipts: List<MultipartBody.Part>   // ✅ was: @PartMap Map<String, MultipartBody.Part>
    ): Response<TripResponse>

    @GET("trips")
    suspend fun getMyTrips(): Response<TripListResponse>

    @GET("trips/stats")
    suspend fun getTripStats(): Response<TripStatsResponse>

    @GET("trips/{id}")
    suspend fun getTrip(@Path("id") id: String): Response<TripResponse>
}