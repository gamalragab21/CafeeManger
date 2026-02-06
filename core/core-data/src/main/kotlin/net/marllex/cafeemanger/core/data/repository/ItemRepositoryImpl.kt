package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.ItemDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.ItemRepository
import net.marllex.cafeemanger.core.model.Item
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.CreateItemRequest
import net.marllex.cafeemanger.core.network.dto.UpdateItemRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class ItemRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
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
        itemDao.insertItems(items.map { it.toEntity() })
        items
    }

    override suspend fun createItem(
        categoryId: String, name: String, description: String?,
        price: Double, imageUrl: String?, available: Boolean
    ): Result<Item> = runCatching {
        val response = api.createItem(CreateItemRequest(categoryId, name, description, price, imageUrl, available))
        val item = response.toDomain()
        itemDao.insertItem(item.toEntity())
        item
    }

    override suspend fun updateItem(
        id: String, categoryId: String?, name: String?,
        description: String?, price: Double?, imageUrl: String?, available: Boolean?
    ): Result<Item> = runCatching {
        val response = api.updateItem(id, UpdateItemRequest(categoryId, name, description, price, imageUrl, available))
        val item = response.toDomain()
        itemDao.insertItem(item.toEntity())
        item
    }

    override suspend fun deleteItem(id: String): Result<Unit> = runCatching {
        api.deleteItem(id)
        itemDao.deleteItem(id)
    }

    override suspend fun toggleAvailability(id: String, available: Boolean): Result<Item> = runCatching {
        val response = api.toggleItemAvailability(id, UpdateItemRequest(available = available))
        val item = response.toDomain()
        itemDao.insertItem(item.toEntity())
        item
    }
}
