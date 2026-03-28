package net.marllex.waselak.cashier.customercredit

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

class CashierCustomerCreditViewModel(
    private val customerCreditRepository: CustomerCreditRepository,
) : ViewModel() {
    private companion object { private const val TAG = "CashierCustomerCredit" }


    data class UiState(
        val debtors: List<CustomerCredit> = emptyList(),
        val selectedCredit: CustomerCredit? = null,
        val transactions: List<CreditTransaction> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Payment dialog
        val showPaymentDialog: Boolean = false,
        val paymentAmount: String = "",
        val paymentNote: String = "",
        // Charge dialog
        val showChargeDialog: Boolean = false,
        val chargeAmount: String = "",
        val chargeNote: String = "",
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "CashierCustomerCreditViewModel")
        AppLogger.d(TAG, "load called")
        CrashReporter.addBreadcrumb("Loading debtors", "CashierCustomerCreditViewModel")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = _uiState.value.debtors.isEmpty(), error = null) }
            customerCreditRepository.getDebtors()
                .onSuccess { list ->
                    CrashReporter.addBreadcrumb("Debtors loaded: ${list.size} items", "CashierCustomerCreditViewModel")
                    _uiState.update { it.copy(debtors = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.addBreadcrumb("Debtors load failed: ${e.message}", "CashierCustomerCreditViewModel")
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectCustomer(credit: CustomerCredit) {
        AppLogger.d(TAG, "selectCustomer called")
        CrashReporter.addBreadcrumb("Selected customer: ${credit.customerId}", "CashierCustomerCreditViewModel")
        _uiState.update { it.copy(selectedCredit = credit) }
        viewModelScope.launch {
            customerCreditRepository.getTransactions(credit.customerId)
                .onSuccess { list -> _uiState.update { it.copy(transactions = list) } }
        }
    }

    fun clearSelection() { _uiState.update { it.copy(selectedCredit = null, transactions = emptyList()) } }

    // Payment
    fun showPaymentDialog() { _uiState.update { it.copy(showPaymentDialog = true, paymentAmount = "", paymentNote = "") } }
    fun dismissPaymentDialog() { _uiState.update { it.copy(showPaymentDialog = false) } }
    fun onPaymentAmountChange(v: String) { _uiState.update { it.copy(paymentAmount = v) } }
    fun onPaymentNoteChange(v: String) { _uiState.update { it.copy(paymentNote = v) } }

    fun recordPayment() {
        AppLogger.d(TAG, "recordPayment called")
        CrashReporter.addBreadcrumb("Recording payment", "CashierCustomerCreditViewModel")
        val credit = _uiState.value.selectedCredit ?: return
        val amount = _uiState.value.paymentAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            customerCreditRepository.payCredit(
                customerId = credit.customerId,
                amount = amount,
                note = _uiState.value.paymentNote.ifBlank { null },
            )
                .onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(isSaving = false, showPaymentDialog = false) }
                    selectCustomer(credit)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.addBreadcrumb("Record payment failed: ${e.message}", "CashierCustomerCreditViewModel")
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    // Charge
    fun showChargeDialog() { _uiState.update { it.copy(showChargeDialog = true, chargeAmount = "", chargeNote = "") } }
    fun dismissChargeDialog() { _uiState.update { it.copy(showChargeDialog = false) } }
    fun onChargeAmountChange(v: String) { _uiState.update { it.copy(chargeAmount = v) } }
    fun onChargeNoteChange(v: String) { _uiState.update { it.copy(chargeNote = v) } }

    fun recordCharge() {
        AppLogger.d(TAG, "recordCharge called")
        CrashReporter.addBreadcrumb("Recording charge", "CashierCustomerCreditViewModel")
        val credit = _uiState.value.selectedCredit ?: return
        val amount = _uiState.value.chargeAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            customerCreditRepository.chargeCredit(
                customerId = credit.customerId,
                amount = amount,
                note = _uiState.value.chargeNote.ifBlank { null },
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, showChargeDialog = false) }
                    selectCustomer(credit)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.addBreadcrumb("Record charge failed: ${e.message}", "CashierCustomerCreditViewModel")
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
