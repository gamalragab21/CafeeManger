package net.marllex.waselak.manager.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.StockRepository
import net.marllex.waselak.core.domain.repository.SupplierRepository
import net.marllex.waselak.core.model.PurchaseOrder
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.network.dto.CreatePurchaseOrderItemRequest
import net.marllex.waselak.core.network.dto.CreatePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.CreateSupplierRequest
import net.marllex.waselak.core.network.dto.ReceiveItemRequest
import net.marllex.waselak.core.network.dto.ReceivePurchaseOrderRequest
import net.marllex.waselak.core.network.dto.UpdateSupplierRequest
import net.marllex.waselak.core.common.logging.AppLogger

data class PoItemDraft(
    val stockId: String,
    val stockName: String,
    val quantity: Double = 1.0,
    val unitCost: Double = 0.0,
    val unit: String = "PIECE",
)

data class ReceiveItemDraft(
    val poItemId: String,
    val stockName: String,
    val requestedQty: Double,
    val alreadyReceived: Double,
    val receivedQty: Double = 0.0,
    val batchNumber: String = "",
    val expiryDate: String = "",
    val noExpiry: Boolean = false,
    val unit: String = "PIECE",
)

class SuppliersViewModel(
    private val supplierRepository: SupplierRepository,
    private val stockRepository: StockRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Suppliers" }


    data class UiState(
        val suppliers: List<Supplier> = emptyList(),
        val purchaseOrders: List<PurchaseOrder> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedTab: Int = 0, // 0=Suppliers, 1=Purchase Orders
        val searchQuery: String = "",
        // Add/Edit Supplier Dialog
        val showAddDialog: Boolean = false,
        val editingSupplier: Supplier? = null,
        val dialogName: String = "",
        val dialogContactName: String = "",
        val dialogPhone: String = "",
        val dialogEmail: String = "",
        val dialogAddress: String = "",
        val dialogNotes: String = "",
        val isSaving: Boolean = false,
        val showDeleteConfirm: Boolean = false,
        val deletingSupplier: Supplier? = null,
        // PO Management
        val stockItems: List<Stock> = emptyList(),
        val showCreatePoDialog: Boolean = false,
        val poSupplierId: String = "",
        val poItems: List<PoItemDraft> = emptyList(),
        val poNotes: String = "",
        val poExpectedDate: String = "",
        val isCreatingPo: Boolean = false,
        val showPoDetail: PurchaseOrder? = null,
        val showReceiveDialog: PurchaseOrder? = null,
        val receiveItems: List<ReceiveItemDraft> = emptyList(),
        val showCancelConfirm: PurchaseOrder? = null,
        val successMessage: String? = null,
    ) {
        val filteredSuppliers: List<Supplier>
            get() = if (searchQuery.isBlank()) suppliers
            else suppliers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.contactName?.contains(searchQuery, ignoreCase = true) == true)
            }

        val poSubtotal: Double
            get() = poItems.sumOf { it.quantity * it.unitCost }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadStock()
    }

    fun load() {
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            supplierRepository.getSuppliers()
                .onSuccess { list -> _uiState.update { it.copy(suppliers = list, isLoading = false) } }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
        viewModelScope.launch {
            supplierRepository.getPurchaseOrders()
                .onSuccess { list -> _uiState.update { it.copy(purchaseOrders = list) } }
        }
    }

    private fun loadStock() {
        viewModelScope.launch {
            stockRepository.refreshStock()
                .onSuccess { list -> _uiState.update { it.copy(stockItems = list) } }
        }
    }

    fun onSearchQueryChange(query: String) { _uiState.update { it.copy(searchQuery = query) } }
    fun onTabChange(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }
    fun clearSuccessMessage() { _uiState.update { it.copy(successMessage = null) } }

    // --- Supplier CRUD ---

    fun showAddDialog(supplier: Supplier? = null) {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingSupplier = supplier,
                dialogName = supplier?.name ?: "",
                dialogContactName = supplier?.contactName ?: "",
                dialogPhone = supplier?.phone ?: "",
                dialogEmail = supplier?.email ?: "",
                dialogAddress = supplier?.address ?: "",
                dialogNotes = supplier?.notes ?: "",
            )
        }
    }

    fun dismissAddDialog() { _uiState.update { it.copy(showAddDialog = false, editingSupplier = null) } }

    fun onDialogNameChange(v: String) { _uiState.update { it.copy(dialogName = v) } }
    fun onDialogContactNameChange(v: String) { _uiState.update { it.copy(dialogContactName = v) } }
    fun onDialogPhoneChange(v: String) { _uiState.update { it.copy(dialogPhone = v) } }
    fun onDialogEmailChange(v: String) { _uiState.update { it.copy(dialogEmail = v) } }
    fun onDialogAddressChange(v: String) { _uiState.update { it.copy(dialogAddress = v) } }
    fun onDialogNotesChange(v: String) { _uiState.update { it.copy(dialogNotes = v) } }

    fun saveSupplier() {
        AppLogger.d(TAG, "saveSupplier called")
        val s = _uiState.value
        if (s.dialogName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (s.editingSupplier != null) {
                supplierRepository.updateSupplier(s.editingSupplier.id, UpdateSupplierRequest(
                    name = s.dialogName, contactName = s.dialogContactName.ifBlank { null },
                    phone = s.dialogPhone.ifBlank { null }, email = s.dialogEmail.ifBlank { null },
                    address = s.dialogAddress.ifBlank { null }, notes = s.dialogNotes.ifBlank { null },
                ))
            } else {
                supplierRepository.createSupplier(CreateSupplierRequest(
                    name = s.dialogName, contactName = s.dialogContactName.ifBlank { null },
                    phone = s.dialogPhone.ifBlank { null }, email = s.dialogEmail.ifBlank { null },
                    address = s.dialogAddress.ifBlank { null }, notes = s.dialogNotes.ifBlank { null },
                ))
            }
            result
                .onSuccess { _uiState.update { it.copy(isSaving = false, showAddDialog = false) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun confirmDelete(supplier: Supplier) {
        AppLogger.d(TAG, "confirmDelete called"); _uiState.update { it.copy(showDeleteConfirm = true, deletingSupplier = supplier) } }
    fun dismissDelete() { _uiState.update { it.copy(showDeleteConfirm = false, deletingSupplier = null) } }
    fun deleteSupplier() {
        AppLogger.d(TAG, "deleteSupplier called")
        val s = _uiState.value.deletingSupplier ?: return
        viewModelScope.launch {
            supplierRepository.deleteSupplier(s.id)
                .onSuccess { dismissDelete(); load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // --- Purchase Order Management ---

    fun showCreatePo() {
        _uiState.update {
            it.copy(
                showCreatePoDialog = true,
                poSupplierId = "",
                poItems = emptyList(),
                poNotes = "",
                poExpectedDate = "",
            )
        }
    }

    fun dismissCreatePo() { _uiState.update { it.copy(showCreatePoDialog = false) } }
    fun setPoSupplier(supplierId: String) { _uiState.update { it.copy(poSupplierId = supplierId) } }
    fun setPoNotes(v: String) { _uiState.update { it.copy(poNotes = v) } }
    fun setPoExpectedDate(v: String) { _uiState.update { it.copy(poExpectedDate = v) } }

    fun addPoItem(stock: Stock) {
        AppLogger.d(TAG, "addPoItem called")
        _uiState.update { state ->
            if (state.poItems.any { it.stockId == stock.id }) return
            state.copy(
                poItems = state.poItems + PoItemDraft(
                    stockId = stock.id,
                    stockName = stock.itemName,
                    quantity = 1.0,
                    unitCost = stock.costPrice,
                    unit = stock.unit,
                ),
            )
        }
    }

    fun updatePoItemQuantity(index: Int, qty: Double) {
        _uiState.update { state ->
            state.copy(poItems = state.poItems.toMutableList().apply {
                this[index] = this[index].copy(quantity = qty.coerceAtLeast(0.1))
            })
        }
    }

    fun updatePoItemCost(index: Int, cost: Double) {
        _uiState.update { state ->
            state.copy(poItems = state.poItems.toMutableList().apply {
                this[index] = this[index].copy(unitCost = cost.coerceAtLeast(0.0))
            })
        }
    }

    fun removePoItem(index: Int) {
        AppLogger.d(TAG, "removePoItem called")
        _uiState.update { state ->
            state.copy(poItems = state.poItems.toMutableList().apply { removeAt(index) })
        }
    }

    fun createPurchaseOrder() {
        AppLogger.d(TAG, "createPurchaseOrder called")
        val s = _uiState.value
        if (s.poSupplierId.isBlank() || s.poItems.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPo = true) }
            val request = CreatePurchaseOrderRequest(
                supplierId = s.poSupplierId,
                notes = s.poNotes.ifBlank { null },
                expectedDeliveryDate = s.poExpectedDate.ifBlank { null },
                items = s.poItems.map {
                    CreatePurchaseOrderItemRequest(
                        stockId = it.stockId,
                        requestedQuantity = it.quantity,
                        unitCost = it.unitCost,
                        unit = it.unit,
                    )
                },
            )
            supplierRepository.createPurchaseOrder(request)
                .onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(isCreatingPo = false, showCreatePoDialog = false, successMessage = "po_created") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isCreatingPo = false, error = e.message) } }
        }
    }

    // PO Detail
    fun showPoDetail(po: PurchaseOrder) {
        viewModelScope.launch {
            supplierRepository.getPurchaseOrder(po.id)
                .onSuccess { fullPo -> _uiState.update { it.copy(showPoDetail = fullPo) } }
                .onFailure { _uiState.update { it.copy(showPoDetail = po) } }
        }
    }
    fun dismissPoDetail() { _uiState.update { it.copy(showPoDetail = null) } }

    // Submit PO
    fun submitPo(id: String) {
        AppLogger.d(TAG, "submitPo called")
        viewModelScope.launch {
            supplierRepository.submitPurchaseOrder(id)
                .onSuccess {
                    _uiState.update { it.copy(showPoDetail = null, successMessage = "po_submitted") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // Receive
    fun showReceiveDialog(po: PurchaseOrder) {
        viewModelScope.launch {
            supplierRepository.getPurchaseOrder(po.id)
                .onSuccess { fullPo ->
                    _uiState.update {
                        it.copy(
                            showReceiveDialog = fullPo,
                            showPoDetail = null,
                            receiveItems = fullPo.items.filter { item -> !item.isFullyReceived }.map { item ->
                                ReceiveItemDraft(
                                    poItemId = item.id,
                                    stockName = item.stockName ?: item.stockId,
                                    requestedQty = item.requestedQuantity,
                                    alreadyReceived = item.receivedQuantity,
                                    receivedQty = item.pendingQuantity,
                                    unit = item.unit,
                                )
                            },
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun dismissReceiveDialog() { _uiState.update { it.copy(showReceiveDialog = null, receiveItems = emptyList()) } }

    fun updateReceiveQuantity(index: Int, qty: Double) {
        _uiState.update { state ->
            state.copy(receiveItems = state.receiveItems.toMutableList().apply {
                val item = this[index]
                this[index] = item.copy(receivedQty = qty.coerceIn(0.0, item.requestedQty - item.alreadyReceived))
            })
        }
    }

    fun updateBatchNumber(index: Int, v: String) {
        _uiState.update { state ->
            state.copy(receiveItems = state.receiveItems.toMutableList().apply {
                this[index] = this[index].copy(batchNumber = v)
            })
        }
    }

    fun updateExpiryDate(index: Int, v: String) {
        _uiState.update { state ->
            state.copy(receiveItems = state.receiveItems.toMutableList().apply {
                this[index] = this[index].copy(expiryDate = v)
            })
        }
    }

    fun toggleNoExpiry(index: Int) {
        AppLogger.d(TAG, "toggleNoExpiry called")
        _uiState.update { state ->
            state.copy(receiveItems = state.receiveItems.toMutableList().apply {
                val item = this[index]
                this[index] = item.copy(noExpiry = !item.noExpiry, expiryDate = "")
            })
        }
    }

    fun receivePo() {
        AppLogger.d(TAG, "receivePo called")
        val s = _uiState.value
        val po = s.showReceiveDialog ?: return
        val items = s.receiveItems.filter { it.receivedQty > 0 }
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPo = true) }
            val request = ReceivePurchaseOrderRequest(
                items = items.map {
                    ReceiveItemRequest(
                        purchaseOrderItemId = it.poItemId,
                        receivedQuantity = it.receivedQty,
                        batchNumber = it.batchNumber.ifBlank { null },
                        expiryDate = it.expiryDate.ifBlank { null },
                    )
                },
            )
            supplierRepository.receivePurchaseOrder(po.id, request)
                .onSuccess {
                    _uiState.update { it.copy(isCreatingPo = false, showReceiveDialog = null, receiveItems = emptyList(), successMessage = "items_received") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isCreatingPo = false, error = e.message) } }
        }
    }

    // Cancel
    fun confirmCancelPo(po: PurchaseOrder) {
        AppLogger.d(TAG, "confirmCancelPo called"); _uiState.update { it.copy(showCancelConfirm = po, showPoDetail = null) } }
    fun dismissCancelPo() { _uiState.update { it.copy(showCancelConfirm = null) } }

    fun cancelPo() {
        AppLogger.d(TAG, "cancelPo called")
        val po = _uiState.value.showCancelConfirm ?: return
        viewModelScope.launch {
            supplierRepository.deletePurchaseOrder(po.id)
                .onSuccess {
                    _uiState.update { it.copy(showCancelConfirm = null, successMessage = "po_cancelled") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
