package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.ItemVariantGroupsTable
import net.marllex.waselak.backend.data.database.ItemVariantOptionsTable
import net.marllex.waselak.backend.data.database.CategoriesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class ItemDto(
    val id: String,
    val vendor_id: String,
    val category_id: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val cost_price: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val image_url: String? = null,
    val available: Boolean = true,
    val stock_behavior: String = "NONE", // NONE, DIRECT, RECIPE
    val variant_groups: List<VariantGroupDto> = emptyList(),
    val created_at: Long? = null,
    val updated_at: Long? = null
)

@Serializable
data class VariantGroupDto(
    val id: String,
    val name: String,
    val required: Boolean = false,
    val display_order: Int = 0,
    val options: List<VariantOptionDto> = emptyList()
)

@Serializable
data class VariantOptionDto(
    val id: String,
    val name: String,
    val price_adjustment: Double = 0.0,
    val is_default: Boolean = false,
    val display_order: Int = 0
)

@Serializable
data class CreateVariantGroupDto(
    val name: String,
    val required: Boolean = false,
    val display_order: Int = 0,
    val options: List<CreateVariantOptionDto> = emptyList()
)

@Serializable
data class CreateVariantOptionDto(
    val name: String,
    val price_adjustment: Double = 0.0,
    val is_default: Boolean = false,
    val display_order: Int = 0
)

@Serializable
data class CreateItemDto(
    val category_id: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val cost_price: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val image_url: String? = null,
    val available: Boolean = true,
    val stock_behavior: String = "NONE", // NONE, DIRECT, RECIPE
)

@Serializable
data class UpdateItemDto(
    val category_id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val cost_price: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val image_url: String? = null,
    val available: Boolean? = null,
    val stock_behavior: String? = null, // NONE, DIRECT, RECIPE
)

