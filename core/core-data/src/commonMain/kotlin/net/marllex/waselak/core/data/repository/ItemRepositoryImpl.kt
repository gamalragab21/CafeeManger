package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.ItemDao
import net.marllex.waselak.core.database.dao.ItemVariantDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.VariantGroup
import net.marllex.waselak.core.model.VariantOption
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateItemRequest
import net.marllex.waselak.core.network.dto.CreateVariantGroupRequest
import net.marllex.waselak.core.network.dto.CreateVariantOptionRequest
import net.marllex.waselak.core.network.dto.UpdateItemRequest
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.database.Item_variant_groups
import net.marllex.waselak.core.database.Item_variant_options

class ItemRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val itemDao: ItemDao,
    private val itemVariantDao: ItemVariantDao,
    private val authRepository: AuthRepository,
) : ItemRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    private suspend fun List<Item>.withVariants(): List<Item> {
        if (isEmpty()) return this
        // Batch load: 2 queries total instead of N+1
        val itemIds = map { it.id }
        val allGroups = itemIds.flatMap { id -> itemVariantDao.getVariantGroupsByItemList(id).map { id to it } }
            .groupBy({ it.first }, { it.second })
        val allGroupIds = allGroups.values.flatten().map { it.id }
        val allOptions = if (allGroupIds.isNotEmpty()) {
            allGroupIds.flatMap { gid -> itemVariantDao.getVariantOptionsByGroupList(gid).map { gid to it } }
                .groupBy({ it.first }, { it.second })
        } else emptyMap()

        return map { item ->
            val groups = allGroups[item.id]
            if (groups.isNullOrEmpty()) return@map item
            item.copy(variantGroups = groups.map { group ->
                val options = allOptions[group.id] ?: emptyList()
                group.toDomain(options.map { it.toDomain() })
            })
        }
    }

    override fun getItems(categoryId: String?): Flow<List<Item>> {
        AppLogger.d("ItemRepo", "Reading items from local DB: categoryId=$categoryId")
        val itemsFlow = if (categoryId != null) {
            itemDao.getItemsByCategory(vendorId, categoryId).map { list -> list.map { it.toDomain() } }
        } else {
            itemDao.getItems(vendorId).map { list -> list.map { it.toDomain() } }
        }
        return itemsFlow.map { items -> items.withVariants() }
    }

    override fun getAvailableItems(): Flow<List<Item>> {
        AppLogger.d("ItemRepo", "Reading available items from local DB")
        return itemDao.getAvailableItems(vendorId).map { list ->
            list.map { it.toDomain() }.withVariants()
        }
    }

    override fun getItemByBarcode(barcode: String): Flow<Item?> {
        AppLogger.d("ItemRepo", "Reading item by barcode from local DB: barcode=$barcode")
        return itemDao.getItemByBarcode(vendorId, barcode).map { dbItem ->
            val item = dbItem?.toDomain() ?: return@map null
            // Enrich with variants so barcode-scanned items show variant selector
            val groups = itemVariantDao.getVariantGroupsByItemList(item.id)
            if (groups.isEmpty()) return@map item
            val variantGroups = groups.map { group ->
                val options = itemVariantDao.getVariantOptionsByGroupList(group.id)
                group.toDomain(options.map { it.toDomain() })
            }
            item.copy(variantGroups = variantGroups)
        }
    }

    override suspend fun refreshItems(): Result<List<Item>> = runCatching {
        AppLogger.d("ItemRepo", "Refreshing items from API")
        val response = api.getItems()
        val items = response.map { it.toDomain() }
        AppLogger.i("ItemRepo", "Fetched ${items.size} items from API")
        AppLogger.d("ItemRepo", "Saving ${items.size} items to local DB")
        itemDao.deleteAllItems(vendorId)
        itemDao.insertItems(items.map { it.toDbEntity() })
        // Save variant groups locally for offline access
        AppLogger.d("ItemRepo", "Saving variant groups to local DB")
        itemVariantDao.deleteAllVariants()
        response.forEach { itemResponse ->
            if (itemResponse.variantGroups.isNotEmpty()) {
                val groups = itemResponse.variantGroups.map { g ->
                    Item_variant_groups(
                        id = g.id, item_id = itemResponse.id, name = g.name,
                        required = g.required, display_order = g.displayOrder
                    )
                }
                val options = itemResponse.variantGroups.flatMap { g ->
                    g.options.map { o ->
                        Item_variant_options(
                            id = o.id, group_id = g.id, name = o.name,
                            price_adjustment = o.priceAdjustment, is_default = o.isDefault,
                            display_order = o.displayOrder
                        )
                    }
                }
                itemVariantDao.insertVariantGroups(itemResponse.id, groups, options)
            }
        }
        AppLogger.i("ItemRepo", "Items refresh complete: ${items.size} items with variants saved")
        items
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to refresh items", e)
    }

    override suspend fun createItem(
        categoryId: String, name: String, description: String?,
        price: Double, imageUrl: String?, available: Boolean,
        barcode: String?, sku: String?,
    ): Result<Item> = runCatching {
        AppLogger.d("ItemRepo", "Creating item: name=$name, price=$price")
        val response = api.createItem(CreateItemRequest(
            categoryId = categoryId, name = name, description = description,
            price = price, imageUrl = imageUrl, available = available,
            barcode = barcode, sku = sku,
        ))
        val item = response.toDomain()
        AppLogger.d("ItemRepo", "Saving created item to local DB: id=${item.id}")
        itemDao.insertItem(item.toDbEntity())
        AppLogger.i("ItemRepo", "Item created: id=${item.id}, name=${item.name}")
        item
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to create item: name=$name", e)
    }

    override suspend fun updateItem(
        id: String, categoryId: String?, name: String?,
        description: String?, price: Double?, imageUrl: String?, available: Boolean?,
        barcode: String?, sku: String?,
    ): Result<Item> = runCatching {
        AppLogger.d("ItemRepo", "Updating item: id=$id, name=$name")
        val response = api.updateItem(id, UpdateItemRequest(
            categoryId = categoryId, name = name, description = description,
            price = price, imageUrl = imageUrl, available = available,
            barcode = barcode, sku = sku,
        ))
        val item = response.toDomain()
        AppLogger.d("ItemRepo", "Saving updated item to local DB: id=${item.id}")
        itemDao.insertItem(item.toDbEntity())
        AppLogger.i("ItemRepo", "Item updated: id=${item.id}, name=${item.name}")
        item
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to update item: id=$id", e)
    }

    override suspend fun deleteItem(id: String): Result<Unit> = runCatching {
        AppLogger.d("ItemRepo", "Deleting item: id=$id")
        api.deleteItem(id)
        AppLogger.d("ItemRepo", "Removing item from local DB: id=$id")
        itemDao.deleteItem(id)
        AppLogger.i("ItemRepo", "Item deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to delete item: id=$id", e)
    }

    override suspend fun toggleAvailability(id: String, available: Boolean): Result<Item> = runCatching {
        AppLogger.d("ItemRepo", "Toggling availability: id=$id, available=$available")
        val response = api.toggleItemAvailability(id, UpdateItemRequest(available = available))
        val item = response.toDomain()
        AppLogger.d("ItemRepo", "Saving availability change to local DB: id=$id")
        itemDao.insertItem(item.toDbEntity())
        AppLogger.i("ItemRepo", "Item availability updated: id=$id, available=${item.available}")
        item
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to toggle availability: id=$id, available=$available", e)
    }

    override suspend fun updateItemVariants(itemId: String, groups: List<VariantGroup>): Result<Item> = runCatching {
        AppLogger.d("ItemRepo", "Updating variants for item=$itemId, groups=${groups.size}")
        val request = groups.map { group ->
            CreateVariantGroupRequest(
                name = group.name,
                required = group.required,
                displayOrder = group.displayOrder,
                options = group.options.map { option ->
                    CreateVariantOptionRequest(
                        name = option.name,
                        priceAdjustment = option.priceAdjustment,
                        isDefault = option.isDefault,
                        displayOrder = option.displayOrder
                    )
                }
            )
        }
        val response = api.updateItemVariants(itemId, request)
        val item = response.toDomain()
        itemDao.insertItem(item.toDbEntity())
        // Update local variant cache
        if (response.variantGroups.isNotEmpty()) {
            val dbGroups = response.variantGroups.map { g ->
                Item_variant_groups(
                    id = g.id, item_id = itemId, name = g.name,
                    required = g.required, display_order = g.displayOrder
                )
            }
            val dbOptions = response.variantGroups.flatMap { g ->
                g.options.map { o ->
                    Item_variant_options(
                        id = o.id, group_id = g.id, name = o.name,
                        price_adjustment = o.priceAdjustment, is_default = o.isDefault,
                        display_order = o.displayOrder
                    )
                }
            }
            itemVariantDao.insertVariantGroups(itemId, dbGroups, dbOptions)
        } else {
            // Clear variants if empty
            itemVariantDao.insertVariantGroups(itemId, emptyList(), emptyList())
        }
        AppLogger.i("ItemRepo", "Variants updated for item=$itemId, groups=${groups.size}")
        item
    }.onFailure { e ->
        AppLogger.e("ItemRepo", "Failed to update variants for item=$itemId", e)
    }
}
