package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.VariantGroup

interface ItemRepository {
    fun getItems(categoryId: String? = null): Flow<List<Item>>
    fun getAvailableItems(): Flow<List<Item>>
    fun getItemByBarcode(barcode: String): Flow<Item?>
    suspend fun refreshItems(): Result<List<Item>>
    suspend fun createItem(
        categoryId: String, name: String, description: String?,
        price: Double, imageUrl: String?, available: Boolean,
        barcode: String? = null, sku: String? = null,
    ): Result<Item>
    suspend fun updateItem(
        id: String, categoryId: String?, name: String?,
        description: String?, price: Double?, imageUrl: String?,
        available: Boolean?, barcode: String? = null, sku: String? = null,
    ): Result<Item>
    suspend fun deleteItem(id: String): Result<Unit>
    suspend fun toggleAvailability(id: String, available: Boolean): Result<Item>
    suspend fun updateItemVariants(itemId: String, groups: List<VariantGroup>): Result<Item>
}
