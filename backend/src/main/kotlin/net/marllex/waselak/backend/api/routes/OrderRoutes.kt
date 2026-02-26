package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.ActivityLogsTable
import net.marllex.waselak.backend.data.database.CustomerAddressesTable
import net.marllex.waselak.backend.data.database.CustomersTable
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.OrderItemsTable
import net.marllex.waselak.backend.data.database.OrdersTable
import net.marllex.waselak.backend.data.database.StockTable
import net.marllex.waselak.backend.data.database.StockTransactionsTable
import net.marllex.waselak.backend.data.database.RecipesTable
import net.marllex.waselak.backend.data.database.RecipeIngredientsTable
import net.marllex.waselak.backend.data.database.TaxPlacesTable
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.data.database.TablesTable
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.domain.service.OrderService
import net.marllex.waselak.backend.config.JwtConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.plugins.origin
import io.ktor.server.routing.application
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.koin.java.KoinJavaComponent
import java.math.BigDecimal
import java.util.UUID
import java.util.Date

@Serializable
data class CreateOrderDto(
    val channel: String,
    val table_id: String? = null,
    val customer_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val client_address: String? = null,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val payment_method: String,
    val payment_timing: String = "PAY_NOW",
    val delivery_fee: Double = 0.0,
    val discount: Double = 0.0,
    val discount_type: String = "FIXED", // FIXED or PERCENT
    val tax_place_id: String? = null,
    val notes: String? = null,
    val items: List<CreateOrderItemDto>
)

@Serializable
data class CreateOrderItemDto(
    val item_id: String, val quantity: Int, val note: String? = null
)

@Serializable
data class UpdateOrderDto(
    val client_name: String? = null,
    val client_phone: String? = null,
    val client_address: String? = null,
    val notes: String? = null,
    val payment_method: String? = null,
    val delivery_fee: Double? = null,
    val tax_place_id: String? = null,
    val items: List<CreateOrderItemDto>? = null,
)

@Serializable
data class UpdateStatusDto(val status: String)

@Serializable
data class UpdatePaymentStatusDto(
    val payment_status: String,
    val payment_method: String? = null,
)

@Serializable
data class AssignDeliveryDto(val delivery_user_id: String)

@Serializable
data class ShareReceiptResponseDto(
    val url: String, val token: String, val expires_at: Long
)

@Serializable
data class OrderDto(
    val id: String,
    val vendor_id: String,
    val channel: String,
    val status: String,
    val table_id: String? = null,
    val table_number: String? = null,
    val cashier_id: String,
    val cashier_name: String? = null,
    val delivery_user_id: String? = null,
    val delivery_user_name: String? = null,
    val customer_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val client_address: String? = null,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val payment_method: String,
    val payment_status: String = "PENDING",
    val payment_timing: String = "PAY_NOW",
    val payment_confirmed_at: Long? = null,
    val payment_confirmed_by: String? = null,
    val subtotal: Double,
    val delivery_fee: Double = 0.0,
    val discount: Double = 0.0,
    val discount_type: String = "FIXED",
    val tax: Double = 0.0,
    val tax_percent: Double = 0.0,
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
    val orders: List<OrderDto>, val total: Int, val has_more: Boolean
)

@Serializable
data class DeliveryDashboardItemDto(
    val delivery_user_id: String,
    val delivery_user_name: String,
    val delivery_user_phone: String,
    val status: String,
    val active_order_count: Int,
    val active_orders: List<DeliveryOrderSummaryDto>,
)

@Serializable
data class DeliveryOrderSummaryDto(
    val order_id: String,
    val status: String,
    val client_name: String? = null,
    val client_address: String? = null,
    val total: Double,
    val created_at: Long,
)

