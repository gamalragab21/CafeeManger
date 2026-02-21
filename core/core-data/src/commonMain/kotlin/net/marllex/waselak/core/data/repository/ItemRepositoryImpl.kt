package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.ItemDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateItemRequest
import net.marllex.waselak.core.network.dto.UpdateItemRequest
import net.marllex.waselak.core.network.mapper.toDomain

class ItemRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val itemDao: ItemDao,
    private val authRepository: AuthRepository,
) : ItemRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getItems(categoryId: String?): Flow<List<Item>> =
        if (categoryId != null) {
            itemDao.getItemsByCategory(vendorId, categoryId).map { list -> list.map { it.toDomain() } }
        } else {
            itemDao.getItems(vendorId).map { list -> list.map { it.toDomain() } }
        }

    override fun getAvailableItems(): Flow<List<Item>> =
        itemDao.getAvailableItems(vendorId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshItems(): Result<List<Item>> = runCatching {
        val response = api.getItems()
        val items = response.map { it.toDomain() }
        itemDao.deleteAllItems(vendorId)
        itemDao.insertItems(items.map { it.toDbEntity() })
        items
    }

    override suspend fun createItem(
        categoryId: String, name: String, description: String?,
        price: Double, imageUrl: String?, available: Boolean
    ): Result<Item> = runCatching {
        val response = api.createItem(CreateItemRequest(
            categoryId = categoryId, name = name, description = description,
            price = price, imageUrl = imageUrl, available = available
        ))
        val item = response.toDomain()
        itemDao.insertItem(item.toDbEntity())
        item
    }

    override suspend fun updateItem(
        id: String, categoryId: String?, name: String?,
        description: String?, price: Double?, imageUrl: String?, available: Boolean?
    ): Result<Item> = runCatching {
        val response = api.updateItem(id, UpdateItemRequest(
            categoryId = categoryId, name = name, description = description,
            price = price, imageUrl = imageUrl, available = available
        ))
        val item = response.toDomain()
        itemDao.insertItem(item.toDbEntity())
        item
    }

    override suspend fun deleteItem(id: String): Result<Unit> = runCatching {
        api.deleteItem(id)
        itemDao.deleteItem(id)
    }

    override suspend fun toggleAvailability(id: String, available: Boolean): Result<Item> = runCatching {
        val response = api.toggleItemAvailability(id, UpdateItemRequest(available = available))
        val item = response.toDomain()
        itemDao.insertItem(item.toDbEntity())
        item
    }
}
