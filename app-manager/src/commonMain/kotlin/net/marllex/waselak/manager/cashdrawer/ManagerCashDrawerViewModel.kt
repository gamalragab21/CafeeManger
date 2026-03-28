package net.marllex.waselak.manager.cashdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CashDrawerRepository
import net.marllex.waselak.core.domain.repository.UserManagementRepository
import net.marllex.waselak.core.model.CashDrawerSession
import net.marllex.waselak.core.model.CashMovement
import net.marllex.waselak.core.model.DrawerSummary
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

data class CashierOption(val id: String, val name: String)

class ManagerCashDrawerViewModel(
    private val cashDrawerRepository: CashDrawerRepository,
    private val userRepository: UserManagementRepository,
) : ViewModel() {
    private companion object { private const val TAG = "ManagerCashDrawer" }


    data class UiState(
        val currentSession: CashDrawerSession? = null,
        val summary: DrawerSummary? = null,
        val movements: List<CashMovement> = emptyList(),
        val sessions: List<CashDrawerSession> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedTab: Int = 0, // 0=Current, 1=History
        // Cashier filter
        val cashiers: List<CashierOption> = emptyList(),
        val selectedCashierId: String? = null, // null = all/manager's own
        // Open drawer dialog
        val showOpenDialog: Boolean = false,
        val openingBalance: String = "0",
        val openNotes: String = "",
        // Close drawer dialog
        val showCloseDialog: Boolean = false,
        val closingBalance: String = "",
        val closeNotes: String = "",
        // Movement dialog
        val showMovementDialog: Boolean = false,
        val movementType: String = "CASH_IN",
        val movementAmount: String = "",
        val movementReason: String = "",
        val isSaving: Boolean = false,
    ) {
        val hasOpenSession: Boolean get() = currentSession?.isOpen == true
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCashiers()
        load()
    }

    private fun loadCashiers() {
        viewModelScope.launch {
            try {
                userRepository.getUsers().collect { users ->
                    val cashierList = users
                        .filter { it.role == net.marllex.waselak.core.model.UserRole.CASHIER }
                        .map { CashierOption(id = it.id, name = it.name) }
                    _uiState.update { it.copy(cashiers = cashierList) }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load cashiers", e)
            }
        }
    }

    fun selectCashier(cashierId: String?) {
        _uiState.update { it.copy(selectedCashierId = cashierId) }
        load()
    }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "ManagerCashDrawerViewModel")
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            cashDrawerRepository.getCurrentSession()
                .onSuccess { session ->
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(currentSession = session, movements = session?.movements ?: emptyList(), isLoading = false) }
                }
                .onFailure { _uiState.update { it.copy(currentSession = null, isLoading = false) } }
        }
        viewModelScope.launch {
            cashDrawerRepository.getSummary()
                .onSuccess { s -> _uiState.update { it.copy(summary = s) } }
        }
        viewModelScope.launch {
            val cashierId = _uiState.value.selectedCashierId
            cashDrawerRepository.getSessions(cashierId = cashierId)
                .onSuccess { list -> _uiState.update { it.copy(sessions = list) } }
        }
    }

    fun onTabChange(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }

    // Open drawer
    fun showOpenDialog() { _uiState.update { it.copy(showOpenDialog = true, openingBalance = "0", openNotes = "") } }
    fun dismissOpenDialog() { _uiState.update { it.copy(showOpenDialog = false) } }
    fun onOpeningBalanceChange(v: String) { _uiState.update { it.copy(openingBalance = v) } }
    fun onOpenNotesChange(v: String) { _uiState.update { it.copy(openNotes = v) } }
    fun openDrawer() {
        AppLogger.d(TAG, "openDrawer called")
        val balance = _uiState.value.openingBalance.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            cashDrawerRepository.openDrawer(balance, _uiState.value.openNotes.ifBlank { null })
                .onSuccess { _uiState.update { it.copy(isSaving = false, showOpenDialog = false) }; load() }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    // Close drawer
    fun showCloseDialog() { _uiState.update { it.copy(showCloseDialog = true, closingBalance = "", closeNotes = "") } }
    fun dismissCloseDialog() { _uiState.update { it.copy(showCloseDialog = false) } }
    fun onClosingBalanceChange(v: String) { _uiState.update { it.copy(closingBalance = v) } }
    fun onCloseNotesChange(v: String) { _uiState.update { it.copy(closeNotes = v) } }
    fun closeDrawer() {
        AppLogger.d(TAG, "closeDrawer called")
        val balance = _uiState.value.closingBalance.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            cashDrawerRepository.closeDrawer(balance, _uiState.value.closeNotes.ifBlank { null })
                .onSuccess { _uiState.update { it.copy(isSaving = false, showCloseDialog = false) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    // Movement
    fun showMovementDialog() { _uiState.update { it.copy(showMovementDialog = true, movementType = "CASH_IN", movementAmount = "", movementReason = "") } }
    fun dismissMovementDialog() { _uiState.update { it.copy(showMovementDialog = false) } }
    fun onMovementTypeChange(v: String) { _uiState.update { it.copy(movementType = v) } }
    fun onMovementAmountChange(v: String) { _uiState.update { it.copy(movementAmount = v) } }
    fun onMovementReasonChange(v: String) { _uiState.update { it.copy(movementReason = v) } }
    fun addMovement() {
        AppLogger.d(TAG, "addMovement called")
        val amount = _uiState.value.movementAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            cashDrawerRepository.createMovement(_uiState.value.movementType, amount, _uiState.value.movementReason.ifBlank { null })
                .onSuccess { _uiState.update { it.copy(isSaving = false, showMovementDialog = false) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
