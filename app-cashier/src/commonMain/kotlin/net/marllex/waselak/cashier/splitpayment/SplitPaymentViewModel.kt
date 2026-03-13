package net.marllex.waselak.cashier.splitpayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.SplitPaymentRepository
import net.marllex.waselak.core.model.OrderPayment
import net.marllex.waselak.core.model.SplitPaymentSummary

class SplitPaymentViewModel(
    private val splitPaymentRepository: SplitPaymentRepository,
) : ViewModel() {

    data class UiState(
        val summary: SplitPaymentSummary? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val orderId: String = "",
        val orderIdInput: String = "",
        // Add payment dialog
        val showAddDialog: Boolean = false,
        val paymentMethod: String = "CASH",
        val paymentAmount: String = "",
        val paymentNote: String = "",
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onOrderIdInputChange(v: String) { _uiState.update { it.copy(orderIdInput = v) } }

    fun loadOrder() {
        val orderId = _uiState.value.orderIdInput.trim()
        if (orderId.isBlank()) return
        _uiState.update { it.copy(orderId = orderId) }
        load()
    }

    fun loadForOrder(orderId: String) {
        _uiState.update { it.copy(orderId = orderId, orderIdInput = orderId) }
        load()
    }

    private fun load() {
        val orderId = _uiState.value.orderId
        if (orderId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            splitPaymentRepository.getPaymentSummary(orderId)
                .onSuccess { s -> _uiState.update { it.copy(summary = s, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    // Add payment dialog
    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, paymentMethod = "CASH", paymentAmount = "", paymentNote = "") }
    }
    fun dismissAddDialog() { _uiState.update { it.copy(showAddDialog = false) } }
    fun onPaymentMethodChange(v: String) { _uiState.update { it.copy(paymentMethod = v) } }
    fun onPaymentAmountChange(v: String) { _uiState.update { it.copy(paymentAmount = v) } }
    fun onPaymentNoteChange(v: String) { _uiState.update { it.copy(paymentNote = v) } }

    fun addPayment() {
        val state = _uiState.value
        val amount = state.paymentAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            splitPaymentRepository.addPayment(
                orderId = state.orderId,
                paymentMethod = state.paymentMethod,
                amount = amount,
                note = state.paymentNote.ifBlank { null },
            )
                .onSuccess { _uiState.update { it.copy(isSaving = false, showAddDialog = false) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            splitPaymentRepository.deletePayment(_uiState.value.orderId, paymentId)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
