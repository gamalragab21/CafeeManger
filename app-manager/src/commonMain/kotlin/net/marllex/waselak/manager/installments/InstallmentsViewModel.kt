package net.marllex.waselak.manager.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.InstallmentAnalytics
import net.marllex.waselak.core.model.InstallmentPayment
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class InstallmentsViewModel(
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Installments" }

    data class UiState(
        val plans: List<InstallmentPlan> = emptyList(),
        val analytics: InstallmentAnalytics? = null,
        val selectedPlan: InstallmentPlan? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedTab: Int = 0,
        val statusFilter: String? = null,
        // Create plan dialog
        val showCreateDialog: Boolean = false,
        val createCustomerId: String = "",
        val createCustomerName: String = "",
        val createTotalAmount: String = "",
        val createDownPayment: String = "0",
        val createMonths: String = "3",
        val createLateFeePercent: String = "0",
        val isCreating: Boolean = false,
        // Record payment dialog
        val showPaymentDialog: Boolean = false,
        val paymentAmount: String = "",
        val paymentNote: String = "",
        val isSaving: Boolean = false,
        // Status update
        val showStatusDialog: InstallmentPlan? = null,
        val successMessage: String? = null,
    ) {
        val filteredPlans: List<InstallmentPlan>
            get() = if (statusFilter == null) plans
            else plans.filter { it.status == statusFilter }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load()", TAG)
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            installmentRepository.getPlans()
                .onSuccess { list ->
                    AppLogger.i(TAG, "Loaded ${list.size} plans")
                    _uiState.update { it.copy(plans = list, isLoading = false) }
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e)
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
        viewModelScope.launch {
            installmentRepository.getAnalytics()
                .onSuccess { a -> _uiState.update { it.copy(analytics = a) } }
        }
    }

    fun onStatusFilter(status: String?) {
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun selectPlan(plan: InstallmentPlan) {
        CrashReporter.addBreadcrumb("selectPlan(${plan.id})", TAG)
        viewModelScope.launch {
            installmentRepository.getPlan(plan.id)
                .onSuccess { full -> _uiState.update { it.copy(selectedPlan = full) } }
                .onFailure { _uiState.update { it.copy(selectedPlan = plan) } }
        }
    }

    fun clearSelection() { _uiState.update { it.copy(selectedPlan = null) } }
    fun clearSuccessMessage() { _uiState.update { it.copy(successMessage = null) } }

    // ── Create Plan ──

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createCustomerId = "", createCustomerName = "",
                createTotalAmount = "", createDownPayment = "0",
                createMonths = "3", createLateFeePercent = "0",
            )
        }
    }

    fun dismissCreateDialog() { _uiState.update { it.copy(showCreateDialog = false) } }
    fun onCreateCustomerId(v: String) { _uiState.update { it.copy(createCustomerId = v) } }
    fun onCreateCustomerName(v: String) { _uiState.update { it.copy(createCustomerName = v) } }
    fun onCreateTotalAmount(v: String) { _uiState.update { it.copy(createTotalAmount = v) } }
    fun onCreateDownPayment(v: String) { _uiState.update { it.copy(createDownPayment = v) } }
    fun onCreateMonths(v: String) { _uiState.update { it.copy(createMonths = v) } }
    fun onCreateLateFeePercent(v: String) { _uiState.update { it.copy(createLateFeePercent = v) } }

    fun createPlan() {
        CrashReporter.addBreadcrumb("createPlan()", TAG)
        val s = _uiState.value
        val total = s.createTotalAmount.toDoubleOrNull() ?: return
        val down = s.createDownPayment.toDoubleOrNull() ?: 0.0
        val months = s.createMonths.toIntOrNull() ?: return
        val fee = s.createLateFeePercent.toDoubleOrNull() ?: 0.0
        if (s.createCustomerId.isBlank() || total <= 0 || months <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            installmentRepository.createPlan(
                customerId = s.createCustomerId,
                totalAmount = total,
                numInstallments = months,
                downPayment = down,
                lateFeePercent = fee,
            )
                .onSuccess {
                    AppLogger.i(TAG, "Plan created successfully")
                    _uiState.update { it.copy(isCreating = false, showCreateDialog = false, successMessage = "plan_created") }
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isCreating = false, error = e.message) }
                }
        }
    }

    // ── Record Payment ──

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
                    AppLogger.i(TAG, "Payment recorded")
                    _uiState.update { it.copy(isSaving = false, showPaymentDialog = false, successMessage = "payment_recorded") }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    // ── Status Update ──

    fun showStatusDialog(plan: InstallmentPlan) { _uiState.update { it.copy(showStatusDialog = plan) } }
    fun dismissStatusDialog() { _uiState.update { it.copy(showStatusDialog = null) } }

    fun updateStatus(status: String) {
        CrashReporter.addBreadcrumb("updateStatus($status)", TAG)
        val plan = _uiState.value.showStatusDialog ?: return
        viewModelScope.launch {
            installmentRepository.updatePlanStatus(plan.id, status)
                .onSuccess {
                    _uiState.update { it.copy(showStatusDialog = null, successMessage = "status_updated") }
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── Late Fee ──

    fun applyLateFee() {
        CrashReporter.addBreadcrumb("applyLateFee()", TAG)
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            installmentRepository.applyLateFee(plan.id)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "late_fee_applied") }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}
