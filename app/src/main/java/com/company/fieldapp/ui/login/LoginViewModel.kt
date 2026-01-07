package com.company.fieldapp.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.model.UserDatabase
import com.company.fieldapp.data.session.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)

    private val _employeeId = MutableStateFlow("")
    val employeeId: StateFlow<String> = _employeeId

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess

    fun onEmployeeIdChange(value: String) {
        _employeeId.value = value
        _errorMessage.value = null
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun login() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Simulate network delay
            delay(1000)

            // Authenticate user
            val user = UserDatabase.authenticate(
                employeeId = _employeeId.value.trim(),
                password = _password.value
            )

            if (user != null) {
                // Save session
                sessionManager.saveLoginSession(
                    employeeId = user.employeeId,
                    name = user.name,
                    email = user.email,
                    department = user.department,
                    designation = user.designation
                )

                _loginSuccess.value = true
            } else {
                _errorMessage.value = "Invalid Employee ID or Password"
            }

            _isLoading.value = false
        }
    }
}