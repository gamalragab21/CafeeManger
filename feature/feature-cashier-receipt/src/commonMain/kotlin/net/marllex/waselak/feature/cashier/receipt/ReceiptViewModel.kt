package net.marllex.waselak.feature.cashier.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.common.logging.AppLogger

class ReceiptViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val vendorRepository: VendorRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Receipt" }


    data class UiState(
        val order: Order? = null,
        val vendor: Vendor? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSharing: Boolean = false,
        val shareUrl: String? = null,
        val shareExpiresAt: Long? = null,
        val digitalReceiptEnabled: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val orderId: String = savedStateHandle["orderId"] ?: ""
    private var vendorLoaded = false
    private var orderLoaded = false

    init {
        if (orderId.isNotBlank()) {
            loadReceipt()
        }
    }

    fun loadReceipt() {
        AppLogger.d(TAG, "loadReceipt called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Refresh vendor to ensure store info is available for the receipt
            val vendor = vendorRepository.refreshVendor().getOrNull()
                ?: runCatching { vendorRepository.getMyVendor().first() }.getOrNull()
            vendorLoaded = true // allow progress even if vendor is null
            val fetchResult = orderRepository.fetchOrder(orderId)
            fetchResult.onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e)
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
            val order = orderRepository.getOrderById(orderId).first()
            orderLoaded = true
            _uiState.update {
                it.copy(
                    vendor = vendor ?: it.vendor,
                    order = order ?: it.order,
                    isLoading = false
                )
            }
            // Auto-generate share link for QR code
            generateShareLink()
        }
    }

    fun generateShareLink() {
        AppLogger.d(TAG, "generateShareLink called")
        if (orderId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSharing = true) }
            orderRepository.shareReceipt(orderId)
                .onSuccess { link ->
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update {
                        it.copy(
                            isSharing = false,
                            shareUrl = link.url,
                            shareExpiresAt = link.expiresAt,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    if (e.isFeatureNotAvailableOrOffline()) {
                        // Silently hide QR/Share buttons — no bottom sheet
                        _uiState.update {
                            it.copy(
                                isSharing = false,
                                digitalReceiptEnabled = false,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isSharing = false, error = e.message) }
                    }
                }
        }
    }

}
