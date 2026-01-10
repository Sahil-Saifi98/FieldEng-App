package com.company.fieldapp.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.repository.ProfileRepository
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.data.remote.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UserProfile(
    val name: String = "",
    val designation: String = "",
    val employeeId: String = "",
    val email: String = "",
    val department: String = "",
    val initials: String = ""
)

data class ProfileStatistics(
    val checkIns: Int = 0,
    val expenses: Int = 0,
    val tasksDone: Int = 0
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProfileRepository
    private val sessionManager = SessionManager(application)

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    private val _statistics = MutableStateFlow(ProfileStatistics())
    val statistics: StateFlow<ProfileStatistics> = _statistics

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        val dao = AppDatabase.getDatabase(application).attendanceDao()
        repository = ProfileRepository(dao, application)
        loadUserProfile()
        loadStatistics()
    }

    private fun loadUserProfile() {
        val session = sessionManager.getCurrentUser()
        if (session != null) {
            val initials = session.name
                .split(" ")
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .take(2)
                .joinToString("")

            _userProfile.value = UserProfile(
                name = session.name,
                designation = session.designation,
                employeeId = session.employeeId,
                email = session.email,
                department = session.department,
                initials = initials
            )
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Small delay to ensure data is loaded
                delay(300)

                // Try to fetch from backend first
                val response = RetrofitClient.attendanceApi.getAttendanceStats()

                if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                    val stats = response.body()!!.data
                    _statistics.value = ProfileStatistics(
                        checkIns = stats.today,
                        expenses = 0,
                        tasksDone = 0
                    )
                    println("✅ Stats loaded from server: ${stats.today} check-ins")
                } else {
                    println("⚠️ Server stats failed, loading from local DB")
                    loadLocalStatistics()
                }
            } catch (e: Exception) {
                println("❌ Error loading stats: ${e.message}")
                // Fallback to local database
                loadLocalStatistics()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadLocalStatistics() {
        try {
            val checkIns = repository.getTotalCheckIns()
            _statistics.value = _statistics.value.copy(checkIns = checkIns)
            println("✅ Stats loaded from local: $checkIns check-ins")
        } catch (e: Exception) {
            println("❌ Error loading local stats: ${e.message}")
        }
    }

    fun refreshStatistics() {
        loadStatistics()
    }

    fun signOut() {
        sessionManager.logout()
    }
}