fun Route.orderRoutes() {
    val orderService by KoinJavaComponent.inject<OrderService>(
        clazz = OrderService::class.java
    )
    val jwtConfig by lazy { JwtConfig(this.application.environment.config) }

    route("/api/v1/orders") {
        // IMPORTANT: specific paths MUST come before /{id} to avoid route conflict

        get("/delivery-dashboard") {
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val dashboard = transaction {
                val deliveryUsers = UsersTable.selectAll().where {
                    (UsersTable.vendorId eq vendorUUID) and (UsersTable.role eq "DELIVERY") and (UsersTable.active eq true)
                }.toList()

                val activeStatuses = listOf("ASSIGNED", "OUT_FOR_DELIVERY")

                deliveryUsers.map { user ->
                    val userId = user[UsersTable.id].value
                    val activeOrders = OrdersTable.selectAll().where {
                        (OrdersTable.vendorId eq vendorUUID) and
                        (OrdersTable.deliveryUserId eq userId) and
                        (OrdersTable.status inList activeStatuses)
                    }.orderBy(OrdersTable.createdAt, SortOrder.DESC).map { row ->
                        DeliveryOrderSummaryDto(
                            order_id = row[OrdersTable.id].toString(),
                            status = row[OrdersTable.status],
                            client_name = row[OrdersTable.clientName],
                            client_address = row[OrdersTable.clientAddress],
                            total = row[OrdersTable.total].toDouble(),
                            created_at = row[OrdersTable.createdAt].toEpochMilliseconds(),
                        )
                    }

                    DeliveryDashboardItemDto(
                        delivery_user_id = userId.toString(),
                        delivery_user_name = user[UsersTable.name],
                        delivery_user_phone = user[UsersTable.phone],
                        status = if (activeOrders.isEmpty()) "AVAILABLE" else "BUSY",
                        active_order_count = activeOrders.size,
                        active_orders = activeOrders,
                    )
                }
            }
            call.respond(HttpStatusCode.OK, dashboard)
        }

        get("/delivery/available") {
            val principal = requireRole("DELIVERY")

            val orders = transaction {
                val orderRows = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq UUID.fromString(principal.vendorId)) and (OrdersTable.channel eq "DELIVERY") and (OrdersTable.deliveryUserId.isNull()) and (OrdersTable.status eq "READY")
                }.orderBy(OrdersTable.createdAt, SortOrder.DESC).toList()
                val userIds = orderRows.flatMap { listOf(it[OrdersTable.cashierId], it[OrdersTable.deliveryUserId]) }
                    .filterNotNull().distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                orderRows.map { orderRow ->
                    val items =
                        OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                            .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items, usersMap)
                }
            }
            call.respond(HttpStatusCode.OK, orders)
        }

        get("/delivery/mine") {
            val principal = requireRole("DELIVERY")
            val status = call.parameters["status"]

            val orders = transaction {
                var query = OrdersTable.selectAll().where {
                    (OrdersTable.deliveryUserId eq UUID.fromString(principal.userId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }

                val orderRows = query.orderBy(OrdersTable.createdAt, SortOrder.DESC).toList()
                val userIds = orderRows.flatMap { listOf(it[OrdersTable.cashierId], it[OrdersTable.deliveryUserId]) }
                    .filterNotNull().distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                orderRows.map { orderRow ->
                    val items =
                        OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                            .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items, usersMap)
                }
            }
            call.respond(HttpStatusCode.OK, orders)
        }

        get {
            val principal = currentUser()
            println("principal== ${principal.role}")
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            val vendorUUID = UUID.fromString(principal.vendorId)

            val (orders, total) = transaction {
                var query = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }
                status?.let { query = query.andWhere { OrdersTable.status eq it } }
                channel?.let { query = query.andWhere { OrdersTable.channel eq it } }
                cashierId?.let { query = query.andWhere { OrdersTable.cashierId eq it } }
                deliveryUserId?.let { query = query.andWhere { OrdersTable.deliveryUserId eq it } }
                from?.let { ts ->
                    query = query.andWhere {
                        OrdersTable.createdAt greaterEq kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    }
                }
                to?.let { ts ->
                    query =
                        query.andWhere { OrdersTable.createdAt lessEq kotlinx.datetime.Instant.fromEpochMilliseconds(ts) }
                }

                val totalCount = query.count().toInt()
                val orderRows =
                    query.orderBy(OrdersTable.createdAt, SortOrder.DESC).limit(limit).offset(offset.toLong()).toList()
                val cashierIds = orderRows.map { it[OrdersTable.cashierId] }.distinct()
                val deliveryIds = orderRows.mapNotNull { it[OrdersTable.deliveryUserId] }.distinct()
                val userIds = (cashierIds + deliveryIds).distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tableIds = orderRows.mapNotNull { it[OrdersTable.tableId] }.distinct()
                val tablesMap = if (tableIds.isEmpty()) emptyMap() else {
                    TablesTable.selectAll().where { TablesTable.id inList tableIds }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                }
                val results = orderRows.map { orderRow ->
                    val items =
                        OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                            .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items, usersMap, tablesMap)
                }
                results to totalCount
            }
            call.respond(HttpStatusCode.OK, PaginatedOrders(orders, total, offset + limit < total))
        }

        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val order = transaction {
                val orderRow = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }
                val userIds =
                    listOf(orderRow[OrdersTable.cashierId], orderRow[OrdersTable.deliveryUserId]).filterNotNull()
                        .distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tablesMap = orderRow[OrdersTable.tableId]?.let { tid ->
                    TablesTable.selectAll().where { TablesTable.id eq tid }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                } ?: emptyMap()
                orderRow.toOrderDto(items, usersMap, tablesMap)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        // Generate a temporary shareable receipt URL (30 minutes)
        post("/{id}/share") {
            val principal = currentUser()
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(orderId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")
            }

            val expiresMs = 30 * 60 * 1000 // 30 minutes
            val token = generateReceiptToken(jwtConfig, orderId, principal.vendorId, expiresMs)
            val origin = call.request.origin
            val portPart = when (origin.serverPort) {
                80, 443 -> ""
                else -> ":${origin.serverPort}"
            }
            val url = "${origin.scheme}://${origin.serverHost}$portPart/public/receipts/$orderId?token=$token"
            call.respond(
                ShareReceiptResponseDto(
                    url = url, token = token, expires_at = System.currentTimeMillis() + expiresMs
                )
            )
        }

        post {
            val principal = requireRole("CASHIER", "MANAGER")
            val request = call.receive<CreateOrderDto>()
            require(request.items.isNotEmpty()) { "Order must have at least one item" }

            // Validate channel value
            val validChannels = listOf("DINE_IN", "DELIVERY", "TAKEAWAY", "IN_STORE", "PICKUP_LATER")
            require(request.channel in validChannels) { "Invalid channel. Must be one of: ${validChannels.joinToString()}" }

            // Validate table for DINE_IN orders
            if (request.channel == "DINE_IN" && request.table_id != null) {
                transaction {
                    val table = TablesTable.selectAll().where {
                        (TablesTable.id eq UUID.fromString(request.table_id)) and
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Table not found")
                }
            }

            val order = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                // Load vendor settings for tax and stock mode
                val vendor = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorUUID }
                    .first()
                val vendorStockMode = vendor[VendorsTable.stockMode]

                // Calculate totals from item snapshots
                var subtotal = BigDecimal.ZERO
                val itemSnapshots = request.items.map { orderItem ->
                    val item = ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(orderItem.item_id) }
                        .firstOrNull() ?: throw NoSuchElementException("Item ${orderItem.item_id} not found")

                    // Stock enforcement check — ENFORCE blocks order, WARN allows but logs
                    if (vendorStockMode == "ENFORCE" || vendorStockMode == "WARN") {
                        val behavior = item[ItemsTable.stockBehavior]
                        when (behavior) {
                            "DIRECT" -> {
                                val stockRow = StockTable.selectAll().where {
                                    (StockTable.itemId eq UUID.fromString(orderItem.item_id)) and
                                    (StockTable.vendorId eq vendorUUID)
                                }.firstOrNull()
                                if (stockRow != null && stockRow[StockTable.quantity] < BigDecimal(orderItem.quantity)) {
                                    if (vendorStockMode == "ENFORCE") {
                                        throw IllegalStateException(
                                            "Insufficient stock for '${item[ItemsTable.name]}': available=${stockRow[StockTable.quantity].toPlainString()}, requested=${orderItem.quantity}"
                                        )
                                    }
                                    // WARN: allow order to proceed — stock will go negative during deduction
                                }
                            }
                            "RECIPE" -> {
                                val recipe = RecipesTable.selectAll().where {
                                    (RecipesTable.itemId eq UUID.fromString(orderItem.item_id)) and
                                    (RecipesTable.vendorId eq vendorUUID) and
                                    (RecipesTable.status eq "ACTIVE")
                                }.firstOrNull()
                                if (recipe == null && vendorStockMode == "ENFORCE") {
                                    throw IllegalStateException(
                                        "Item '${item[ItemsTable.name]}' is set to RECIPE mode but has no active recipe"
                                    )
                                }
                                if (recipe != null) {
                                    val recipeId = recipe[RecipesTable.id]
                                    val ingredients = RecipeIngredientsTable.selectAll().where {
                                        RecipeIngredientsTable.recipeId eq recipeId
                                    }.toList()
                                    val yieldQty = recipe[RecipesTable.yieldQuantity]
                                    val multiplier = BigDecimal(orderItem.quantity).divide(yieldQty, 6, java.math.RoundingMode.HALF_UP)

                                    for (ingredient in ingredients) {
                                        val stockRow = StockTable.selectAll().where {
                                            StockTable.id eq ingredient[RecipeIngredientsTable.stockId]
                                        }.firstOrNull() ?: continue
                                        val requiredQty = ingredient[RecipeIngredientsTable.quantity].toDouble() * multiplier.toDouble()
                                        val convertedRequired = convertUnits(requiredQty, ingredient[RecipeIngredientsTable.unit], stockRow[StockTable.unit])
                                        if (stockRow[StockTable.quantity] < BigDecimal.valueOf(convertedRequired)) {
                                            if (vendorStockMode == "ENFORCE") {
                                                throw IllegalStateException(
                                                    "Insufficient ingredient '${stockRow[StockTable.itemName]}' for '${item[ItemsTable.name]}': " +
                                                    "available=${stockRow[StockTable.quantity].toPlainString()} ${stockRow[StockTable.unit]}, " +
                                                    "required=${BigDecimal.valueOf(convertedRequired).toPlainString()} ${stockRow[StockTable.unit]}"
                                                )
                                            }
                                            // WARN: allow order to proceed — stock will go negative during deduction
                                        }
                                    }
                                }
                            }
                            // "NONE" -> skip stock check
                        }
                    }

                    val price = item[ItemsTable.price]
                    subtotal += price * BigDecimal(orderItem.quantity)
                    Triple(item, orderItem, price)
                }

                val deliveryFeeAmount = if (request.channel == "DELIVERY") {
                    BigDecimal.valueOf(request.delivery_fee)
                } else BigDecimal.ZERO

                // Discount calculation (Phase 3)
                val discountType = request.discount_type.uppercase()
                val discountAmount = when (discountType) {
                    "PERCENT" -> (subtotal * BigDecimal.valueOf(request.discount) / BigDecimal(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                    else -> BigDecimal.valueOf(request.discount).coerceAtMost(subtotal)
                }

                val afterDiscount = (subtotal - discountAmount).coerceAtLeast(BigDecimal.ZERO)

                // Tax calculation - percentage-based on all channels (Phase 3)
                var taxAmount = BigDecimal.ZERO
                var taxPercentValue = BigDecimal.ZERO
                val taxPlaceIdUuid = request.tax_place_id?.let { UUID.fromString(it) }
                if (taxPlaceIdUuid != null) {
                    val taxPlace = TaxPlacesTable.selectAll().where {
                        (TaxPlacesTable.id eq taxPlaceIdUuid) and (TaxPlacesTable.vendorId eq vendorUUID)
                    }.firstOrNull()
                    taxPlace?.let {
                        taxPercentValue = it[TaxPlacesTable.taxPercent]
                        taxAmount = (afterDiscount * taxPercentValue / BigDecimal(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP)
                    }
                } else if (vendor[VendorsTable.taxEnabled]) {
                    // Use vendor default tax percent if no tax place specified
                    taxPercentValue = vendor[VendorsTable.defaultTaxPercent]
                    if (taxPercentValue > BigDecimal.ZERO) {
                        taxAmount = (afterDiscount * taxPercentValue / BigDecimal(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP)
                    }
                }
                val total = afterDiscount + deliveryFeeAmount + taxAmount

                val orderId = OrdersTable.insertAndGetId { stmt ->
                    stmt[OrdersTable.vendorId] = vendorUUID
                    stmt[channel] = request.channel
                    stmt[status] = "CREATED"
                    request.table_id?.let { tid -> stmt[tableId] = UUID.fromString(tid) }
                    stmt[cashierId] = UUID.fromString(principal.userId)
                    stmt[OrdersTable.deliveryUserId] = null
                    request.customer_id?.let { cid -> stmt[OrdersTable.customerId] = UUID.fromString(cid) }
                    taxPlaceIdUuid?.let { uuid -> stmt[OrdersTable.taxPlaceId] = uuid }
                    stmt[clientName] = request.client_name
                    stmt[clientPhone] = request.client_phone
                    stmt[clientAddress] = request.client_address
                    stmt[geoLat] = request.geo_lat
                    stmt[geoLng] = request.geo_lng
                    stmt[paymentMethod] = request.payment_method
                    stmt[paymentStatus] = "PENDING"
                    stmt[paymentTiming] = request.payment_timing
                    stmt[OrdersTable.subtotal] = subtotal
                    stmt[deliveryFee] = deliveryFeeAmount
                    stmt[OrdersTable.discount] = discountAmount
                    stmt[OrdersTable.discountType] = discountType
                    stmt[tax] = taxAmount
                    stmt[OrdersTable.taxPercent] = taxPercentValue
                    stmt[OrdersTable.total] = total
                    stmt[notes] = request.notes
                    stmt[createdAt] = Clock.System.now()
                    stmt[updatedAt] = Clock.System.now()
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

                // Deduct stock for each item in the order — branches on stockBehavior
                // In WARN mode, allow negative stock (no coerceAtLeast). In ENFORCE/NONE, clamp to zero.
                val allowNegativeStock = vendorStockMode == "WARN"
                itemSnapshots.forEach { (item, orderItem, _) ->
                    val itemUUID = UUID.fromString(orderItem.item_id)
                    val behavior = item[ItemsTable.stockBehavior]
                    val now = Clock.System.now()

                    when (behavior) {
                        "DIRECT" -> {
                            val stockRow = StockTable.selectAll().where {
                                (StockTable.itemId eq itemUUID) and (StockTable.vendorId eq vendorUUID)
                            }.firstOrNull()
                            if (stockRow != null) {
                                val oldQty = stockRow[StockTable.quantity]
                                val deductAmt = BigDecimal(orderItem.quantity)
                                val newQty = if (allowNegativeStock) oldQty - deductAmt
                                    else (oldQty - deductAmt).coerceAtLeast(BigDecimal.ZERO)
                                StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                    it[quantity] = newQty
                                    it[updatedAt] = now
                                }
                                StockTransactionsTable.insert {
                                    it[stockId] = stockRow[StockTable.id]
                                    it[type] = "SALE_DIRECT"
                                    it[StockTransactionsTable.quantity] = deductAmt
                                    it[previousQuantity] = oldQty
                                    it[StockTransactionsTable.orderId] = orderId
                                    it[note] = "Order deduction (direct)"
                                    it[createdAt] = now
                                }
                            }
                        }
                        "RECIPE" -> {
                            val recipe = RecipesTable.selectAll().where {
                                (RecipesTable.itemId eq itemUUID) and
                                (RecipesTable.vendorId eq vendorUUID) and
                                (RecipesTable.status eq "ACTIVE")
                            }.firstOrNull() ?: return@forEach
                            val recipeId = recipe[RecipesTable.id]
                            val ingredients = RecipeIngredientsTable.selectAll().where {
                                RecipeIngredientsTable.recipeId eq recipeId
                            }.toList()
                            val yieldQty = recipe[RecipesTable.yieldQuantity]
                            val multiplier = BigDecimal(orderItem.quantity).divide(yieldQty, 6, java.math.RoundingMode.HALF_UP)

                            for (ingredient in ingredients) {
                                val stockRow = StockTable.selectAll().where {
                                    StockTable.id eq ingredient[RecipeIngredientsTable.stockId]
                                }.firstOrNull() ?: continue
                                val isFixed = ingredient[RecipeIngredientsTable.fixedQuantity]
                                val effectiveMultiplier = if (isFixed) BigDecimal.ONE else multiplier
                                val requiredQty = ingredient[RecipeIngredientsTable.quantity].toDouble() * effectiveMultiplier.toDouble()
                                val convertedRequired = convertUnits(requiredQty, ingredient[RecipeIngredientsTable.unit], stockRow[StockTable.unit])
                                val convertedRequiredDecimal = BigDecimal.valueOf(convertedRequired)
                                val oldQty = stockRow[StockTable.quantity]
                                val newQty = if (allowNegativeStock) oldQty - convertedRequiredDecimal
                                    else (oldQty - convertedRequiredDecimal).coerceAtLeast(BigDecimal.ZERO)
                                StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                    it[quantity] = newQty
                                    it[updatedAt] = now
                                }
                                StockTransactionsTable.insert {
                                    it[stockId] = stockRow[StockTable.id]
                                    it[type] = "SALE_RECIPE"
                                    it[StockTransactionsTable.quantity] = convertedRequiredDecimal
                                    it[previousQuantity] = oldQty
                                    it[StockTransactionsTable.orderId] = orderId
                                    it[StockTransactionsTable.recipeId] = recipeId
                                    it[note] = "Recipe deduction: ${item[ItemsTable.name]} x${orderItem.quantity}"
                                    it[createdAt] = now
                                }
                            }
                        }
                        // "NONE" -> skip stock deduction
                    }
                }

                // Auto-set table to OCCUPIED for dine-in orders
                if (request.channel == "DINE_IN" && request.table_id != null) {
                    TablesTable.update({
                        TablesTable.id eq UUID.fromString(request.table_id)
                    }) {
                        it[TablesTable.status] = "OCCUPIED"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[ActivityLogsTable.orderId] = orderId
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "ORDER_CREATED"
                    it[payload] = """{"channel":"${request.channel}"}"""
                    it[createdAt] = Clock.System.now()
                }

                // Update customer stats (order_count, total_spent, last_order_at)
                request.customer_id?.let { cid ->
                    val customerUUID = UUID.fromString(cid)
                    CustomersTable.update({ CustomersTable.id eq customerUUID }) { stmt ->
                        with(SqlExpressionBuilder) {
                            stmt[orderCount] = orderCount + 1
                            stmt[totalSpent] = totalSpent + total
                        }
                        stmt[lastOrderAt] = Clock.System.now()
                        stmt[updatedAt] = Clock.System.now()
                    }

                    // Auto-save delivery address if not already stored
                    val addr = request.client_address
                    if (!addr.isNullOrBlank()) {
                        val alreadyExists = CustomerAddressesTable.selectAll()
                            .where {
                                (CustomerAddressesTable.customerId eq customerUUID) and
                                (CustomerAddressesTable.address eq addr)
                            }.count() > 0

                        if (!alreadyExists) {
                            val isFirst = CustomerAddressesTable.selectAll()
                                .where { CustomerAddressesTable.customerId eq customerUUID }
                                .count() == 0L
                            CustomerAddressesTable.insertAndGetId {
                                it[CustomerAddressesTable.customerId] = customerUUID
                                it[address] = addr
                                it[label] = if (isFirst) "Default" else null
                                it[geoLat] = request.geo_lat
                                it[geoLng] = request.geo_lng
                                it[isDefault] = isFirst
                                it[createdAt] = Clock.System.now()
                            }
                        }
                    }
                }

                val orderRow = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.first()
                val userIds =
                    listOf(orderRow[OrdersTable.cashierId], orderRow[OrdersTable.deliveryUserId]).filterNotNull()
                        .distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tablesMap = orderRow[OrdersTable.tableId]?.let { tid ->
                    TablesTable.selectAll().where { TablesTable.id eq tid }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                } ?: emptyMap()
                orderRow.toOrderDto(orderItems, usersMap, tablesMap)
            }
            call.respond(HttpStatusCode.Created, order)
        }

        put("/{id}") {
            val principal = requireRole("CASHIER", "MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateOrderDto>()

            val order = transaction {
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and
                    (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = current[OrdersTable.status]
                if (currentStatus in listOf("COMPLETED", "CANCELED")) {
                    throw IllegalStateException("Cannot edit a $currentStatus order")
                }

                // Update order fields
                OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) { stmt ->
                    request.client_name?.let { stmt[clientName] = it }
                    request.client_phone?.let { stmt[clientPhone] = it }
                    request.client_address?.let { stmt[clientAddress] = it }
                    request.notes?.let { stmt[notes] = it }
                    request.payment_method?.let { stmt[paymentMethod] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                // If items are provided, replace them and recalculate totals
                if (request.items != null && request.items.isNotEmpty()) {
                    // Restore stock from old items — branches on stockBehavior
                    val vendorUUID = UUID.fromString(principal.vendorId)
                    val oldItems = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq UUID.fromString(id) }.toList()
                    restoreStockForOrderItems(vendorUUID, UUID.fromString(id), oldItems)

                    // Delete old items
                    OrderItemsTable.deleteWhere { OrderItemsTable.orderId eq UUID.fromString(id) }

                    // Insert new items and calculate totals
                    var subtotal = BigDecimal.ZERO
                    val newOrderItems = request.items.map { orderItem ->
                        val item = ItemsTable.selectAll()
                            .where { ItemsTable.id eq UUID.fromString(orderItem.item_id) }
                            .firstOrNull() ?: throw NoSuchElementException("Item ${orderItem.item_id} not found")
                        val price = item[ItemsTable.price]
                        subtotal += price * BigDecimal(orderItem.quantity)

                        val oiId = OrderItemsTable.insertAndGetId {
                            it[OrderItemsTable.orderId] = UUID.fromString(id)
                            it[itemId] = UUID.fromString(orderItem.item_id)
                            it[itemNameSnapshot] = item[ItemsTable.name]
                            it[itemPriceSnapshot] = price
                            it[OrderItemsTable.quantity] = orderItem.quantity
                            it[note] = orderItem.note
                            it[createdAt] = Clock.System.now()
                        }

                        // Deduct stock for new item — branches on stockBehavior
                        val editBehavior = item[ItemsTable.stockBehavior]
                        val editNow = Clock.System.now()
                        when (editBehavior) {
                            "DIRECT" -> {
                                val stockRow = StockTable.selectAll().where {
                                    (StockTable.itemId eq UUID.fromString(orderItem.item_id)) and (StockTable.vendorId eq vendorUUID)
                                }.firstOrNull()
                                if (stockRow != null) {
                                    val sOldQty = stockRow[StockTable.quantity]
                                    val sNewQty = (sOldQty - BigDecimal(orderItem.quantity)).coerceAtLeast(BigDecimal.ZERO)
                                    StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                        it[quantity] = sNewQty
                                        it[updatedAt] = editNow
                                    }
                                    StockTransactionsTable.insert {
                                        it[stockId] = stockRow[StockTable.id]
                                        it[type] = "SALE_DIRECT"
                                        it[StockTransactionsTable.quantity] = BigDecimal(orderItem.quantity)
                                        it[previousQuantity] = sOldQty
                                        it[StockTransactionsTable.orderId] = UUID.fromString(id)
                                        it[note] = "Order edit deduction (direct)"
                                        it[createdAt] = editNow
                                    }
                                }
                            }
                            "RECIPE" -> {
                                val recipe = RecipesTable.selectAll().where {
                                    (RecipesTable.itemId eq UUID.fromString(orderItem.item_id)) and
                                    (RecipesTable.vendorId eq vendorUUID) and (RecipesTable.status eq "ACTIVE")
                                }.firstOrNull()
                                if (recipe != null) {
                                    val recipeId = recipe[RecipesTable.id]
                                    val ingredients = RecipeIngredientsTable.selectAll().where {
                                        RecipeIngredientsTable.recipeId eq recipeId
                                    }.toList()
                                    val yieldQty = recipe[RecipesTable.yieldQuantity]
                                    val multiplier = BigDecimal(orderItem.quantity).divide(yieldQty, 6, java.math.RoundingMode.HALF_UP)
                                    for (ingredient in ingredients) {
                                        val stockRow = StockTable.selectAll().where {
                                            StockTable.id eq ingredient[RecipeIngredientsTable.stockId]
                                        }.firstOrNull() ?: continue
                                        val isFixed = ingredient[RecipeIngredientsTable.fixedQuantity]
                                        val effectiveMultiplier = if (isFixed) BigDecimal.ONE else multiplier
                                        val requiredQty = ingredient[RecipeIngredientsTable.quantity].toDouble() * effectiveMultiplier.toDouble()
                                        val convertedRequired = convertUnits(requiredQty, ingredient[RecipeIngredientsTable.unit], stockRow[StockTable.unit])
                                        val sOldQty = stockRow[StockTable.quantity]
                                        val sNewQty = (sOldQty - BigDecimal.valueOf(convertedRequired)).coerceAtLeast(BigDecimal.ZERO)
                                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                            it[quantity] = sNewQty
                                            it[updatedAt] = editNow
                                        }
                                        StockTransactionsTable.insert {
                                            it[stockId] = stockRow[StockTable.id]
                                            it[type] = "SALE_RECIPE"
                                            it[StockTransactionsTable.quantity] = BigDecimal.valueOf(convertedRequired)
                                            it[previousQuantity] = sOldQty
                                            it[StockTransactionsTable.orderId] = UUID.fromString(id)
                                            it[StockTransactionsTable.recipeId] = recipeId
                                            it[note] = "Order edit deduction (recipe): ${item[ItemsTable.name]} x${orderItem.quantity}"
                                            it[createdAt] = editNow
                                        }
                                    }
                                }
                            }
                            // "NONE" -> skip
                        }

                        OrderItemDto(
                            id = oiId.toString(), order_id = id,
                            item_id = orderItem.item_id,
                            item_name_snapshot = item[ItemsTable.name],
                            item_price_snapshot = price.toDouble(),
                            quantity = orderItem.quantity, note = orderItem.note
                        )
                    }

                    // Recalculate totals
                    val channel = current[OrdersTable.channel]
                    val deliveryFeeAmount = if (channel == "DELIVERY") {
                        request.delivery_fee?.let { BigDecimal.valueOf(it) } ?: current[OrdersTable.deliveryFee]
                    } else BigDecimal.ZERO

                    var taxAmount = BigDecimal.ZERO
                    val taxPlaceIdUuid: UUID? = request.tax_place_id?.let { UUID.fromString(it) }
                        ?: current[OrdersTable.taxPlaceId]?.value
                    if (channel == "DELIVERY" && taxPlaceIdUuid != null) {
                        val taxPlace = TaxPlacesTable.selectAll().where {
                            (TaxPlacesTable.id eq taxPlaceIdUuid) and
                            (TaxPlacesTable.vendorId eq vendorUUID)
                        }.firstOrNull()
                        taxPlace?.let {
                            taxAmount = it[TaxPlacesTable.taxPercent].setScale(2, java.math.RoundingMode.HALF_UP)
                        }
                    }
                    val total = subtotal + deliveryFeeAmount + taxAmount

                    OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) { stmt ->
                        stmt[OrdersTable.subtotal] = subtotal
                        stmt[deliveryFee] = deliveryFeeAmount
                        stmt[tax] = taxAmount
                        stmt[OrdersTable.total] = total
                        request.delivery_fee?.let { stmt[deliveryFee] = BigDecimal.valueOf(it) }
                        request.tax_place_id?.let { stmt[OrdersTable.taxPlaceId] = UUID.fromString(it) }
                    }
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "ORDER_UPDATED"
                    it[payload] = """{"updated_fields":"items,details"}"""
                    it[createdAt] = Clock.System.now()
                }

                val updatedRow = OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }.first()
                val items = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }
                val userIds = listOf(updatedRow[OrdersTable.cashierId], updatedRow[OrdersTable.deliveryUserId])
                    .filterNotNull().distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tablesMap = updatedRow[OrdersTable.tableId]?.let { tid ->
                    TablesTable.selectAll().where { TablesTable.id eq tid }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                } ?: emptyMap()
                updatedRow.toOrderDto(items, usersMap, tablesMap)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        patch("/{id}/status") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateStatusDto>()

            val order = transaction {
                val targetStatus = request.status
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val valid = orderService.validateStatusTransition(
                    current[OrdersTable.status], targetStatus, current[OrdersTable.channel], principal.role
                )
                if (!valid) throw IllegalStateException("Invalid status transition")

                // Block completion without payment
                if (targetStatus == "COMPLETED") {
                    val paymentStatus = current[OrdersTable.paymentStatus]
                    if (paymentStatus != "PAID") {
                        throw IllegalStateException("Order must be fully paid before completing. Current payment status: $paymentStatus")
                    }
                }

                OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) {
                    it[status] = targetStatus
                    it[updatedAt] = Clock.System.now()
                }

                // Auto-free table when dine-in order is completed
                if (targetStatus == "COMPLETED" && current[OrdersTable.channel] == "DINE_IN") {
                    val tableUUID = current[OrdersTable.tableId]
                    if (tableUUID != null) {
                        TablesTable.update({ TablesTable.id eq tableUUID }) {
                            it[TablesTable.status] = "AVAILABLE"
                            it[updatedAt] = Clock.System.now()
                        }
                    }
                }

                // Restore stock when order is cancelled
                if (targetStatus == "CANCELLED") {
                    val vendorUUID = UUID.fromString(principal.vendorId)
                    val orderItems = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq UUID.fromString(id) }.toList()
                    restoreStockForOrderItems(vendorUUID, UUID.fromString(id), orderItems)

                    // Also free table if dine-in
                    if (current[OrdersTable.channel] == "DINE_IN") {
                        val tableUUID = current[OrdersTable.tableId]
                        if (tableUUID != null) {
                            TablesTable.update({ TablesTable.id eq tableUUID }) {
                                it[TablesTable.status] = "AVAILABLE"
                                it[TablesTable.updatedAt] = Clock.System.now()
                            }
                        }
                    }
                }

                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "STATUS_CHANGED"
                    it[payload] = """{"from":"${current[OrdersTable.status]}","to":"$targetStatus"}"""
                    it[createdAt] = Clock.System.now()
                }

                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }
                val updatedRow = OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }.first()
                val userIds =
                    listOf(updatedRow[OrdersTable.cashierId], updatedRow[OrdersTable.deliveryUserId]).filterNotNull()
                        .distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tablesMap = updatedRow[OrdersTable.tableId]?.let { tid ->
                    TablesTable.selectAll().where { TablesTable.id eq tid }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                } ?: emptyMap()
                updatedRow.toOrderDto(items, usersMap, tablesMap)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        // Update payment status independently from order status
        patch("/{id}/payment-status") {
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdatePaymentStatusDto>()

            val validStatuses = listOf("PENDING", "PAID", "PARTIALLY_PAID", "REFUNDED", "FAILED")
            require(request.payment_status in validStatuses) {
                "Invalid payment status. Must be one of: ${validStatuses.joinToString()}"
            }
            request.payment_method?.let { method ->
                val validMethods = listOf("CASH", "WALLET", "CARD")
                require(method in validMethods) {
                    "Invalid payment method. Must be one of: ${validMethods.joinToString()}"
                }
            }

            val order = transaction {
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and
                    (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) {
                    it[paymentStatus] = request.payment_status
                    request.payment_method?.let { method -> it[paymentMethod] = method }
                    it[updatedAt] = Clock.System.now()
                    if (request.payment_status == "PAID") {
                        it[paymentConfirmedAt] = Clock.System.now()
                        it[paymentConfirmedBy] = UUID.fromString(principal.userId)
                    }
                }

                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "PAYMENT_STATUS_CHANGED"
                    it[payload] = """{"from":"${current[OrdersTable.paymentStatus]}","to":"${request.payment_status}"}"""
                    it[createdAt] = Clock.System.now()
                }

                // Re-fetch and return full order DTO
                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }
                val updatedRow = OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }.first()
                val userIds = listOf(updatedRow[OrdersTable.cashierId], updatedRow[OrdersTable.deliveryUserId])
                    .filterNotNull().distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val tablesMap = updatedRow[OrdersTable.tableId]?.let { tid ->
                    TablesTable.selectAll().where { TablesTable.id eq tid }
                        .associate { it[TablesTable.id].value to it[TablesTable.number] }
                } ?: emptyMap()
                updatedRow.toOrderDto(items, usersMap, tablesMap)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        // Generate a temporary shareable receipt URL (30 minutes)
        post("/{id}/share") {
            val principal = currentUser()
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(orderId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")
            }

            val expiresMs = 30 * 60 * 1000 // 30 minutes
            val token = generateReceiptToken(jwtConfig, orderId, principal.vendorId, expiresMs)
            val origin = call.request.origin
            val portPart = when (origin.serverPort) {
                80, 443 -> ""
                else -> ":${origin.serverPort}"
            }
            val url = "${origin.scheme}://${origin.serverHost}$portPart/public/receipts/$orderId?token=$token"
            call.respond(
                ShareReceiptResponseDto(
                    url = url, token = token, expires_at = System.currentTimeMillis() + expiresMs
                )
            )
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
                val currentOrder = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = currentOrder[OrdersTable.status]
                if (currentStatus != "READY" && currentStatus != "ASSIGNED") {
                    throw IllegalStateException("Order must be READY to assign delivery")
                }

                OrdersTable.update({
                    (OrdersTable.id eq UUID.fromString(id)) and (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
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

                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq UUID.fromString(id) }
                    .map { it.toOrderItemDto() }
                val updatedRow = OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(id) }.first()
                val userIds =
                    listOf(updatedRow[OrdersTable.cashierId], updatedRow[OrdersTable.deliveryUserId]).filterNotNull()
                        .distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                updatedRow.toOrderDto(items, usersMap)
            }
            call.respond(HttpStatusCode.OK, order)
        }

    }
}

