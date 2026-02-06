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
        val dailyData: List<DailyAnalytics> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedStatus: String? = null,
        val selectedChannel: String? = null,
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
            val settlementsResult = analyticsRepository.getSettlements(thirtyDaysAgo, now)
            val deliveryResult = analyticsRepository.getDeliveryPerformance(thirtyDaysAgo, now)
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
        }
    }

    fun applyFilters(status: String?, channel: String?, from: Long?, to: Long?) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true,
                    selectedStatus = status,
                    selectedChannel = channel,
                    fromDate = from,
                    toDate = to
                )
            }

            val filteredResult = analyticsRepository.getFilteredSummary(status, channel, from, to)
            
            filteredResult.onSuccess { summary ->
                _uiState.update { 
                    it.copy(
                        filteredSummary = summary,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun clearFilters() {
        _uiState.update { 
            it.copy(
                selectedStatus = null,
                selectedChannel = null,
                fromDate = null,
                toDate = null,
                filteredSummary = null
            )
        }
    }
}
