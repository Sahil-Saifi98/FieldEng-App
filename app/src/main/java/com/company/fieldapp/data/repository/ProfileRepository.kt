package com.company.fieldapp.data.repository

import com.company.fieldapp.data.local.AttendanceDao

class ProfileRepository(
    private val attendanceDao: AttendanceDao
) {

    suspend fun getTotalCheckIns(): Int {
        return try {
            attendanceDao.getTodayAttendance().size
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