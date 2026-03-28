package net.marllex.waselak.manager.taxplaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.TaxPlaceRepository
import net.marllex.waselak.core.model.TaxPlace
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class TaxPlacesViewModel(
    private val taxPlaceRepository: TaxPlaceRepository,
) : ViewModel() {
    private companion object { private const val TAG = "TaxPlaces" }


    data class UiState(
        val places: List<TaxPlace> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "TaxPlacesViewModel")
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            taxPlaceRepository.getTaxPlaces()
                .onSuccess { list -> _uiState.update { it.copy(places = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun create(name: String, taxPercent: Double, isDefault: Boolean) {
        AppLogger.d(TAG, "create called")
        viewModelScope.launch {
            taxPlaceRepository.createTaxPlace(name, taxPercent, isDefault, _uiState.value.places.size)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun update(id: String, name: String?, taxPercent: Double?, isDefault: Boolean?) {
        viewModelScope.launch {
            taxPlaceRepository.updateTaxPlace(id, name, taxPercent, isDefault, null)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun delete(id: String) {
        AppLogger.d(TAG, "delete called")
        viewModelScope.launch {
            taxPlaceRepository.deleteTaxPlace(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
