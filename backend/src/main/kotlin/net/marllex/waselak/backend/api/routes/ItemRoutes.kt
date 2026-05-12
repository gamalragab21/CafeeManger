package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.ItemVariantGroupsTable
import net.marllex.waselak.backend.data.database.ItemVariantOptionsTable
import net.marllex.waselak.backend.data.database.CategoriesTable
import net.marllex.waselak.backend.data.database.OrderItemsTable
import net.marllex.waselak.backend.data.database.RecipesTable
import net.marllex.waselak.backend.data.database.RecipeIngredientsTable
import net.marllex.waselak.backend.data.database.StockTable
import net.marllex.waselak.backend.data.database.StockTransactionsTable
import net.marllex.waselak.backend.data.database.OfferItemsTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
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
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/items") {
        get {
            val trace = call.routeTrace()
            trace.step("List items started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val categoryId = call.parameters["category_id"]
            trace.step("Filter params", mapOf("categoryId" to (categoryId ?: "null")))
            val h = call.request.header("Host") ?: "localhost:8080"
            val s = call.request.header("X-Forwarded-Proto") ?: "http"

            // Compute ETag from (max(updatedAt), rowCount) of the filtered query first —
            // if the client already has fresh data we can 304 before doing the expensive
            // DTO serialization with URL rewriting.
            val vendorUuid = UUID.fromString(principal.vendorId)
            val categoryUuid = categoryId?.let { UUID.fromString(it) }
            val (maxUpdatedAt, rowCount) = transaction {
                val maxUpd = ItemsTable.updatedAt.max()
                val cnt = ItemsTable.id.count()
                var q = ItemsTable.select(maxUpd, cnt)
                    .where { ItemsTable.vendorId eq vendorUuid }
                if (categoryUuid != null) {
                    q = q.andWhere { ItemsTable.categoryId eq categoryUuid }
                }
                val row = q.first()
                (row[maxUpd]?.toEpochMilliseconds() ?: 0L) to (row[cnt])
            }
            val etag = ETagSupport.weakEtag(maxUpdatedAt, rowCount)
            if (ETagSupport.respondNotModifiedIfMatches(call, etag)) {
                trace.step("Items 304 Not Modified", mapOf("count" to rowCount.toString()))
                return@get
            }

            val items = transaction {
                var query = ItemsTable.selectAll()
                    .where { ItemsTable.vendorId eq vendorUuid }

                if (categoryUuid != null) {
                    query = query.andWhere { ItemsTable.categoryId eq categoryUuid }
                }

                query.orderBy(ItemsTable.name)
                    .map { it.toItemDto(host = h, scheme = s) }
            }
            trace.step("Items fetched", mapOf("count" to items.size.toString()))
            trace.step("List items completed")
            call.respond(HttpStatusCode.OK, items)
        }

        get("/available") {
            val trace = call.routeTrace()
            trace.step("List available items started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val categoryId = call.parameters["category_id"]
            trace.step("Filter params", mapOf("categoryId" to (categoryId ?: "null")))
            val h = call.request.header("Host") ?: "localhost:8080"
            val s = call.request.header("X-Forwarded-Proto") ?: "http"

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
                    .map { it.toItemDto(host = h, scheme = s) }
            }
            trace.step("Available items fetched", mapOf("count" to items.size.toString()))
            trace.step("List available items completed")
            call.respond(HttpStatusCode.OK, items)
        }

        // Lookup item by barcode
        get("/barcode/{barcode}") {
            val trace = call.routeTrace()
            trace.step("Lookup item by barcode started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val barcode = call.parameters["barcode"] ?: throw IllegalArgumentException("Barcode required")
            trace.step("Barcode param", mapOf("barcode" to barcode))
            val h = call.request.header("Host") ?: "localhost:8080"
            val s = call.request.header("X-Forwarded-Proto") ?: "http"

            val item = transaction {
                ItemsTable.selectAll()
                    .where {
                        (ItemsTable.barcode eq barcode) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toItemDto(host = h, scheme = s)
            }
            if (item != null) {
                trace.step("Item found", mapOf("itemId" to item.id, "itemName" to item.name))
                trace.step("Lookup item by barcode completed")
                call.respond(HttpStatusCode.OK, item)
            } else {
                trace.step("Item not found for barcode", mapOf("barcode" to barcode))
                trace.step("Lookup item by barcode completed")
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found for barcode: $barcode"))
            }
        }

        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get item by ID started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Item ID param", mapOf("itemId" to id))
            val h = call.request.header("Host") ?: "localhost:8080"
            val s = call.request.header("X-Forwarded-Proto") ?: "http"

            val item = transaction {
                ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq UUID.fromString(id)) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toItemDto(host = h, scheme = s)
                    ?: throw NoSuchElementException("Item not found")
            }
            trace.step("Item found", mapOf("itemName" to item.name, "price" to item.price.toString(), "available" to item.available.toString()))
            trace.step("Get item by ID completed")
            call.respond(HttpStatusCode.OK, item)
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val request = call.receive<CreateItemDto>()
            trace.step("Request received", mapOf(
                "name" to request.name,
                "categoryId" to request.category_id,
                "price" to request.price.toString(),
                "stockBehavior" to request.stock_behavior,
                "available" to request.available.toString()
            ))
            require(request.name.isNotBlank()) { "Item name is required" }
            require(request.price > 0) { "Price must be positive" }

            // ─── Plan limit check ─────────────────────────
            trace.step("Checking plan item limit")
            planService.checkItemCreation(UUID.fromString(principal.vendorId))
            trace.step("Plan limit check passed")

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
                val h2 = call.request.header("Host") ?: "localhost:8080"
                val s2 = call.request.header("X-Forwarded-Proto") ?: "http"
                ItemsTable.selectAll().where { ItemsTable.id eq id }.first().toItemDto(host = h2, scheme = s2)
            }
            trace.step("Item created", mapOf("itemId" to item.id, "itemName" to item.name))
            trace.step("Create item completed")
            call.respond(HttpStatusCode.Created, item)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Item ID param", mapOf("itemId" to id))
            val request = call.receive<UpdateItemDto>()
            trace.step("Update fields received", mapOf(
                "name" to (request.name ?: "null"),
                "categoryId" to (request.category_id ?: "null"),
                "price" to (request.price?.toString() ?: "null"),
                "stockBehavior" to (request.stock_behavior ?: "null"),
                "available" to (request.available?.toString() ?: "null")
            ))

            val updated = transaction {
                // If changing category, verify it belongs to this vendor
                request.category_id?.let { catId ->
                    trace.step("Verifying new category", mapOf("categoryId" to catId))
                    val categoryExists = CategoriesTable.selectAll()
                        .where {
                            (CategoriesTable.id eq UUID.fromString(catId)) and
                            (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                        }.count() > 0
                    if (!categoryExists) throw NoSuchElementException("Category not found")
                    trace.step("Category verified")
                }

                // Delete old image file if being replaced
                if (request.image_url != null) {
                    val oldImageUrl = ItemsTable.selectAll()
                        .where { ItemsTable.id eq UUID.fromString(id) }
                        .firstOrNull()?.get(ItemsTable.imageUrl)
                    if (oldImageUrl != null && oldImageUrl != request.image_url) {
                        trace.step("Replacing old image")
                        deleteUploadedFile(oldImageUrl)
                    }
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

                val h3 = call.request.header("Host") ?: "localhost:8080"
                val s3 = call.request.header("X-Forwarded-Proto") ?: "http"
                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto(host = h3, scheme = s3) ?: throw NoSuchElementException("Item not found")
            }
            trace.step("Item updated", mapOf("itemId" to updated.id, "itemName" to updated.name))
            trace.step("Update item completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/{id}/availability") {
            val trace = call.routeTrace()
            trace.step("Toggle item availability started")
            val principal = requireRole("MANAGER", "CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Item ID param", mapOf("itemId" to id))

            @Serializable
            data class AvailabilityDto(val available: Boolean)
            val request = call.receive<AvailabilityDto>()
            trace.step("Availability value", mapOf("available" to request.available.toString()))

            val updated = transaction {
                ItemsTable.update({
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[available] = request.available
                    it[updatedAt] = Clock.System.now()
                }
                val h4 = call.request.header("Host") ?: "localhost:8080"
                val s4 = call.request.header("X-Forwarded-Proto") ?: "http"
                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto(host = h4, scheme = s4) ?: throw NoSuchElementException("Item not found")
            }
            trace.step("Item availability updated", mapOf("itemId" to updated.id, "itemName" to updated.name, "available" to updated.available.toString()))
            trace.step("Toggle item availability completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/{id}/variants") {
            val trace = call.routeTrace()
            trace.step("Update item variants started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Item ID param", mapOf("itemId" to id))
            val groups = call.receive<List<CreateVariantGroupDto>>()
            trace.step("Variant groups received", mapOf("groupCount" to groups.size.toString()))

            val item = transaction {
                // Verify item belongs to this vendor
                val itemRow = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq UUID.fromString(id)) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Item not found")
                trace.step("Item verified", mapOf("itemName" to itemRow[ItemsTable.name]))

                // Delete existing variant options first (due to FK), then groups
                val existingGroupIds = ItemVariantGroupsTable.selectAll()
                    .where { ItemVariantGroupsTable.itemId eq UUID.fromString(id) }
                    .map { it[ItemVariantGroupsTable.id] }

                trace.step("Deleting old variants", mapOf("existingGroupCount" to existingGroupIds.size.toString()))
                if (existingGroupIds.isNotEmpty()) {
                    ItemVariantOptionsTable.deleteWhere { groupId inList existingGroupIds }
                }
                ItemVariantGroupsTable.deleteWhere { ItemVariantGroupsTable.itemId eq UUID.fromString(id) }

                // Insert new groups and options
                var totalOptions = 0
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
                        totalOptions++
                    }
                }
                trace.step("New variants inserted", mapOf("groupCount" to groups.size.toString(), "totalOptions" to totalOptions.toString()))

                // Return updated item with variants
                val h5 = call.request.header("Host") ?: "localhost:8080"
                val s5 = call.request.header("X-Forwarded-Proto") ?: "http"
                itemRow.toItemDto(loadVariants = true, host = h5, scheme = s5)
            }
            trace.step("Update item variants completed")
            call.respond(HttpStatusCode.OK, item)
        }

        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Item ID param", mapOf("itemId" to id))

            // ── Smart delete cascade ──────────────────────────────
            //
            // Previous implementation just did `DELETE FROM items WHERE id = ?`
            // which FK-failed whenever the item was used by a recipe, an
            // order, an offer, a return, a stock transaction, etc. The
            // merchant complaint was "can't delete items used in recipes."
            //
            // New behavior:
            //   • Auto-cascade ANY recipe that references this item
            //     (recipe_ingredients goes via ON DELETE CASCADE; we just
            //     delete the recipe row and SET NULL the stock_transactions
            //     that point at it — no historical data is lost since the
            //     recipes are trivially recreatable).
            //   • Refuse to delete when ORDERS reference the item: the
            //     order_items snapshot has its own `item_name_snapshot`
            //     and `item_price_snapshot` columns so deleting the item
            //     wouldn't destroy receipts, BUT the FK is NOT NULL and
            //     the safest user-facing fix is to mark it UNAVAILABLE.
            //     We surface a structured `ITEM_IN_ORDERS` error so the
            //     client can offer the right remediation.
            //   • Cascade-clean lighter refs (offer_items, return_items,
            //     stock entries referencing this item) so the delete
            //     succeeds when there's no order reference.
            val itemUUID = UUID.fromString(id)
            val vendorUUID = UUID.fromString(principal.vendorId)
            val result = transaction {
                val exists = ItemsTable.selectAll().where {
                    (ItemsTable.id eq itemUUID) and (ItemsTable.vendorId eq vendorUUID)
                }.count() > 0
                if (!exists) return@transaction "not_found"

                val orderCount = OrderItemsTable.selectAll().where {
                    OrderItemsTable.itemId eq itemUUID
                }.count()
                if (orderCount > 0) return@transaction "in_orders:$orderCount"

                // Recipes for this item — delete with their stock-tx refs.
                val recipeIds = RecipesTable.selectAll().where {
                    (RecipesTable.itemId eq itemUUID) and (RecipesTable.vendorId eq vendorUUID)
                }.map { it[RecipesTable.id].value }
                if (recipeIds.isNotEmpty()) {
                    runCatching {
                        StockTransactionsTable.update({ StockTransactionsTable.recipeId inList recipeIds }) {
                            it[recipeId] = null
                        }
                    }
                    RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId inList recipeIds }
                    RecipesTable.deleteWhere { RecipesTable.id inList recipeIds }
                }

                // Stock + offers + return items + other FK refs.
                runCatching {
                    StockTable.deleteWhere {
                        (StockTable.itemId eq itemUUID) and (StockTable.vendorId eq vendorUUID)
                    }
                }
                runCatching {
                    OfferItemsTable.deleteWhere { OfferItemsTable.itemId eq itemUUID }
                }

                val rows = runCatching {
                    ItemsTable.deleteWhere {
                        (ItemsTable.id eq itemUUID) and (ItemsTable.vendorId eq vendorUUID)
                    }
                }.getOrElse { return@transaction "fk_blocked" }
                if (rows == 0) "not_found" else "deleted:$rows"
            }

            when {
                result == "not_found" ->
                    throw NoSuchElementException("Item not found")
                result == "fk_blocked" -> {
                    trace.step("Item delete refused — unhandled FK")
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf(
                            "error" to "Item is referenced by other records and cannot be deleted. Mark it unavailable instead.",
                            "code" to "ITEM_REFERENCED",
                        ),
                    )
                }
                result.startsWith("in_orders:") -> {
                    val n = result.removePrefix("in_orders:")
                    trace.step("Item delete refused — used by orders", mapOf("orderItemCount" to n))
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf(
                            "error" to "Item is used in $n past order(s). Mark it unavailable instead of deleting so order history stays intact.",
                            "code" to "ITEM_IN_ORDERS",
                            "order_item_count" to n,
                        ),
                    )
                }
                result.startsWith("deleted:") -> {
                    trace.step("Item deleted from database",
                        mapOf("rowsAffected" to result.removePrefix("deleted:")))
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                }
            }
            trace.step("Delete item completed")
        }
    }
}

private fun ResultRow.toItemDto(loadVariants: Boolean = true, host: String = "localhost:8080", scheme: String = "http"): ItemDto {
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
        image_url = rewriteUploadUrl(this[ItemsTable.imageUrl], host, scheme),
        available = this[ItemsTable.available],
        stock_behavior = this[ItemsTable.stockBehavior],
        variant_groups = variantGroups,
        created_at = this[ItemsTable.createdAt].toEpochMilliseconds(),
        updated_at = this[ItemsTable.updatedAt].toEpochMilliseconds()
    )
}
