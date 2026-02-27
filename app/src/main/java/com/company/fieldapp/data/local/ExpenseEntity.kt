package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val employeeId: String,

    // ── Trip header ───────────────────────────────────────────
    val tripId: String,
    val serverId: String = "",           // MongoDB _id after sync
    val stationVisited: String,
    val periodFrom: String,
    val periodTo: String,
    val advanceAmount: Double = 0.0,

    // ── Expense line ──────────────────────────────────────────
    val expenseType: String,
    val details: String = "",
    val travelFrom: String = "",
    val travelTo: String = "",
    val travelMode: String = "",
    val daysCount: Int = 0,
    val ratePerDay: Double = 0.0,
    val amount: Double,
    val receiptImagePath: String? = null, // local file path
    val receiptUrl: String? = null,       // Cloudinary URL after upload

    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)