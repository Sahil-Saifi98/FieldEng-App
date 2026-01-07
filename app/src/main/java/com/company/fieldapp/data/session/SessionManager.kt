package com.company.fieldapp.data.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_NAME = "FieldAppSession"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_EMPLOYEE_ID = "employeeId"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_DESIGNATION = "designation"
    }

    fun saveLoginSession(
        employeeId: String,
        name: String,
        email: String,
        department: String,
        designation: String
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_EMPLOYEE_ID, employeeId)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
            putString(KEY_DEPARTMENT, department)
            putString(KEY_DESIGNATION, designation)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
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

    fun logout() {
        prefs.edit().apply {
            clear()
            apply()
        }
    }

    fun getCurrentUser(): UserSession? {
        if (!isLoggedIn()) return null

        return UserSession(
            employeeId = getEmployeeId() ?: return null,
            name = getName() ?: return null,
            email = getEmail() ?: return null,
            department = getDepartment() ?: return null,
            designation = getDesignation() ?: return null
        )
    }
}

data class UserSession(
    val employeeId: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String
)