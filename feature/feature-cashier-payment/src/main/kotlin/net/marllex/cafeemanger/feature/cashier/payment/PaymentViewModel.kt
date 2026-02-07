package net.marllex.cafeemanger.feature.cashier.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.OrderRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderStatus
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    data class UiState(
        val order: Order? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isProcessing: Boolean = false,
        val paymentCompleted: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val orderId: String = savedStateHandle["orderId"] ?: ""

    init {
        if (orderId.isNotBlank()) loadOrder()
    }

    fun loadOrder() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            orderRepository.getOrderById(orderId).collect { order ->
                _uiState.update { it.copy(order = order, isLoading = false) }
            }
        }
    }

    fun completePayment() {
        val order = _uiState.value.order ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            // Move order to COMPLETED if it's in a valid state
            val targetStatus = when (order.status) {
                OrderStatus.READY,
                OrderStatus.ASSIGNED,
                OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.DELIVERED -> OrderStatus.COMPLETED
                else -> null
            }
            if (targetStatus != null) {
                orderRepository.updateOrderStatus(order.id, targetStatus)
                    .onSuccess {
                        _uiState.update { it.copy(isProcessing = false, paymentCompleted = true) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isProcessing = false, error = e.message) }
                    }
            } else {
                _uiState.update { it.copy(isProcessing = false, paymentCompleted = true) }
            }
        }
    }
}
