package net.marllex.waselak.cashier.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class CashierInstallmentsViewModel(
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    private companion object { private const val TAG = "CashierInstallments" }

    data class UiState(
        val plans: List<InstallmentPlan> = emptyList(),
        val selectedPlan: InstallmentPlan? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val showPaymentDialog: Boolean = false,
        val paymentAmount: String = "",
        val paymentNote: String = "",
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load()", TAG)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            installmentRepository.getPlans()
                .onSuccess { list -> _uiState.update { it.copy(plans = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectPlan(plan: InstallmentPlan) {
        viewModelScope.launch {
            installmentRepository.getPlan(plan.id)
                .onSuccess { full -> _uiState.update { it.copy(selectedPlan = full) } }
                .onFailure { _uiState.update { it.copy(selectedPlan = plan) } }
        }
    }

    fun clearSelection() { _uiState.update { it.copy(selectedPlan = null) } }

    fun showPaymentDialog() { _uiState.update { it.copy(showPaymentDialog = true, paymentAmount = "", paymentNote = "") } }
    fun dismissPaymentDialog() { _uiState.update { it.copy(showPaymentDialog = false) } }
    fun onPaymentAmount(v: String) { _uiState.update { it.copy(paymentAmount = v) } }
    fun onPaymentNote(v: String) { _uiState.update { it.copy(paymentNote = v) } }

    fun recordPayment() {
        CrashReporter.addBreadcrumb("recordPayment()", TAG)
        val s = _uiState.value
        val plan = s.selectedPlan ?: return
        val amount = s.paymentAmount.toDoubleOrNull() ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            installmentRepository.recordPayment(plan.id, amount, s.paymentNote.ifBlank { null })
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, showPaymentDialog = false) }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }
}
