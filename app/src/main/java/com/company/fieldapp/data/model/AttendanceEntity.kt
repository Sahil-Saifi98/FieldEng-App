package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val selfiePath: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isSynced: Boolean = false
)