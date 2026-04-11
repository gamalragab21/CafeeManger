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
    val exchange_item_id: String? = null,
    val exchange_item_name: String? = null,
    val exchange_item_price: Double? = null,
    val exchange_quantity: Int = 0,
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
    // Exchange: replacement item
    val exchange_item_id: String? = null,
    val exchange_quantity: Int = 1,
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
data class ReturnsSummaryDto(
    val total: Int,
    val pending: Int = 0,
    val completed: Int,
    val rejected: Int = 0,
    val total_refunded: Double,
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

                // Get already-returned quantities for this order
                val existingReturns = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.orderId eq orderUUID) and
                    (ProductReturnsTable.status eq "COMPLETED")
                }.map { it[ProductReturnsTable.id] }

                val alreadyReturnedQty = mutableMapOf<String, Int>()
                if (existingReturns.isNotEmpty()) {
                    ReturnItemsTable.selectAll().where {
                        ReturnItemsTable.returnId inList existingReturns
                    }.forEach { ri ->
                        val oiId = ri[ReturnItemsTable.orderItemId].toString()
                        alreadyReturnedQty[oiId] = (alreadyReturnedQty[oiId] ?: 0) + ri[ReturnItemsTable.quantity]
                    }
                }

                // Calculate refund amount and validate items
                var totalRefund = BigDecimal.ZERO
                val returnItemsData = request.items.map { returnItem ->
                    val orderItem = orderItemMap[returnItem.order_item_id]
                        ?: throw NoSuchElementException("Order item ${returnItem.order_item_id} not found")

                    val originalQty = orderItem[OrderItemsTable.quantity]
                    val previouslyReturned = alreadyReturnedQty[returnItem.order_item_id] ?: 0
                    val remainingQty = originalQty - previouslyReturned

                    require(returnItem.quantity > 0) { "Return quantity must be positive" }
                    require(returnItem.quantity <= remainingQty) {
                        "Return quantity (${returnItem.quantity}) exceeds remaining quantity ($remainingQty). Already returned: $previouslyReturned"
                    }

                    // Calculate per-item refund: item price * return qty
                    val itemPrice = orderItem[OrderItemsTable.itemPriceSnapshot]
                    val perItemRefund = itemPrice * BigDecimal(returnItem.quantity)

                    totalRefund += perItemRefund

                    Triple(returnItem, orderItem, perItemRefund)
                }

                // Get customer ID from order
                val customerId = order[OrdersTable.customerId]

                // Resolve exchange item if provided
                val exchangeItemUUID = request.exchange_item_id?.let { UUID.fromString(it) }
                val exchangeItemRow = exchangeItemUUID?.let {
                    ItemsTable.selectAll().where { ItemsTable.id eq it }.firstOrNull()
                }

                // Create the return record as PENDING — needs approval
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
                    if (exchangeItemUUID != null) {
                        it[exchangeItemId] = exchangeItemUUID
                        it[exchangeQuantity] = request.exchange_quantity
                    }
                    it[createdAt] = now
                }

                // Create return items (no stock changes yet — happens on approval)
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
                        id = riId.toString(), return_id = returnId.toString(),
                        order_item_id = returnItem.order_item_id, item_id = itemId.toString(),
                        item_name = orderItem[OrderItemsTable.itemNameSnapshot],
                        quantity = returnItem.quantity, reason = returnItem.reason,
                        item_condition = returnItem.item_condition, restockable = returnItem.restockable,
                        refund_amount = perItemRefund.toDouble(),
                    )
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[orderId] = orderUUID
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "RETURN_CREATED"
                    it[payload] = """{"return_id":"$returnId","refund_amount":${totalRefund.toDouble()},"status":"PENDING"}"""
                    it[createdAt] = now
                }

                ProductReturnDto(
                    id = returnId.toString(), vendor_id = vendorUUID.toString(),
                    order_id = request.order_id, customer_id = customerId?.toString(),
                    return_type = request.return_type, status = "PENDING",
                    reason = request.reason, refund_amount = totalRefund.toDouble(),
                    refund_method = request.refund_method, notes = request.notes,
                    items = createdItems,
                    exchange_item_id = exchangeItemUUID?.toString(),
                    exchange_item_name = exchangeItemRow?.get(ItemsTable.name),
                    exchange_item_price = exchangeItemRow?.get(ItemsTable.price)?.toDouble(),
                    exchange_quantity = if (exchangeItemRow != null) request.exchange_quantity else 0,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Return created", mapOf("returnId" to returnDto.id, "itemCount" to returnDto.items.size.toString()))
            call.respond(HttpStatusCode.Created, returnDto)
        }

        // PROCESS a return (approve/reject/complete)
        patch("/{id}/process") {
            val trace = call.routeTrace()
            trace.step("Process return started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            @Serializable
            data class ProcessRequest(val status: String, val notes: String? = null)
            val request = call.receive<ProcessRequest>()
            require(request.status in listOf("COMPLETED", "REJECTED")) { "Status must be COMPLETED or REJECTED" }

            val returnDto = transaction {
                val now = Clock.System.now()
                val returnRow = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.id eq UUID.fromString(id)) and (ProductReturnsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Return not found")

                val currentStatus = returnRow[ProductReturnsTable.status]
                require(currentStatus == "PENDING") { "Can only process PENDING returns (current: $currentStatus)" }

                val returnUUID = returnRow[ProductReturnsTable.id]
                val orderUUID = returnRow[ProductReturnsTable.orderId]
                val customerId = returnRow[ProductReturnsTable.customerId]
                val totalRefund = returnRow[ProductReturnsTable.refundAmount]
                val returnType = returnRow[ProductReturnsTable.returnType]
                val exchangeItemId = returnRow[ProductReturnsTable.exchangeItemId]
                val exchangeQty = returnRow[ProductReturnsTable.exchangeQuantity]

                // Update return status
                ProductReturnsTable.update({ ProductReturnsTable.id eq UUID.fromString(id) }) {
                    it[status] = request.status
                    it[processedBy] = UUID.fromString(principal.userId)
                    it[processedAt] = now
                    if (request.notes != null) it[notes] = request.notes
                }

                if (request.status == "COMPLETED") {
                    // Restore stock for returned items
                    val returnItems = ReturnItemsTable.selectAll().where { ReturnItemsTable.returnId eq returnUUID }.toList()
                    for (ri in returnItems) {
                        if (!ri[ReturnItemsTable.restockable]) continue
                        val itemId = ri[ReturnItemsTable.itemId]
                        val returnQty = ri[ReturnItemsTable.quantity]
                        val stockRow = StockTable.selectAll().where {
                            (StockTable.itemId eq itemId) and (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: continue

                        val currentQty = stockRow[StockTable.quantity]
                        val restoreAmount = BigDecimal(returnQty)
                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                            it[quantity] = currentQty + restoreAmount
                            it[updatedAt] = now
                        }
                        StockTransactionsTable.insert {
                            it[stockId] = stockRow[StockTable.id]
                            it[type] = "RETURN"
                            it[StockTransactionsTable.quantity] = restoreAmount
                            it[previousQuantity] = currentQty
                            it[orderId] = orderUUID
                            it[note] = "Product return restock"
                            it[createdAt] = now
                        }
                    }

                    // Exchange: add replacement item to order + deduct stock
                    if (returnType == "EXCHANGE" && exchangeItemId != null) {
                        val exchangeItem = ItemsTable.selectAll().where { ItemsTable.id eq exchangeItemId }.firstOrNull()
                        if (exchangeItem != null) {
                            val exchangePrice = exchangeItem[ItemsTable.price]
                            OrderItemsTable.insert {
                                it[orderId] = orderUUID
                                it[OrderItemsTable.itemId] = exchangeItemId
                                it[itemNameSnapshot] = exchangeItem[ItemsTable.name]
                                it[itemPriceSnapshot] = exchangePrice
                                it[quantity] = exchangeQty
                                it[note] = "Exchange replacement"
                                it[createdAt] = now
                            }
                            val exchangeTotal = exchangePrice * BigDecimal(exchangeQty)
                            OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                                with(SqlExpressionBuilder) {
                                    it[subtotal] = subtotal + exchangeTotal - totalRefund
                                    it[total] = total + exchangeTotal - totalRefund
                                }
                                it[updatedAt] = now
                            }
                            // Deduct exchange item stock
                            val exchStock = StockTable.selectAll().where {
                                (StockTable.itemId eq exchangeItemId) and (StockTable.vendorId eq vendorUUID)
                            }.firstOrNull()
                            if (exchStock != null) {
                                val cq = exchStock[StockTable.quantity]
                                val da = BigDecimal(exchangeQty)
                                StockTable.update({ StockTable.id eq exchStock[StockTable.id] }) {
                                    it[quantity] = cq - da; it[updatedAt] = now
                                }
                            }
                        }
                    }

                    // Update customer stats
                    if (customerId != null && totalRefund > BigDecimal.ZERO) {
                        CustomersTable.update({ CustomersTable.id eq customerId }) {
                            with(SqlExpressionBuilder) { it[totalSpent] = totalSpent - totalRefund }
                        }
                    }

                    // Check if all items fully returned → mark order REFUNDED
                    val orderItems = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderUUID }.toList()
                    val allReturnIds = ProductReturnsTable.selectAll().where {
                        (ProductReturnsTable.orderId eq orderUUID) and (ProductReturnsTable.status eq "COMPLETED")
                    }.map { it[ProductReturnsTable.id] }
                    val totalReturnedQty = mutableMapOf<String, Int>()
                    if (allReturnIds.isNotEmpty()) {
                        ReturnItemsTable.selectAll().where { ReturnItemsTable.returnId inList allReturnIds }.forEach { ri ->
                            val oiId = ri[ReturnItemsTable.orderItemId].toString()
                            totalReturnedQty[oiId] = (totalReturnedQty[oiId] ?: 0) + ri[ReturnItemsTable.quantity]
                        }
                    }
                    val allFullyReturned = orderItems.all { oi ->
                        val oiId = oi[OrderItemsTable.id].toString()
                        (totalReturnedQty[oiId] ?: 0) >= oi[OrderItemsTable.quantity]
                    }
                    OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                        if (allFullyReturned) it[status] = "REFUNDED"
                        it[refundedAt] = now
                        it[refundedBy] = UUID.fromString(principal.userId)
                        it[updatedAt] = now
                    }

                    // Cash drawer refund entry
                    val drawerSession = CashDrawerSessionsTable.selectAll().where {
                        (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                        (CashDrawerSessionsTable.cashierId eq UUID.fromString(principal.userId)) and
                        (CashDrawerSessionsTable.status eq "OPEN")
                    }.firstOrNull()
                    if (drawerSession != null) {
                        CashMovementsTable.insert {
                            it[sessionId] = drawerSession[CashDrawerSessionsTable.id]
                            it[CashMovementsTable.vendorId] = vendorUUID
                            it[type] = "REFUND"
                            it[amount] = totalRefund
                            it[reason] = "Return approved"
                            it[CashMovementsTable.orderId] = orderUUID
                            it[createdBy] = UUID.fromString(principal.userId)
                            it[createdAt] = now
                        }
                    }
                }

                // Log
                ActivityLogsTable.insert {
                    it[orderId] = orderUUID
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = if (request.status == "COMPLETED") "RETURN_COMPLETED" else "RETURN_REJECTED"
                    it[payload] = """{"return_id":"$id","status":"${request.status}"}"""
                    it[createdAt] = now
                }

                // Return updated DTO
                val items = ReturnItemsTable.selectAll().where { ReturnItemsTable.returnId eq returnUUID }.map { it.toReturnItemDto() }
                val updatedRow = ProductReturnsTable.selectAll().where { ProductReturnsTable.id eq UUID.fromString(id) }.first()
                updatedRow.toProductReturnDto(items)
            }

            trace.step("Return processed", mapOf("returnId" to id, "newStatus" to request.status))
            call.respond(HttpStatusCode.OK, returnDto)
        }

        // GET returns summary for a vendor
        get("/summary") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val allReturns = ProductReturnsTable.selectAll().where {
                    ProductReturnsTable.vendorId eq vendorUUID
                }.toList()

                val pending = allReturns.count { it[ProductReturnsTable.status] == "PENDING" }
                val completed = allReturns.count { it[ProductReturnsTable.status] == "COMPLETED" }
                val rejected = allReturns.count { it[ProductReturnsTable.status] == "REJECTED" }
                val totalRefunded = allReturns.filter { it[ProductReturnsTable.status] == "COMPLETED" }
                    .sumOf { it[ProductReturnsTable.refundAmount].toDouble() }

                ReturnsSummaryDto(
                    total = allReturns.size,
                    pending = pending,
                    completed = completed,
                    rejected = rejected,
                    total_refunded = Math.round(totalRefunded * 100.0) / 100.0,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toProductReturnDto(items: List<ReturnItemDto> = emptyList()): ProductReturnDto {
    val exchItemId = this[ProductReturnsTable.exchangeItemId]
    val exchItemName = exchItemId?.let {
        ItemsTable.selectAll().where { ItemsTable.id eq it }.firstOrNull()?.get(ItemsTable.name)
    }
    val exchItemPrice = exchItemId?.let {
        ItemsTable.selectAll().where { ItemsTable.id eq it }.firstOrNull()?.get(ItemsTable.price)?.toDouble()
    }
    return ProductReturnDto(
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
        exchange_item_id = exchItemId?.toString(),
        exchange_item_name = exchItemName,
        exchange_item_price = exchItemPrice,
        exchange_quantity = this[ProductReturnsTable.exchangeQuantity],
        created_at = this[ProductReturnsTable.createdAt].toEpochMilliseconds(),
    )
}

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
