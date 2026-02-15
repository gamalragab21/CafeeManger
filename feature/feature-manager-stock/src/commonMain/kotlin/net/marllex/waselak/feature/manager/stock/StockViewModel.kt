package net.marllex.waselak.feature.manager.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.StockRepository
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.StockSummary
import net.marllex.waselak.core.model.StockTransaction

class StockViewModel constructor(
    private val stockRepository: StockRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    data class UiState(
        // Main data
        val stockItems: List<Stock> = emptyList(),
        val availableItems: List<Item> = emptyList(),
        val summary: StockSummary = StockSummary(),

        // Tab/filter
        val selectedTab: Int = 0, // 0=Overview, 1=Items, 2=Alerts, 3=Transactions

        // Search
        val searchQuery: String = "",

        // Loading/Error
        val isLoading: Boolean = true,
        val error: String? = null,

        // Add/Edit Stock Dialog
        val showAddDialog: Boolean = false,
        val editingStock: Stock? = null,
        val dialogIsIndependent: Boolean = false, // true = text input, false = menu item selection
        val dialogSelectedItemId: String = "",
        val dialogSelectedItemName: String = "",
        val dialogCustomItemName: String = "", // For independent items (free text)
        val dialogQuantity: String = "",
        val dialogMinQuantity: String = "5",
        val dialogCostPrice: String = "",
        val dialogUnit: String = "pcs",
        val dialogAlertEnabled: Boolean = true,
        val isSaving: Boolean = false,

        // Add/Deduct Quantity Dialog
        val showQuantityDialog: Boolean = false,
        val quantityDialogStock: Stock? = null,
        val quantityDialogIsAdd: Boolean = true,
        val quantityDialogAmount: String = "",
        val quantityDialogNote: String = "",

        // Delete confirmation
        val showDeleteDialog: Boolean = false,
        val deletingStock: Stock? = null,

        // Transactions
        val transactions: List<StockTransaction> = emptyList(),
        val transactionsLoading: Boolean = false,
    ) {
        val filteredStockItems: List<Stock>
            get() = if (searchQuery.isBlank()) stockItems
            else stockItems.filter { it.itemName.contains(searchQuery, ignoreCase = true) }

        val lowStockItems: List<Stock>
            get() = stockItems.filter { it.isLowStock && it.alertEnabled }

        val outOfStockItems: List<Stock>
            get() = stockItems.filter { it.isOutOfStock && it.alertEnabled }

        val alertItems: List<Stock>
            get() = stockItems.filter { (it.isLowStock || it.isOutOfStock) && it.alertEnabled }

        val topValueItems: List<Stock>
            get() = stockItems.sortedByDescending { it.totalValue }.take(5)

        // Items that don't have stock tracking yet (menu-linked)
        val unTrackedItems: List<Item>
            get() {
                val trackedItemIds = stockItems.mapNotNull { it.itemId }.toSet()
                return availableItems.filter { it.id !in trackedItemIds }
            }

        // Stats for independent vs menu items
        val menuItemsCount: Int get() = stockItems.count { it.isMenuItem }
        val independentItemsCount: Int get() = stockItems.count { !it.isMenuItem }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            itemRepository.refreshItems()
            stockRepository.refreshStock()

            combine(
                stockRepository.getAllStock(),
                itemRepository.getItems(),
            ) { stocks, items ->
                val summary = StockSummary(
                    totalItems = stocks.size,
                    totalValue = stocks.sumOf { it.totalValue },
                    lowStockCount = stocks.count { it.isLowStock },
                    outOfStockCount = stocks.count { it.isOutOfStock },
                    healthyStockCount = stocks.count { !it.isLowStock && !it.isOutOfStock },
                )
                _uiState.value.copy(
                    stockItems = stocks,
                    availableItems = items,
                    summary = summary,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ─── Tab Selection ──────────────────────────────────────────────
    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ─── Search ─────────────────────────────────────────────────────
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ─── Add Stock Dialog ───────────────────────────────────────────
    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingStock = null,
                dialogIsIndependent = false,
                dialogSelectedItemId = "",
                dialogSelectedItemName = "",
                dialogCustomItemName = "",
                dialogQuantity = "",
                dialogMinQuantity = "5",
                dialogCostPrice = "",
                dialogUnit = "pcs",
                dialogAlertEnabled = true,
            )
        }
    }

    fun showEditDialog(stock: Stock) {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingStock = stock,
                dialogIsIndependent = !stock.isMenuItem,
                dialogSelectedItemId = stock.itemId ?: "",
                dialogSelectedItemName = if (stock.isMenuItem) stock.itemName else "",
                dialogCustomItemName = if (!stock.isMenuItem) stock.itemName else "",
                dialogQuantity = stock.quantity.toString(),
                dialogMinQuantity = stock.minQuantity.toString(),
                dialogCostPrice = stock.costPrice.toString(),
                dialogUnit = stock.unit,
                dialogAlertEnabled = stock.alertEnabled,
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingStock = null) }
    }

    fun toggleDialogMode(isIndependent: Boolean) {
        _uiState.update {
            it.copy(
                dialogIsIndependent = isIndependent,
                dialogSelectedItemId = "",
                dialogSelectedItemName = "",
                dialogCustomItemName = "",
            )
        }
    }

    fun selectItem(item: Item) {
        _uiState.update {
            it.copy(dialogSelectedItemId = item.id, dialogSelectedItemName = item.name)
        }
    }

    fun updateDialogCustomItemName(v: String) { _uiState.update { it.copy(dialogCustomItemName = v) } }
    fun updateDialogQuantity(v: String) { _uiState.update { it.copy(dialogQuantity = v) } }
    fun updateDialogMinQuantity(v: String) { _uiState.update { it.copy(dialogMinQuantity = v) } }
    fun updateDialogCostPrice(v: String) { _uiState.update { it.copy(dialogCostPrice = v) } }
    fun updateDialogUnit(v: String) { _uiState.update { it.copy(dialogUnit = v) } }
    fun updateDialogAlertEnabled(v: Boolean) { _uiState.update { it.copy(dialogAlertEnabled = v) } }

    fun saveStockItem() {
        val s = _uiState.value

        // Validation
        val itemName = if (s.dialogIsIndependent) s.dialogCustomItemName else s.dialogSelectedItemName
        val itemId = if (s.dialogIsIndependent) null else s.dialogSelectedItemId.takeIf { it.isNotBlank() }

        if (itemName.isBlank() || s.dialogQuantity.isBlank() || s.dialogCostPrice.isBlank()) return
        if (!s.dialogIsIndependent && s.dialogSelectedItemId.isBlank()) return

        val quantity = s.dialogQuantity.toIntOrNull() ?: return
        val minQuantity = s.dialogMinQuantity.toIntOrNull() ?: 5
        val costPrice = s.dialogCostPrice.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = if (s.editingStock != null) {
                stockRepository.updateStockItem(
                    id = s.editingStock.id,
                    itemName = if (!s.editingStock.isMenuItem) itemName else null, // Only update name for independent items
                    quantity = quantity,
                    minQuantity = minQuantity,
                    costPrice = costPrice,
                    unit = s.dialogUnit,
                    alertEnabled = s.dialogAlertEnabled,
                )
            } else {
                stockRepository.addStockItem(
                    itemId = itemId,
                    itemName = itemName,
                    quantity = quantity,
                    minQuantity = minQuantity,
                    costPrice = costPrice,
                    unit = s.dialogUnit,
                    alertEnabled = s.dialogAlertEnabled,
                )
            }

            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    // ─── Transactions Tab ────────────────────────────────────────
    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(transactionsLoading = true) }
            stockRepository.getAllTransactions(limit = 100)
                .onSuccess { transactions ->
                    _uiState.update { it.copy(transactions = transactions, transactionsLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(transactionsLoading = false, error = e.message) }
                }
        }
    }

    // ─── Quantity Dialog ────────────────────────────────────────────
    fun showAddQuantityDialog(stock: Stock) {
        _uiState.update {
            it.copy(
                showQuantityDialog = true,
                quantityDialogStock = stock,
                quantityDialogIsAdd = true,
                quantityDialogAmount = "",
                quantityDialogNote = "",
            )
        }
    }

    fun showDeductQuantityDialog(stock: Stock) {
        _uiState.update {
            it.copy(
                showQuantityDialog = true,
                quantityDialogStock = stock,
                quantityDialogIsAdd = false,
                quantityDialogAmount = "",
                quantityDialogNote = "",
            )
        }
    }

    fun dismissQuantityDialog() {
        _uiState.update { it.copy(showQuantityDialog = false, quantityDialogStock = null) }
    }

    fun updateQuantityDialogAmount(v: String) { _uiState.update { it.copy(quantityDialogAmount = v) } }
    fun updateQuantityDialogNote(v: String) { _uiState.update { it.copy(quantityDialogNote = v) } }

    fun confirmQuantityChange() {
        val s = _uiState.value
        val stock = s.quantityDialogStock ?: return
        val amount = s.quantityDialogAmount.toIntOrNull() ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = if (s.quantityDialogIsAdd) {
                stockRepository.addQuantity(stock.id, amount, s.quantityDialogNote.ifBlank { null })
            } else {
                stockRepository.deductQuantity(stock.id, amount, note = s.quantityDialogNote.ifBlank { null })
            }

            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, showQuantityDialog = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    // ─── Delete ─────────────────────────────────────────────────────
    fun showDeleteConfirmation(stock: Stock) {
        _uiState.update { it.copy(showDeleteDialog = true, deletingStock = stock) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, deletingStock = null) }
    }

    fun confirmDelete() {
        val stock = _uiState.value.deletingStock ?: return
        viewModelScope.launch {
            stockRepository.deleteStockItem(stock.id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(showDeleteDialog = false, deletingStock = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
