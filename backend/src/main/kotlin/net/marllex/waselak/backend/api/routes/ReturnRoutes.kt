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

                // Create the return record — auto-complete so stock is restored immediately
                val returnId = ProductReturnsTable.insertAndGetId {
                    it[ProductReturnsTable.vendorId] = vendorUUID
                    it[ProductReturnsTable.orderId] = orderUUID
                    it[ProductReturnsTable.customerId] = customerId
                    it[returnType] = request.return_type
                    it[status] = "COMPLETED"
                    it[reason] = request.reason
                    it[refundAmount] = totalRefund
                    it[refundMethod] = request.refund_method
                    it[processedBy] = UUID.fromString(principal.userId)
                    it[processedAt] = now
                    it[notes] = request.notes
                    if (exchangeItemUUID != null) {
                        it[exchangeItemId] = exchangeItemUUID
                        it[exchangeQuantity] = request.exchange_quantity
                    }
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

                // Auto-restore stock for all restockable items (all business types)
                for ((returnItem, orderItem, _) in returnItemsData) {
                    if (!returnItem.restockable) continue
                    val itemId = orderItem[OrderItemsTable.itemId]
                    val returnQty = returnItem.quantity

                    val stockRow = StockTable.selectAll().where {
                        (StockTable.itemId eq itemId) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: continue

                    val currentQty = stockRow[StockTable.quantity]
                    val restoreAmount = BigDecimal(returnQty)
                    val newQty = currentQty + restoreAmount

                    StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                        it[quantity] = newQty
                        it[updatedAt] = now
                    }

                    // FIFO batch restoration
                    val batchesRestored = fifoRestoreBatches(
                        stockUUID = stockRow[StockTable.id].value,
                        vendorUUID = vendorUUID,
                        amount = restoreAmount,
                        orderId = orderUUID,
                        note = "Product return restock"
                    )

                    if (batchesRestored.isEmpty()) {
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
                }

                // EXCHANGE: Add replacement item to the order
                if (request.return_type == "EXCHANGE" && exchangeItemRow != null) {
                    val exchangePrice = exchangeItemRow[ItemsTable.price]
                    val exchangeQty = request.exchange_quantity

                    // Add new order item
                    OrderItemsTable.insert {
                        it[orderId] = orderUUID
                        it[itemId] = exchangeItemUUID!!
                        it[itemNameSnapshot] = exchangeItemRow[ItemsTable.name]
                        it[itemPriceSnapshot] = exchangePrice
                        it[quantity] = exchangeQty
                        it[note] = "Exchange replacement"
                        it[createdAt] = now
                    }

                    // Update order total: add exchange item price, subtract refund
                    val exchangeTotal = exchangePrice * BigDecimal(exchangeQty)
                    val priceDiff = exchangeTotal - totalRefund // positive = customer pays more
                    OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                        with(SqlExpressionBuilder) {
                            it[subtotal] = subtotal + exchangeTotal - totalRefund
                            it[total] = total + exchangeTotal - totalRefund
                        }
                        it[updatedAt] = now
                    }

                    // Deduct stock for exchange item
                    val exchangeStockRow = StockTable.selectAll().where {
                        (StockTable.itemId eq exchangeItemUUID!!) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull()
                    if (exchangeStockRow != null) {
                        val currentQty = exchangeStockRow[StockTable.quantity]
                        val deductAmount = BigDecimal(exchangeQty)
                        StockTable.update({ StockTable.id eq exchangeStockRow[StockTable.id] }) {
                            it[quantity] = currentQty - deductAmount
                            it[updatedAt] = now
                        }
                        StockTransactionsTable.insert {
                            it[stockId] = exchangeStockRow[StockTable.id]
                            it[type] = "SALE_DIRECT"
                            it[StockTransactionsTable.quantity] = deductAmount
                            it[previousQuantity] = currentQty
                            it[StockTransactionsTable.orderId] = orderUUID
                            it[StockTransactionsTable.note] = "Exchange replacement"
                            it[StockTransactionsTable.createdAt] = now
                        }
                    }
                }

                // Update customer stats
                if (customerId != null && totalRefund > BigDecimal.ZERO) {
                    CustomersTable.update({ CustomersTable.id eq customerId }) {
                        with(SqlExpressionBuilder) {
                            it[totalSpent] = totalSpent - totalRefund
                        }
                    }
                }

                // Update order: mark as REFUNDED if all items returned, update total
                val allOrderItemIds = orderItems.map { it[OrderItemsTable.id].toString() }
                val totalReturnedAfter = mutableMapOf<String, Int>()
                // Re-query all returns including this one
                val allReturnIds = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.orderId eq orderUUID) and
                    (ProductReturnsTable.status eq "COMPLETED")
                }.map { it[ProductReturnsTable.id] }
                if (allReturnIds.isNotEmpty()) {
                    ReturnItemsTable.selectAll().where {
                        ReturnItemsTable.returnId inList allReturnIds
                    }.forEach { ri ->
                        val oiId = ri[ReturnItemsTable.orderItemId].toString()
                        totalReturnedAfter[oiId] = (totalReturnedAfter[oiId] ?: 0) + ri[ReturnItemsTable.quantity]
                    }
                }
                val allFullyReturned = allOrderItemIds.all { oiId ->
                    val originalQty = orderItemMap[oiId]?.get(OrderItemsTable.quantity) ?: 0
                    val returnedQty = totalReturnedAfter[oiId] ?: 0
                    returnedQty >= originalQty
                }

                // Update order status and refund info
                OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                    if (allFullyReturned) {
                        it[status] = "REFUNDED"
                    }
                    it[refundedAt] = now
                    it[refundedBy] = UUID.fromString(principal.userId)
                    it[refundReason] = request.reason
                    it[updatedAt] = now
                }

                // Record refund in cash drawer if there's an open session
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
                        it[reason] = "Return: ${request.reason}"
                        it[CashMovementsTable.orderId] = orderUUID
                        it[createdBy] = UUID.fromString(principal.userId)
                        it[createdAt] = now
                    }
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[orderId] = orderUUID
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "RETURN_COMPLETED"
                    it[payload] = """{"return_id":"${returnId}","refund_amount":${totalRefund.toDouble()},"all_returned":$allFullyReturned}"""
                    it[createdAt] = now
                }

                ProductReturnDto(
                    id = returnId.toString(),
                    vendor_id = vendorUUID.toString(),
                    order_id = request.order_id,
                    customer_id = customerId?.toString(),
                    return_type = request.return_type,
                    status = "COMPLETED",
                    reason = request.reason,
                    refund_amount = totalRefund.toDouble(),
                    refund_method = request.refund_method,
                    processed_by = principal.userId,
                    processed_at = now.toEpochMilliseconds(),
                    notes = request.notes,
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

        // GET returns summary for a vendor
        get("/summary") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "RETURNS")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val returns = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.vendorId eq vendorUUID) and
                    (ProductReturnsTable.status eq "COMPLETED")
                }.toList()

                val totalRefunded = returns.sumOf { it[ProductReturnsTable.refundAmount].toDouble() }

                ReturnsSummaryDto(
                    total = returns.size,
                    completed = returns.size,
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
