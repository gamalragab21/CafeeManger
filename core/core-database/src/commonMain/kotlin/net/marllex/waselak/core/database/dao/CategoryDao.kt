package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Categories

class CategoryDao(private val db: WaselakDatabase) {
    private val queries get() = db.categoryQueries

    fun getCategories(vendorId: String): Flow<List<Categories>> =
        queries.getCategories(vendorId).asFlow().mapToList(Dispatchers.IO)

    fun getCategoryById(id: String): Flow<Categories?> =
        queries.getCategoryById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun insertCategories(categories: List<Categories>) {
        db.transaction {
            categories.forEach { category ->
                queries.insertCategory(
                    id = category.id,
                    vendor_id = category.vendor_id,
                    name = category.name,
                    display_order = category.display_order
                )
            }
        }
    }

    suspend fun insertCategory(category: Categories) {
        queries.insertCategory(
            id = category.id,
            vendor_id = category.vendor_id,
            name = category.name,
            display_order = category.display_order
        )
    }

    suspend fun deleteCategory(id: String) {
        queries.deleteCategory(id)
    }

    suspend fun deleteAllCategories(vendorId: String) {
        queries.deleteAllCategories(vendorId)
    }
}
