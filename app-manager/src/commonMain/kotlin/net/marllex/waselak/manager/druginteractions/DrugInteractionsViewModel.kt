package net.marllex.waselak.manager.druginteractions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.DrugInteractionRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.network.dto.CreateDrugInteractionRequest
import net.marllex.waselak.core.common.logging.AppLogger

class DrugInteractionsViewModel(
    private val drugInteractionRepository: DrugInteractionRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {
    private companion object { private const val TAG = "DrugInteractions" }


    data class UiState(
        val interactions: List<DrugInteraction> = emptyList(),
        val allItems: List<Item> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val searchQuery: String = "",
        val showAddDialog: Boolean = false,
        val dialogItemA: Item? = null,
        val dialogItemB: Item? = null,
        val dialogItemIdA: String = "",
        val dialogItemIdB: String = "",
        val dialogSearchA: String = "",
        val dialogSearchB: String = "",
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

        val filteredItemsA: List<Item>
            get() = if (dialogSearchA.isBlank()) allItems
            else allItems.filter {
                it.name.contains(dialogSearchA, ignoreCase = true) ||
                    (it.barcode?.contains(dialogSearchA, ignoreCase = true) == true)
            }

        val filteredItemsB: List<Item>
            get() = if (dialogSearchB.isBlank()) allItems
            else allItems.filter {
                it.name.contains(dialogSearchB, ignoreCase = true) ||
                    (it.barcode?.contains(dialogSearchB, ignoreCase = true) == true)
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadItems()
    }

    fun load() {
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            drugInteractionRepository.getInteractions()
                .onSuccess { list -> _uiState.update { it.copy(interactions = list, isLoading = false) } }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            itemRepository.getItems().collect { items ->
                _uiState.update { it.copy(allItems = items) }
            }
        }
    }

    fun onSearchQueryChange(q: String) { _uiState.update { it.copy(searchQuery = q) } }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                dialogItemA = null, dialogItemB = null,
                dialogItemIdA = "", dialogItemIdB = "",
                dialogSearchA = "", dialogSearchB = "",
                dialogSeverity = "MODERATE",
                dialogDescription = "", dialogDescriptionAr = "",
                dialogRecommendation = "",
            )
        }
    }

    fun dismissAddDialog() { _uiState.update { it.copy(showAddDialog = false) } }

    fun onDialogSearchAChange(v: String) { _uiState.update { it.copy(dialogSearchA = v) } }
    fun onDialogSearchBChange(v: String) { _uiState.update { it.copy(dialogSearchB = v) } }

    fun selectItemA(item: Item) {
        AppLogger.d(TAG, "selectItemA called")
        _uiState.update { it.copy(dialogItemA = item, dialogItemIdA = item.id, dialogSearchA = "") }
    }

    fun selectItemB(item: Item) {
        AppLogger.d(TAG, "selectItemB called")
        _uiState.update { it.copy(dialogItemB = item, dialogItemIdB = item.id, dialogSearchB = "") }
    }

    fun clearItemA() { _uiState.update { it.copy(dialogItemA = null, dialogItemIdA = "") } }
    fun clearItemB() { _uiState.update { it.copy(dialogItemB = null, dialogItemIdB = "") } }

    // Keep for backward compat
    fun onDialogItemIdAChange(v: String) { _uiState.update { it.copy(dialogItemIdA = v) } }
    fun onDialogItemIdBChange(v: String) { _uiState.update { it.copy(dialogItemIdB = v) } }

    fun onDialogSeverityChange(v: String) { _uiState.update { it.copy(dialogSeverity = v) } }
    fun onDialogDescriptionChange(v: String) { _uiState.update { it.copy(dialogDescription = v) } }
    fun onDialogDescriptionArChange(v: String) { _uiState.update { it.copy(dialogDescriptionAr = v) } }
    fun onDialogRecommendationChange(v: String) { _uiState.update { it.copy(dialogRecommendation = v) } }

    fun save() {
        AppLogger.d(TAG, "save called")
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
        AppLogger.d(TAG, "toggle called")
        viewModelScope.launch {
            drugInteractionRepository.toggleInteraction(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun delete(id: String) {
        AppLogger.d(TAG, "delete called")
        viewModelScope.launch {
            drugInteractionRepository.deleteInteraction(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
