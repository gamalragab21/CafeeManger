package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.CategoryDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.model.Category
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateCategoryRequest
import net.marllex.waselak.core.network.dto.ReorderCategoriesRequest
import net.marllex.waselak.core.network.dto.UpdateCategoryRequest
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.mapper.toDomain

class CategoryRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val categoryDao: CategoryDao,
    private val authRepository: AuthRepository,
) : CategoryRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getCategories(): Flow<List<Category>> =
        categoryDao.getCategories(vendorId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshCategories(): Result<List<Category>> = runCatching {
        AppLogger.d("CategoryRepo", "Refreshing categories")
        val response = api.getCategories()
        val categories = response.map { it.toDomain() }
        AppLogger.i("CategoryRepo", "Fetched ${categories.size} categories")
        categoryDao.deleteAllCategories(vendorId)
        categoryDao.insertCategories(categories.map { it.toDbEntity() })
        categories
    }

    override suspend fun createCategory(name: String, displayOrder: Int): Result<Category> =
        runCatching {
            AppLogger.d("CategoryRepo", "Creating category: name=$name")
            val response = api.createCategory(CreateCategoryRequest(name, displayOrder))
            val category = response.toDomain()
            AppLogger.i("CategoryRepo", "Category created: id=${category.id}")
            categoryDao.insertCategory(category.toDbEntity())
            category
        }

    override suspend fun updateCategory(id: String, name: String?, displayOrder: Int?): Result<Category> =
        runCatching {
            AppLogger.d("CategoryRepo", "Updating category: id=$id")
            val response = api.updateCategory(id, UpdateCategoryRequest(name, displayOrder))
            val category = response.toDomain()
            categoryDao.insertCategory(category.toDbEntity())
            category
        }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        AppLogger.d("CategoryRepo", "Deleting category: id=$id")
        api.deleteCategory(id)
        categoryDao.deleteCategory(id)
        AppLogger.i("CategoryRepo", "Category deleted: id=$id")
    }

    override suspend fun reorderCategories(orderedIds: List<String>): Result<List<Category>> =
        runCatching {
            AppLogger.d("CategoryRepo", "Reordering ${orderedIds.size} categories")
            val response = api.reorderCategories(ReorderCategoriesRequest(orderedIds))
            val categories = response.map { it.toDomain() }
            categoryDao.deleteAllCategories(vendorId)
            categoryDao.insertCategories(categories.map { it.toDbEntity() })
            categories
        }
}
