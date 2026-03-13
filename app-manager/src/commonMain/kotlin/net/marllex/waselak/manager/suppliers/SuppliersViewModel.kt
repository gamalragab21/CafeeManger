package net.marllex.waselak.manager.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.SupplierRepository
import net.marllex.waselak.core.model.PurchaseOrder
import net.marllex.waselak.core.model.Supplier
import net.marllex.waselak.core.network.dto.CreateSupplierRequest
import net.marllex.waselak.core.network.dto.UpdateSupplierRequest

class SuppliersViewModel(
    private val supplierRepository: SupplierRepository,
) : ViewModel() {

    data class UiState(
        val suppliers: List<Supplier> = emptyList(),
        val purchaseOrders: List<PurchaseOrder> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedTab: Int = 0, // 0=Suppliers, 1=Purchase Orders
        val searchQuery: String = "",
        // Add Dialog
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
    ) {
        val filteredSuppliers: List<Supplier>
            get() = if (searchQuery.isBlank()) suppliers
            else suppliers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.contactName?.contains(searchQuery, ignoreCase = true) == true)
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            supplierRepository.getSuppliers()
                .onSuccess { list -> _uiState.update { it.copy(suppliers = list, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
        viewModelScope.launch {
            supplierRepository.getPurchaseOrders()
                .onSuccess { list -> _uiState.update { it.copy(purchaseOrders = list) } }
        }
    }

    fun onSearchQueryChange(query: String) { _uiState.update { it.copy(searchQuery = query) } }
    fun onTabChange(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }

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

    fun confirmDelete(supplier: Supplier) { _uiState.update { it.copy(showDeleteConfirm = true, deletingSupplier = supplier) } }
    fun dismissDelete() { _uiState.update { it.copy(showDeleteConfirm = false, deletingSupplier = null) } }
    fun deleteSupplier() {
        val s = _uiState.value.deletingSupplier ?: return
        viewModelScope.launch {
            supplierRepository.deleteSupplier(s.id)
                .onSuccess { dismissDelete(); load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
