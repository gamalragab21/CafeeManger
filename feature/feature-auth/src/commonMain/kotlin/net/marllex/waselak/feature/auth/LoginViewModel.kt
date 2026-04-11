package net.marllex.waselak.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.userFriendlyMessage
import net.marllex.waselak.core.common.crash.CrashReporter

class LoginViewModel constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Login" }


    data class UiState(
        val phone: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isLoggedIn: Flow<Boolean> = authRepository.isLoggedIn

    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phone = phone, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login(appType: String, onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Phone number is required") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            authRepository.login(state.phone, state.password, appType)
                .onSuccess { user ->
                    if (isRoleAllowed(user.role, appType)) {
                    AppLogger.i(TAG, "Data loaded successfully")
                        CrashReporter.setUser(user.id, user.name, user.role.name, user.vendorId)
                        CrashReporter.setTag("user.role", user.role.name)
                        CrashReporter.setTag("vendor.id", user.vendorId ?: "none")
                        CrashReporter.setExtra("user.name", user.name ?: "unknown")
                        CrashReporter.setExtra("user.phone", state.phone)
                        CrashReporter.logTransaction("login", "auth")
                        CrashReporter.logUserAction("login_success", "LoginScreen", mapOf("role" to user.role.name, "app" to appType))
                        CrashReporter.captureMessage("User logged in: ${user.name} (${user.role.name})")
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    } else {
                        // Role not allowed for this app — clear session
                        authRepository.logout()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Your account doesn't have permission to access this app",
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    CrashReporter.captureException(throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Login failed. Please try again.",
                        )
                    }
                }
        }
    }

    private fun isRoleAllowed(role: UserRole, appType: String): Boolean {
        // MANAGER role can access all apps
        if (role == UserRole.MANAGER) return true

        return when (appType) {
            "MANAGER" -> false // Only MANAGER role allowed
            "CASHIER" -> role == UserRole.CASHIER
            "DELIVERY" -> role == UserRole.DELIVERY
            "KDS" -> role == UserRole.KITCHEN
            else -> false
        }
    }
}
