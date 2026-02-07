package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.currentUser
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.ItemsTable
import net.marllex.cafeemanger.backend.data.database.StockTable
import net.marllex.cafeemanger.backend.data.database.StockTransactionsTable
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
    val item_id: String,
    val item_name: String,
    val quantity: Int,
    val min_quantity: Int,
    val cost_price: Double,
    val unit: String,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateStockDto(
    val item_id: String,
    val quantity: Int,
    val min_quantity: Int = 5,
    val cost_price: Double = 0.0,
    val unit: String = "pcs",
)

@Serializable
data class UpdateStockDto(
    val quantity: Int? = null,
    val min_quantity: Int? = null,
    val cost_price: Double? = null,
    val unit: String? = null,
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
    val type: String,
    val quantity: Int,
    val previous_quantity: Int,
    val order_id: String? = null,
    val note: String? = null,
    val created_at: Long? = null,
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
        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateStockDto>()
            require(request.quantity >= 0) { "Quantity must be non-negative" }

            val stock = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val itemUUID = UUID.fromString(request.item_id)

                // Verify item exists and belongs to vendor
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

                val now = Clock.System.now()
                val stockId = StockTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[StockTable.itemId] = itemUUID
                    it[itemName] = item[ItemsTable.name]
                    it[quantity] = request.quantity
                    it[minQuantity] = request.min_quantity
                    it[costPrice] = BigDecimal.valueOf(request.cost_price)
                    it[unit] = request.unit
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

                StockTable.update({
                    (StockTable.id eq stockUUID) and
                    (StockTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.quantity?.let { stmt[quantity] = it }
                    request.min_quantity?.let { stmt[minQuantity] = it }
                    request.cost_price?.let { stmt[costPrice] = BigDecimal.valueOf(it) }
                    request.unit?.let { stmt[unit] = it }
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
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toStockDto() = StockDto(
    id = this[StockTable.id].toString(),
    vendor_id = this[StockTable.vendorId].toString(),
    item_id = this[StockTable.itemId].toString(),
    item_name = this[StockTable.itemName],
    quantity = this[StockTable.quantity],
    min_quantity = this[StockTable.minQuantity],
    cost_price = this[StockTable.costPrice].toDouble(),
    unit = this[StockTable.unit],
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
