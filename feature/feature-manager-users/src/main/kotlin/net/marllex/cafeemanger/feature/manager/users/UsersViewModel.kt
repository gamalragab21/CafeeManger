package net.marllex.cafeemanger.feature.manager.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.UserManagementRepository
import net.marllex.cafeemanger.core.model.User
import net.marllex.cafeemanger.core.model.UserRole
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserManagementRepository,
) : ViewModel() {

    data class UiState(
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
            userRepository.getUsers(_uiState.value.selectedRole)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { users -> _uiState.update { it.copy(users = users, isLoading = false) } }
        }
    }

    fun filterByRole(role: UserRole?) {
        _uiState.update { it.copy(selectedRole = role) }
        loadUsers()
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
            )
        }
    }

    fun deleteUser(id: String) {
        viewModelScope.launch {
            userRepository.deleteUser(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
