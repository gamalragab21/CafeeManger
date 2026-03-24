package net.marllex.waselak.kds.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.AuthRepository

private const val TAG = "KdsProfileVM"

class KdsProfileViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val userRole: String = "",
        val userPhone: String = "",
        val userEmail: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            userName = user.name,
                            userRole = user.role.name,
                            userPhone = user.phone,
                            userEmail = user.email,
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        AppLogger.i(TAG, "User logging out from profile")
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
