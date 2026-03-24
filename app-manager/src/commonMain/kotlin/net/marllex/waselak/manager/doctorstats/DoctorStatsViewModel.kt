package net.marllex.waselak.manager.doctorstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.DoctorStatsResponse
import net.marllex.waselak.core.common.logging.AppLogger

class DoctorStatsViewModel(
    private val api: WaselakApiClient,
) : ViewModel() {
    private companion object { private const val TAG = "DoctorStats" }


    data class UiState(
        val stats: List<DoctorStatsResponse> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = api.getDoctorStats()
                _uiState.update { it.copy(stats = result, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
