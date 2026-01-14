package com.company.fieldapp.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldapp.data.remote.LoginRequest
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.session.SessionManager
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

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

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

            try {
                // Call backend API
                val response = RetrofitClient.authApi.login(
                    LoginRequest(
                        employeeId = _employeeId.value.trim(),
                        password = _password.value
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!

                    if (authResponse.success && authResponse.user != null && authResponse.token != null) {
                        // Save session
                        sessionManager.saveLoginSession(
                            token = authResponse.token,
                            userId = authResponse.user.id,
                            employeeId = authResponse.user.employeeId,
                            name = authResponse.user.name,
                            email = authResponse.user.email,
                            department = authResponse.user.department,
                            designation = authResponse.user.designation,
                            role = authResponse.user.role
                        )

                        _isAdmin.value = authResponse.user.role == "admin"
                        _loginSuccess.value = true
                    } else {
                        _errorMessage.value = authResponse.message ?: "Login failed"
                    }
                } else {
                    _errorMessage.value = "Invalid Employee ID or Password"
                }
            } catch (e: Exception) {
                _errorMessage.value = when {
                    e.message?.contains("Unable to resolve host") == true ->
                        "Cannot connect to server. Check your internet connection."
                    e.message?.contains("timeout") == true ->
                        "Connection timeout. Please try again."
                    else ->
                        "Error: ${e.message ?: "Something went wrong"}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}