/**
 * Restore stock quantities for all items in an order.
 * Handles both DIRECT and RECIPE stock behaviors.
 * Must be called inside an Exposed transaction{} block.
 */
private fun restoreStockForOrderItems(
    vendorUUID: UUID,
    orderUUID: UUID,
    orderItems: List<ResultRow>,
) {
    val now = Clock.System.now()
    orderItems.forEach { orderItem ->
        val itemUUID = orderItem[OrderItemsTable.itemId]
        val itemRow = ItemsTable.selectAll().where { ItemsTable.id eq itemUUID }.firstOrNull()
        val behavior = itemRow?.get(ItemsTable.stockBehavior) ?: "NONE"

        when (behavior) {
            "DIRECT" -> {
                val stockRow = StockTable.selectAll().where {
                    (StockTable.itemId eq itemUUID) and (StockTable.vendorId eq vendorUUID)
                }.firstOrNull()
                if (stockRow != null) {
                    val currentQty = stockRow[StockTable.quantity]
                    val restoredQty = currentQty + BigDecimal(orderItem[OrderItemsTable.quantity])
                    StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                        it[StockTable.quantity] = restoredQty
                        it[StockTable.updatedAt] = now
                    }
                    StockTransactionsTable.insert {
                        it[stockId] = stockRow[StockTable.id]
                        it[type] = "RETURN"
                        it[StockTransactionsTable.quantity] = BigDecimal(orderItem[OrderItemsTable.quantity])
                        it[previousQuantity] = currentQty
                        it[StockTransactionsTable.orderId] = orderUUID
                        it[note] = "Stock restored (order cancel/edit)"
                        it[createdAt] = now
                    }
                }
            }
            "RECIPE" -> {
                val recipe = RecipesTable.selectAll().where {
                    (RecipesTable.itemId eq itemUUID) and
                    (RecipesTable.vendorId eq vendorUUID) and
                    (RecipesTable.status eq "ACTIVE")
                }.firstOrNull()
                if (recipe != null) {
                    val recipeId = recipe[RecipesTable.id]
                    val ingredients = RecipeIngredientsTable.selectAll().where {
                        RecipeIngredientsTable.recipeId eq recipeId
                    }.toList()
                    val yieldQty = recipe[RecipesTable.yieldQuantity]
                    val multiplier = BigDecimal(orderItem[OrderItemsTable.quantity])
                        .divide(yieldQty, 6, java.math.RoundingMode.HALF_UP)

                    for (ingredient in ingredients) {
                        val stockRow = StockTable.selectAll().where {
                            StockTable.id eq ingredient[RecipeIngredientsTable.stockId]
                        }.firstOrNull() ?: continue
                        val isFixed = ingredient[RecipeIngredientsTable.fixedQuantity]
                        val effectiveMultiplier = if (isFixed) BigDecimal.ONE else multiplier
                        val restoreQty = ingredient[RecipeIngredientsTable.quantity].toDouble() * effectiveMultiplier.toDouble()
                        val convertedRestore = convertUnits(
                            restoreQty,
                            ingredient[RecipeIngredientsTable.unit],
                            stockRow[StockTable.unit]
                        )
                        val currentQty = stockRow[StockTable.quantity]
                        val newQty = currentQty + BigDecimal.valueOf(convertedRestore)
                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                            it[StockTable.quantity] = newQty
                            it[StockTable.updatedAt] = now
                        }
                        StockTransactionsTable.insert {
                            it[stockId] = stockRow[StockTable.id]
                            it[type] = "RETURN"
                            it[StockTransactionsTable.quantity] = BigDecimal.valueOf(convertedRestore)
                            it[previousQuantity] = currentQty
                            it[StockTransactionsTable.orderId] = orderUUID
                            it[StockTransactionsTable.recipeId] = recipeId
                            it[note] = "Stock restored (order cancel/edit)"
                            it[createdAt] = now
                        }
                    }
                }
            }
            // "NONE" -> skip
        }
    }
}


