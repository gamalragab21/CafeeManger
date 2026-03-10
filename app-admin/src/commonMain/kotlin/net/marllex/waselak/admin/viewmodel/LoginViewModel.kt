package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient

class LoginViewModel(
    private val apiClient: AdminApiClient,
) : ViewModel() {

    private val TAG = "LoginVM"

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun updateEmail(value: String) { _email.value = value }
    fun updatePassword(value: String) { _password.value = value }
    fun clearError() { _error.value = null }

    fun login(onSuccess: () -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Please enter email and password"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Logger.i(TAG) { "Attempting login with email=${_email.value.trim()}" }

            val result = apiClient.login(_email.value.trim(), _password.value)

            result.onSuccess { loginResponse ->
                Logger.i(TAG) { "Login successful: ${loginResponse.email}" }
                onSuccess()
            }.onFailure { exception ->
                val errorMsg = exception.message ?: "Unknown error"
                Logger.e(TAG) { "Login failed: $errorMsg" }
                _error.value = errorMsg
            }

            _isLoading.value = false
        }
    }
}
