package com.company.fieldapp.data.repository

import android.content.Context
import com.company.fieldapp.data.local.AttendanceDao
import com.company.fieldapp.data.session.SessionManager

class ProfileRepository(
    private val attendanceDao: AttendanceDao,
    private val context: Context
) {
    private val sessionManager = SessionManager(context)

    suspend fun getTotalCheckIns(): Int {
        return try {
            val userId = sessionManager.getUserId() ?: return 0
            attendanceDao.getTodayAttendance(userId).size
        } catch (e: Exception) {
            0
        }
    }

    // Can add more methods for expenses and tasks when those modules are implemented
    suspend fun getTotalExpenses(): Int {
        // TODO: Implement when expense module is ready
        return 0
    }

    suspend fun getTotalTasksDone(): Int {
        // TODO: Implement when task module is ready
        return 1 // Default value as shown in design
    }
}