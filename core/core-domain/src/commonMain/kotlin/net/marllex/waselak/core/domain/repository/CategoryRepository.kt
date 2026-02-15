package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Category

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun refreshCategories(): Result<List<Category>>
    suspend fun createCategory(name: String, displayOrder: Int): Result<Category>
    suspend fun updateCategory(id: String, name: String?, displayOrder: Int?): Result<Category>
    suspend fun deleteCategory(id: String): Result<Unit>
    suspend fun reorderCategories(orderedIds: List<String>): Result<List<Category>>
}
