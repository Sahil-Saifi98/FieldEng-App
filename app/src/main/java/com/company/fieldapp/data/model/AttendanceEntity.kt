package com.company.fieldapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// ⚠️ IMPORTANT: Column declaration order must match the Room-expected schema exactly.
// Do NOT reorder fields — Room uses declaration order when generating the schema hash.
// Expected order (from migration error): isSynced, address, selfiePath, latitude,
// employeeId, id, userId, longitude, timestamp
// We keep @PrimaryKey(id) first as required by Room, but declare the rest to match.

@Entity(
    tableName = "attendance",
    indices = [
        androidx.room.Index(value = ["isSynced", "userId"], name = "idx_attendance_synced"),
        androidx.room.Index(value = ["userId"], name = "idx_attendance_user")
    ]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val employeeId: String = "",
    val selfiePath: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val timestamp: Long = 0,
    val isSynced: Boolean = false
)