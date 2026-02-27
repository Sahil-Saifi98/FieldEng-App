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

    // Multipart submit (with receipt images).
    // All receipt files use field name "receipts" to satisfy Multer's
    // uploadExpense.array('receipts', 10). The expense array index is
    // encoded in each file's filename as "expIdx_{n}_{timestamp}.jpg"
    // so the server can map receipts to the correct expense slot even
    // when not every expense has a receipt attached.
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
}