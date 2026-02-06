package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.currentUser
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.ActivityLogsTable
import net.marllex.cafeemanger.backend.data.database.ItemsTable
import net.marllex.cafeemanger.backend.data.database.OrderItemsTable
import net.marllex.cafeemanger.backend.data.database.OrdersTable
import net.marllex.cafeemanger.backend.domain.service.OrderService
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.java.KoinJavaComponent
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class CreateOrderDto(
    val channel: String,
    val table_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val client_address: String? = null,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val payment_method: String,
    val delivery_fee: Double = 0.0,
    val notes: String? = null,
    val items: List<CreateOrderItemDto>
)

@Serializable
data class CreateOrderItemDto(
    val item_id: String,
    val quantity: Int,
    val note: String? = null
)

@Serializable
data class UpdateStatusDto(val status: String)

@Serializable
data class AssignDeliveryDto(val delivery_user_id: String)

@Serializable
data class OrderDto(
    val id: String,
    val vendor_id: String,
    val channel: String,
    val status: String,
    val table_id: String? = null,
    val cashier_id: String,
    val delivery_user_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val client_address: String? = null,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val payment_method: String,
    val subtotal: Double,
    val delivery_fee: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double,
    val notes: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long? = null
)

@Serializable
data class OrderItemDto(
    val id: String,
    val order_id: String,
    val item_id: String,
    val item_name_snapshot: String,
    val item_price_snapshot: Double,
    val quantity: Int,
    val note: String? = null
)

@Serializable
data class PaginatedOrders(
    val orders: List<OrderDto>,
    val total: Int,
    val has_more: Boolean
)

fun Route.orderRoutes() {
    val orderService by KoinJavaComponent.inject<OrderService>(
        clazz = OrderService::class.java
    )

    route("/api/v1/orders") {
        // IMPORTANT: specific paths MUST come before /{id} to avoid route conflict
        get("/delivery/available") {
            val principal = requireRole("DELIVERY")

            val orders = transaction {
                val orderRows = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq UUID.fromString(principal.vendorId)) and
                                (OrdersTable.channel eq "DELIVERY") and
                                (OrdersTable.status eq "READY")
                    }
                    .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .toList()
                println("GAMALRAGAB:--> vendor_id=${orderRows.first().toOrderDto().vendor_id}")
                println("GAMALRAGAB:--> vendorId=${principal.vendorId}")
                println("GAMALRAGAB:--> UUID=${UUID.fromString(principal.vendorId)}")
                orderRows.map { orderRow ->
                    val items = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                        .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items)

                }
            }

            print("GAMALRAGAB:--> orders=${orders}")
            call.respond(HttpStatusCode.OK, orders)
        }

        get("/delivery/mine") {
            val principal = requireRole("DELIVERY")
            val status = call.parameters["status"]

            val orders = transaction {
                var query = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.deliveryUserId eq UUID.fromString(principal.userId)) and
                                (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }

                val orderRows = query.orderBy(OrdersTable.createdAt, SortOrder.DESC).toList()
                orderRows.map { orderRow ->
                    val items = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                        .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items)
                }
            }
            call.respond(HttpStatusCode.OK, orders)
        }

        get {
            val principal = currentUser()
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0

            val (orders, total) = transaction {
                var query = OrdersTable.selectAll()
                    .where { OrdersTable.vendorId eq UUID.fromString(principal.vendorId) }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }
                channel?.let { query = query.andWhere { OrdersTable.channel eq it } }

                val totalCount = query.count().toInt()
                val results = query
                    .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .limit(limit).offset(offset.toLong())
                    .map { orderRow ->
                        val items = OrderItemsTable.selectAll()
                            .where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                            .map { it.toOrderItemDto() }
                        orderRow.toOrderDto(items)
                    }
                results to totalCount
            }
            call.respond(HttpStatusCode.OK, PaginatedOrders(orders, total, offset + limit < total))
        }

        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val order = transaction {
                val orderRow = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.id eq UUID.fromString(id)) and
                                (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val items = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }

                orderRow.toOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        post {
            val principal = requireRole("CASHIER", "MANAGER")
            val request = call.receive<CreateOrderDto>()
            require(request.items.isNotEmpty()) { "Order must have at least one item" }

            val order = transaction {
                // Calculate totals from item snapshots
                var subtotal = BigDecimal.ZERO
                val itemSnapshots = request.items.map { orderItem ->
                    val item = ItemsTable.selectAll()
                        .where { ItemsTable.id eq UUID.fromString(orderItem.item_id) }
                        .firstOrNull()
                        ?: throw NoSuchElementException("Item ${orderItem.item_id} not found")

                    val price = item[ItemsTable.price]
                    subtotal += price * BigDecimal(orderItem.quantity)
                    Triple(item, orderItem, price)
                }

                val deliveryFeeAmount = if (request.channel == "DELIVERY") {
                    BigDecimal.valueOf(request.delivery_fee)
                } else BigDecimal.ZERO
                val total = subtotal + deliveryFeeAmount

                val orderId = OrdersTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[channel] = request.channel
                    it[status] = "CREATED"
                    request.table_id?.let { tid -> it[tableId] = UUID.fromString(tid) }
                    it[cashierId] = UUID.fromString(principal.userId)
                    it[clientName] = request.client_name
                    it[clientPhone] = request.client_phone
                    it[clientAddress] = request.client_address
                    it[geoLat] = request.geo_lat
                    it[geoLng] = request.geo_lng
                    it[paymentMethod] = request.payment_method
                    it[OrdersTable.subtotal] = subtotal
                    it[deliveryFee] = deliveryFeeAmount
                    it[tax] = BigDecimal.ZERO
                    it[OrdersTable.total] = total
                    it[notes] = request.notes
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Insert order items
                val orderItems = itemSnapshots.map { (item, orderItem, price) ->
                    val oiId = OrderItemsTable.insertAndGetId {
                        it[OrderItemsTable.orderId] = orderId
                        it[itemId] = UUID.fromString(orderItem.item_id)
                        it[itemNameSnapshot] = item[ItemsTable.name]
                        it[itemPriceSnapshot] = price
                        it[quantity] = orderItem.quantity
                        it[note] = orderItem.note
                        it[createdAt] = Clock.System.now()
                    }
                    OrderItemDto(
                        id = oiId.toString(),
                        order_id = orderId.toString(),
                        item_id = orderItem.item_id,
                        item_name_snapshot = item[ItemsTable.name],
                        item_price_snapshot = price.toDouble(),
                        quantity = orderItem.quantity,
                        note = orderItem.note
                    )
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[ActivityLogsTable.orderId] = orderId
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "ORDER_CREATED"
                    it[payload] = """{"channel":"${request.channel}"}"""
                    it[createdAt] = Clock.System.now()
                }

                OrdersTable.selectAll().where { OrdersTable.id eq orderId }
                    .first().toOrderDto(orderItems)
            }
            call.respond(HttpStatusCode.Created, order)
        }

        patch("/{id}/status") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateStatusDto>()

            val order = transaction {
                val current = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.id eq UUID.fromString(id)) and
                                (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val valid = orderService.validateStatusTransition(
                    current[OrdersTable.status], request.status,
                    current[OrdersTable.channel], principal.role
                )
                if (!valid) throw IllegalStateException("Invalid status transition")

                OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) {
                    it[status] = request.status
                    it[updatedAt] = Clock.System.now()
                }

                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "STATUS_CHANGED"
                    it[payload] =
                        """{"from":"${current[OrdersTable.status]}","to":"${request.status}"}"""
                    it[createdAt] = Clock.System.now()
                }

                val items = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }

                OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }
                    .first().toOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        patch("/{id}/assign") {
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<AssignDeliveryDto>()

            // Delivery users can only self-assign
            val deliveryUserId = if (principal.role == "DELIVERY") {
                principal.userId
            } else {
                request.delivery_user_id
            }

            val order = transaction {
                // Verify order is READY for assignment
                val currentOrder = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.id eq UUID.fromString(id)) and
                                (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = currentOrder[OrdersTable.status]
                if (currentStatus != "READY" && currentStatus != "ASSIGNED") {
                    throw IllegalStateException("Order must be READY to assign delivery")
                }

                OrdersTable.update({
                    (OrdersTable.id eq UUID.fromString(id)) and
                            (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[OrdersTable.deliveryUserId] = UUID.fromString(deliveryUserId)
                    it[status] = "ASSIGNED"
                    it[updatedAt] = Clock.System.now()
                }

                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "DELIVERY_ASSIGNED"
                    it[payload] = """{"delivery_user_id":"$deliveryUserId"}"""
                    it[createdAt] = Clock.System.now()
                }

                val items = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }

                OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }
                    .first().toOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, order)
        }

    }
}

