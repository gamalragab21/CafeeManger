package net.marllex.waselak.feature.manager.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.model.Category
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.VariantGroup
import net.marllex.waselak.core.model.VariantOption

data class EditableVariantGroup(
    val id: String = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() + (0..9999).random(),
    val name: String = "",
    val required: Boolean = false,
    val options: List<EditableVariantOption> = emptyList()
)

data class EditableVariantOption(
    val id: String = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString() + (0..9999).random(),
    val name: String = "",
    val priceAdjustment: Double = 0.0,
    val isDefault: Boolean = false
)

class ItemsViewModel constructor(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    data class UiState(
        val items: List<Item> = emptyList(),
        val allItems: List<Item> = emptyList(),
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
        val dialogBarcode: String = "",
        val dialogAvailable: Boolean = true,
        val dialogVariantGroups: List<EditableVariantGroup> = emptyList(),
        val isSaving: Boolean = false,
        val showBarcodeScanner: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    init {
        observeItems()
        loadItems()
    }

    // Network refresh only — does NOT start a new collection
    fun loadItems() {
        viewModelScope.launch {
            AppLogger.d("Items", "Loading items and categories")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                itemRepository.refreshItems()
                categoryRepository.refreshCategories()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Single reactive observer — flatMapLatest cancels old query on filter change
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeItems() {
        viewModelScope.launch {
            combine(
                _selectedCategoryId.flatMapLatest { categoryId ->
                    itemRepository.getItems(categoryId)
                },
                itemRepository.getItems(null),
                categoryRepository.getCategories(),
            ) { filteredItems, allItems, categories ->
                _uiState.value.copy(
                    items = filteredItems,
                    allItems = allItems,
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
        AppLogger.d("Items", "Filter by category: $categoryId")
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        _selectedCategoryId.value = categoryId
    }

    fun showAddDialog() {
        AppLogger.i("Items", "User action: open add item dialog")
        val firstCategoryId = _uiState.value.categories.firstOrNull()?.id ?: ""
        _uiState.update {
            it.copy(
                showAddDialog = true, editingItem = null,
                dialogName = "", dialogDescription = "", dialogPrice = "",
                dialogCategoryId = firstCategoryId, dialogImageUrl = "", dialogBarcode = "",
                dialogAvailable = true, dialogVariantGroups = emptyList(),
                showBarcodeScanner = false,
            )
        }
    }

    fun showEditDialog(item: Item) {
        AppLogger.i("Items", "User action: open edit item dialog for id=${item.id}")
        _uiState.update {
            it.copy(
                showAddDialog = true, editingItem = item,
                dialogName = item.name, dialogDescription = item.description ?: "",
                dialogPrice = item.price.toString(), dialogCategoryId = item.categoryId,
                dialogImageUrl = item.imageUrl ?: "", dialogBarcode = item.barcode ?: "",
                dialogAvailable = item.available, showBarcodeScanner = false,
                dialogVariantGroups = item.variantGroups.map { group ->
                    EditableVariantGroup(
                        id = group.id, name = group.name, required = group.required,
                        options = group.options.map { option ->
                            EditableVariantOption(
                                id = option.id, name = option.name,
                                priceAdjustment = option.priceAdjustment, isDefault = option.isDefault
                            )
                        }
                    )
                },
            )
        }
    }

    // Swipe-dismiss — just hide sheet, preserve form data
    fun hideSheet() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    // Explicit cancel — hide sheet AND clear all form data
    fun cancelDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = false, editingItem = null,
                dialogName = "", dialogDescription = "", dialogPrice = "",
                dialogCategoryId = "", dialogImageUrl = "", dialogBarcode = "",
                dialogAvailable = true, dialogVariantGroups = emptyList(),
                showBarcodeScanner = false,
            )
        }
    }

    fun updateDialogName(v: String) { _uiState.update { it.copy(dialogName = v) } }
    fun updateDialogDescription(v: String) { _uiState.update { it.copy(dialogDescription = v) } }
    fun updateDialogPrice(v: String) { _uiState.update { it.copy(dialogPrice = v) } }
    fun updateDialogCategoryId(v: String) { _uiState.update { it.copy(dialogCategoryId = v) } }
    fun updateDialogBarcode(v: String) { _uiState.update { it.copy(dialogBarcode = v) } }
    fun updateDialogAvailable(v: Boolean) { _uiState.update { it.copy(dialogAvailable = v) } }
    fun toggleBarcodeScanner() {
        AppLogger.d("Items", "Toggling barcode scanner")
        _uiState.update { it.copy(showBarcodeScanner = !it.showBarcodeScanner) }
    }
    fun onBarcodeScanResult(barcode: String) {
        AppLogger.d("Items", "Barcode scanned: $barcode")
        _uiState.update { it.copy(dialogBarcode = barcode, showBarcodeScanner = false) }
    }

    // ─── Variant Management ──────────────────────────────────────
    fun addVariantGroup() {
        AppLogger.d("Items", "Adding variant group")
        _uiState.update { it.copy(dialogVariantGroups = it.dialogVariantGroups + EditableVariantGroup()) }
    }

    fun removeVariantGroup(groupId: String) {
        AppLogger.d("Items", "Removing variant group: groupId=$groupId")
        _uiState.update { it.copy(dialogVariantGroups = it.dialogVariantGroups.filter { g -> g.id != groupId }) }
    }

    fun updateVariantGroupName(groupId: String, name: String) {
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(name = name) else g
            })
        }
    }

    fun toggleVariantGroupRequired(groupId: String) {
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(required = !g.required) else g
            })
        }
    }

    fun addVariantOption(groupId: String) {
        AppLogger.d("Items", "Adding variant option to group: groupId=$groupId")
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(options = g.options + EditableVariantOption()) else g
            })
        }
    }

    fun removeVariantOption(groupId: String, optionId: String) {
        AppLogger.d("Items", "Removing variant option: groupId=$groupId, optionId=$optionId")
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(options = g.options.filter { o -> o.id != optionId }) else g
            })
        }
    }

    fun updateVariantOptionName(groupId: String, optionId: String, name: String) {
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(options = g.options.map { o ->
                    if (o.id == optionId) o.copy(name = name) else o
                }) else g
            })
        }
    }

    fun updateVariantOptionPrice(groupId: String, optionId: String, price: String) {
        _uiState.update { state ->
            state.copy(dialogVariantGroups = state.dialogVariantGroups.map { g ->
                if (g.id == groupId) g.copy(options = g.options.map { o ->
                    if (o.id == optionId) o.copy(priceAdjustment = price.toDoubleOrNull() ?: 0.0) else o
                }) else g
            })
        }
    }

    fun saveItem() {
        val s = _uiState.value
        AppLogger.d("Items", "Saving item: name=${s.dialogName}, editing=${s.editingItem != null}")
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
                    barcode = s.dialogBarcode.ifBlank { null },
                )
            } else {
                itemRepository.createItem(
                    categoryId = s.dialogCategoryId, name = s.dialogName,
                    description = s.dialogDescription.ifBlank { null },
                    price = price, imageUrl = s.dialogImageUrl.ifBlank { null },
                    available = s.dialogAvailable,
                    barcode = s.dialogBarcode.ifBlank { null },
                )
            }

            result.onSuccess { savedItem ->
                // Save variant groups if any (filter out empty groups/options)
                val validGroups = s.dialogVariantGroups
                    .filter { it.name.isNotBlank() }
                    .map { group ->
                        VariantGroup(
                            id = group.id, name = group.name, required = group.required,
                            displayOrder = s.dialogVariantGroups.indexOf(group),
                            options = group.options.filter { it.name.isNotBlank() }.mapIndexed { idx, opt ->
                                VariantOption(
                                    id = opt.id, name = opt.name,
                                    priceAdjustment = opt.priceAdjustment,
                                    isDefault = opt.isDefault, displayOrder = idx
                                )
                            }
                        )
                    }
                if (validGroups.isNotEmpty() || s.editingItem?.variantGroups?.isNotEmpty() == true) {
                    itemRepository.updateItemVariants(savedItem.id, validGroups)
                }
                AppLogger.i("Items", "Item saved successfully")
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
            }.onFailure { e ->
                AppLogger.e("Items", "Failed to save item", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun toggleAvailability(item: Item) {
        viewModelScope.launch {
            AppLogger.d("Items", "Toggling availability for item: id=${item.id}")
            itemRepository.toggleAvailability(item.id, !item.available)
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            AppLogger.d("Items", "Deleting item: id=$id")
            itemRepository.deleteItem(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
