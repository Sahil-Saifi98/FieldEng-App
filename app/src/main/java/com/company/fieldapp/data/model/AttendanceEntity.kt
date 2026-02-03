package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "", // Add userId to filter by user
    val employeeId: String = "", // Add employeeId
    val selfiePath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val timestamp: Long,
    val isSynced: Boolean = false
)