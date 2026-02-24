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
import net.marllex.waselak.core.domain.repository.IngredientInput
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.RecipeRepository
import net.marllex.waselak.core.domain.repository.StockRepository
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Recipe
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.StockSummary
import net.marllex.waselak.core.model.StockTransaction

data class RecipeIngredientForm(
    val stockId: String = "",
    val stockName: String = "",
    val quantity: String = "",
    val unit: String = "PIECE",
    val baseUnit: String = "PIECE",
)

/**
 * Smart baseUnit inference: derives the category's base unit from any unit in that category.
 * - Weight: GRAM, KILOGRAM → GRAM
 * - Volume: MILLILITER, LITER → MILLILITER
 * - Count: PIECE, DOZEN → PIECE
 * - Package: BOX, BAG, BOTTLE, CAN, PACK → itself (each is its own base)
 */
private fun inferBaseUnit(unit: String): String {
    return when (unit.uppercase()) {
        "GRAM", "KILOGRAM" -> "GRAM"
        "MILLILITER", "LITER" -> "MILLILITER"
        "PIECE", "DOZEN" -> "PIECE"
        else -> unit.uppercase()
    }
}

class StockViewModel constructor(
    private val stockRepository: StockRepository,
    private val itemRepository: ItemRepository,
    private val recipeRepository: RecipeRepository,
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

        // Recipes
        val recipes: List<Recipe> = emptyList(),
        val recipesLoading: Boolean = false,

        // Recipe Form (Create/Edit)
        val showRecipeSheet: Boolean = false,
        val editingRecipe: Recipe? = null,
        val recipeSelectedItemId: String = "",
        val recipeSelectedItemName: String = "",
        val recipeName: String = "",
        val recipeDescription: String = "",
        val recipeYieldQuantity: String = "1",
        val recipeYieldUnit: String = "PIECE",
        val recipeIngredients: List<RecipeIngredientForm> = emptyList(),
        val recipeSaving: Boolean = false,
        val recipeError: String? = null,
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

        // Items that don't have a recipe yet (for recipe creation dropdown)
        val itemsWithoutRecipe: List<Item>
            get() {
                val itemIdsWithRecipe = recipes.map { it.itemId }.toSet()
                return availableItems.filter { it.id !in itemIdsWithRecipe }
            }
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
                dialogUnit = "PIECE",
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

        val quantity = s.dialogQuantity.toDoubleOrNull() ?: return
        val minQuantity = s.dialogMinQuantity.toDoubleOrNull() ?: 5.0
        val costPrice = s.dialogCostPrice.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val smartBaseUnit = inferBaseUnit(s.dialogUnit)

            val result = if (s.editingStock != null) {
                stockRepository.updateStockItem(
                    id = s.editingStock.id,
                    itemName = if (!s.editingStock.isMenuItem) itemName else null, // Only update name for independent items
                    quantity = quantity,
                    minQuantity = minQuantity,
                    costPrice = costPrice,
                    unit = s.dialogUnit,
                    baseUnit = smartBaseUnit,
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
                    baseUnit = smartBaseUnit,
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
        val amount = s.quantityDialogAmount.toDoubleOrNull() ?: return
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

    // ─── Recipes ─────────────────────────────────────────────────────
    fun loadRecipes() {
        viewModelScope.launch {
            _uiState.update { it.copy(recipesLoading = true) }
            recipeRepository.refreshRecipes()
            recipeRepository.getAllRecipes()
                .catch { e ->
                    _uiState.update { it.copy(recipesLoading = false, error = e.message) }
                }
                .collect { recipes ->
                    _uiState.update { it.copy(recipes = recipes, recipesLoading = false) }
                }
        }
    }

    fun deleteRecipe(id: String) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(id).onSuccess {
                loadRecipes()
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Recipe Form (Create/Edit) ──────────────────────────────────

    fun showAddRecipeSheet() {
        _uiState.update {
            it.copy(
                showRecipeSheet = true,
                editingRecipe = null,
                recipeSelectedItemId = "",
                recipeSelectedItemName = "",
                recipeName = "",
                recipeDescription = "",
                recipeYieldQuantity = "1",
                recipeYieldUnit = "PIECE",
                recipeIngredients = listOf(RecipeIngredientForm()),
                recipeSaving = false,
                recipeError = null,
            )
        }
    }

    fun showEditRecipeSheet(recipe: Recipe) {
        _uiState.update { state ->
            val stockMap = state.stockItems.associateBy { it.id }
            state.copy(
                showRecipeSheet = true,
                editingRecipe = recipe,
                recipeSelectedItemId = recipe.itemId,
                recipeSelectedItemName = recipe.itemName,
                recipeName = recipe.name,
                recipeDescription = recipe.description ?: "",
                recipeYieldQuantity = recipe.yieldQuantity.toString(),
                recipeYieldUnit = recipe.yieldUnit,
                recipeIngredients = recipe.ingredients.map { ing ->
                    val stock = stockMap[ing.stockId]
                    RecipeIngredientForm(
                        stockId = ing.stockId,
                        stockName = ing.stockItemName,
                        quantity = ing.quantity.toString(),
                        unit = ing.unit,
                        baseUnit = stock?.baseUnit ?: ing.unit,
                    )
                }.ifEmpty { listOf(RecipeIngredientForm()) },
                recipeSaving = false,
                recipeError = null,
            )
        }
    }

    fun dismissRecipeSheet() {
        _uiState.update { it.copy(showRecipeSheet = false) }
    }

    fun updateRecipeName(v: String) { _uiState.update { it.copy(recipeName = v) } }
    fun updateRecipeDescription(v: String) { _uiState.update { it.copy(recipeDescription = v) } }
    fun updateRecipeYieldQuantity(v: String) { _uiState.update { it.copy(recipeYieldQuantity = v) } }
    fun updateRecipeYieldUnit(v: String) { _uiState.update { it.copy(recipeYieldUnit = v) } }

    fun selectRecipeItem(item: Item) {
        _uiState.update {
            it.copy(
                recipeSelectedItemId = item.id,
                recipeSelectedItemName = item.name,
                recipeName = it.recipeName.ifBlank { item.name },
            )
        }
    }

    fun addRecipeIngredient() {
        _uiState.update {
            it.copy(recipeIngredients = it.recipeIngredients + RecipeIngredientForm())
        }
    }

    fun removeRecipeIngredient(index: Int) {
        _uiState.update {
            it.copy(
                recipeIngredients = it.recipeIngredients.toMutableList().apply {
                    if (size > 1) removeAt(index)
                }
            )
        }
    }

    fun updateRecipeIngredient(index: Int, form: RecipeIngredientForm) {
        _uiState.update {
            it.copy(
                recipeIngredients = it.recipeIngredients.toMutableList().apply {
                    if (index in indices) set(index, form)
                }
            )
        }
    }

    fun selectIngredientStock(index: Int, stock: Stock) {
        _uiState.update {
            val updated = it.recipeIngredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(
                    stockId = stock.id,
                    stockName = stock.itemName,
                    unit = stock.unit,
                    baseUnit = stock.baseUnit,
                )
            }
            it.copy(recipeIngredients = updated)
        }
    }

    fun saveRecipe() {
        val s = _uiState.value

        // Validation
        if (s.recipeSelectedItemId.isBlank()) return
        if (s.recipeName.isBlank()) return
        val yieldQty = s.recipeYieldQuantity.toDoubleOrNull() ?: return
        if (yieldQty <= 0) return

        val ingredients = s.recipeIngredients.mapIndexedNotNull { idx, form ->
            val qty = form.quantity.toDoubleOrNull() ?: return@mapIndexedNotNull null
            if (qty <= 0 || form.stockId.isBlank()) return@mapIndexedNotNull null
            IngredientInput(
                stockId = form.stockId,
                quantity = qty,
                unit = form.unit,
                displayOrder = idx,
            )
        }
        if (ingredients.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(recipeSaving = true) }

            val result = if (s.editingRecipe != null) {
                recipeRepository.updateRecipe(
                    id = s.editingRecipe.id,
                    name = s.recipeName,
                    description = s.recipeDescription.ifBlank { null },
                    yieldQuantity = yieldQty,
                    yieldUnit = s.recipeYieldUnit,
                    ingredients = ingredients,
                )
            } else {
                recipeRepository.createRecipe(
                    itemId = s.recipeSelectedItemId,
                    name = s.recipeName,
                    description = s.recipeDescription.ifBlank { null },
                    yieldQuantity = yieldQty,
                    yieldUnit = s.recipeYieldUnit,
                    ingredients = ingredients,
                )
            }

            result.onSuccess {
                _uiState.update { it.copy(recipeSaving = false, showRecipeSheet = false, recipeError = null) }
                loadRecipes()
            }.onFailure { e ->
                _uiState.update { it.copy(recipeSaving = false, recipeError = e.message) }
            }
        }
    }
}
