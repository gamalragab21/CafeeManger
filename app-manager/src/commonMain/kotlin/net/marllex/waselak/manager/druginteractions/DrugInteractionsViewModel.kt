package net.marllex.waselak.manager.druginteractions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.DrugInteractionRepository
import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.network.dto.CreateDrugInteractionRequest

class DrugInteractionsViewModel(
    private val drugInteractionRepository: DrugInteractionRepository,
) : ViewModel() {

    data class UiState(
        val interactions: List<DrugInteraction> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val searchQuery: String = "",
        val showAddDialog: Boolean = false,
        val dialogItemIdA: String = "",
        val dialogItemIdB: String = "",
        val dialogSeverity: String = "MODERATE",
        val dialogDescription: String = "",
        val dialogDescriptionAr: String = "",
        val dialogRecommendation: String = "",
        val isSaving: Boolean = false,
    ) {
        val filteredInteractions: List<DrugInteraction>
            get() = if (searchQuery.isBlank()) interactions
            else interactions.filter {
                (it.itemNameA?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.itemNameB?.contains(searchQuery, ignoreCase = true) == true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            drugInteractionRepository.getInteractions()
                .onSuccess { list -> _uiState.update { it.copy(interactions = list, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onSearchQueryChange(q: String) { _uiState.update { it.copy(searchQuery = q) } }
    fun showAddDialog() { _uiState.update { it.copy(showAddDialog = true, dialogItemIdA = "", dialogItemIdB = "", dialogSeverity = "MODERATE", dialogDescription = "", dialogDescriptionAr = "", dialogRecommendation = "") } }
    fun dismissAddDialog() { _uiState.update { it.copy(showAddDialog = false) } }
    fun onDialogItemIdAChange(v: String) { _uiState.update { it.copy(dialogItemIdA = v) } }
    fun onDialogItemIdBChange(v: String) { _uiState.update { it.copy(dialogItemIdB = v) } }
    fun onDialogSeverityChange(v: String) { _uiState.update { it.copy(dialogSeverity = v) } }
    fun onDialogDescriptionChange(v: String) { _uiState.update { it.copy(dialogDescription = v) } }
    fun onDialogDescriptionArChange(v: String) { _uiState.update { it.copy(dialogDescriptionAr = v) } }
    fun onDialogRecommendationChange(v: String) { _uiState.update { it.copy(dialogRecommendation = v) } }

    fun save() {
        val s = _uiState.value
        if (s.dialogItemIdA.isBlank() || s.dialogItemIdB.isBlank() || s.dialogDescription.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            drugInteractionRepository.createInteraction(CreateDrugInteractionRequest(
                itemIdA = s.dialogItemIdA, itemIdB = s.dialogItemIdB,
                severity = s.dialogSeverity, description = s.dialogDescription,
                descriptionAr = s.dialogDescriptionAr.ifBlank { null },
                recommendation = s.dialogRecommendation.ifBlank { null },
            ))
                .onSuccess { _uiState.update { it.copy(isSaving = false, showAddDialog = false) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun toggle(id: String) {
        viewModelScope.launch {
            drugInteractionRepository.toggleInteraction(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            drugInteractionRepository.deleteInteraction(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
