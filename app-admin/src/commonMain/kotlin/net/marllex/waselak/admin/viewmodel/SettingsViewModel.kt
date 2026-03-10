package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.AdminProfile
import net.marllex.waselak.admin.session.AdminSessionManager

class SettingsViewModel(
    private val apiClient: AdminApiClient,
    private val sessionManager: AdminSessionManager,
) : ViewModel() {
    private val _profile = MutableStateFlow<AdminProfile?>(null)
    val profile: StateFlow<AdminProfile?> = _profile.asStateFlow()

    private val _currentPassword = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }
    fun updateCurrentPassword(v: String) { _currentPassword.value = v }
    fun updateNewPassword(v: String) { _newPassword.value = v }
    fun updateConfirmPassword(v: String) { _confirmPassword.value = v }

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = apiClient.getProfile()
        }
    }

    fun changePassword() {
        if (_newPassword.value.length < 6) {
            _message.value = "Password must be at least 6 characters"
            return
        }
        if (_newPassword.value != _confirmPassword.value) {
            _message.value = "Passwords do not match"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val success = apiClient.changePassword(_currentPassword.value, _newPassword.value)
            if (success) {
                _message.value = "Password changed successfully"
                _currentPassword.value = ""
                _newPassword.value = ""
                _confirmPassword.value = ""
            } else {
                _message.value = "Current password is incorrect"
            }
            _isLoading.value = false
        }
    }

    fun logout(onLogout: () -> Unit) {
        apiClient.clearToken()
        sessionManager.clearToken()
        onLogout()
    }
}
