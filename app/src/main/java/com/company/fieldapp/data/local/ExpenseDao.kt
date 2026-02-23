package com.company.fieldapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllExpenses(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedExpenses(userId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND status = 'pending'")
    fun getPendingExpenses(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND status = 'pending'")
    suspend fun getTotalPending(userId: String): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND status = 'approved'")
    suspend fun getTotalApproved(userId: String): Double?

    @Query("UPDATE expenses SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}