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

class LoginViewModel constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

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

            authRepository.login(state.phone, state.password)
                .onSuccess { user ->
                    if (isRoleAllowed(user.role, appType)) {
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
            else -> false
        }
    }
}
