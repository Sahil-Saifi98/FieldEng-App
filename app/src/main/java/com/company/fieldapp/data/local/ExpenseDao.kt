package com.company.fieldapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllExpenses(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND userId = :userId")
    suspend fun getExpensesByTrip(tripId: String, userId: String): List<ExpenseEntity>

    // For auto-sync on startup — distinct unsynced trip IDs
    @Query("SELECT DISTINCT tripId FROM expenses WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedTripIds(userId: String): List<String>

    // Fetch all items of a trip (used during sync)
    @Query("SELECT * FROM expenses WHERE tripId = :tripId")
    suspend fun getItemsByTripId(tripId: String): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1, serverId = :serverId WHERE tripId = :tripId")
    suspend fun markTripSynced(tripId: String, serverId: String)

    @Query("UPDATE expenses SET status = :status WHERE tripId = :tripId")
    suspend fun updateTripStatus(tripId: String, status: String)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}