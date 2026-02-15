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

class UsersViewModel constructor(
    private val userRepository: UserManagementRepository,
) : ViewModel() {

    data class UiState(
        val allUsers: List<User> = emptyList(),
        val users: List<User> = emptyList(),
        val selectedRole: UserRole? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val showAddDialog: Boolean = false,
        val dialogName: String = "",
        val dialogPhone: String = "",
        val dialogEmail: String = "",
        val dialogPassword: String = "",
        val dialogRole: UserRole = UserRole.CASHIER,
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            userRepository.refreshUsers()
            val role = _uiState.value.selectedRole
            val fetched = userRepository.getUsers(role).first()
            _uiState.update { it.copy(allUsers = fetched, users = fetched, isLoading = false) }
        }
    }

    fun filterByRole(role: UserRole?) {
        val all = _uiState.value.allUsers
        val filtered = if (role == null) all else all.filter { it.role == role }
        _uiState.update { it.copy(selectedRole = role, users = filtered) }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                dialogName = "",
                dialogPhone = "",
                dialogEmail = "",
                dialogPassword = "",
                dialogRole = UserRole.CASHIER
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateDialogName(v: String) {
        _uiState.update { it.copy(dialogName = v) }
    }

    fun updateDialogPhone(v: String) {
        _uiState.update { it.copy(dialogPhone = v) }
    }

    fun updateDialogEmail(v: String) {
        _uiState.update { it.copy(dialogEmail = v) }
    }

    fun updateDialogPassword(v: String) {
        _uiState.update { it.copy(dialogPassword = v) }
    }

    fun updateDialogRole(v: UserRole) {
        _uiState.update { it.copy(dialogRole = v) }
    }

    fun saveUser() {
        val s = _uiState.value
        if (s.dialogName.isBlank() || s.dialogPhone.isBlank() || s.dialogPassword.length < 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            userRepository.createUser(
                role = s.dialogRole, name = s.dialogName, phone = s.dialogPhone,
                email = s.dialogEmail.ifBlank { null }, password = s.dialogPassword,
            ).onSuccess {
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
                loadUsers()
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun toggleActive(user: User) {
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
        viewModelScope.launch {
            userRepository.deleteUser(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }.onSuccess { loadUsers() }
        }
    }
}
