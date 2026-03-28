package net.marllex.waselak.feature.delivery.orders.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class DeliveryHistoryViewModel constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "DeliveryHistory" }


    data class UiState(
        val grouped: Map<String, List<Order>> = emptyMap(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "DeliveryHistoryViewModel")
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            orderRepository.refreshMyDeliveryOrders(status = "COMPLETED")
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(error = e.message, isLoading = false) } }
            orderRepository.getMyDeliveryOrders(status = "COMPLETED").collect { orders ->
                val grouped = orders.groupBy { epochDay(it.createdAt) }
                    .entries.sortedByDescending { it.key }
                    .associate { it.toPair() } // latest day first
                _uiState.update { it.copy(grouped = grouped, isLoading = false) }
            }
        }
    }

    private fun epochDay(epochMs: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return localDateTime.date.toString()
    }
}
