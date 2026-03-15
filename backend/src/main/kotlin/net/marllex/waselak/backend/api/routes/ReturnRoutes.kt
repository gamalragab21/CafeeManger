package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class ProductReturnDto(
    val id: String,
    val vendor_id: String,
    val order_id: String,
    val customer_id: String? = null,
    val return_type: String,           // RETURN, EXCHANGE
    val status: String,                // PENDING, APPROVED, REJECTED, COMPLETED
    val reason: String,
    val refund_amount: Double,
    val refund_method: String? = null,
    val processed_by: String? = null,
    val processed_at: Long? = null,
    val notes: String? = null,
    val items: List<ReturnItemDto> = emptyList(),
    val created_at: Long? = null,
)

@Serializable
data class ReturnItemDto(
    val id: String,
    val return_id: String,
    val order_item_id: String,
    val item_id: String,
    val item_name: String? = null,
    val quantity: Int,
    val reason: String? = null,        // DEFECTIVE, WRONG_ITEM, CHANGED_MIND, EXPIRED, OTHER
    val item_condition: String = "GOOD",// GOOD, DAMAGED, EXPIRED
    val restockable: Boolean = true,
    val refund_amount: Double,
    val created_at: Long? = null,
)

@Serializable
data class CreateReturnDto(
    val order_id: String,
    val return_type: String = "RETURN", // RETURN or EXCHANGE
    val reason: String,
    val refund_method: String? = null,  // CASH, CARD, CREDIT, ORIGINAL_METHOD
    val notes: String? = null,
    val items: List<CreateReturnItemDto>,
)

@Serializable
data class CreateReturnItemDto(
    val order_item_id: String,
    val quantity: Int,
    val reason: String? = null,
    val item_condition: String = "GOOD",
    val restockable: Boolean = true,
)

