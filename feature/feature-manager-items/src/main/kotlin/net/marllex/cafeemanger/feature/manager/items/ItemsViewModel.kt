package net.marllex.cafeemanger.feature.manager.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.CategoryRepository
import net.marllex.cafeemanger.core.domain.repository.ItemRepository
import net.marllex.cafeemanger.core.model.Category
import net.marllex.cafeemanger.core.model.Item
import javax.inject.Inject

@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    data class UiState(
        val items: List<Item> = emptyList(),
        val categories: List<Category> = emptyList(),
        val selectedCategoryId: String? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val showAddDialog: Boolean = false,
        val editingItem: Item? = null,
        val dialogName: String = "",
        val dialogDescription: String = "",
        val dialogPrice: String = "",
        val dialogCategoryId: String = "",
        val dialogImageUrl: String = "",
        val dialogAvailable: Boolean = true,
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            itemRepository.refreshItems()
            categoryRepository.refreshCategories()

            combine(
                itemRepository.getItems(_uiState.value.selectedCategoryId),
                categoryRepository.getCategories()
            ) { items, categories ->
                _uiState.value.copy(
                    items = items,
                    categories = categories,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun filterByCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadItems()
    }

    fun showAddDialog() {
        val firstCategoryId = _uiState.value.categories.firstOrNull()?.id ?: ""
        _uiState.update {
            it.copy(
                showAddDialog = true, editingItem = null,
                dialogName = "", dialogDescription = "", dialogPrice = "",
                dialogCategoryId = firstCategoryId, dialogImageUrl = "", dialogAvailable = true,
            )
        }
    }

    fun showEditDialog(item: Item) {
        _uiState.update {
            it.copy(
                showAddDialog = true, editingItem = item,
                dialogName = item.name, dialogDescription = item.description ?: "",
                dialogPrice = item.price.toString(), dialogCategoryId = item.categoryId,
                dialogImageUrl = item.imageUrl ?: "", dialogAvailable = item.available,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingItem = null) }
    }

    fun updateDialogName(v: String) { _uiState.update { it.copy(dialogName = v) } }
    fun updateDialogDescription(v: String) { _uiState.update { it.copy(dialogDescription = v) } }
    fun updateDialogPrice(v: String) { _uiState.update { it.copy(dialogPrice = v) } }
    fun updateDialogCategoryId(v: String) { _uiState.update { it.copy(dialogCategoryId = v) } }
    fun updateDialogAvailable(v: Boolean) { _uiState.update { it.copy(dialogAvailable = v) } }

    fun saveItem() {
        val s = _uiState.value
        if (s.dialogName.isBlank() || s.dialogPrice.isBlank()) return
        val price = s.dialogPrice.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = if (s.editingItem != null) {
                itemRepository.updateItem(
                    id = s.editingItem.id,
                    categoryId = s.dialogCategoryId.ifBlank { null },
                    name = s.dialogName, description = s.dialogDescription.ifBlank { null },
                    price = price, imageUrl = s.dialogImageUrl.ifBlank { null },
                    available = s.dialogAvailable,
                )
            } else {
                itemRepository.createItem(
                    categoryId = s.dialogCategoryId, name = s.dialogName,
                    description = s.dialogDescription.ifBlank { null },
                    price = price, imageUrl = s.dialogImageUrl.ifBlank { null },
                    available = s.dialogAvailable,
                )
            }

            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun toggleAvailability(item: Item) {
        viewModelScope.launch {
            itemRepository.toggleAvailability(item.id, !item.available)
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            itemRepository.deleteItem(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
