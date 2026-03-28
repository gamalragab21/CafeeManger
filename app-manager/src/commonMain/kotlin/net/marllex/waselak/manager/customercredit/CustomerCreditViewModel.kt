package net.marllex.waselak.manager.customercredit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CustomerCreditRepository
import net.marllex.waselak.core.model.CreditTransaction
import net.marllex.waselak.core.model.CustomerCredit
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class CustomerCreditViewModel(
    private val customerCreditRepository: CustomerCreditRepository,
) : ViewModel() {
    private companion object { private const val TAG = "CustomerCredit" }


    data class UiState(
        val debtors: List<CustomerCredit> = emptyList(),
        val selectedCredit: CustomerCredit? = null,
        val transactions: List<CreditTransaction> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Set limit dialog
        val showLimitDialog: Boolean = false,
        val newLimit: String = "",
        val isSaving: Boolean = false,
    ) {
        val totalDebt: Double get() = debtors.sumOf { it.balance }
        val debtorCount: Int get() = debtors.count { it.hasDebt }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "CustomerCreditViewModel")
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = _uiState.value.debtors.isEmpty(), error = null) }
            customerCreditRepository.getDebtors()
                .onSuccess { list -> _uiState.update { it.copy(debtors = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectCustomer(credit: CustomerCredit) {
        AppLogger.d(TAG, "selectCustomer called")
        _uiState.update { it.copy(selectedCredit = credit) }
        viewModelScope.launch {
            customerCreditRepository.getTransactions(credit.customerId)
                .onSuccess { list -> _uiState.update { it.copy(transactions = list) } }
        }
    }

    fun clearSelection() { _uiState.update { it.copy(selectedCredit = null, transactions = emptyList()) } }

    // Set credit limit
    fun showLimitDialog() {
        _uiState.update { it.copy(showLimitDialog = true, newLimit = _uiState.value.selectedCredit?.creditLimit?.toString() ?: "") }
    }
    fun dismissLimitDialog() { _uiState.update { it.copy(showLimitDialog = false) } }
    fun onNewLimitChange(v: String) { _uiState.update { it.copy(newLimit = v) } }

    fun setCreditLimit() {
        val credit = _uiState.value.selectedCredit ?: return
        val limit = _uiState.value.newLimit.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            customerCreditRepository.setCreditLimit(credit.customerId, limit)
                .onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(isSaving = false, showLimitDialog = false) }
                    selectCustomer(credit)
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
