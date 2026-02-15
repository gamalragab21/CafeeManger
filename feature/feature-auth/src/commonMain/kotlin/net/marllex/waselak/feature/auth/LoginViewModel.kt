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

    fun login(onSuccess: () -> Unit) {
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
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
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
}
