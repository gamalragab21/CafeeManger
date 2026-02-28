package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.Item_variant_groups
import net.marllex.waselak.core.database.Item_variant_options
import net.marllex.waselak.core.database.WaselakDatabase

class ItemVariantDao(private val db: WaselakDatabase) {
    private val queries get() = db.itemVariantQueries

    fun getVariantGroupsByItem(itemId: String): Flow<List<Item_variant_groups>> =
        queries.getVariantGroupsByItem(itemId).asFlow().mapToList(Dispatchers.Default)

    fun getVariantOptionsByItem(itemId: String): Flow<List<Item_variant_options>> =
        queries.getVariantOptionsByItem(itemId).asFlow().mapToList(Dispatchers.Default)

    suspend fun getVariantGroupsByItemList(itemId: String): List<Item_variant_groups> =
        queries.getVariantGroupsByItem(itemId).executeAsList()

    suspend fun getVariantOptionsByGroupList(groupId: String): List<Item_variant_options> =
        queries.getVariantOptionsByGroup(groupId).executeAsList()

    suspend fun getVariantOptionsByItemList(itemId: String): List<Item_variant_options> =
        queries.getVariantOptionsByItem(itemId).executeAsList()

    suspend fun insertVariantGroups(
        itemId: String,
        groups: List<Item_variant_groups>,
        options: List<Item_variant_options>
    ) {
        db.transaction {
            // Delete existing options first (FK dependency), then groups
            queries.deleteVariantOptionsByItem(itemId)
            queries.deleteVariantGroupsByItem(itemId)

            // Insert new groups
            groups.forEach { group ->
                queries.insertVariantGroup(
                    id = group.id,
                    item_id = group.item_id,
                    name = group.name,
                    required = group.required,
                    display_order = group.display_order,
                )
            }
            // Insert new options
            options.forEach { option ->
                queries.insertVariantOption(
                    id = option.id,
                    group_id = option.group_id,
                    name = option.name,
                    price_adjustment = option.price_adjustment,
                    is_default = option.is_default,
                    display_order = option.display_order,
                )
            }
        }
    }

    suspend fun deleteAllVariants() {
        db.transaction {
            queries.deleteAllVariantOptions()
            queries.deleteAllVariantGroups()
        }
    }
}
