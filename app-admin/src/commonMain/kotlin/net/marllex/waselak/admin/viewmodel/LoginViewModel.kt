package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.util.UiMessage
import waselak.app_admin.generated.resources.Res
import waselak.app_admin.generated.resources.*

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

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    fun updateEmail(value: String) { _email.value = value }
    fun updatePassword(value: String) { _password.value = value }
    fun clearError() { _error.value = null }

    fun login(onSuccess: () -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = UiMessage.Resource(Res.string.enter_email_password)
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
                Logger.e(TAG) { "Login failed: ${exception.message}" }
                _error.value = if (exception.message != null) UiMessage.Text(exception.message!!) else UiMessage.Resource(Res.string.unknown_error)
            }

            _isLoading.value = false
        }
    }
}
