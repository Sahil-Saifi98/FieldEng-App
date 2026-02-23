package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val employeeId: String,
    val category: String,       // Travel, Meals, Lodging, Supplies, Equipment, Other
    val amount: Double,
    val date: String,           // "YYYY-MM-DD"
    val description: String,
    val receiptImagePath: String? = null,
    val status: String = "pending",  // pending, approved, rejected
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)