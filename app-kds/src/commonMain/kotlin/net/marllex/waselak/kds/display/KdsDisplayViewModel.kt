package net.marllex.waselak.kds.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.KdsRepository
import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsSummary
import java.util.concurrent.atomic.AtomicInteger
import net.marllex.waselak.core.common.crash.CrashReporter

private const val TAG = "KdsDisplayVM"

class KdsDisplayViewModel(
    private val kdsRepository: KdsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val orders: List<KdsOrder> = emptyList(),
        val summary: KdsSummary = KdsSummary(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedStation: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private val pendingUpdates = AtomicInteger(0)

    init {
        load()
    }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "KdsDisplayViewModel")
        AppLogger.d(TAG, "Loading KDS orders, station=${_uiState.value.selectedStation}")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = _uiState.value.orders.isEmpty(), error = null) }
            kdsRepository.getKdsOrders(station = _uiState.value.selectedStation)
                .onSuccess { list ->
                    val filtered = list.filter { order -> !order.allServed }
                        .sortedBy { order -> order.createdAt }
                    AppLogger.i(TAG, "Loaded ${list.size} orders, ${filtered.size} active")
                    _uiState.update { it.copy(orders = filtered, isLoading = false) }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Failed to load orders: ${e.message}")
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
        viewModelScope.launch {
            kdsRepository.getSummary()
                .onSuccess { s -> _uiState.update { it.copy(summary = s) } }
        }
    }

    /** Start periodic polling — call when screen enters composition */
    fun startPolling() {
        if (refreshJob?.isActive == true) return
        AppLogger.d(TAG, "Polling started")
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(1_500) // 1.5 seconds for dedicated KDS display
                if (pendingUpdates.get() == 0) load()
            }
        }
    }

    /** Stop periodic polling — call when screen leaves composition */
    fun stopPolling() {
        AppLogger.d(TAG, "Polling stopped")
        refreshJob?.cancel()
        refreshJob = null
    }

    fun onStationFilter(station: String?) {
        _uiState.update { it.copy(selectedStation = station) }
        load()
    }

    fun updateItemStatus(itemId: String, status: String) {
        AppLogger.i(TAG, "Updating item $itemId to $status")
        // Optimistic UI update — reflect change immediately
        _uiState.update { state ->
            state.copy(
                orders = state.orders.map { order ->
                    order.copy(items = order.items.map { item ->
                        if (item.id == itemId) item.copy(kitchenStatus = status) else item
                    })
                },
                summary = recalcSummary(state),
            )
        }
        pendingUpdates.incrementAndGet()
        viewModelScope.launch {
            kdsRepository.updateItemStatus(itemId, status)
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Failed to update item $itemId status: ${e.message}")
                    load()
                    _uiState.update { it.copy(error = e.message) }
                }
            pendingUpdates.decrementAndGet()
        }
    }

    fun markAllReady(orderId: String, itemIds: List<String>) {
        AppLogger.i(TAG, "Marking all ready for order $orderId, ${itemIds.size} items")
        // Optimistic UI update — mark all items as READY immediately
        _uiState.update { state ->
            state.copy(
                orders = state.orders.map { order ->
                    if (order.orderId == orderId) {
                        order.copy(items = order.items.map { item ->
                            if (item.id in itemIds) item.copy(kitchenStatus = "READY") else item
                        })
                    } else order
                },
                summary = recalcSummary(state),
            )
        }
        pendingUpdates.incrementAndGet()
        viewModelScope.launch {
            kdsRepository.bulkUpdateStatus(orderId, itemIds, "READY")
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    load()
                    _uiState.update { it.copy(error = e.message) }
                }
            pendingUpdates.decrementAndGet()
        }
    }

    private fun recalcSummary(state: UiState): KdsSummary {
        val allItems = state.orders.flatMap { it.items }
        return state.summary.copy(
            pending = allItems.count { it.isPending },
            cooking = allItems.count { it.isCooking },
            ready = allItems.count { it.isReady },
            served = allItems.count { it.isServed },
        )
    }

    fun logout() {
        AppLogger.i(TAG, "User logging out")
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
