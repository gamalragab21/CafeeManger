package net.marllex.waselak.manager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.PlanFeaturesResponse
import net.marllex.waselak.core.network.dto.PlanSummaryDto
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class PlansComparisonViewModel(
    private val api: WaselakApiClient,
) : ViewModel() {
    private companion object { private const val TAG = "PlansComparison" }


    data class UiState(
        val currentPlan: PlanFeaturesResponse? = null,
        val allPlans: List<PlanSummaryDto> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currentPlan = api.getMyPlan()
                val allPlans = api.getAllPlans()
                _uiState.update { it.copy(
                    currentPlan = currentPlan,
                    allPlans = allPlans,
                    isLoading = false,
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun retry() = loadPlans()
}