private fun ResultRow.toOrderDto(items: List<OrderItemDto> = emptyList()) = OrderDto(
    id = this[OrdersTable.id].toString(),
    vendor_id = this[OrdersTable.vendorId].toString(),
    channel = this[OrdersTable.channel],
    status = this[OrdersTable.status],
    table_id = this[OrdersTable.tableId]?.toString(),
    cashier_id = this[OrdersTable.cashierId].toString(),
    delivery_user_id = this[OrdersTable.deliveryUserId]?.toString(),
    client_name = this[OrdersTable.clientName],
    client_phone = this[OrdersTable.clientPhone],
    client_address = this[OrdersTable.clientAddress],
    geo_lat = this[OrdersTable.geoLat],
    geo_lng = this[OrdersTable.geoLng],
    payment_method = this[OrdersTable.paymentMethod],
    subtotal = this[OrdersTable.subtotal].toDouble(),
    delivery_fee = this[OrdersTable.deliveryFee].toDouble(),
    tax = this[OrdersTable.tax].toDouble(),
    total = this[OrdersTable.total].toDouble(),
    notes = this[OrdersTable.notes],
    items = items,
    created_at = this[OrdersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[OrdersTable.updatedAt].toEpochMilliseconds()
)

private fun ResultRow.toOrderItemDto() = OrderItemDto(
    id = this[OrderItemsTable.id].toString(),
    order_id = this[OrderItemsTable.orderId].toString(),
    item_id = this[OrderItemsTable.itemId].toString(),
    item_name_snapshot = this[OrderItemsTable.itemNameSnapshot],
    item_price_snapshot = this[OrderItemsTable.itemPriceSnapshot].toDouble(),
    quantity = this[OrderItemsTable.quantity],
    note = this[OrderItemsTable.note]
)
