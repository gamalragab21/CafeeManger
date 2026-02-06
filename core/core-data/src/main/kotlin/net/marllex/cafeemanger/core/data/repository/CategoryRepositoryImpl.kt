package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.CategoryDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.CategoryRepository
import net.marllex.cafeemanger.core.model.Category
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.CreateCategoryRequest
import net.marllex.cafeemanger.core.network.dto.ReorderCategoriesRequest
import net.marllex.cafeemanger.core.network.dto.UpdateCategoryRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val categoryDao: CategoryDao,
    private val authRepository: AuthRepository,
) : CategoryRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getCategories(): Flow<List<Category>> =
        categoryDao.getCategories(vendorId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshCategories(): Result<List<Category>> = runCatching {
        val response = api.getCategories()
        val categories = response.map { it.toDomain() }
        categoryDao.deleteAllCategories(vendorId)
        categoryDao.insertCategories(categories.map { it.toEntity() })
        categories
    }

    override suspend fun createCategory(name: String, displayOrder: Int): Result<Category> =
        runCatching {
            val response = api.createCategory(CreateCategoryRequest(name, displayOrder))
            val category = response.toDomain()
            categoryDao.insertCategory(category.toEntity())
            category
        }

    override suspend fun updateCategory(id: String, name: String?, displayOrder: Int?): Result<Category> =
        runCatching {
            val response = api.updateCategory(id, UpdateCategoryRequest(name, displayOrder))
            val category = response.toDomain()
            categoryDao.insertCategory(category.toEntity())
            category
        }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        api.deleteCategory(id)
        categoryDao.deleteCategory(id)
    }

    override suspend fun reorderCategories(orderedIds: List<String>): Result<List<Category>> =
        runCatching {
            val response = api.reorderCategories(ReorderCategoriesRequest(orderedIds))
            val categories = response.map { it.toDomain() }
            categoryDao.deleteAllCategories(vendorId)
            categoryDao.insertCategories(categories.map { it.toEntity() })
            categories
        }
}