private fun ResultRow.toOrderDto(
    items: List<OrderItemDto> = emptyList(),
    usersMap: Map<UUID, String> = emptyMap(),
    tablesMap: Map<UUID, String> = emptyMap(),
) = OrderDto(
    id = this[OrdersTable.id].toString(),
    vendor_id = this[OrdersTable.vendorId].toString(),
    channel = this[OrdersTable.channel],
    status = this[OrdersTable.status],
    table_id = this[OrdersTable.tableId]?.toString(),
    table_number = this[OrdersTable.tableId]?.let { tablesMap[it.value] },
    cashier_id = this[OrdersTable.cashierId].toString(),
    cashier_name = usersMap[this[OrdersTable.cashierId].value],
    delivery_user_id = this[OrdersTable.deliveryUserId]?.toString(),
    delivery_user_name = this[OrdersTable.deliveryUserId]?.let { usersMap[it.value] },
    customer_id = this[OrdersTable.customerId]?.toString(),
    client_name = this[OrdersTable.clientName],
    client_phone = this[OrdersTable.clientPhone],
    client_address = this[OrdersTable.clientAddress],
    geo_lat = this[OrdersTable.geoLat],
    geo_lng = this[OrdersTable.geoLng],
    payment_method = this[OrdersTable.paymentMethod],
    payment_status = this[OrdersTable.paymentStatus],
    payment_timing = this[OrdersTable.paymentTiming],
    payment_confirmed_at = this[OrdersTable.paymentConfirmedAt]?.toEpochMilliseconds(),
    payment_confirmed_by = this[OrdersTable.paymentConfirmedBy]?.toString(),
    subtotal = this[OrdersTable.subtotal].toDouble(),
    delivery_fee = this[OrdersTable.deliveryFee].toDouble(),
    discount = this[OrdersTable.discount].toDouble(),
    discount_type = this[OrdersTable.discountType],
    tax = this[OrdersTable.tax].toDouble(),
    tax_percent = this[OrdersTable.taxPercent].toDouble(),
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

// Public, token-guarded receipt view (no auth header required)
fun Route.orderSharePublicRoutes() {
    val jwtConfig by lazy { JwtConfig(this.application.environment.config) }

    route("/public/receipts") {
        get("/{id}") {
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val token = call.request.queryParameters["token"] ?: throw IllegalArgumentException("token required")

            val verifier = JWT.require(Algorithm.HMAC256(jwtConfig.secret)).withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience).withClaim("type", "receipt").build()

            val decoded = try {
                verifier.verify(token)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid or expired token")
            }

            if (decoded.subject != orderId) throw IllegalArgumentException("Token/order mismatch")
            val vendorIdFromToken = decoded.getClaim("vendor_id").asString()

            val (order, vendorName, vendorAddress, vendorPhone) = transaction {
                val orderRow =
                    OrdersTable.selectAll().where { OrdersTable.id eq UUID.fromString(orderId) }.firstOrNull()
                        ?: throw NoSuchElementException("Order not found")
                if (orderRow[OrdersTable.vendorId].toString() != vendorIdFromToken) {
                    throw IllegalArgumentException("Token/vendor mismatch")
                }

                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                    .map { it.toOrderItemDto() }
                val userIds =
                    listOf(orderRow[OrdersTable.cashierId], orderRow[OrdersTable.deliveryUserId]).filterNotNull()
                        .distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val vendor =
                    VendorsTable.selectAll().where { VendorsTable.id eq orderRow[OrdersTable.vendorId] }.first()
                ReceiptContext(
                    order = orderRow.toOrderDto(items, usersMap),
                    vendorName = vendor[VendorsTable.name],
                    vendorAddress = vendor[VendorsTable.address],
                    vendorPhone = vendor[VendorsTable.contactPhone]
                )
            }

            val html = buildReceiptHtml(order, vendorName, vendorAddress, vendorPhone)
            call.respondText(html, ContentType.Text.Html)
        }
    }
}

