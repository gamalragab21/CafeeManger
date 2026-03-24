package net.marllex.waselak.feature.manager.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.model.Category
import net.marllex.waselak.core.common.logging.AppLogger

class CategoriesViewModel constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Categories" }


    data class UiState(
        val categories: List<Category> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showDialog: Boolean = false,
        val editingCategory: Category? = null,
        val dialogName: String = "",
        val dialogDisplayOrder: String = "0",
        val isSaving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadCategories() }

    fun loadCategories() {
        AppLogger.d(TAG, "loadCategories called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            categoryRepository.refreshCategories()
            categoryRepository.getCategories()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { categories -> _uiState.update { it.copy(categories = categories, isLoading = false) } }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(showDialog = true, editingCategory = null, dialogName = "", dialogDisplayOrder = "${it.categories.size}")
        }
    }

    fun showEditDialog(category: Category) {
        _uiState.update {
            it.copy(showDialog = true, editingCategory = category, dialogName = category.name, dialogDisplayOrder = category.displayOrder.toString())
        }
    }

    fun dismissDialog() { _uiState.update { it.copy(showDialog = false, editingCategory = null) } }
    fun updateDialogName(v: String) { _uiState.update { it.copy(dialogName = v) } }
    fun updateDialogDisplayOrder(v: String) { _uiState.update { it.copy(dialogDisplayOrder = v) } }

    fun saveCategory() {
        AppLogger.d(TAG, "saveCategory called")
        val s = _uiState.value
        if (s.dialogName.isBlank()) return
        val order = s.dialogDisplayOrder.toIntOrNull() ?: 0

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (s.editingCategory != null) {
                categoryRepository.updateCategory(s.editingCategory.id, s.dialogName, order)
            } else {
                categoryRepository.createCategory(s.dialogName, order)
            }
            result.onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                _uiState.update { it.copy(isSaving = false, showDialog = false) }
            }.onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteCategory(id: String) {
        AppLogger.d(TAG, "deleteCategory called")
        viewModelScope.launch {
            categoryRepository.deleteCategory(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
