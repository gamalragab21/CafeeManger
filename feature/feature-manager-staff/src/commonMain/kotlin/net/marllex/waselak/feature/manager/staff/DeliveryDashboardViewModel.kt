package net.marllex.waselak.feature.manager.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.model.DeliveryAvailability
import net.marllex.waselak.core.model.DeliveryOrderSummary
import net.marllex.waselak.core.model.DeliveryPersonStatus
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.common.logging.AppLogger

class DeliveryDashboardViewModel constructor(
    private val api: WaselakApiClient,
) : ViewModel() {
    private companion object { private const val TAG = "DeliveryDashboard" }


    data class UiState(
        val deliveryPersons: List<DeliveryPersonStatus> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        AppLogger.d(TAG, "loadDashboard called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getDeliveryDashboard()
                val persons = response.map { dto ->
                    DeliveryPersonStatus(
                        userId = dto.deliveryUserId,
                        name = dto.deliveryUserName,
                        phone = dto.deliveryUserPhone,
                        status = try {
                            DeliveryAvailability.valueOf(dto.status)
                        } catch (_: Exception) {
                            DeliveryAvailability.AVAILABLE
                        },
                        activeOrderCount = dto.activeOrderCount,
                        activeOrders = dto.activeOrders.map { order ->
                            DeliveryOrderSummary(
                                orderId = order.orderId,
                                status = try {
                                    OrderStatus.valueOf(order.status)
                                } catch (_: Exception) {
                                    OrderStatus.CREATED
                                },
                                clientName = order.clientName,
                                clientAddress = order.clientAddress,
                                total = order.total,
                                createdAt = order.createdAt,
                            )
                        },
                    )
                }
                _uiState.update { it.copy(deliveryPersons = persons, isLoading = false) }
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    _uiState.update { it.copy(isLoading = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "") }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }
}
