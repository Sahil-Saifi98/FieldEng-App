package com.company.fieldapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insert(attendance: AttendanceEntity): Long

    @Update
    suspend fun update(attendance: AttendanceEntity) // ✅ Added update function

    @Query("SELECT * FROM attendance WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedAttendance(userId: String): List<AttendanceEntity>

    @Query("UPDATE attendance SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("SELECT * FROM attendance WHERE userId = :userId AND DATE(timestamp/1000, 'unixepoch') = DATE('now') ORDER BY timestamp DESC")
    suspend fun getTodayAttendance(userId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllAttendance(userId: String): List<AttendanceEntity>

    @Query("DELETE FROM attendance WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}