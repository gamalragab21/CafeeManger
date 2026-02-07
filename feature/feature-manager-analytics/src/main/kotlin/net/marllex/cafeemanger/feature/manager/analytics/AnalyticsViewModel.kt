package net.marllex.cafeemanger.feature.manager.analytics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.AnalyticsRepository
import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.model.DeliveryPerformance
import net.marllex.cafeemanger.core.model.Settlements
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    data class UiState(
        val summary: AnalyticsSummary? = null,
        val filteredSummary: AnalyticsSummary? = null,
        val settlements: Settlements? = null,
        val deliveryPerformance: List<DeliveryPerformance> = emptyList(),
        val cashierPerformance: List<DeliveryPerformance> = emptyList(),
        val dailyData: List<DailyAnalytics> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedCashierId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val fromDate: Long? = null,
        val toDate: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadAnalytics() }

    fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

            val summaryResult = analyticsRepository.getSummary(thirtyDaysAgo, now)
            val settlementsResult = analyticsRepository.getSettlements(null, null, null, null, thirtyDaysAgo, now)
            val deliveryResult = analyticsRepository.getDeliveryPerformance(null, null, thirtyDaysAgo, now)
            val cashierResult = analyticsRepository.getCashierPerformance(null, null, null, thirtyDaysAgo, now)
            val dailyResult = analyticsRepository.getDailyAnalytics(thirtyDaysAgo, now)

            summaryResult.onSuccess { summary ->
                _uiState.update { it.copy(summary = summary, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }

            settlementsResult.onSuccess { settlements ->
                _uiState.update { it.copy(settlements = settlements) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            deliveryResult.onSuccess { performance ->
                _uiState.update { it.copy(deliveryPerformance = performance) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            dailyResult.onSuccess { daily ->
                _uiState.update { it.copy(dailyData = daily) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            cashierResult.onSuccess { cashier ->
                _uiState.update { it.copy(cashierPerformance = cashier) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun applyFilters(
        cashierId: String?,
        deliveryUserId: String?,
        from: Long?,
        to: Long?
    ) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true,
                    selectedCashierId = cashierId,
                    selectedDeliveryUserId = deliveryUserId,
                    fromDate = from,
                    toDate = to
                )
            }

            val fromDate = from ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
            val toDate = to ?: System.currentTimeMillis()
            val filteredResult = analyticsRepository.getFilteredSummary(null, null, cashierId, deliveryUserId, fromDate, toDate)
            val settlementsResult = analyticsRepository.getSettlements(null, null, cashierId, deliveryUserId, fromDate, toDate)
            val deliveryResult = analyticsRepository.getDeliveryPerformance(null, cashierId, fromDate, toDate)
            val cashierResult = analyticsRepository.getCashierPerformance(null, null, deliveryUserId, fromDate, toDate)
            
            filteredResult.onSuccess { summary ->
                _uiState.update { it.copy(filteredSummary = summary) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
            settlementsResult.onSuccess { settlements ->
                _uiState.update { it.copy(settlements = settlements) }
            }
            deliveryResult.onSuccess { performance ->
                _uiState.update { it.copy(deliveryPerformance = performance) }
            }
            cashierResult.onSuccess { performance ->
                _uiState.update { it.copy(cashierPerformance = performance) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedCashierId = null,
                selectedDeliveryUserId = null,
                fromDate = null,
                toDate = null,
                filteredSummary = null,
                settlements = null,
                deliveryPerformance = emptyList(),
                cashierPerformance = emptyList()
            )
        }
        loadAnalytics()
    }
}
