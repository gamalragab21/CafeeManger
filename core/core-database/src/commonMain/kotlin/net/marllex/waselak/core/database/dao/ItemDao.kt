package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Items

class ItemDao(private val db: WaselakDatabase) {
    private val queries get() = db.itemQueries

    fun getItems(vendorId: String): Flow<List<Items>> =
        queries.getItems(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getItemsByCategory(vendorId: String, categoryId: String): Flow<List<Items>> =
        queries.getItemsByCategory(vendorId, categoryId).asFlow().mapToList(Dispatchers.Default)

    fun getAvailableItems(vendorId: String): Flow<List<Items>> =
        queries.getAvailableItems(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getItemById(id: String): Flow<Items?> =
        queries.getItemById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertItems(items: List<Items>) {
        db.transaction {
            items.forEach { item ->
                queries.insertItem(
                    id = item.id,
                    vendor_id = item.vendor_id,
                    category_id = item.category_id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    image_url = item.image_url,
                    available = item.available
                )
            }
        }
    }

    suspend fun insertItem(item: Items) {
        queries.insertItem(
            id = item.id,
            vendor_id = item.vendor_id,
            category_id = item.category_id,
            name = item.name,
            description = item.description,
            price = item.price,
            image_url = item.image_url,
            available = item.available
        )
    }

    suspend fun deleteItem(id: String) {
        queries.deleteItem(id)
    }

    suspend fun deleteAllItems(vendorId: String) {
        queries.deleteAllItems(vendorId)
    }
}
