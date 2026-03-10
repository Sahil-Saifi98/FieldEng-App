package com.company.fieldapp.data.repository

import android.util.Log
import com.company.fieldapp.data.local.ExpenseDao
import com.company.fieldapp.data.local.ExpenseEntity
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.remote.TripExpenseItemRequest
import com.company.fieldapp.data.remote.TripSubmitRequest
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

// Mirror of AttendanceRepository.SyncResult
data class TripSyncResult(
    val total: Int,       // how many unsynced trips were attempted
    val success: Int,     // how many reached the server
    val failed: Int,      // how many still failed (no network / server error)
    val missingReceipts: Int  // trips that synced but some receipt images were gone
)

class ExpenseRepository(private val dao: ExpenseDao) {

    private val gson = Gson()
    private val TAG = "ExpenseRepo"

    // ── Sync all unsynced trips — called on startup, network restore, manual retry ──
    suspend fun syncAllPendingToServer(userId: String): TripSyncResult {
        val unsyncedTripIds = dao.getUnsyncedTripIds(userId)
        if (unsyncedTripIds.isEmpty()) return TripSyncResult(0, 0, 0, 0)

        var success = 0
        var failed  = 0
        var missingReceipts = 0

        for (tripId in unsyncedTripIds) {
            val items = dao.getItemsByTripId(tripId)
            if (items.isEmpty()) continue

            val first   = items.first()
            val advance = first.advanceAmount

            // Check which items have receipt images still on disk
            val itemsWithMissingFiles = items.filter { item ->
                item.receiptImagePath != null && !File(item.receiptImagePath).exists()
            }
            if (itemsWithMissingFiles.isNotEmpty()) {
                Log.w(TAG, "Trip $tripId: ${itemsWithMissingFiles.size} receipt file(s) missing from disk")
                missingReceipts++
            }

            try {
                val hasReceipts = items.any { it.receiptImagePath != null && File(it.receiptImagePath).exists() }

                val serverId = if (hasReceipts) {
                    submitWithReceipts(first, items, advance)
                } else {
                    submitJsonOnly(first, items, advance)
                }

                if (serverId != null) {
                    dao.markTripSynced(tripId, serverId)
                    Log.d(TAG, "Trip $tripId synced → $serverId")
                    success++
                } else {
                    Log.w(TAG, "Trip $tripId: server returned null id — will retry")
                    failed++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Trip $tripId sync failed: ${e.message}")
                failed++
            }
        }

        return TripSyncResult(
            total           = unsyncedTripIds.size,
            success         = success,
            failed          = failed,
            missingReceipts = missingReceipts
        )
    }

    suspend fun getUnsyncedCount(userId: String): Int =
        dao.getUnsyncedTripIds(userId).size

    // ── JSON-only submission (no receipt images) ──────────────────
    private suspend fun submitJsonOnly(
        first: ExpenseEntity,
        items: List<ExpenseEntity>,
        advance: Double
    ): String? {
        val response = RetrofitClient.tripApi.submitTrip(
            TripSubmitRequest(
                stationVisited = first.stationVisited,
                periodFrom     = first.periodFrom,
                periodTo       = first.periodTo,
                advanceAmount  = advance,
                expenses       = items.map { it.toRequest() }
            )
        )
        return if (response.isSuccessful && response.body()?.success == true)
            response.body()?.data?._id
        else {
            Log.e(TAG, "JSON submit error HTTP ${response.code()}: ${response.body()?.message}")
            null
        }
    }

    // ── Multipart submission (with receipt images) ────────────────
    // Uses expIdx_{n}_ filename prefix so server can map each file
    // to the correct expense slot even in sparse (not every item has receipt) cases.
    private suspend fun submitWithReceipts(
        first: ExpenseEntity,
        items: List<ExpenseEntity>,
        advance: Double
    ): String? {
        val textType = "text/plain".toMediaType()
        val jsonType = "application/json".toMediaType()

        val expensesJson = gson.toJson(items.map { it.toRequest() })

        val receiptParts = mutableListOf<MultipartBody.Part>()
        items.forEachIndexed { index, item ->
            item.receiptImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    receiptParts.add(
                        MultipartBody.Part.createFormData(
                            name     = "receipts",
                            filename = "expIdx_${index}_${System.currentTimeMillis()}.jpg",
                            body     = file.asRequestBody("image/jpeg".toMediaType())
                        )
                    )
                }
            }
        }

        val response = RetrofitClient.tripApi.submitTripWithReceipts(
            stationVisited = first.stationVisited.toRequestBody(textType),
            periodFrom     = first.periodFrom.toRequestBody(textType),
            periodTo       = first.periodTo.toRequestBody(textType),
            advanceAmount  = advance.toString().toRequestBody(textType),
            expenses       = expensesJson.toRequestBody(jsonType),
            receipts       = receiptParts
        )

        return if (response.isSuccessful && response.body()?.success == true)
            response.body()?.data?._id
        else {
            Log.e(TAG, "Multipart submit error HTTP ${response.code()}: ${response.body()?.message}")
            null
        }
    }

    // ── Extension — map entity to API request ─────────────────────
    private fun ExpenseEntity.toRequest() = TripExpenseItemRequest(
        expenseType = expenseType,
        details     = details,
        travelFrom  = travelFrom,
        travelTo    = travelTo,
        travelMode  = travelMode,
        daysCount   = daysCount,
        ratePerDay  = ratePerDay,
        amount      = amount
    )
}