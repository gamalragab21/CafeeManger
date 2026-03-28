package net.marllex.waselak.feature.manager.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.UserManagementRepository
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class UsersViewModel constructor(
    private val userRepository: UserManagementRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Users" }


    data class UiState(
        val allUsers: List<User> = emptyList(),
        val users: List<User> = emptyList(),
        val selectedRole: UserRole? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSaving: Boolean = false,

        // Change Role Dialog
        val showChangeRoleDialog: Boolean = false,
        val changeRoleUser: User? = null,
        val changeRoleSelected: UserRole = UserRole.CASHIER,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        AppLogger.d(TAG, "loadUsers called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            userRepository.refreshUsers()
            val role = _uiState.value.selectedRole
            val fetched = userRepository.getUsers(role).first()
            _uiState.update { it.copy(allUsers = fetched, users = fetched, isLoading = false) }
        }
    }

    fun filterByRole(role: UserRole?) {
        AppLogger.d(TAG, "filterByRole called")
        val all = _uiState.value.allUsers
        val filtered = if (role == null) all else all.filter { it.role == role }
        _uiState.update { it.copy(selectedRole = role, users = filtered) }
    }

    fun toggleActive(user: User) {
        AppLogger.d(TAG, "toggleActive called")
        viewModelScope.launch {
            userRepository.updateUser(
                id = user.id,
                active = !user.active,
                phone = null,
                name = null,
                email = null
            ).onSuccess { loadUsers() }
        }
    }

    fun deleteUser(id: String) {
        AppLogger.d(TAG, "deleteUser called")
        viewModelScope.launch {
            userRepository.deleteUser(id).onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e)
                _uiState.update { it.copy(error = e.message) }
            }.onSuccess { loadUsers() }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─── Change Role Dialog ─────────────────────────────────────

    fun showChangeRoleDialog(user: User) {
        _uiState.update {
            it.copy(
                showChangeRoleDialog = true,
                changeRoleUser = user,
                changeRoleSelected = user.role,
            )
        }
    }

    fun dismissChangeRoleDialog() {
        _uiState.update { it.copy(showChangeRoleDialog = false, changeRoleUser = null) }
    }

    fun updateChangeRoleSelected(role: UserRole) {
        _uiState.update { it.copy(changeRoleSelected = role) }
    }

    fun confirmChangeRole() {
        AppLogger.d(TAG, "confirmChangeRole called")
        val user = _uiState.value.changeRoleUser ?: return
        val newRole = _uiState.value.changeRoleSelected
        if (newRole == user.role) {
            dismissChangeRoleDialog()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            userRepository.updateUser(
                id = user.id,
                name = null,
                phone = null,
                email = null,
                active = null,
                role = newRole.name,
            ).onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                _uiState.update { it.copy(isSaving = false, showChangeRoleDialog = false, changeRoleUser = null) }
                loadUsers()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
