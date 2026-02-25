package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val employeeId: String,

    // ── Trip header (shared across items submitted together) ──
    val tripId: String,              // UUID — groups items from same submission
    val stationVisited: String,      // Place/destination
    val periodFrom: String,          // "DD-MM-YYYY"
    val periodTo: String,            // "DD-MM-YYYY"
    val advanceAmount: Double = 0.0, // Advance/Imprest taken

    // ── Expense line ──────────────────────────────────────────
    val expenseType: String,         // Hotel, Travel, DailyAllowance, LocalConveyance, Other
    val details: String = "",        // description / details
    val travelFrom: String = "",     // Travel specific
    val travelTo: String = "",
    val travelMode: String = "",     // Air / Train / Bus
    val daysCount: Int = 0,          // Daily allowance
    val ratePerDay: Double = 0.0,
    val amount: Double,
    val receiptImagePath: String? = null,

    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)