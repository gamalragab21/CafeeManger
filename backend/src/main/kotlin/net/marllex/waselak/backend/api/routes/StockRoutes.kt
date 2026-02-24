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
import net.marllex.waselak.backend.data.database.StockTable
import net.marllex.waselak.backend.data.database.StockTransactionsTable
import net.marllex.waselak.backend.domain.model.StockUnit
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class StockDto(
    val id: String,
    val vendor_id: String,
    val item_id: String?, // Nullable for independent stock items
    val item_name: String,
    val quantity: Double,
    val min_quantity: Double,
    val cost_price: Double,
    val unit: String,
    val base_unit: String = "PIECE",
    val conversion_rate: Double = 1.0,
    val is_menu_item: Boolean = true,
    val alert_enabled: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateStockDto(
    val item_id: String? = null, // Optional - null for independent stock items
    val item_name: String? = null, // Required if item_id is null
    val quantity: Double,
    val min_quantity: Double = 5.0,
    val cost_price: Double = 0.0,
    val unit: String = "PIECE",
    val base_unit: String = "PIECE",
    val conversion_rate: Double = 1.0,
    val alert_enabled: Boolean = true,
)

@Serializable
data class UpdateStockDto(
    val item_name: String? = null,
    val quantity: Double? = null,
    val min_quantity: Double? = null,
    val cost_price: Double? = null,
    val unit: String? = null,
    val base_unit: String? = null,
    val conversion_rate: Double? = null,
    val alert_enabled: Boolean? = null,
)

@Serializable
data class AdjustQuantityDto(
    val quantity: Double,
    val note: String? = null,
)

@Serializable
data class TransferStockDto(
    val target_stock_id: String,
    val quantity: Double,
    val note: String? = null,
)

@Serializable
data class PurchaseStockDto(
    val quantity: Double,
    val unit: String, // Purchase unit (e.g., KILOGRAM) — will be converted to base unit
    val cost_price: Double? = null, // Update cost price if provided
    val note: String? = null,
)

@Serializable
data class StockTransactionDto(
    val id: String,
    val stock_id: String,
    val item_name: String? = null,
    val type: String,
    val quantity: Double,
    val previous_quantity: Double,
    val order_id: String? = null,
    val recipe_id: String? = null,
    val note: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class StockAlertDto(
    val id: String,
    val item_name: String,
    val quantity: Double,
    val min_quantity: Double,
    val unit: String,
    val is_out_of_stock: Boolean,
    val is_menu_item: Boolean,
)

@Serializable
data class StockAnalyticsSummaryDto(
    val total_items: Int,
    val total_value: Double,
    val low_stock_count: Int,
    val out_of_stock_count: Int,
    val healthy_count: Int,
    val menu_items_count: Int,
    val independent_items_count: Int,
    val recipe_items_count: Int = 0,
    val total_transactions_today: Int,
    val total_added_today: Double,
    val total_deducted_today: Double,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.stockRoutes() {
    route("/api/v1/stock") {

        // GET all stock for this vendor
        get {
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)

            val stocks = transaction {
                StockTable.selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .orderBy(StockTable.itemName)
                    .map { it.toStockDto() }
            }
            call.respond(HttpStatusCode.OK, stocks)
        }

        // GET single stock item
        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val stock = transaction {
                StockTable.selectAll()
                    .where {
                        (StockTable.id eq UUID.fromString(id)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toStockDto()
                    ?: throw NoSuchElementException("Stock item not found")
            }
            call.respond(HttpStatusCode.OK, stock)
        }

        // GET stock by item ID
        get("/by-item/{itemId}") {
            val principal = currentUser()
            val itemId = call.parameters["itemId"] ?: throw IllegalArgumentException("Item ID required")

            val stock = transaction {
                StockTable.selectAll()
                    .where {
                        (StockTable.itemId eq UUID.fromString(itemId)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toStockDto()
            }
            if (stock != null) {
                call.respond(HttpStatusCode.OK, stock)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Stock not found for this item"))
            }
        }

        // CREATE stock item (MANAGER only)
        // Supports both menu-linked items (item_id provided) and independent items (item_name provided)
        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateStockDto>()
            require(request.quantity >= 0) { "Quantity must be non-negative" }

            // Validate: either item_id or item_name must be provided
            require(request.item_id != null || !request.item_name.isNullOrBlank()) {
                "Either item_id (for menu items) or item_name (for independent items) must be provided"
            }

            val stock = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val isMenuLinked = request.item_id != null
                val itemUUID = request.item_id?.let { UUID.fromString(it) }
                val finalItemName: String

                if (isMenuLinked && itemUUID != null) {
                    // Menu-linked stock item
                    val item = ItemsTable.selectAll()
                        .where {
                            (ItemsTable.id eq itemUUID) and
                            (ItemsTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: throw NoSuchElementException("Item not found")

                    // Check if stock already exists for this item
                    val existing = StockTable.selectAll()
                        .where {
                            (StockTable.itemId eq itemUUID) and
                            (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull()
                    if (existing != null) throw IllegalStateException("Stock already exists for this item")

                    finalItemName = item[ItemsTable.name]

                    // Auto-set stockBehavior to DIRECT if not already set
                    if (item[ItemsTable.stockBehavior] == "NONE") {
                        ItemsTable.update({ ItemsTable.id eq itemUUID }) {
                            it[stockBehavior] = "DIRECT"
                            it[updatedAt] = now
                        }
                    }
                } else {
                    // Independent stock item (not linked to menu)
                    finalItemName = request.item_name!!

                    // Check for duplicate name in independent items
                    val existing = StockTable.selectAll()
                        .where {
                            (StockTable.vendorId eq vendorUUID) and
                            (StockTable.itemName eq finalItemName) and
                            (StockTable.itemId.isNull())
                        }.firstOrNull()
                    if (existing != null) throw IllegalStateException("Independent stock item with this name already exists")
                }

                val stockId = StockTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[StockTable.itemId] = itemUUID
                    it[itemName] = finalItemName
                    it[quantity] = BigDecimal.valueOf(request.quantity)
                    it[minQuantity] = BigDecimal.valueOf(request.min_quantity)
                    it[costPrice] = BigDecimal.valueOf(request.cost_price)
                    it[unit] = request.unit
                    it[baseUnit] = request.base_unit
                    it[conversionRate] = BigDecimal.valueOf(request.conversion_rate)
                    it[isMenuItem] = isMenuLinked
                    it[alertEnabled] = request.alert_enabled
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                // Record initial transaction
                StockTransactionsTable.insertAndGetId {
                    it[StockTransactionsTable.stockId] = stockId
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = BigDecimal.ZERO
                    it[note] = "Initial stock entry"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockId }.first().toStockDto()
            }
            call.respond(HttpStatusCode.Created, stock)
        }

        // UPDATE stock item (MANAGER only)
        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateStockDto>()

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val now = Clock.System.now()
                val oldQty = current[StockTable.quantity]
                val isMenuItem = current[StockTable.isMenuItem]

                // Only allow item_name update for independent (non-menu) items
                if (request.item_name != null && isMenuItem) {
                    throw IllegalStateException("Cannot rename menu-linked stock items")
                }

                StockTable.update({
                    (StockTable.id eq stockUUID) and
                    (StockTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.item_name?.let { stmt[itemName] = it }
                    request.quantity?.let { stmt[quantity] = BigDecimal.valueOf(it) }
                    request.min_quantity?.let { stmt[minQuantity] = BigDecimal.valueOf(it) }
                    request.cost_price?.let { stmt[costPrice] = BigDecimal.valueOf(it) }
                    request.unit?.let { stmt[unit] = it }
                    request.base_unit?.let { stmt[baseUnit] = it }
                    request.conversion_rate?.let { stmt[conversionRate] = BigDecimal.valueOf(it) }
                    request.alert_enabled?.let { stmt[alertEnabled] = it }
                    stmt[updatedAt] = now
                }

                // Log quantity change
                val newQtyDecimal = request.quantity?.let { BigDecimal.valueOf(it) }
                if (newQtyDecimal != null && newQtyDecimal.compareTo(oldQty) != 0) {
                    StockTransactionsTable.insertAndGetId {
                        it[stockId] = stockUUID
                        it[type] = "ADJUST"
                        it[quantity] = newQtyDecimal
                        it[previousQuantity] = oldQty
                        it[note] = "Manual adjustment"
                        it[createdAt] = now
                    }
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // ADD quantity (MANAGER only)
        patch("/{id}/add") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<AdjustQuantityDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = oldQty + BigDecimal.valueOf(request.quantity)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock added"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // DEDUCT quantity (MANAGER/CASHIER)
        patch("/{id}/deduct") {
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<AdjustQuantityDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = (oldQty - BigDecimal.valueOf(request.quantity)).coerceAtLeast(BigDecimal.ZERO)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "DEDUCT"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock deducted"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // WASTE — record stock wastage (MANAGER only)
        patch("/{id}/waste") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<AdjustQuantityDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = (oldQty - BigDecimal.valueOf(request.quantity)).coerceAtLeast(BigDecimal.ZERO)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "WASTE"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock waste recorded"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // TRANSFER — move stock between stock items (MANAGER only)
        post("/{id}/transfer") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Source stock ID required")
            val request = call.receive<TransferStockDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val result = transaction {
                val sourceUUID = UUID.fromString(id)
                val targetUUID = UUID.fromString(request.target_stock_id)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val source = StockTable.selectAll()
                    .where {
                        (StockTable.id eq sourceUUID) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Source stock item not found")

                val target = StockTable.selectAll()
                    .where {
                        (StockTable.id eq targetUUID) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Target stock item not found")

                val transferQty = BigDecimal.valueOf(request.quantity)
                val sourceOldQty = source[StockTable.quantity]
                val targetOldQty = target[StockTable.quantity]
                val sourceNewQty = (sourceOldQty - transferQty).coerceAtLeast(BigDecimal.ZERO)
                val targetNewQty = targetOldQty + transferQty

                // Deduct from source
                StockTable.update({ StockTable.id eq sourceUUID }) {
                    it[quantity] = sourceNewQty
                    it[updatedAt] = now
                }
                StockTransactionsTable.insertAndGetId {
                    it[stockId] = sourceUUID
                    it[type] = "TRANSFER"
                    it[StockTransactionsTable.quantity] = transferQty
                    it[previousQuantity] = sourceOldQty
                    it[note] = request.note ?: "Transfer to ${target[StockTable.itemName]}"
                    it[createdAt] = now
                }

                // Add to target
                StockTable.update({ StockTable.id eq targetUUID }) {
                    it[quantity] = targetNewQty
                    it[updatedAt] = now
                }
                StockTransactionsTable.insertAndGetId {
                    it[stockId] = targetUUID
                    it[type] = "TRANSFER"
                    it[StockTransactionsTable.quantity] = transferQty
                    it[previousQuantity] = targetOldQty
                    it[note] = request.note ?: "Transfer from ${source[StockTable.itemName]}"
                    it[createdAt] = now
                }

                mapOf(
                    "source" to StockTable.selectAll().where { StockTable.id eq sourceUUID }.first().toStockDto(),
                    "target" to StockTable.selectAll().where { StockTable.id eq targetUUID }.first().toStockDto(),
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // PURCHASE — add stock with unit conversion (MANAGER only)
        post("/{id}/purchase") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<PurchaseStockDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val stockBaseUnit = current[StockTable.baseUnit]
                val now = Clock.System.now()

                // Convert purchase quantity to base unit
                val convertedQty = convertUnits(request.quantity, request.unit, stockBaseUnit)
                val newQty = oldQty + BigDecimal.valueOf(convertedQty)

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                    // Update cost price if provided
                    request.cost_price?.let { cp -> it[costPrice] = BigDecimal.valueOf(cp) }
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "PURCHASE"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(convertedQty)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Purchase: ${request.quantity} ${request.unit} → ${"%.3f".format(convertedQty)} $stockBaseUnit"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // GET transactions for a stock item
        get("/{id}/transactions") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val transactions = transaction {
                // Verify stock belongs to vendor
                StockTable.selectAll()
                    .where {
                        (StockTable.id eq UUID.fromString(id)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                StockTransactionsTable.selectAll()
                    .where { StockTransactionsTable.stockId eq UUID.fromString(id) }
                    .orderBy(StockTransactionsTable.createdAt, SortOrder.DESC)
                    .map { it.toStockTransactionDto() }
            }
            call.respond(HttpStatusCode.OK, transactions)
        }

        // DELETE stock item (MANAGER only)
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Delete transactions first
                val stock = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                StockTransactionsTable.deleteWhere { StockTransactionsTable.stockId eq stockUUID }
                StockTable.deleteWhere {
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // ═══════════════════════════════════════════════════════════════
        // Stock Analytics Endpoints
        // ═══════════════════════════════════════════════════════════════

        // GET all transactions (with filters)
        get("/analytics/transactions") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val stockId = call.parameters["stock_id"]?.let { UUID.fromString(it) }
            val type = call.parameters["type"] // ADD, DEDUCT, ADJUST, PURCHASE, SALE_DIRECT, SALE_RECIPE, RETURN, WASTE, TRANSFER
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100

            val transactions = transaction {
                // Get all stock IDs for this vendor
                val vendorStockIds = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .map { it[StockTable.id] }

                if (vendorStockIds.isEmpty()) {
                    return@transaction emptyList<StockTransactionDto>()
                }

                // Create stock name lookup
                val stockNameMap = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .associate { it[StockTable.id] to it[StockTable.itemName] }

                var query = StockTransactionsTable
                    .selectAll()
                    .where { StockTransactionsTable.stockId inList vendorStockIds }

                stockId?.let { sid ->
                    query = query.andWhere { StockTransactionsTable.stockId eq sid }
                }

                type?.let { t ->
                    query = query.andWhere { StockTransactionsTable.type eq t }
                }

                from?.let { ts ->
                    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    query = query.andWhere { StockTransactionsTable.createdAt greaterEq instant }
                }

                to?.let { ts ->
                    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    query = query.andWhere { StockTransactionsTable.createdAt lessEq instant }
                }

                query
                    .orderBy(StockTransactionsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        StockTransactionDto(
                            id = row[StockTransactionsTable.id].toString(),
                            stock_id = row[StockTransactionsTable.stockId].toString(),
                            item_name = stockNameMap[row[StockTransactionsTable.stockId]],
                            type = row[StockTransactionsTable.type],
                            quantity = row[StockTransactionsTable.quantity].toDouble(),
                            previous_quantity = row[StockTransactionsTable.previousQuantity].toDouble(),
                            order_id = row[StockTransactionsTable.orderId]?.toString(),
                            recipe_id = row[StockTransactionsTable.recipeId]?.toString(),
                            note = row[StockTransactionsTable.note],
                            created_at = row[StockTransactionsTable.createdAt].toEpochMilliseconds(),
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, transactions)
        }

        // GET low stock alerts
        get("/analytics/alerts") {
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)

            val alerts = transaction {
                StockTable
                    .selectAll()
                    .where {
                        (StockTable.vendorId eq vendorUUID) and
                        (StockTable.alertEnabled eq true) and
                        (StockTable.quantity lessEq StockTable.minQuantity)
                    }
                    .orderBy(StockTable.quantity, SortOrder.ASC)
                    .map { row ->
                        StockAlertDto(
                            id = row[StockTable.id].toString(),
                            item_name = row[StockTable.itemName],
                            quantity = row[StockTable.quantity].toDouble(),
                            min_quantity = row[StockTable.minQuantity].toDouble(),
                            unit = row[StockTable.unit],
                            is_out_of_stock = row[StockTable.quantity] <= BigDecimal.ZERO,
                            is_menu_item = row[StockTable.isMenuItem],
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, alerts)
        }

        // GET stock analytics summary
        get("/analytics/summary") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val stocks = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .toList()

                val totalItems = stocks.size
                val totalValue = stocks.sumOf { it[StockTable.quantity].toDouble() * it[StockTable.costPrice].toDouble() }
                val lowStockCount = stocks.count {
                    val qty = it[StockTable.quantity]
                    val minQty = it[StockTable.minQuantity]
                    qty > BigDecimal.ZERO && qty <= minQty
                }
                val outOfStockCount = stocks.count { it[StockTable.quantity] <= BigDecimal.ZERO }
                val healthyCount = totalItems - lowStockCount - outOfStockCount
                val menuItemsCount = stocks.count { it[StockTable.isMenuItem] }
                val independentItemsCount = stocks.count { !it[StockTable.isMenuItem] }

                // Count recipe items (items with stockBehavior = RECIPE)
                val recipeItemsCount = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.vendorId eq vendorUUID) and
                        (ItemsTable.stockBehavior eq "RECIPE")
                    }.count().toInt()

                // Get today's transactions
                val todayStart = kotlinx.datetime.Clock.System.now().let {
                    val str = it.toString().substring(0, 10)
                    kotlinx.datetime.Instant.parse("${str}T00:00:00Z")
                }

                val stockIds = stocks.map { it[StockTable.id] }
                val todayTransactions = if (stockIds.isEmpty()) emptyList() else {
                    StockTransactionsTable
                        .selectAll()
                        .where {
                            (StockTransactionsTable.stockId inList stockIds) and
                            (StockTransactionsTable.createdAt greaterEq todayStart)
                        }.toList()
                }

                val addTypes = setOf("ADD", "PURCHASE")
                val deductTypes = setOf("DEDUCT", "SALE_DIRECT", "SALE_RECIPE")

                val totalTransactionsToday = todayTransactions.size
                val totalAddedToday = todayTransactions.filter { it[StockTransactionsTable.type] in addTypes }
                    .sumOf { it[StockTransactionsTable.quantity].toDouble() }
                val totalDeductedToday = todayTransactions.filter { it[StockTransactionsTable.type] in deductTypes }
                    .sumOf { it[StockTransactionsTable.quantity].toDouble() }

                StockAnalyticsSummaryDto(
                    total_items = totalItems,
                    total_value = totalValue,
                    low_stock_count = lowStockCount,
                    out_of_stock_count = outOfStockCount,
                    healthy_count = healthyCount,
                    menu_items_count = menuItemsCount,
                    independent_items_count = independentItemsCount,
                    recipe_items_count = recipeItemsCount,
                    total_transactions_today = totalTransactionsToday,
                    total_added_today = totalAddedToday,
                    total_deducted_today = totalDeductedToday,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toStockDto() = StockDto(
    id = this[StockTable.id].toString(),
    vendor_id = this[StockTable.vendorId].toString(),
    item_id = this[StockTable.itemId]?.toString(),
    item_name = this[StockTable.itemName],
    quantity = this[StockTable.quantity].toDouble(),
    min_quantity = this[StockTable.minQuantity].toDouble(),
    cost_price = this[StockTable.costPrice].toDouble(),
    unit = this[StockTable.unit],
    base_unit = this[StockTable.baseUnit],
    conversion_rate = this[StockTable.conversionRate].toDouble(),
    is_menu_item = this[StockTable.isMenuItem],
    alert_enabled = this[StockTable.alertEnabled],
    created_at = this[StockTable.createdAt].toEpochMilliseconds(),
    updated_at = this[StockTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toStockTransactionDto() = StockTransactionDto(
    id = this[StockTransactionsTable.id].toString(),
    stock_id = this[StockTransactionsTable.stockId].toString(),
    type = this[StockTransactionsTable.type],
    quantity = this[StockTransactionsTable.quantity].toDouble(),
    previous_quantity = this[StockTransactionsTable.previousQuantity].toDouble(),
    order_id = this[StockTransactionsTable.orderId]?.toString(),
    recipe_id = this[StockTransactionsTable.recipeId]?.toString(),
    note = this[StockTransactionsTable.note],
    created_at = this[StockTransactionsTable.createdAt].toEpochMilliseconds(),
)
