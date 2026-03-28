package net.marllex.waselak.feature.cashier.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.network.isPlanLimitExceeded
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class PaymentViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Payment" }


    data class UiState(
        val order: Order? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isProcessing: Boolean = false,
        val paymentCompleted: Boolean = false,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
        val showPlanLimitDialog: Boolean = false,
        val planLimitMessage: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val orderId: String = savedStateHandle["orderId"] ?: ""

    init {
        if (orderId.isNotBlank()) loadOrder()
    }

    fun loadOrder() {
        AppLogger.d(TAG, "loadOrder called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            orderRepository.getOrderById(orderId).collect { order ->
                _uiState.update { it.copy(order = order, isLoading = false) }
            }
        }
    }

    fun completePayment() {
        AppLogger.d(TAG, "completePayment called")
        val order = _uiState.value.order ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            // Update payment status to PAID (independent from order status)
            orderRepository.updatePaymentStatus(order.id, PaymentStatus.PAID)
                .onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(isProcessing = false, paymentCompleted = true) }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e)
                    when {
                        e.isFeatureNotAvailableOrOffline() -> _uiState.update {
                            it.copy(isProcessing = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "")
                        }
                        e.isPlanLimitExceeded() -> _uiState.update {
                            it.copy(isProcessing = false, showPlanLimitDialog = true, planLimitMessage = e.message ?: "")
                        }
                        else -> _uiState.update { it.copy(isProcessing = false, error = e.message) }
                    }
                }
        }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false, featureNotAvailableMessage = "") }
    }

    fun dismissPlanLimitDialog() {
        _uiState.update { it.copy(showPlanLimitDialog = false, planLimitMessage = "") }
    }
}
