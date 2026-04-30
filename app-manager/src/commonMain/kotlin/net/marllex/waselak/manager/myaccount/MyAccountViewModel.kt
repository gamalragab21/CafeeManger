package net.marllex.waselak.manager.myaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.SetOverridePinRequest

/**
 * Powers [MyAccountScreen]. Tracks identity (from AuthRepository) and drives the
 * Override PIN set/change dialog by calling `POST /api/v1/users/me/override-pin`.
 * The response returns `overridePinSet = true` on success; we flip local state
 * accordingly so the button label switches from "Set" → "Change" on the next
 * open without needing to re-fetch the user.
 */
class MyAccountViewModel(
    private val api: WaselakApiClient,
    authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val userName: String = "",
        val userPhone: String = "",
        /**
         * True when the logged-in manager has an override PIN on file. We don't have
         * a dedicated "fetch my pin status" endpoint yet, so we initialise to false
         * and let the user discover whether they've set one — the action is always
         * idempotent (a new PIN overwrites any existing hash).
         */
        val pinSet: Boolean = false,
        val submitting: Boolean = false,
        val dialogError: String? = null,
        val lastSuccessMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Reflect current user info so the header card shows name + phone.
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(userName = user.name, userPhone = user.phone)
                    }
                }
            }
        }
    }

    fun setOverridePin(currentPassword: String, pin: String, onDone: (success: Boolean) -> Unit = {}) {
        _uiState.update { it.copy(submitting = true, dialogError = null) }
        viewModelScope.launch {
            try {
                val resp = api.setMyOverridePin(SetOverridePinRequest(currentPassword, pin))
                _uiState.update {
                    it.copy(
                        submitting = false,
                        pinSet = resp.overridePinSet,
                        dialogError = null,
                        lastSuccessMessage = "تم حفظ رمز الخصم",
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        submitting = false,
                        dialogError = humanize(e),
                    )
                }
                onDone(false)
            }
        }
    }

    fun clearDialogState() {
        _uiState.update { it.copy(dialogError = null) }
    }

    /** Map Ktor/server error messages to something a manager can read. */
    private fun humanize(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("Wrong password", ignoreCase = true) -> "كلمة السر الحالية غير صحيحة"
            raw.contains("PIN must be", ignoreCase = true) -> "الرمز يجب أن يكون من 4 إلى 6 أرقام"
            raw.contains("Only managers", ignoreCase = true) -> "هذه الميزة متاحة للمديرين فقط"
            raw.isBlank() -> "حدث خطأ، حاول مرة أخرى"
            else -> raw
        }
    }
}
