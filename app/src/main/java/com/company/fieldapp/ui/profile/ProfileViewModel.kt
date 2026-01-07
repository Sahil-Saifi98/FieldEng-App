package com.company.fieldapp.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.local.AppDatabase
import com.company.fieldapp.data.repository.ProfileRepository
import com.company.fieldapp.data.session.SessionManager
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
    val tasksDone: Int = 1
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProfileRepository
    private val sessionManager = SessionManager(application)

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    private val _statistics = MutableStateFlow(ProfileStatistics())
    val statistics: StateFlow<ProfileStatistics> = _statistics

    init {
        val dao = AppDatabase.getDatabase(application).attendanceDao()
        repository = ProfileRepository(dao)
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
            try {
                val checkIns = repository.getTotalCheckIns()
                _statistics.value = _statistics.value.copy(checkIns = checkIns)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun signOut() {
        sessionManager.logout()
    }
}