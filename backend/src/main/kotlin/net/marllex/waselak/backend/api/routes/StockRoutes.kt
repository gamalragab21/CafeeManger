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
    val quantity: Int,
    val min_quantity: Int,
    val cost_price: Double,
    val unit: String,
    val is_menu_item: Boolean = true,
    val alert_enabled: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateStockDto(
    val item_id: String? = null, // Optional - null for independent stock items
    val item_name: String? = null, // Required if item_id is null
    val quantity: Int,
    val min_quantity: Int = 5,
    val cost_price: Double = 0.0,
    val unit: String = "pcs",
    val alert_enabled: Boolean = true,
)

@Serializable
data class UpdateStockDto(
    val item_name: String? = null,
    val quantity: Int? = null,
    val min_quantity: Int? = null,
    val cost_price: Double? = null,
    val unit: String? = null,
    val alert_enabled: Boolean? = null,
)

@Serializable
data class AdjustQuantityDto(
    val quantity: Int,
    val note: String? = null,
)

@Serializable
data class StockTransactionDto(
    val id: String,
    val stock_id: String,
    val item_name: String? = null,
    val type: String,
    val quantity: Int,
    val previous_quantity: Int,
    val order_id: String? = null,
    val note: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class StockAlertDto(
    val id: String,
    val item_name: String,
    val quantity: Int,
    val min_quantity: Int,
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
    val total_transactions_today: Int,
    val total_added_today: Int,
    val total_deducted_today: Int,
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
                    it[quantity] = request.quantity
                    it[minQuantity] = request.min_quantity
                    it[costPrice] = BigDecimal.valueOf(request.cost_price)
                    it[unit] = request.unit
                    it[isMenuItem] = isMenuLinked
                    it[alertEnabled] = request.alert_enabled
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                // Record initial transaction
                StockTransactionsTable.insertAndGetId {
                    it[StockTransactionsTable.stockId] = stockId
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = request.quantity
                    it[previousQuantity] = 0
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
                    request.quantity?.let { stmt[quantity] = it }
                    request.min_quantity?.let { stmt[minQuantity] = it }
                    request.cost_price?.let { stmt[costPrice] = BigDecimal.valueOf(it) }
                    request.unit?.let { stmt[unit] = it }
                    request.alert_enabled?.let { stmt[alertEnabled] = it }
                    stmt[updatedAt] = now
                }

                // Log quantity change
                if (request.quantity != null && request.quantity != oldQty) {
                    StockTransactionsTable.insertAndGetId {
                        it[stockId] = stockUUID
                        it[type] = "ADJUST"
                        it[quantity] = request.quantity
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
                val newQty = oldQty + request.quantity
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
                    it[StockTransactionsTable.quantity] = request.quantity
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
                val newQty = (oldQty - request.quantity).coerceAtLeast(0)
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
                    it[StockTransactionsTable.quantity] = request.quantity
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock deducted"
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
            val type = call.parameters["type"] // ADD, DEDUCT, ADJUST
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
                            quantity = row[StockTransactionsTable.quantity],
                            previous_quantity = row[StockTransactionsTable.previousQuantity],
                            order_id = row[StockTransactionsTable.orderId]?.toString(),
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
                            quantity = row[StockTable.quantity],
                            min_quantity = row[StockTable.minQuantity],
                            unit = row[StockTable.unit],
                            is_out_of_stock = row[StockTable.quantity] <= 0,
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
                val totalValue = stocks.sumOf { it[StockTable.quantity] * it[StockTable.costPrice].toDouble() }
                val lowStockCount = stocks.count {
                    val qty = it[StockTable.quantity]
                    val minQty = it[StockTable.minQuantity]
                    qty in 1..minQty
                }
                val outOfStockCount = stocks.count { it[StockTable.quantity] <= 0 }
                val healthyCount = totalItems - lowStockCount - outOfStockCount
                val menuItemsCount = stocks.count { it[StockTable.isMenuItem] }
                val independentItemsCount = stocks.count { !it[StockTable.isMenuItem] }

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

                val totalTransactionsToday = todayTransactions.size
                val totalAddedToday = todayTransactions.filter { it[StockTransactionsTable.type] == "ADD" }
                    .sumOf { it[StockTransactionsTable.quantity] }
                val totalDeductedToday = todayTransactions.filter { it[StockTransactionsTable.type] == "DEDUCT" }
                    .sumOf { it[StockTransactionsTable.quantity] }

                StockAnalyticsSummaryDto(
                    total_items = totalItems,
                    total_value = totalValue,
                    low_stock_count = lowStockCount,
                    out_of_stock_count = outOfStockCount,
                    healthy_count = healthyCount,
                    menu_items_count = menuItemsCount,
                    independent_items_count = independentItemsCount,
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
    quantity = this[StockTable.quantity],
    min_quantity = this[StockTable.minQuantity],
    cost_price = this[StockTable.costPrice].toDouble(),
    unit = this[StockTable.unit],
    is_menu_item = this[StockTable.isMenuItem],
    alert_enabled = this[StockTable.alertEnabled],
    created_at = this[StockTable.createdAt].toEpochMilliseconds(),
    updated_at = this[StockTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toStockTransactionDto() = StockTransactionDto(
    id = this[StockTransactionsTable.id].toString(),
    stock_id = this[StockTransactionsTable.stockId].toString(),
    type = this[StockTransactionsTable.type],
    quantity = this[StockTransactionsTable.quantity],
    previous_quantity = this[StockTransactionsTable.previousQuantity],
    order_id = this[StockTransactionsTable.orderId]?.toString(),
    note = this[StockTransactionsTable.note],
    created_at = this[StockTransactionsTable.createdAt].toEpochMilliseconds(),
)