@Serializable
data class ProcessReturnDto(
    val status: String,                // APPROVED, REJECTED
    val notes: String? = null,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.returnRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/returns") {

        // GET all returns for vendor
        get {
            val trace = call.routeTrace()
            trace.step("List returns started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val orderId = call.parameters["order_id"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50

            val returns = transaction {
                var query = ProductReturnsTable.selectAll().where {
                    ProductReturnsTable.vendorId eq vendorUUID
                }
                status?.let { s ->
                    query = query.andWhere { ProductReturnsTable.status eq s.uppercase() }
                }
                orderId?.let { oid ->
                    query = query.andWhere { ProductReturnsTable.orderId eq UUID.fromString(oid) }
                }

                query.orderBy(ProductReturnsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val returnId = row[ProductReturnsTable.id]
                        val items = ReturnItemsTable.selectAll().where {
                            ReturnItemsTable.returnId eq returnId
                        }.map { it.toReturnItemDto() }
                        row.toProductReturnDto(items)
                    }
            }
            trace.step("Returns listed", mapOf("count" to returns.size.toString()))
            call.respond(HttpStatusCode.OK, returns)
        }

        // GET single return
        get("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val returnDto = transaction {
                val row = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.id eq UUID.fromString(id)) and
                    (ProductReturnsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Return not found")

                val items = ReturnItemsTable.selectAll().where {
                    ReturnItemsTable.returnId eq row[ProductReturnsTable.id]
                }.map { it.toReturnItemDto() }

                row.toProductReturnDto(items)
            }
            call.respond(HttpStatusCode.OK, returnDto)
        }

        // CREATE a product return
        post {
            val trace = call.routeTrace()
            trace.step("Create return started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<CreateReturnDto>()

            require(request.items.isNotEmpty()) { "At least one return item is required" }
            require(request.reason.isNotBlank()) { "Return reason is required" }
            require(request.return_type in listOf("RETURN", "EXCHANGE")) { "Invalid return type" }

            val returnDto = transaction {
                val orderUUID = UUID.fromString(request.order_id)
                val now = Clock.System.now()

                // Verify order belongs to vendor and is COMPLETED
                val order = OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderUUID) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val orderStatus = order[OrdersTable.status]
                require(orderStatus == "COMPLETED" || orderStatus == "REFUNDED") {
                    "Can only return items from completed orders (current: $orderStatus)"
                }

                // Get order items for validation
                val orderItems = OrderItemsTable.selectAll().where {
                    OrderItemsTable.orderId eq orderUUID
                }.toList()
                val orderItemMap = orderItems.associateBy { it[OrderItemsTable.id].toString() }

                // Calculate refund amount and validate items
                var totalRefund = BigDecimal.ZERO
                val returnItemsData = request.items.map { returnItem ->
                    val orderItem = orderItemMap[returnItem.order_item_id]
                        ?: throw NoSuchElementException("Order item ${returnItem.order_item_id} not found")

                    require(returnItem.quantity > 0) { "Return quantity must be positive" }
                    require(returnItem.quantity <= orderItem[OrderItemsTable.quantity]) {
                        "Return quantity (${returnItem.quantity}) exceeds order quantity (${orderItem[OrderItemsTable.quantity]})"
                    }

                    // Calculate per-item refund: (item price * return qty) / order qty
                    val itemPrice = orderItem[OrderItemsTable.itemPriceSnapshot]
                    val perItemRefund = itemPrice * BigDecimal(returnItem.quantity)

                    totalRefund += perItemRefund

                    Triple(returnItem, orderItem, perItemRefund)
                }

                // Get customer ID from order
                val customerId = order[OrdersTable.customerId]

                // Create the return record
                val returnId = ProductReturnsTable.insertAndGetId {
                    it[ProductReturnsTable.vendorId] = vendorUUID
                    it[ProductReturnsTable.orderId] = orderUUID
                    it[ProductReturnsTable.customerId] = customerId
                    it[returnType] = request.return_type
                    it[status] = "PENDING"
                    it[reason] = request.reason
                    it[refundAmount] = totalRefund
                    it[refundMethod] = request.refund_method
                    it[notes] = request.notes
                    it[createdAt] = now
                }

                // Create return items
                val createdItems = returnItemsData.map { (returnItem, orderItem, perItemRefund) ->
                    val itemId = orderItem[OrderItemsTable.itemId]
                    val riId = ReturnItemsTable.insertAndGetId {
                        it[ReturnItemsTable.returnId] = returnId
                        it[orderItemId] = UUID.fromString(returnItem.order_item_id)
                        it[ReturnItemsTable.itemId] = itemId
                        it[quantity] = returnItem.quantity
                        it[reason] = returnItem.reason
                        it[condition] = returnItem.item_condition
                        it[restockable] = returnItem.restockable
                        it[refundAmount] = perItemRefund
                        it[createdAt] = now
                    }

                    ReturnItemDto(
                        id = riId.toString(),
                        return_id = returnId.toString(),
                        order_item_id = returnItem.order_item_id,
                        item_id = itemId.toString(),
                        item_name = orderItem[OrderItemsTable.itemNameSnapshot],
                        quantity = returnItem.quantity,
                        reason = returnItem.reason,
                        item_condition = returnItem.item_condition,
                        restockable = returnItem.restockable,
                        refund_amount = perItemRefund.toDouble(),
                    )
                }

                ProductReturnDto(
                    id = returnId.toString(),
                    vendor_id = vendorUUID.toString(),
                    order_id = request.order_id,
                    customer_id = customerId?.toString(),
                    return_type = request.return_type,
                    status = "PENDING",
                    reason = request.reason,
                    refund_amount = totalRefund.toDouble(),
                    refund_method = request.refund_method,
                    notes = request.notes,
                    items = createdItems,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Return created", mapOf("returnId" to returnDto.id, "itemCount" to returnDto.items.size.toString()))
            call.respond(HttpStatusCode.Created, returnDto)
        }

        // PROCESS a return (approve/reject) -- MANAGER only
        patch("/{id}/process") {
            val trace = call.routeTrace()
            trace.step("Process return started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<ProcessReturnDto>()

            require(request.status in listOf("APPROVED", "REJECTED")) {
                "Status must be APPROVED or REJECTED"
            }

            val returnDto = transaction {
                val returnUUID = UUID.fromString(id)
                val now = Clock.System.now()

                val returnRow = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.id eq returnUUID) and
                    (ProductReturnsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Return not found")

                require(returnRow[ProductReturnsTable.status] == "PENDING") {
                    "Can only process pending returns"
                }

                val newStatus = if (request.status == "APPROVED") "COMPLETED" else "REJECTED"

                // Update return status
                ProductReturnsTable.update({
                    ProductReturnsTable.id eq returnUUID
                }) {
                    it[status] = newStatus
                    it[processedBy] = UUID.fromString(principal.userId)
                    it[processedAt] = now
                    it[notes] = request.notes ?: returnRow[ProductReturnsTable.notes]
                }

                // If approved, restore stock for restockable items
                if (request.status == "APPROVED") {
                    val returnItems = ReturnItemsTable.selectAll().where {
                        ReturnItemsTable.returnId eq returnUUID
                    }.toList()

                    for (returnItem in returnItems) {
                        if (!returnItem[ReturnItemsTable.restockable]) continue

                        val itemId = returnItem[ReturnItemsTable.itemId]
                        val returnQty = returnItem[ReturnItemsTable.quantity]

                        // Find stock row for this item
                        val stockRow = StockTable.selectAll().where {
                            (StockTable.itemId eq itemId) and (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: continue

                        val currentQty = stockRow[StockTable.quantity]
                        val restoreAmount = BigDecimal(returnQty)
                        val newQty = currentQty + restoreAmount

                        // Restore to main stock
                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                            it[quantity] = newQty
                            it[updatedAt] = now
                        }

                        // FIFO batch restoration
                        val batchesRestored = fifoRestoreBatches(
                            stockUUID = stockRow[StockTable.id].value,
                            vendorUUID = vendorUUID,
                            amount = restoreAmount,
                            orderId = returnRow[ProductReturnsTable.orderId].value,
                            note = "Product return restock"
                        )

                        if (batchesRestored.isEmpty()) {
                            StockTransactionsTable.insert {
                                it[stockId] = stockRow[StockTable.id]
                                it[type] = "RETURN"
                                it[StockTransactionsTable.quantity] = restoreAmount
                                it[previousQuantity] = currentQty
                                it[orderId] = returnRow[ProductReturnsTable.orderId]
                                it[note] = "Product return restock"
                                it[createdAt] = now
                            }
                        }
                    }

                    // Update customer stats if applicable
                    val customerId = returnRow[ProductReturnsTable.customerId]
                    val refundAmount = returnRow[ProductReturnsTable.refundAmount]
                    if (customerId != null && refundAmount > BigDecimal.ZERO) {
                        CustomersTable.update({ CustomersTable.id eq customerId }) {
                            with(SqlExpressionBuilder) {
                                it[totalSpent] = totalSpent - refundAmount
                            }
                        }
                    }

                    // Log activity
                    ActivityLogsTable.insert {
                        it[orderId] = returnRow[ProductReturnsTable.orderId]
                        it[userId] = UUID.fromString(principal.userId)
                        it[action] = "RETURN_APPROVED"
                        it[payload] = """{"return_id":"$id","refund_amount":${refundAmount.toDouble()}}"""
                        it[createdAt] = now
                    }
                }

                // Fetch updated return
                val updatedRow = ProductReturnsTable.selectAll().where {
                    ProductReturnsTable.id eq returnUUID
                }.first()
                val items = ReturnItemsTable.selectAll().where {
                    ReturnItemsTable.returnId eq returnUUID
                }.map { it.toReturnItemDto() }

                updatedRow.toProductReturnDto(items)
            }
            trace.step("Return processed", mapOf("returnId" to returnDto.id, "status" to returnDto.status))
            call.respond(HttpStatusCode.OK, returnDto)
        }

        // GET returns summary for a vendor
        get("/summary") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val returns = ProductReturnsTable.selectAll().where {
                    ProductReturnsTable.vendorId eq vendorUUID
                }.toList()

                val pending = returns.count { it[ProductReturnsTable.status] == "PENDING" }
                val completed = returns.count { it[ProductReturnsTable.status] == "COMPLETED" }
                val rejected = returns.count { it[ProductReturnsTable.status] == "REJECTED" }
                val totalRefunded = returns
                    .filter { it[ProductReturnsTable.status] == "COMPLETED" }
                    .sumOf { it[ProductReturnsTable.refundAmount].toDouble() }

                mapOf(
                    "total" to returns.size,
                    "pending" to pending,
                    "completed" to completed,
                    "rejected" to rejected,
                    "total_refunded" to totalRefunded,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toProductReturnDto(items: List<ReturnItemDto> = emptyList()) = ProductReturnDto(
    id = this[ProductReturnsTable.id].toString(),
    vendor_id = this[ProductReturnsTable.vendorId].toString(),
    order_id = this[ProductReturnsTable.orderId].toString(),
    customer_id = this[ProductReturnsTable.customerId]?.toString(),
    return_type = this[ProductReturnsTable.returnType],
    status = this[ProductReturnsTable.status],
    reason = this[ProductReturnsTable.reason],
    refund_amount = this[ProductReturnsTable.refundAmount].toDouble(),
    refund_method = this[ProductReturnsTable.refundMethod],
    processed_by = this[ProductReturnsTable.processedBy]?.toString(),
    processed_at = this[ProductReturnsTable.processedAt]?.toEpochMilliseconds(),
    notes = this[ProductReturnsTable.notes],
    items = items,
    created_at = this[ProductReturnsTable.createdAt].toEpochMilliseconds(),
)

private fun ResultRow.toReturnItemDto(): ReturnItemDto {
    val itemId = this[ReturnItemsTable.itemId]
    val itemName = ItemsTable.selectAll().where { ItemsTable.id eq itemId }
        .firstOrNull()?.get(ItemsTable.name)

    return ReturnItemDto(
        id = this[ReturnItemsTable.id].toString(),
        return_id = this[ReturnItemsTable.returnId].toString(),
        order_item_id = this[ReturnItemsTable.orderItemId].toString(),
        item_id = itemId.toString(),
        item_name = itemName,
        quantity = this[ReturnItemsTable.quantity],
        reason = this[ReturnItemsTable.reason],
        item_condition = this[ReturnItemsTable.condition],
        restockable = this[ReturnItemsTable.restockable],
        refund_amount = this[ReturnItemsTable.refundAmount].toDouble(),
        created_at = this[ReturnItemsTable.createdAt].toEpochMilliseconds(),
    )
}