private fun generateReceiptToken(jwtConfig: JwtConfig, orderId: String, vendorId: String, ttlMs: Int): String {
    return JWT.create().withSubject(orderId).withIssuer(jwtConfig.issuer).withAudience(jwtConfig.audience)
        .withClaim("vendor_id", vendorId).withClaim("type", "receipt").withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + ttlMs)).sign(Algorithm.HMAC256(jwtConfig.secret))
}

private data class ReceiptContext(
    val order: OrderDto,
    val vendorName: String?,
    val vendorAddress: String?,
    val vendorPhone: String?,
)

private fun buildReceiptHtml(
    order: OrderDto, vendorName: String?, vendorAddress: String?, vendorPhone: String?
): String {
    val itemsHtml = order.items.joinToString("") { item ->
        """
        <tr>
          <td class="item-desc">
            <span class="qty">${item.quantity}x</span> ${item.item_name_snapshot}
          </td>
          <td class="item-price">${"%.2f".format(item.item_price_snapshot * item.quantity)}</td>
        </tr>
        """.trimIndent()
    }

    return """
    <!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { 
            background-color: #f4f4f7; 
            font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; 
            margin: 0; 
            padding: 20px; 
            display: flex; 
            justify-content: center;
          }
          .receipt-card { 
            background: #ffffff; 
            max-width: 400px; 
            width: 100%; 
            padding: 30px; 
            box-shadow: 0 4px 10px rgba(0,0,0,0.1); 
            border-radius: 8px;
          }
          header { text-align: center; margin-bottom: 20px; }
          .vendor-name { font-size: 22px; font-weight: bold; margin: 0; color: #1a1a1a; }
          .vendor-info { font-size: 13px; color: #717171; margin-top: 4px; }
          
          .divider { 
            border-top: 2px dashed #eeeeee; 
            margin: 20px 0; 
          }
          
          .order-meta { font-size: 13px; color: #444; margin-bottom: 15px; }
          .meta-row { display: flex; justify-content: space-between; margin-bottom: 4px; }
          .label { color: #888; }
          
          table { width: 100%; border-collapse: collapse; margin: 20px 0; }
          .item-desc { font-size: 15px; padding: 8px 0; color: #2d2d2d; }
          .qty { color: #888; font-weight: bold; margin-right: 5px; }
          .item-price { text-align: right; font-family: monospace; font-size: 15px; color: #1a1a1a; }
          
          .totals-section { border-top: 1px solid #f0f0f0; padding-top: 15px; }
          .total-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; }
          .grand-total { 
            font-size: 18px; 
            font-weight: bold; 
            color: #2e7d32; 
            margin-top: 10px; 
            padding-top: 10px; 
            border-top: 2px solid #f0f0f0;
          }
          
          footer { text-align: center; margin-top: 30px; font-size: 12px; color: #aaaaaa; }
        </style>
      </head>
      <body>
        <div class="receipt-card">
          <header>
            <div class="vendor-name">${vendorName ?: "Our Store"}</div>
            <div class="vendor-info">${vendorAddress ?: ""}</div>
            <div class="vendor-info">${vendorPhone ?: ""}</div>
          </header>

          <div class="order-meta">
            <div class="meta-row"><span class="label">Order ID:</span> <span>#${order.id.takeLast(8).uppercase()}</span></div>
            <div class="meta-row"><span class="label">Date:</span> <span>${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(java.util.Date(order.created_at))}</span></div>
            <div class="meta-row"><span class="label">Payment:</span> <span>${order.payment_method}</span></div>
          </div>

          <div class="divider"></div>

          <table>
            $itemsHtml
          </table>

          <div class="totals-section">
            <div class="total-row"><span class="label">Subtotal</span> <span>${"%.2f".format(order.subtotal)} EGP</span></div>
            <div class="total-row"><span class="label">Delivery Fee</span> <span>${"%.2f".format(order.delivery_fee + order.tax)} EGP</span></div>
            <div class="total-row grand-total"><span>Total</span> <span>${"%.2f".format(order.total)} EGP</span></div>
          </div>

          <footer>
            <p>Thank you for choosing us!</p>
            <p>Generated at ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}</p>
          </footer>
        </div>
      </body>
    </html>
    """.trimIndent()
}
