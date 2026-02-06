package net.marllex.cafeemanger.feature.cashier.receipt

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
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.Vendor
import javax.inject.Inject

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val vendorRepository: VendorRepository,
) : ViewModel() {

    data class UiState(
        val order: Order? = null,
        val vendor: Vendor? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val orderId: String = savedStateHandle["orderId"] ?: ""

    init {
        if (orderId.isNotBlank()) loadReceipt()
    }

    fun loadReceipt() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            vendorRepository.getMyVendor().collect { vendor ->
                _uiState.update { it.copy(vendor = vendor) }
            }
        }
        viewModelScope.launch {
            orderRepository.getOrderById(orderId).collect { order ->
                _uiState.update { it.copy(order = order, isLoading = false) }
            }
        }
    }
}
