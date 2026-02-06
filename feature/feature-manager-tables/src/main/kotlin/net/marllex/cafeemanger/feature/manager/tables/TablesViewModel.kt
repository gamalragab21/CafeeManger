package net.marllex.cafeemanger.feature.manager.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.TableRepository
import net.marllex.cafeemanger.core.model.Table
import net.marllex.cafeemanger.core.model.TableStatus
import javax.inject.Inject

@HiltViewModel
class TablesViewModel @Inject constructor(
    private val tableRepository: TableRepository,
) : ViewModel() {

    data class UiState(
        val tables: List<Table> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showAddDialog: Boolean = false,
        val editingTable: Table? = null,
        val dialogNumber: String = "",
        val dialogCapacity: String = "4",
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadTables() }

    fun loadTables() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            tableRepository.refreshTables()
            tableRepository.getTables()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { tables -> _uiState.update { it.copy(tables = tables, isLoading = false) } }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingTable = null, dialogNumber = "", dialogCapacity = "4") }
    }

    fun showEditDialog(table: Table) {
        _uiState.update {
            it.copy(showAddDialog = true, editingTable = table, dialogNumber = table.number, dialogCapacity = table.capacity.toString())
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingTable = null) }
    }

    fun updateDialogNumber(v: String) { _uiState.update { it.copy(dialogNumber = v) } }
    fun updateDialogCapacity(v: String) { _uiState.update { it.copy(dialogCapacity = v) } }

    fun saveTable() {
        val s = _uiState.value
        if (s.dialogNumber.isBlank()) return
        val capacity = s.dialogCapacity.toIntOrNull() ?: 4

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (s.editingTable != null) {
                tableRepository.updateTable(s.editingTable.id, s.dialogNumber, capacity)
            } else {
                tableRepository.createTable(s.dialogNumber, capacity)
            }
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateStatus(table: Table, newStatus: TableStatus) {
        viewModelScope.launch {
            tableRepository.updateTableStatus(table.id, newStatus.name)
        }
    }

    fun deleteTable(id: String) {
        viewModelScope.launch {
            tableRepository.deleteTable(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