fun Route.itemRoutes() {
    route("/api/v1/items") {
        get {
            val principal = currentUser()
            val categoryId = call.parameters["category_id"]

            val items = transaction {
                var query = ItemsTable.selectAll()
                    .where { ItemsTable.vendorId eq UUID.fromString(principal.vendorId) }

                categoryId?.let {
                    query = query.andWhere { ItemsTable.categoryId eq UUID.fromString(it) }
                }

                query.orderBy(ItemsTable.name)
                    .map { it.toItemDto() }
            }
            call.respond(HttpStatusCode.OK, items)
        }

        get("/available") {
            val principal = currentUser()
            val categoryId = call.parameters["category_id"]

            val items = transaction {
                var query = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId)) and
                        (ItemsTable.available eq true)
                    }

                categoryId?.let {
                    query = query.andWhere { ItemsTable.categoryId eq UUID.fromString(it) }
                }

                query.orderBy(ItemsTable.name)
                    .map { it.toItemDto() }
            }
            call.respond(HttpStatusCode.OK, items)
        }

        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val item = transaction {
                ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq UUID.fromString(id)) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toItemDto()
                    ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, item)
        }

        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateItemDto>()
            require(request.name.isNotBlank()) { "Item name is required" }
            require(request.price > 0) { "Price must be positive" }

            val item = transaction {
                // Verify category belongs to this vendor
                val categoryExists = CategoriesTable.selectAll()
                    .where {
                        (CategoriesTable.id eq UUID.fromString(request.category_id)) and
                        (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.count() > 0

                if (!categoryExists) throw NoSuchElementException("Category not found")

                val id = ItemsTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[categoryId] = UUID.fromString(request.category_id)
                    it[name] = request.name
                    it[description] = request.description
                    it[price] = BigDecimal.valueOf(request.price)
                    request.cost_price?.let { cp -> it[costPrice] = BigDecimal.valueOf(cp) }
                    it[sku] = request.sku
                    it[barcode] = request.barcode
                    it[imageUrl] = request.image_url
                    it[available] = request.available
                    it[stockBehavior] = request.stock_behavior
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                ItemsTable.selectAll().where { ItemsTable.id eq id }.first().toItemDto()
            }
            call.respond(HttpStatusCode.Created, item)
        }

        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateItemDto>()

            val updated = transaction {
                // If changing category, verify it belongs to this vendor
                request.category_id?.let { catId ->
                    val categoryExists = CategoriesTable.selectAll()
                        .where {
                            (CategoriesTable.id eq UUID.fromString(catId)) and
                            (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                        }.count() > 0
                    if (!categoryExists) throw NoSuchElementException("Category not found")
                }

                ItemsTable.update({
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.category_id?.let { stmt[categoryId] = UUID.fromString(it) }
                    request.name?.let { stmt[name] = it }
                    request.description?.let { stmt[description] = it }
                    request.price?.let { stmt[price] = BigDecimal.valueOf(it) }
                    request.cost_price?.let { stmt[costPrice] = BigDecimal.valueOf(it) }
                    request.sku?.let { stmt[sku] = it }
                    request.barcode?.let { stmt[barcode] = it }
                    request.image_url?.let { stmt[imageUrl] = it }
                    request.available?.let { stmt[available] = it }
                    request.stock_behavior?.let { stmt[stockBehavior] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto() ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/{id}/availability") {
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            @Serializable
            data class AvailabilityDto(val available: Boolean)
            val request = call.receive<AvailabilityDto>()

            val updated = transaction {
                ItemsTable.update({
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[available] = request.available
                    it[updatedAt] = Clock.System.now()
                }
                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto() ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/{id}/variants") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val groups = call.receive<List<CreateVariantGroupDto>>()

            val item = transaction {
                // Verify item belongs to this vendor
                val itemRow = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq UUID.fromString(id)) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Item not found")

                // Delete existing variant options first (due to FK), then groups
                val existingGroupIds = ItemVariantGroupsTable.selectAll()
                    .where { ItemVariantGroupsTable.itemId eq UUID.fromString(id) }
                    .map { it[ItemVariantGroupsTable.id] }

                if (existingGroupIds.isNotEmpty()) {
                    ItemVariantOptionsTable.deleteWhere { groupId inList existingGroupIds }
                }
                ItemVariantGroupsTable.deleteWhere { ItemVariantGroupsTable.itemId eq UUID.fromString(id) }

                // Insert new groups and options
                groups.forEachIndexed { gIndex, group ->
                    val groupId = ItemVariantGroupsTable.insertAndGetId {
                        it[itemId] = UUID.fromString(id)
                        it[name] = group.name
                        it[required] = group.required
                        it[displayOrder] = group.display_order.takeIf { it != 0 } ?: gIndex
                        it[createdAt] = Clock.System.now()
                    }
                    group.options.forEachIndexed { oIndex, option ->
                        ItemVariantOptionsTable.insertAndGetId {
                            it[ItemVariantOptionsTable.groupId] = groupId.value
                            it[name] = option.name
                            it[priceAdjustment] = BigDecimal.valueOf(option.price_adjustment)
                            it[isDefault] = option.is_default
                            it[displayOrder] = option.display_order.takeIf { it != 0 } ?: oIndex
                            it[createdAt] = Clock.System.now()
                        }
                    }
                }

                // Return updated item with variants
                itemRow.toItemDto(loadVariants = true)
            }
            call.respond(HttpStatusCode.OK, item)
        }

        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = ItemsTable.deleteWhere {
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toItemDto(loadVariants: Boolean = true): ItemDto {
    val itemId = this[ItemsTable.id]
    val variantGroups = if (loadVariants) {
        val groups = ItemVariantGroupsTable.selectAll()
            .where { ItemVariantGroupsTable.itemId eq itemId }
            .orderBy(ItemVariantGroupsTable.displayOrder)
            .toList()

        val groupIds = groups.map { it[ItemVariantGroupsTable.id] }
        val allOptions = if (groupIds.isNotEmpty()) {
            ItemVariantOptionsTable.selectAll()
                .where { ItemVariantOptionsTable.groupId inList groupIds }
                .orderBy(ItemVariantOptionsTable.displayOrder)
                .toList()
        } else emptyList()

        groups.map { group ->
            val gId = group[ItemVariantGroupsTable.id]
            VariantGroupDto(
                id = gId.toString(),
                name = group[ItemVariantGroupsTable.name],
                required = group[ItemVariantGroupsTable.required],
                display_order = group[ItemVariantGroupsTable.displayOrder],
                options = allOptions
                    .filter { it[ItemVariantOptionsTable.groupId] == gId }
                    .map { opt ->
                        VariantOptionDto(
                            id = opt[ItemVariantOptionsTable.id].toString(),
                            name = opt[ItemVariantOptionsTable.name],
                            price_adjustment = opt[ItemVariantOptionsTable.priceAdjustment].toDouble(),
                            is_default = opt[ItemVariantOptionsTable.isDefault],
                            display_order = opt[ItemVariantOptionsTable.displayOrder]
                        )
                    }
            )
        }
    } else emptyList()

    return ItemDto(
        id = itemId.toString(),
        vendor_id = this[ItemsTable.vendorId].toString(),
        category_id = this[ItemsTable.categoryId].toString(),
        name = this[ItemsTable.name],
        description = this[ItemsTable.description],
        price = this[ItemsTable.price].toDouble(),
        cost_price = this[ItemsTable.costPrice]?.toDouble(),
        sku = this[ItemsTable.sku],
        barcode = this[ItemsTable.barcode],
        image_url = this[ItemsTable.imageUrl],
        available = this[ItemsTable.available],
        stock_behavior = this[ItemsTable.stockBehavior],
        variant_groups = variantGroups,
        created_at = this[ItemsTable.createdAt].toEpochMilliseconds(),
        updated_at = this[ItemsTable.updatedAt].toEpochMilliseconds()
    )
}
