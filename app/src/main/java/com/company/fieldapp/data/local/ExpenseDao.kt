package com.company.fieldapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    // All expenses for user, newest first
    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllExpenses(userId: String): Flow<List<ExpenseEntity>>

    // Get all items belonging to one trip submission
    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND userId = :userId")
    suspend fun getExpensesByTrip(tripId: String, userId: String): List<ExpenseEntity>

    // Distinct trip IDs for building the grouped list
    @Query("SELECT DISTINCT tripId FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    fun getDistinctTripIds(userId: String): Flow<List<String>>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND status = 'pending'")
    suspend fun getTotalPending(userId: String): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND status = 'approved'")
    suspend fun getTotalApproved(userId: String): Double?

    @Query("UPDATE expenses SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}