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
import net.marllex.waselak.core.database.Item_variant_groups
import net.marllex.waselak.core.database.Item_variant_options

class ItemRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val itemDao: ItemDao,
    private val itemVariantDao: ItemVariantDao,
    private val authRepository: AuthRepository,
) : ItemRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    private suspend fun List<Item>.withVariants(): List<Item> = map { item ->
        val groups = itemVariantDao.getVariantGroupsByItemList(item.id)
        if (groups.isEmpty()) return@map item
        val variantGroups = groups.map { group ->
            val options = itemVariantDao.getVariantOptionsByGroupList(group.id)
            group.toDomain(options.map { it.toDomain() })
        }
        item.copy(variantGroups = variantGroups)
    }

    override fun getItems(categoryId: String?): Flow<List<Item>> {
        val itemsFlow = if (categoryId != null) {
            itemDao.getItemsByCategory(vendorId, categoryId).map { list -> list.map { it.toDomain() } }
        } else {
            itemDao.getItems(vendorId).map { list -> list.map { it.toDomain() } }
        }
        return itemsFlow.map { items -> items.withVariants() }
    }

    override fun getAvailableItems(): Flow<List<Item>> =
        itemDao.getAvailableItems(vendorId).map { list ->
            list.map { it.toDomain() }.withVariants()
        }

    override suspend fun refreshItems(): Result<List<Item>> = runCatching {
        val response = api.getItems()
        val items = response.map { it.toDomain() }
        itemDao.deleteAllItems(vendorId)
        itemDao.insertItems(items.map { it.toDbEntity() })
        // Save variant groups locally for offline access
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

    override suspend fun updateItemVariants(itemId: String, groups: List<VariantGroup>): Result<Item> = runCatching {
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
        item
    }
}
