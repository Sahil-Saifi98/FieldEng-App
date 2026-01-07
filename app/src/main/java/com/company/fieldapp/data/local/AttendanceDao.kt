package com.company.fieldapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insert(attendance: AttendanceEntity): Long

    @Query("SELECT * FROM attendance WHERE isSynced = 0")
    suspend fun getUnsyncedAttendance(): List<AttendanceEntity>

    @Query("UPDATE attendance SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("SELECT * FROM attendance WHERE DATE(timestamp/1000, 'unixepoch') = DATE('now') ORDER BY timestamp DESC")
    suspend fun getTodayAttendance(): List<AttendanceEntity>
}