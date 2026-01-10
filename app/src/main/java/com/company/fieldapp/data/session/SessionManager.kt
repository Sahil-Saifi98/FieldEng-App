package com.company.fieldapp.data.session

import android.content.Context
import android.content.SharedPreferences
import com.company.fieldapp.data.remote.RetrofitClient

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_NAME = "FieldAppSession"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "userId"
        private const val KEY_EMPLOYEE_ID = "employeeId"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_DESIGNATION = "designation"
        private const val KEY_ROLE = "role"
    }

    fun saveLoginSession(
        token: String,
        userId: String,
        employeeId: String,
        name: String,
        email: String,
        department: String,
        designation: String,
        role: String = "employee"
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_EMPLOYEE_ID, employeeId)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
            putString(KEY_DEPARTMENT, department)
            putString(KEY_DESIGNATION, designation)
            putString(KEY_ROLE, role)
            apply()
        }

        // Set token in Retrofit client
        RetrofitClient.setAuthToken(token)
    }

    fun isLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            // Set token in Retrofit client when app starts
            val token = getToken()
            RetrofitClient.setAuthToken(token)
        }
        return isLoggedIn
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getEmployeeId(): String? {
        return prefs.getString(KEY_EMPLOYEE_ID, null)
    }

    fun getName(): String? {
        return prefs.getString(KEY_NAME, null)
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun getDepartment(): String? {
        return prefs.getString(KEY_DEPARTMENT, null)
    }

    fun getDesignation(): String? {
        return prefs.getString(KEY_DESIGNATION, null)
    }

    fun getRole(): String? {
        return prefs.getString(KEY_ROLE, null)
    }

    fun logout() {
        prefs.edit().apply {
            clear()
            apply()
        }
        // Clear token from Retrofit client
        RetrofitClient.setAuthToken(null)
    }

    fun getCurrentUser(): UserSession? {
        if (!isLoggedIn()) return null

        return UserSession(
            userId = getUserId() ?: return null,
            employeeId = getEmployeeId() ?: return null,
            name = getName() ?: return null,
            email = getEmail() ?: return null,
            department = getDepartment() ?: return null,
            designation = getDesignation() ?: return null,
            role = getRole() ?: "employee"
        )
    }
}

data class UserSession(
    val userId: String,
    val employeeId: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val role: String
)