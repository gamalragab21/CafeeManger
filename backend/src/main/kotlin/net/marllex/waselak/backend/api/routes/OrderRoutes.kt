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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.ActivityLogsTable
import net.marllex.waselak.backend.data.database.CustomerAddressesTable
import net.marllex.waselak.backend.data.database.CreditTransactionsTable
import net.marllex.waselak.backend.data.database.CustomerCreditsTable
import net.marllex.waselak.backend.data.database.CustomersTable
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.OffersTable
import net.marllex.waselak.backend.data.database.OrderItemsTable
import net.marllex.waselak.backend.data.database.OrdersTable
import net.marllex.waselak.backend.data.database.StockTable
import net.marllex.waselak.backend.data.database.StockTransactionsTable
import net.marllex.waselak.backend.data.database.RecipesTable
import net.marllex.waselak.backend.data.database.RecipeIngredientsTable
import net.marllex.waselak.backend.data.database.PointsTransactionsTable
import net.marllex.waselak.backend.data.database.TaxPlacesTable
import net.marllex.waselak.backend.data.database.WorkersTable
import net.marllex.waselak.backend.data.database.AttendanceTable
import org.jetbrains.exposed.sql.or
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.data.database.ReservationsTable
import net.marllex.waselak.backend.data.database.TablesTable
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.data.database.ProductReturnsTable
import net.marllex.waselak.backend.data.database.ReturnItemsTable
import net.marllex.waselak.backend.domain.service.OrderService
import net.marllex.waselak.backend.domain.service.PlanService
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
    val reservation_id: String? = null,
    val notes: String? = null,
    val items: List<CreateOrderItemDto>,
    val offer_id: String? = null,
    val points_redeemed: Int = 0,
    val discount_reason: String? = null,
    val doctor_name: String? = null,
    val diagnosis: String? = null,
)

@Serializable
data class CreateOrderItemDto(
    val item_id: String,
    val quantity: Int,
    val note: String? = null,
    val variant_selections: List<VariantSelectionDto>? = null
)

@Serializable
data class VariantSelectionDto(
    val group_name: String,
    val option_name: String,
    val price_adjustment: Double
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
data class RefundOrderDto(val reason: String)

@Serializable
data class ShareReceiptResponseDto(
    val url: String, val token: String, val expires_at: Long
)

@Serializable
data class ShiftSummaryDto(
    val total_revenue: Double,
    val total_orders: Int,
    val cash_revenue: Double,
    val wallet_revenue: Double,
    val card_revenue: Double,
    val cash_orders: Int,
    val wallet_orders: Int,
    val card_orders: Int,
    val cancelled_total: Double,
    val cancelled_count: Int,
    val refunded_total: Double,
    val refunded_count: Int,
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
    val offer_id: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long? = null,
    val points_earned: Int = 0,
    val points_redeemed: Int = 0,
    val discount_reason: String? = null,
    val refunded_at: Long? = null,
    val refunded_by: String? = null,
    val refund_reason: String? = null,
    val refunded_amount: Double = 0.0,
    val returned_item_count: Int = 0,
    val doctor_name: String? = null,
    val diagnosis: String? = null,
)

@Serializable
data class OrderItemDto(
    val id: String,
    val order_id: String,
    val item_id: String,
    val item_name_snapshot: String,
    val item_price_snapshot: Double,
    val quantity: Int,
    val note: String? = null,
    val variant_options_snapshot: String? = null
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
    val planService by KoinJavaComponent.inject<PlanService>(
        clazz = PlanService::class.java
    )
    val jwtConfig by lazy { JwtConfig(this.application.environment.config) }

    route("/api/v1/orders") {
        // IMPORTANT: specific paths MUST come before /{id} to avoid route conflict

        get("/delivery-dashboard") {
            val trace = call.routeTrace()
            trace.step("Delivery dashboard started")
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorUUID, "DELIVERY")
            trace.step("Feature check passed", mapOf("vendorId" to vendorUUID.toString()))

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
            trace.step("Dashboard built", mapOf("deliveryUsersCount" to dashboard.size.toString()))
            trace.step("Delivery dashboard completed")
            call.respond(HttpStatusCode.OK, dashboard)
        }

        get("/delivery/available") {
            val trace = call.routeTrace()
            trace.step("List available delivery orders started")
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
            trace.step("Available orders fetched", mapOf("count" to orders.size.toString()))
            trace.step("List available delivery orders completed")
            call.respond(HttpStatusCode.OK, orders)
        }

        get("/delivery/mine") {
            val trace = call.routeTrace()
            trace.step("List my delivery orders started")
            val principal = requireRole("DELIVERY")
            val status = call.parameters["status"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            trace.step("Filters applied", mapOf("status" to (status ?: "null"), "limit" to limit.toString(), "offset" to offset.toString()))

            val (orders, total) = transaction {
                var query = OrdersTable.selectAll().where {
                    (OrdersTable.deliveryUserId eq UUID.fromString(principal.userId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }

                val totalCount = query.count().toInt()
                val orderRows = query.orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .limit(limit).offset(offset.toLong()).toList()
                val userIds = orderRows.flatMap { listOf(it[OrdersTable.cashierId], it[OrdersTable.deliveryUserId]) }
                    .filterNotNull().distinct()
                val usersMap = if (userIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll().where { UsersTable.id inList userIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }
                val results = orderRows.map { orderRow ->
                    val items =
                        OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderRow[OrdersTable.id] }
                            .map { it.toOrderItemDto() }
                    orderRow.toOrderDto(items, usersMap)
                }
                results to totalCount
            }
            trace.step("My delivery orders fetched", mapOf("count" to orders.size.toString(), "total" to total.toString()))
            trace.step("List my delivery orders completed")
            call.respond(HttpStatusCode.OK, PaginatedOrders(orders, total, offset + limit < total))
        }

        get {
            val trace = call.routeTrace()
            trace.step("List orders started")
            val principal = currentUser()
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { UUID.fromString(it) }
            val tableId = call.parameters["table_id"]?.let { UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Filters applied", mapOf(
                "status" to (status ?: "null"),
                "channel" to (channel ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null"),
                "limit" to limit.toString(),
                "offset" to offset.toString()
            ))

            val (orders, total) = transaction {
                var query = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }
                status?.let { query = query.andWhere { OrdersTable.status eq it } }
                channel?.let { query = query.andWhere { OrdersTable.channel eq it } }
                cashierId?.let { query = query.andWhere { OrdersTable.cashierId eq it } }
                deliveryUserId?.let { query = query.andWhere { OrdersTable.deliveryUserId eq it } }
                tableId?.let { query = query.andWhere { OrdersTable.tableId eq it } }
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
            trace.step("Orders fetched", mapOf("count" to orders.size.toString(), "total" to total.toString()))
            trace.step("List orders completed")
            call.respond(HttpStatusCode.OK, PaginatedOrders(orders, total, offset + limit < total))
        }

        route("my-shift-summary") {
            get {
                val trace = call.routeTrace()
                trace.step("My shift summary started")
                val principal = requireRole("CASHIER", "DELIVERY", "MANAGER")
                val vendorUUID = UUID.fromString(principal.vendorId)
                val userUUID = UUID.fromString(principal.userId)
                trace.step("User identified", mapOf("userId" to userUUID.toString(), "role" to principal.role))
                val scope = call.parameters["scope"] // "today" or null
                val from = call.parameters["from"]?.toLongOrNull()

                val summary = transaction {
                    var query = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }

                    // Scope to the authenticated user based on role
                    when (principal.role) {
                        "CASHIER" -> query = query.andWhere { OrdersTable.cashierId eq userUUID }
                        "DELIVERY" -> query = query.andWhere { OrdersTable.deliveryUserId eq userUUID }
                        // MANAGER sees all orders
                    }

                    if (scope == "today") {
                        // Start of today using java.time (available on JVM backend)
                        val todayStart = kotlinx.datetime.Instant.fromEpochMilliseconds(
                            java.time.LocalDate.now()
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        )
                        // Include orders created today OR paid today
                        query = query.andWhere {
                            (OrdersTable.createdAt greaterEq todayStart) or
                            (OrdersTable.paymentConfirmedAt greaterEq todayStart)
                        }
                    } else {
                        from?.let { ts ->
                            val fromInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                            // Include orders created, paid, or updated after login
                            query = query.andWhere {
                                (OrdersTable.createdAt greaterEq fromInstant) or
                                (OrdersTable.updatedAt greaterEq fromInstant) or
                                (OrdersTable.paymentConfirmedAt greaterEq fromInstant)
                            }
                        }
                    }

                    val allOrders = query.toList()

                    val completed = allOrders.filter { it[OrdersTable.status] in listOf("COMPLETED", "PAID") }
                    val cancelled = allOrders.filter { it[OrdersTable.status] == "CANCELED" }
                    val refunded = allOrders.filter { it[OrdersTable.status] == "REFUNDED" }

                    val cashCompleted = completed.filter { it[OrdersTable.paymentMethod] == "CASH" }
                    val walletCompleted = completed.filter { it[OrdersTable.paymentMethod] == "WALLET" }
                    val cardCompleted = completed.filter { it[OrdersTable.paymentMethod] == "CARD" }

                    fun Double.r2() = Math.round(this * 100.0) / 100.0
                    ShiftSummaryDto(
                        total_revenue = completed.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        total_orders = completed.size,
                        cash_revenue = cashCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        wallet_revenue = walletCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        card_revenue = cardCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        cash_orders = cashCompleted.size,
                        wallet_orders = walletCompleted.size,
                        card_orders = cardCompleted.size,
                        cancelled_total = cancelled.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        cancelled_count = cancelled.size,
                        refunded_total = refunded.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        refunded_count = refunded.size,
                    )
                }
                trace.step("Summary calculated", mapOf(
                    "totalRevenue" to summary.total_revenue.toString(),
                    "totalOrders" to summary.total_orders.toString(),
                    "cancelledCount" to summary.cancelled_count.toString(),
                    "refundedCount" to summary.refunded_count.toString()
                ))
                trace.step("My shift summary completed")
                call.respond(HttpStatusCode.OK, summary)
            }
        }

        // Manager-only: get shift summary for a specific user (cashier/delivery)
        route("shift-summary/{userId}") {
            get {
                val trace = call.routeTrace()
                trace.step("User shift summary started")
                val principal = requireRole("MANAGER")
                val vendorUUID = UUID.fromString(principal.vendorId)
                val targetUserId = call.parameters["userId"] ?: throw IllegalArgumentException("userId required")
                val targetUserUUID = UUID.fromString(targetUserId)
                trace.step("Target user identified", mapOf("targetUserId" to targetUserId))
                val from = call.parameters["from"]?.toLongOrNull()

                val summary = transaction {
                    // Look up the target user's role to apply the correct filter
                    val userRole = UsersTable.selectAll()
                        .where { UsersTable.id eq targetUserUUID }
                        .firstOrNull()?.get(UsersTable.role)
                        ?: throw NoSuchElementException("User not found")

                    var query = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }

                    // Filter by the correct column based on user role
                    when (userRole) {
                        "DELIVERY" -> query = query.andWhere { OrdersTable.deliveryUserId eq targetUserUUID }
                        else -> query = query.andWhere { OrdersTable.cashierId eq targetUserUUID }
                    }

                    // If no 'from' provided, auto-detect shift start from latest attendance check-in today
                    val effectiveFrom = if (from != null) {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(from)
                    } else {
                        // Find the worker record linked to this user
                        val workerRow = WorkersTable.selectAll()
                            .where { WorkersTable.userId eq targetUserUUID }
                            .firstOrNull()

                        val today = java.time.LocalDate.now().toString() // YYYY-MM-DD

                        if (workerRow != null) {
                            // Get the latest check-in for this worker today
                            AttendanceTable.selectAll().where {
                                (AttendanceTable.workerId eq workerRow[WorkersTable.id]) and
                                    (AttendanceTable.date eq today)
                            }.orderBy(AttendanceTable.checkIn, SortOrder.DESC)
                                .firstOrNull()?.get(AttendanceTable.checkIn)
                        } else {
                            null
                        }
                    }

                    // Always filter: if no shift check-in found, default to start of today
                    val filterFrom = effectiveFrom ?: kotlinx.datetime.Instant.fromEpochMilliseconds(
                        java.time.LocalDate.now()
                            .atStartOfDay(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    )

                    query = query.andWhere {
                        OrdersTable.createdAt greaterEq filterFrom
                    }

                    val allOrders = query.toList()

                    val completed = allOrders.filter { it[OrdersTable.status] in listOf("COMPLETED", "PAID") }
                    val cancelled = allOrders.filter { it[OrdersTable.status] == "CANCELED" }
                    val refunded = allOrders.filter { it[OrdersTable.status] == "REFUNDED" }

                    val cashCompleted = completed.filter { it[OrdersTable.paymentMethod] == "CASH" }
                    val walletCompleted = completed.filter { it[OrdersTable.paymentMethod] == "WALLET" }
                    val cardCompleted = completed.filter { it[OrdersTable.paymentMethod] == "CARD" }

                    fun Double.r2() = Math.round(this * 100.0) / 100.0
                    ShiftSummaryDto(
                        total_revenue = completed.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        total_orders = completed.size,
                        cash_revenue = cashCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        wallet_revenue = walletCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        card_revenue = cardCompleted.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        cash_orders = cashCompleted.size,
                        wallet_orders = walletCompleted.size,
                        card_orders = cardCompleted.size,
                        cancelled_total = cancelled.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        cancelled_count = cancelled.size,
                        refunded_total = refunded.sumOf { it[OrdersTable.total].toDouble() }.r2(),
                        refunded_count = refunded.size,
                    )
                }
                trace.step("Summary calculated", mapOf(
                    "totalRevenue" to summary.total_revenue.toString(),
                    "totalOrders" to summary.total_orders.toString(),
                    "cancelledCount" to summary.cancelled_count.toString(),
                    "refundedCount" to summary.refunded_count.toString()
                ))
                trace.step("User shift summary completed")
                call.respond(HttpStatusCode.OK, summary)
            }
        }

        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get order started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Order ID parsed", mapOf("orderId" to id))
            try {
                UUID.fromString(id)
            } catch (_: IllegalArgumentException) {
                throw NoSuchElementException("Order not found")
            }

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
            trace.step("Order found", mapOf("status" to order.status, "total" to order.total.toString()))
            trace.step("Get order completed")
            call.respond(HttpStatusCode.OK, order)
        }

        // Generate a temporary shareable receipt URL (30 minutes)
        post("/{id}/share") {
            val trace = call.routeTrace()
            trace.step("Share receipt started")
            val principal = currentUser()
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Order ID parsed", mapOf("orderId" to orderId))

            // Plan gate: digital receipt
            planService.checkFeature(UUID.fromString(principal.vendorId), "DIGITAL_RECEIPT")
            trace.step("Feature check passed")

            transaction {
                OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(orderId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")
            }
            trace.step("Order verified")

            val expiresMs = 30 * 60 * 1000 // 30 minutes
            val token = generateReceiptToken(jwtConfig, orderId, principal.vendorId, expiresMs)
            val origin = call.request.origin
            val portPart = when (origin.serverPort) {
                80, 443 -> ""
                else -> ":${origin.serverPort}"
            }
            val url = "${origin.scheme}://${origin.serverHost}$portPart/public/receipts/$orderId?token=$token"
            trace.step("Receipt URL generated", mapOf("expiresMs" to expiresMs.toString()))
            trace.step("Share receipt completed")
            call.respond(
                ShareReceiptResponseDto(
                    url = url, token = token, expires_at = System.currentTimeMillis() + expiresMs
                )
            )
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create order started")
            val principal = requireRole("CASHIER", "MANAGER")
            val request = call.receive<CreateOrderDto>()
            require(request.items.isNotEmpty()) { "Order must have at least one item" }
            trace.step("Request parsed", mapOf(
                "channel" to request.channel,
                "itemsCount" to request.items.size.toString(),
                "paymentMethod" to request.payment_method,
                "paymentTiming" to request.payment_timing
            ))

            // Validate channel value
            val validChannels = listOf("DINE_IN", "DELIVERY", "TAKEAWAY", "IN_STORE", "PICKUP_LATER")
            require(request.channel in validChannels) { "Invalid channel. Must be one of: ${validChannels.joinToString()}" }

            // ─── Plan limit checks ────────────────────────
            val vendorUuidForPlan = UUID.fromString(principal.vendorId)
            planService.checkOrderCreation(vendorUuidForPlan)
            if (request.channel == "DELIVERY") {
                planService.checkDeliveryChannel(vendorUuidForPlan)
            }
            if (request.points_redeemed > 0) {
                planService.checkFeature(vendorUuidForPlan, "LOYALTY")
            }
            if (!request.discount_reason.isNullOrBlank() && request.discount > 0) {
                planService.checkFeature(vendorUuidForPlan, "MANUAL_DISCOUNT")
            }
            trace.step("Plan limits checked", mapOf("vendorId" to vendorUuidForPlan.toString()))

            // Validate table for DINE_IN orders
            var resolvedTableId = request.table_id
            if (request.channel == "DINE_IN" && request.table_id != null) {
                transaction {
                    val table = TablesTable.selectAll().where {
                        (TablesTable.id eq UUID.fromString(request.table_id)) and
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Table not found")
                }
            }

            // Validate reservation if provided
            if (request.reservation_id != null) {
                transaction {
                    val reservation = ReservationsTable.selectAll().where {
                        (ReservationsTable.id eq UUID.fromString(request.reservation_id)) and
                        (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Reservation not found")

                    val resStatus = reservation[ReservationsTable.status]
                    require(resStatus in listOf("PENDING", "CONFIRMED")) {
                        "Reservation is $resStatus and cannot be linked to a new order"
                    }

                    // Auto-resolve table_id from reservation if not provided
                    if (resolvedTableId == null) {
                        resolvedTableId = reservation[ReservationsTable.tableId].value.toString()
                    }
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

                    val basePrice = item[ItemsTable.price]
                    val variantAdjustment = orderItem.variant_selections
                        ?.sumOf { it.price_adjustment }
                        ?.let { BigDecimal.valueOf(it) }
                        ?: BigDecimal.ZERO
                    val price = basePrice + variantAdjustment
                    subtotal += price * BigDecimal(orderItem.quantity)
                    Triple(item, orderItem, price)
                }

                // Delivery fee: only for DELIVERY orders
                // If cashier provides a fee > 0, use it; otherwise fall back to vendor default
                val deliveryFeeAmount = if (request.channel == "DELIVERY") {
                    if (request.delivery_fee > 0.0) {
                        BigDecimal.valueOf(request.delivery_fee)
                    } else {
                        vendor[VendorsTable.defaultDeliveryFee]
                    }
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
                trace.step("Tax calculated", mapOf(
                    "subtotal" to subtotal.toPlainString(),
                    "discount" to discountAmount.toPlainString(),
                    "taxAmount" to taxAmount.toPlainString(),
                    "taxPercent" to taxPercentValue.toPlainString(),
                    "deliveryFee" to deliveryFeeAmount.toPlainString(),
                    "total" to total.toPlainString()
                ))

                val orderId = OrdersTable.insertAndGetId { stmt ->
                    stmt[OrdersTable.vendorId] = vendorUUID
                    stmt[channel] = request.channel
                    stmt[status] = "CREATED"
                    resolvedTableId?.let { tid -> stmt[tableId] = UUID.fromString(tid) }
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
                    // PAY_NOW: customer already paid → mark as PAID immediately
                    // PAY_LATER: payment pending → cashier must confirm via PATCH /payment-status before completing
                    stmt[paymentStatus] = if (request.payment_timing == "PAY_NOW") "PAID" else "PENDING"
                    stmt[paymentTiming] = request.payment_timing
                    if (request.payment_timing == "PAY_NOW") {
                        stmt[paymentConfirmedAt] = Clock.System.now()
                        stmt[paymentConfirmedBy] = UUID.fromString(principal.userId)
                    }
                    stmt[OrdersTable.subtotal] = subtotal
                    stmt[deliveryFee] = deliveryFeeAmount
                    stmt[OrdersTable.discount] = discountAmount
                    stmt[OrdersTable.discountType] = discountType
                    stmt[tax] = taxAmount
                    stmt[OrdersTable.taxPercent] = taxPercentValue
                    stmt[OrdersTable.total] = total
                    stmt[notes] = request.notes
                    stmt[OrdersTable.doctorName] = request.doctor_name
                    stmt[OrdersTable.diagnosis] = request.diagnosis
                    request.offer_id?.let { oid -> stmt[OrdersTable.offerId] = UUID.fromString(oid) }
                    stmt[createdAt] = Clock.System.now()
                    stmt[updatedAt] = Clock.System.now()
                }

                // Increment offer used_count if an offer was applied
                request.offer_id?.let { oid ->
                    val offerUUID = UUID.fromString(oid)
                    OffersTable.update({ OffersTable.id eq offerUUID }) { stmt ->
                        with(SqlExpressionBuilder) {
                            stmt[usedCount] = usedCount + 1
                        }
                    }
                }

                // Insert order items
                val orderItems = itemSnapshots.map { (item, orderItem, price) ->
                    val variantSnapshot = orderItem.variant_selections
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { Json.encodeToString(it) }

                    val oiId = OrderItemsTable.insertAndGetId {
                        it[OrderItemsTable.orderId] = orderId
                        it[itemId] = UUID.fromString(orderItem.item_id)
                        it[itemNameSnapshot] = item[ItemsTable.name]
                        it[itemPriceSnapshot] = price
                        it[quantity] = orderItem.quantity
                        it[note] = orderItem.note
                        it[variantOptionsSnapshot] = variantSnapshot
                        it[createdAt] = Clock.System.now()
                    }
                    OrderItemDto(
                        id = oiId.toString(),
                        order_id = orderId.toString(),
                        item_id = orderItem.item_id,
                        item_name_snapshot = item[ItemsTable.name],
                        item_price_snapshot = price.toDouble(),
                        quantity = orderItem.quantity,
                        note = orderItem.note,
                        variant_options_snapshot = variantSnapshot
                    )
                }
                trace.step("Order items processed", mapOf("count" to orderItems.size.toString()))

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
                                // FIFO batch deduction — if batches exist, deduct from oldest first
                                val batchesAffected = fifoDeductBatches(
                                    stockUUID = stockRow[StockTable.id].value,
                                    vendorUUID = vendorUUID,
                                    amount = deductAmt,
                                    transactionType = "SALE_DIRECT",
                                    orderId = orderId.value,
                                    note = "Order deduction (direct)"
                                )
                                // If no batches exist, record legacy aggregate transaction
                                if (batchesAffected.isEmpty()) {
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
                                // Stock alert notifications
                                if (stockRow[StockTable.alertEnabled]) {
                                    val minQty = stockRow[StockTable.minQuantity]
                                    val itemName = stockRow[StockTable.itemName]
                                    if (newQty <= BigDecimal.ZERO && oldQty > BigDecimal.ZERO) {
                                        createSystemNotification(
                                            vendorUUID = vendorUUID,
                                            type = "OUT_OF_STOCK",
                                            title = "Out of Stock",
                                            body = "'$itemName' is now out of stock",
                                            priority = "HIGH",
                                            actionUrl = "/stock",
                                        )
                                    } else if (newQty <= minQty && oldQty > minQty) {
                                        createSystemNotification(
                                            vendorUUID = vendorUUID,
                                            type = "LOW_STOCK",
                                            title = "Low Stock Alert",
                                            body = "'$itemName' is running low (${newQty.toPlainString()} remaining)",
                                            actionUrl = "/stock",
                                        )
                                    }
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
                                // FIFO batch deduction for recipe ingredients
                                val batchesAffected = fifoDeductBatches(
                                    stockUUID = stockRow[StockTable.id].value,
                                    vendorUUID = vendorUUID,
                                    amount = convertedRequiredDecimal,
                                    transactionType = "SALE_RECIPE",
                                    orderId = orderId.value,
                                    recipeId = recipeId.value,
                                    note = "Recipe deduction: ${item[ItemsTable.name]} x${orderItem.quantity}"
                                )
                                if (batchesAffected.isEmpty()) {
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
                                // Stock alert notifications for recipe ingredients
                                if (stockRow[StockTable.alertEnabled]) {
                                    val minQty = stockRow[StockTable.minQuantity]
                                    val ingredientName = stockRow[StockTable.itemName]
                                    if (newQty <= BigDecimal.ZERO && oldQty > BigDecimal.ZERO) {
                                        createSystemNotification(
                                            vendorUUID = vendorUUID,
                                            type = "OUT_OF_STOCK",
                                            title = "Out of Stock",
                                            body = "'$ingredientName' is now out of stock",
                                            priority = "HIGH",
                                            actionUrl = "/stock",
                                        )
                                    } else if (newQty <= minQty && oldQty > minQty) {
                                        createSystemNotification(
                                            vendorUUID = vendorUUID,
                                            type = "LOW_STOCK",
                                            title = "Low Stock Alert",
                                            body = "'$ingredientName' is running low (${newQty.toPlainString()} remaining)",
                                            actionUrl = "/stock",
                                        )
                                    }
                                }
                            }
                        }
                        // "NONE" -> skip stock deduction
                    }
                }
                trace.step("Stock deducted", mapOf("stockMode" to vendorStockMode))

                // Auto-set table to OCCUPIED for dine-in orders
                if (request.channel == "DINE_IN" && resolvedTableId != null) {
                    TablesTable.update({
                        TablesTable.id eq UUID.fromString(resolvedTableId)
                    }) {
                        it[TablesTable.status] = "OCCUPIED"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                // Auto-link reservation to order
                if (request.reservation_id != null) {
                    ReservationsTable.update({
                        ReservationsTable.id eq UUID.fromString(request.reservation_id)
                    }) {
                        it[ReservationsTable.orderId] = orderId
                        it[ReservationsTable.status] = "CONFIRMED"
                        it[ReservationsTable.updatedAt] = Clock.System.now()
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

                    // ─── Loyalty Points: Redeem ────────────────────────
                    if (request.points_redeemed > 0 && vendor[VendorsTable.loyaltyEnabled]) {
                        val customerRow = CustomersTable.selectAll()
                            .where { CustomersTable.id eq customerUUID }
                            .first()
                        val currentBalance = customerRow[CustomersTable.pointsBalance]
                        val pointsToRedeem = request.points_redeemed.coerceAtMost(currentBalance)
                        if (pointsToRedeem > 0) {
                            // Deduct points from customer balance
                            CustomersTable.update({ CustomersTable.id eq customerUUID }) { stmt ->
                                with(SqlExpressionBuilder) {
                                    stmt[pointsBalance] = pointsBalance - pointsToRedeem
                                }
                            }
                            // Record redemption transaction
                            PointsTransactionsTable.insert {
                                it[PointsTransactionsTable.customerId] = customerUUID
                                it[PointsTransactionsTable.vendorId] = vendorUUID
                                it[PointsTransactionsTable.orderId] = orderId
                                it[type] = "REDEEM"
                                it[points] = pointsToRedeem
                                it[description] = "Points redeemed on order"
                                it[createdAt] = Clock.System.now()
                            }
                            // Store on order
                            OrdersTable.update({ OrdersTable.id eq orderId }) { stmt ->
                                stmt[pointsRedeemed] = pointsToRedeem
                            }
                        }
                    }

                    // ─── Loyalty Points: Earn ──────────────────────────
                    if (vendor[VendorsTable.loyaltyEnabled]) {
                        val earnRate = vendor[VendorsTable.pointsEarnRate].toDouble()
                        val earned = kotlin.math.floor(total.toDouble() * earnRate).toInt()
                        if (earned > 0) {
                            CustomersTable.update({ CustomersTable.id eq customerUUID }) { stmt ->
                                with(SqlExpressionBuilder) {
                                    stmt[pointsBalance] = pointsBalance + earned
                                }
                            }
                            PointsTransactionsTable.insert {
                                it[PointsTransactionsTable.customerId] = customerUUID
                                it[PointsTransactionsTable.vendorId] = vendorUUID
                                it[PointsTransactionsTable.orderId] = orderId
                                it[type] = "EARN"
                                it[points] = earned
                                it[description] = "Points earned from order"
                                it[createdAt] = Clock.System.now()
                            }
                            OrdersTable.update({ OrdersTable.id eq orderId }) { stmt ->
                                stmt[pointsEarned] = earned
                            }
                        }
                    }

                    // Store discount reason on order
                    if (!request.discount_reason.isNullOrBlank()) {
                        OrdersTable.update({ OrdersTable.id eq orderId }) { stmt ->
                            stmt[discountReason] = request.discount_reason
                        }
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

                // Store discount reason even without customer
                if (request.customer_id == null && !request.discount_reason.isNullOrBlank()) {
                    OrdersTable.update({ OrdersTable.id eq orderId }) { stmt ->
                        stmt[discountReason] = request.discount_reason
                    }
                }

                // Auto-charge customer credit when payment method is CREDIT
                if (request.payment_method == "CREDIT" && request.customer_id != null) {
                    val customerUUID = UUID.fromString(request.customer_id)
                    val now = Clock.System.now()

                    // Get or create credit record
                    val creditRow = CustomerCreditsTable.selectAll().where {
                        (CustomerCreditsTable.vendorId eq vendorUUID) and
                        (CustomerCreditsTable.customerId eq customerUUID)
                    }.firstOrNull()

                    val creditId = if (creditRow != null) {
                        creditRow[CustomerCreditsTable.id]
                    } else {
                        CustomerCreditsTable.insertAndGetId {
                            it[CustomerCreditsTable.vendorId] = vendorUUID
                            it[CustomerCreditsTable.customerId] = customerUUID
                            it[balance] = BigDecimal.ZERO
                            it[creditLimit] = BigDecimal("500.00")
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }

                    val currentBalance = creditRow?.get(CustomerCreditsTable.balance) ?: BigDecimal.ZERO
                    val newBalance = currentBalance + total

                    // Update balance
                    CustomerCreditsTable.update({
                        CustomerCreditsTable.id eq creditId
                    }) {
                        it[balance] = newBalance
                        it[updatedAt] = now
                    }

                    // Record credit transaction
                    CreditTransactionsTable.insert {
                        it[CreditTransactionsTable.creditId] = creditId
                        it[CreditTransactionsTable.vendorId] = vendorUUID
                        it[CreditTransactionsTable.orderId] = orderId
                        it[type] = "CHARGE"
                        it[amount] = total
                        it[previousBalance] = currentBalance
                        it[CreditTransactionsTable.newBalance] = newBalance
                        it[note] = "Auto-charge from order"
                        it[createdBy] = UUID.fromString(principal.userId)
                        it[createdAt] = now
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
            trace.step("Order created", mapOf(
                "orderId" to order.id,
                "total" to order.total.toString(),
                "channel" to order.channel
            ))
            trace.step("Create order completed")
            call.respond(HttpStatusCode.Created, order)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update order started")
            val principal = requireRole("CASHIER", "MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateOrderDto>()
            trace.step("Request parsed", mapOf(
                "orderId" to id,
                "hasItems" to (request.items != null).toString(),
                "hasClientName" to (request.client_name != null).toString(),
                "hasPaymentMethod" to (request.payment_method != null).toString()
            ))

            val order = transaction {
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and
                    (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = current[OrdersTable.status]
                trace.step("Current order status", mapOf("status" to currentStatus))
                if (currentStatus in listOf("COMPLETED", "CANCELED", "REFUNDED")) {
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
                        val basePrice = item[ItemsTable.price]
                        val variantAdj = orderItem.variant_selections
                            ?.sumOf { it.price_adjustment }
                            ?.let { BigDecimal.valueOf(it) }
                            ?: BigDecimal.ZERO
                        val price = basePrice + variantAdj
                        subtotal += price * BigDecimal(orderItem.quantity)

                        val variantSnapshot = orderItem.variant_selections
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { Json.encodeToString(it) }

                        val oiId = OrderItemsTable.insertAndGetId {
                            it[OrderItemsTable.orderId] = UUID.fromString(id)
                            it[itemId] = UUID.fromString(orderItem.item_id)
                            it[itemNameSnapshot] = item[ItemsTable.name]
                            it[itemPriceSnapshot] = price
                            it[OrderItemsTable.quantity] = orderItem.quantity
                            it[note] = orderItem.note
                            it[variantOptionsSnapshot] = variantSnapshot
                            it[createdAt] = Clock.System.now()
                        }

                        // Deduct stock for new item — branches on stockBehavior
                        val editBehavior = item[ItemsTable.stockBehavior]
                        val editNow = Clock.System.now()
                        val editOrderUUID = UUID.fromString(id)
                        when (editBehavior) {
                            "DIRECT" -> {
                                val stockRow = StockTable.selectAll().where {
                                    (StockTable.itemId eq UUID.fromString(orderItem.item_id)) and (StockTable.vendorId eq vendorUUID)
                                }.firstOrNull()
                                if (stockRow != null) {
                                    val sOldQty = stockRow[StockTable.quantity]
                                    val deductAmt = BigDecimal(orderItem.quantity)
                                    val sNewQty = (sOldQty - deductAmt).coerceAtLeast(BigDecimal.ZERO)
                                    StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                        it[quantity] = sNewQty
                                        it[updatedAt] = editNow
                                    }
                                    val batchesAffected = fifoDeductBatches(
                                        stockUUID = stockRow[StockTable.id].value,
                                        vendorUUID = vendorUUID,
                                        amount = deductAmt,
                                        transactionType = "SALE_DIRECT",
                                        orderId = editOrderUUID,
                                        note = "Order edit deduction (direct)"
                                    )
                                    if (batchesAffected.isEmpty()) {
                                        StockTransactionsTable.insert {
                                            it[stockId] = stockRow[StockTable.id]
                                            it[type] = "SALE_DIRECT"
                                            it[StockTransactionsTable.quantity] = deductAmt
                                            it[previousQuantity] = sOldQty
                                            it[StockTransactionsTable.orderId] = editOrderUUID
                                            it[note] = "Order edit deduction (direct)"
                                            it[createdAt] = editNow
                                        }
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
                                        val convertedRequiredDecimal = BigDecimal.valueOf(convertedRequired)
                                        val sOldQty = stockRow[StockTable.quantity]
                                        val sNewQty = (sOldQty - convertedRequiredDecimal).coerceAtLeast(BigDecimal.ZERO)
                                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                            it[quantity] = sNewQty
                                            it[updatedAt] = editNow
                                        }
                                        val batchesAffected = fifoDeductBatches(
                                            stockUUID = stockRow[StockTable.id].value,
                                            vendorUUID = vendorUUID,
                                            amount = convertedRequiredDecimal,
                                            transactionType = "SALE_RECIPE",
                                            orderId = editOrderUUID,
                                            recipeId = recipeId.value,
                                            note = "Order edit deduction (recipe): ${item[ItemsTable.name]} x${orderItem.quantity}"
                                        )
                                        if (batchesAffected.isEmpty()) {
                                            StockTransactionsTable.insert {
                                                it[stockId] = stockRow[StockTable.id]
                                                it[type] = "SALE_RECIPE"
                                                it[StockTransactionsTable.quantity] = convertedRequiredDecimal
                                                it[previousQuantity] = sOldQty
                                                it[StockTransactionsTable.orderId] = editOrderUUID
                                                it[StockTransactionsTable.recipeId] = recipeId
                                                it[note] = "Order edit deduction (recipe): ${item[ItemsTable.name]} x${orderItem.quantity}"
                                                it[createdAt] = editNow
                                            }
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
                    // Delivery fee only for DELIVERY orders; cashier can override, otherwise keep existing
                    val deliveryFeeAmount = if (channel == "DELIVERY") {
                        request.delivery_fee?.let { BigDecimal.valueOf(it) } ?: current[OrdersTable.deliveryFee]
                    } else BigDecimal.ZERO

                    // Discount recalculation
                    val discountAmt = current[OrdersTable.discount]
                    val afterDiscount = (subtotal - discountAmt).coerceAtLeast(BigDecimal.ZERO)

                    // Tax recalculation (same logic as create)
                    val vendor = VendorsTable.selectAll()
                        .where { VendorsTable.id eq vendorUUID }.first()
                    var taxAmount = BigDecimal.ZERO
                    var taxPercentValue = BigDecimal.ZERO
                    val taxPlaceIdUuid: UUID? = request.tax_place_id?.let { UUID.fromString(it) }
                        ?: current[OrdersTable.taxPlaceId]?.value
                    if (taxPlaceIdUuid != null) {
                        val taxPlace = TaxPlacesTable.selectAll().where {
                            (TaxPlacesTable.id eq taxPlaceIdUuid) and
                            (TaxPlacesTable.vendorId eq vendorUUID)
                        }.firstOrNull()
                        taxPlace?.let {
                            taxPercentValue = it[TaxPlacesTable.taxPercent]
                            taxAmount = (afterDiscount * taxPercentValue / BigDecimal(100))
                                .setScale(2, java.math.RoundingMode.HALF_UP)
                        }
                    } else if (vendor[VendorsTable.taxEnabled]) {
                        taxPercentValue = vendor[VendorsTable.defaultTaxPercent]
                        if (taxPercentValue > BigDecimal.ZERO) {
                            taxAmount = (afterDiscount * taxPercentValue / BigDecimal(100))
                                .setScale(2, java.math.RoundingMode.HALF_UP)
                        }
                    }
                    val total = afterDiscount + deliveryFeeAmount + taxAmount

                    OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) { stmt ->
                        stmt[OrdersTable.subtotal] = subtotal
                        stmt[deliveryFee] = deliveryFeeAmount
                        stmt[tax] = taxAmount
                        stmt[OrdersTable.taxPercent] = taxPercentValue
                        stmt[OrdersTable.total] = total
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
            trace.step("Order updated", mapOf("orderId" to order.id, "total" to order.total.toString()))
            trace.step("Update order completed")
            call.respond(HttpStatusCode.OK, order)
        }

        patch("/{id}/status") {
            val trace = call.routeTrace()
            trace.step("Update order status started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateStatusDto>()
            trace.step("Request parsed", mapOf("orderId" to id, "newStatus" to request.status))

            val order = transaction {
                val targetStatus = request.status
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = current[OrdersTable.status]
                val currentChannel = current[OrdersTable.channel]
                trace.step("Current status", mapOf("status" to currentStatus, "channel" to currentChannel))
                val valid = orderService.validateStatusTransition(
                    currentStatus, targetStatus, currentChannel, principal.role
                )
                if (!valid) throw IllegalStateException(
                    "Invalid status transition from $currentStatus to $targetStatus (channel=$currentChannel, role=${principal.role})"
                )

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
                    trace.step("Stock restored for cancelled order", mapOf("itemCount" to orderItems.size.toString()))

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

                    // Notification: ORDER_CANCELLED
                    createSystemNotification(
                        vendorUUID = UUID.fromString(principal.vendorId),
                        type = "ORDER_CANCELLED",
                        title = "Order Cancelled",
                        body = "Order #${id.takeLast(6).uppercase()} has been cancelled",
                        data = """{"orderId":"$id"}""",
                        actionUrl = "/orders/$id",
                    )
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
            trace.step("Status updated", mapOf("orderId" to order.id, "newStatus" to order.status))
            trace.step("Update order status completed")
            call.respond(HttpStatusCode.OK, order)
        }

        // Refund a completed order (restore stock, update customer stats)
        post("/{id}/refund") {
            val trace = call.routeTrace()
            trace.step("Refund started")
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<RefundOrderDto>()
            require(request.reason.isNotBlank()) { "Refund reason is required" }
            trace.step("Refund request parsed", mapOf("orderId" to id, "reason" to request.reason))

            val order = transaction {
                val current = OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(id)) and
                    (OrdersTable.vendorId eq UUID.fromString(principal.vendorId))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val currentStatus = current[OrdersTable.status]
                val currentPayment = current[OrdersTable.paymentStatus]
                if (currentStatus != "COMPLETED" || currentPayment != "PAID") {
                    throw IllegalStateException("Only completed and paid orders can be refunded. Current: status=$currentStatus, payment=$currentPayment")
                }

                val now = Clock.System.now()

                // Update order: status → REFUNDED, paymentStatus → REFUNDED
                OrdersTable.update({ OrdersTable.id eq UUID.fromString(id) }) {
                    it[status] = "REFUNDED"
                    it[paymentStatus] = "REFUNDED"
                    it[refundedAt] = now
                    it[refundedBy] = UUID.fromString(principal.userId)
                    it[refundReason] = request.reason
                    it[updatedAt] = now
                }

                // Restore stock for all order items
                val vendorUUID = UUID.fromString(principal.vendorId)
                val orderItems = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId eq UUID.fromString(id) }.toList()
                restoreStockForOrderItems(vendorUUID, UUID.fromString(id), orderItems)
                trace.step("Stock restored", mapOf("itemCount" to orderItems.size.toString()))

                // Decrement customer stats
                val customerId = current[OrdersTable.customerId]
                if (customerId != null) {
                    val orderTotal = current[OrdersTable.total]
                    CustomersTable.update({ CustomersTable.id eq customerId }) { stmt ->
                        with(SqlExpressionBuilder) {
                            stmt[orderCount] = orderCount - 1
                            stmt[totalSpent] = totalSpent - orderTotal
                        }
                        stmt[updatedAt] = now
                    }
                }

                // Free table if dine-in
                if (current[OrdersTable.channel] == "DINE_IN") {
                    val tableUUID = current[OrdersTable.tableId]
                    if (tableUUID != null) {
                        TablesTable.update({ TablesTable.id eq tableUUID }) {
                            it[TablesTable.status] = "AVAILABLE"
                            it[TablesTable.updatedAt] = now
                        }
                    }
                }

                // Log activity
                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "ORDER_REFUNDED"
                    it[payload] = """{"reason":"${request.reason.replace("\"", "\\\"")}"}"""
                    it[createdAt] = now
                }

                // Notification: ORDER_REFUNDED
                createSystemNotification(
                    vendorUUID = UUID.fromString(principal.vendorId),
                    type = "ORDER_REFUNDED",
                    title = "Order Refunded",
                    body = "Order #${id.takeLast(6).uppercase()} has been refunded. Reason: ${request.reason}",
                    data = """{"orderId":"$id"}""",
                    actionUrl = "/orders/$id",
                )

                // Return updated order DTO
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
            trace.step("Refund processed", mapOf("orderId" to order.id, "total" to order.total.toString()))
            trace.step("Refund completed")
            call.respond(HttpStatusCode.OK, order)
        }

        // Update payment status independently from order status
        patch("/{id}/payment-status") {
            val trace = call.routeTrace()
            trace.step("Update payment status started")
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdatePaymentStatusDto>()
            trace.step("Request parsed", mapOf(
                "orderId" to id,
                "paymentStatus" to request.payment_status,
                "paymentMethod" to (request.payment_method ?: "null")
            ))

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

            // Validate payment_method if provided
            val validMethods = listOf("CASH", "CARD", "WALLET")
            if (request.payment_method != null) {
                require(request.payment_method in validMethods) {
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
                    // Update payment method if provided (allows choosing method at payment time)
                    request.payment_method?.let { method -> it[paymentMethod] = method }
                }

                val methodInfo = request.payment_method?.let { ",\"payment_method\":\"$it\"" } ?: ""
                ActivityLogsTable.insert {
                    it[orderId] = UUID.fromString(id)
                    it[userId] = UUID.fromString(principal.userId)
                    it[action] = "PAYMENT_STATUS_CHANGED"
                    it[payload] = """{"from":"${current[OrdersTable.paymentStatus]}","to":"${request.payment_status}"$methodInfo}"""
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
            trace.step("Payment updated", mapOf(
                "orderId" to order.id,
                "paymentStatus" to order.payment_status,
                "paymentMethod" to order.payment_method
            ))
            trace.step("Update payment status completed")
            call.respond(HttpStatusCode.OK, order)
        }

        // Generate a temporary shareable receipt URL (30 minutes)
        post("/{id}/share") {
            val trace = call.routeTrace()
            trace.step("Share receipt started")
            val principal = currentUser()
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Order ID parsed", mapOf("orderId" to orderId))

            // Plan gate: digital receipt
            planService.checkFeature(UUID.fromString(principal.vendorId), "DIGITAL_RECEIPT")
            trace.step("Feature check passed")

            transaction {
                OrdersTable.selectAll().where {
                    (OrdersTable.id eq UUID.fromString(orderId)) and (OrdersTable.vendorId eq UUID.fromString(
                        principal.vendorId
                    ))
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")
            }
            trace.step("Order verified")

            val expiresMs = 30 * 60 * 1000 // 30 minutes
            val token = generateReceiptToken(jwtConfig, orderId, principal.vendorId, expiresMs)
            val origin = call.request.origin
            val portPart = when (origin.serverPort) {
                80, 443 -> ""
                else -> ":${origin.serverPort}"
            }
            val url = "${origin.scheme}://${origin.serverHost}$portPart/public/receipts/$orderId?token=$token"
            trace.step("Receipt URL generated", mapOf("expiresMs" to expiresMs.toString()))
            trace.step("Share receipt completed")
            call.respond(
                ShareReceiptResponseDto(
                    url = url, token = token, expires_at = System.currentTimeMillis() + expiresMs
                )
            )
        }

        patch("/{id}/assign") {
            val trace = call.routeTrace()
            trace.step("Assign delivery started")
            val principal = requireRole("MANAGER", "CASHIER", "DELIVERY")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<AssignDeliveryDto>()
            trace.step("Request parsed", mapOf("orderId" to id))

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
            trace.step("Delivery user assigned", mapOf("orderId" to order.id, "deliveryUserId" to (order.delivery_user_id ?: "null")))
            trace.step("Assign delivery completed")
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
                    val restoreAmount = BigDecimal(orderItem[OrderItemsTable.quantity])
                    val currentQty = stockRow[StockTable.quantity]
                    val restoredQty = currentQty + restoreAmount
                    StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                        it[StockTable.quantity] = restoredQty
                        it[StockTable.updatedAt] = now
                    }
                    // FIFO batch restoration — restore to most recently depleted batches
                    val batchesRestored = fifoRestoreBatches(
                        stockUUID = stockRow[StockTable.id].value,
                        vendorUUID = vendorUUID,
                        amount = restoreAmount,
                        orderId = orderUUID,
                        note = "Stock restored (order cancel/edit)"
                    )
                    if (batchesRestored.isEmpty()) {
                        StockTransactionsTable.insert {
                            it[stockId] = stockRow[StockTable.id]
                            it[type] = "RETURN"
                            it[StockTransactionsTable.quantity] = restoreAmount
                            it[previousQuantity] = currentQty
                            it[StockTransactionsTable.orderId] = orderUUID
                            it[note] = "Stock restored (order cancel/edit)"
                            it[createdAt] = now
                        }
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
                        val restoreAmount = BigDecimal.valueOf(convertedRestore)
                        val currentQty = stockRow[StockTable.quantity]
                        val newQty = currentQty + restoreAmount
                        StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                            it[StockTable.quantity] = newQty
                            it[StockTable.updatedAt] = now
                        }
                        // FIFO batch restoration for recipe ingredients
                        val batchesRestored = fifoRestoreBatches(
                            stockUUID = stockRow[StockTable.id].value,
                            vendorUUID = vendorUUID,
                            amount = restoreAmount,
                            orderId = orderUUID,
                            note = "Stock restored (order cancel/edit)"
                        )
                        if (batchesRestored.isEmpty()) {
                            StockTransactionsTable.insert {
                                it[stockId] = stockRow[StockTable.id]
                                it[type] = "RETURN"
                                it[StockTransactionsTable.quantity] = restoreAmount
                                it[previousQuantity] = currentQty
                                it[StockTransactionsTable.orderId] = orderUUID
                                it[StockTransactionsTable.recipeId] = recipeId
                                it[note] = "Stock restored (order cancel/edit)"
                                it[createdAt] = now
                            }
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
): OrderDto {
    val orderUUID = this[OrdersTable.id]
    // Auto-calculate refund data from completed returns
    val completedReturns = ProductReturnsTable.selectAll().where {
        (ProductReturnsTable.orderId eq orderUUID) and
        (ProductReturnsTable.status eq "COMPLETED")
    }.toList()
    val refundedAmount = completedReturns.sumOf { it[ProductReturnsTable.refundAmount].toDouble() }
    val returnedItemCount = if (completedReturns.isNotEmpty()) {
        val returnIds = completedReturns.map { it[ProductReturnsTable.id] }
        ReturnItemsTable.selectAll().where {
            ReturnItemsTable.returnId inList returnIds
        }.sumOf { it[ReturnItemsTable.quantity] }
    } else 0

    return OrderDto(
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
    offer_id = this[OrdersTable.offerId]?.toString(),
    items = items,
    points_earned = this[OrdersTable.pointsEarned],
    points_redeemed = this[OrdersTable.pointsRedeemed],
    discount_reason = this[OrdersTable.discountReason],
    created_at = this[OrdersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[OrdersTable.updatedAt].toEpochMilliseconds(),
    refunded_at = this[OrdersTable.refundedAt]?.toEpochMilliseconds(),
    refunded_by = this[OrdersTable.refundedBy]?.toString(),
    refund_reason = this[OrdersTable.refundReason],
    refunded_amount = Math.round(refundedAmount * 100.0) / 100.0,
    returned_item_count = returnedItemCount,
    doctor_name = this[OrdersTable.doctorName],
    diagnosis = this[OrdersTable.diagnosis],
)
}

private fun ResultRow.toOrderItemDto() = OrderItemDto(
    id = this[OrderItemsTable.id].toString(),
    order_id = this[OrderItemsTable.orderId].toString(),
    item_id = this[OrderItemsTable.itemId].toString(),
    item_name_snapshot = this[OrderItemsTable.itemNameSnapshot],
    item_price_snapshot = this[OrderItemsTable.itemPriceSnapshot].toDouble(),
    quantity = this[OrderItemsTable.quantity],
    note = this[OrderItemsTable.note],
    variant_options_snapshot = this[OrderItemsTable.variantOptionsSnapshot]
)

// Public, token-guarded receipt view (no auth header required)
fun Route.orderSharePublicRoutes() {
    val jwtConfig by lazy { JwtConfig(this.application.environment.config) }

    route("/public/receipts") {
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Public receipt view started")
            val orderId = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val token = call.request.queryParameters["token"] ?: throw IllegalArgumentException("token required")
            trace.step("Receipt request parsed", mapOf("orderId" to orderId))

            val verifier = JWT.require(Algorithm.HMAC256(jwtConfig.secret)).withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience).withClaim("type", "receipt").build()

            val decoded = try {
                verifier.verify(token)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid or expired token")
            }

            if (decoded.subject != orderId) throw IllegalArgumentException("Token/order mismatch")
            trace.step("Token verified")
            val vendorIdFromToken = decoded.getClaim("vendor_id").asString()

            val host = call.request.header("Host") ?: "localhost:8080"
            val scheme = call.request.header("X-Forwarded-Proto") ?: call.request.origin.scheme

            val (order, vendorName, vendorAddress, vendorPhone, vendorLogoUrl) = transaction {
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
                    vendorPhone = vendor[VendorsTable.contactPhone],
                    vendorLogoUrl = rewriteUploadUrl(vendor[VendorsTable.logoUrl], host, scheme),
                )
            }

            val html = buildReceiptHtml(order, vendorName, vendorAddress, vendorPhone, vendorLogoUrl)
            trace.step("Receipt HTML generated", mapOf("orderId" to orderId))
            trace.step("Public receipt view completed")
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
    val vendorLogoUrl: String?,
)

private fun buildReceiptHtml(
    order: OrderDto, vendorName: String?, vendorAddress: String?, vendorPhone: String?, vendorLogoUrl: String? = null
): String {
    fun String.esc() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun fmt(amount: Double) = "%.2f".format(amount)

    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd  HH:mm").format(java.util.Date(order.created_at))
    val channelLabel = when (order.channel) {
        "DINE_IN" -> "Dine In"; "DELIVERY" -> "Delivery"; "TAKEAWAY" -> "Takeaway"
        "IN_STORE" -> "In-Store"; "PICKUP_LATER" -> "Pickup Later"; else -> order.channel
    }
    val paymentLabel = when (order.payment_method) {
        "CASH" -> "Cash"; "WALLET" -> "Wallet"; "CARD" -> "Card"; else -> order.payment_method
    }

    val itemsHtml = order.items.joinToString("") { item ->
        val lineTotal = item.item_price_snapshot * item.quantity
        """<tr>
            <td class="name">${item.item_name_snapshot.esc()}</td>
            <td class="qty">${item.quantity}</td>
            <td class="price">${fmt(lineTotal)} EGP</td>
        </tr>"""
    }

    val logoHtml = if (!vendorLogoUrl.isNullOrBlank()) {
        """<img src="${vendorLogoUrl.esc()}" style="width:60px;height:60px;border-radius:50%;object-fit:cover;margin-bottom:8px;" alt="Logo"><br>"""
    } else ""

    val clientHtml = buildString {
        if (order.client_name != null || order.client_phone != null || order.client_address != null) {
            append("""<div class="info-section">""")
            order.client_name?.let { append("""<div class="info-row"><span class="label">Client</span><span class="value">${it.esc()}</span></div>""") }
            order.client_phone?.let { append("""<div class="info-row"><span class="label">Phone</span><span class="value">${it.esc()}</span></div>""") }
            order.client_address?.let { append("""<div class="info-row"><span class="label">Address</span><span class="value">${it.esc()}</span></div>""") }
            append("</div>")
        }
    }

    val notesHtml = order.notes?.let {
        """<div class="notes"><div class="title">Notes</div><div class="text">${it.esc()}</div></div>"""
    } ?: ""

    val taxHtml = if (order.tax > 0) """<div class="info-row"><span class="label">Tax</span><span class="value">${fmt(order.tax)} EGP</span></div>""" else ""
    val deliveryFeeHtml = if (order.delivery_fee > 0) """<div class="info-row"><span class="label">Delivery Fee</span><span class="value">${fmt(order.delivery_fee)} EGP</span></div>""" else ""

    return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: 'Segoe UI', Arial, Helvetica, sans-serif;
    font-size: 13px;
    color: #1C1917;
    background: #f4f4f7;
    display: flex;
    justify-content: center;
    padding: 20px;
  }
  .receipt-card {
    background: #fff;
    max-width: 420px;
    width: 100%;
    padding: 28px;
    border-radius: 16px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  }
  .header { text-align: center; margin-bottom: 16px; }
  .header h1 { font-size: 18px; font-weight: bold; margin-bottom: 2px; }
  .header p { font-size: 12px; color: #78716C; }
  .divider { border: none; border-top: 1px dashed #D6D3D1; margin: 14px 0; }
  .info-section {
    background: #FAFAF9;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 10px;
  }
  .info-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 4px;
    font-size: 12px;
  }
  .info-row .label { color: #78716C; }
  .info-row .value { font-weight: 500; }
  .info-row .value.bold { font-weight: 700; }
  table { width: 100%; border-collapse: collapse; margin: 10px 0; }
  th { font-size: 11px; color: #78716C; font-weight: 500; text-align: left; padding: 6px 4px; border-bottom: 1px solid #E7E5E4; }
  th.qty, td.qty { text-align: center; width: 40px; }
  th.price, td.price { text-align: right; width: 80px; }
  td { font-size: 12px; padding: 6px 4px; vertical-align: middle; }
  td.name { font-weight: 500; }
  .totals { margin-top: 10px; }
  .totals .info-row { font-size: 13px; }
  .grand-total {
    background: #F0FDFA;
    border: 1px solid #CCFBF1;
    border-radius: 10px;
    padding: 12px 14px;
    margin-top: 10px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .grand-total .label { font-size: 16px; font-weight: 700; }
  .grand-total .value { font-size: 16px; font-weight: 700; color: #0D9488; }
  .notes { background: #FAFAF9; border-radius: 8px; padding: 10px 12px; margin-top: 12px; }
  .notes .title { font-size: 11px; color: #78716C; margin-bottom: 2px; }
  .notes .text { font-size: 12px; color: #44403C; }
  .footer { text-align: center; font-style: italic; color: #A8A29E; font-size: 12px; margin-top: 20px; padding-bottom: 8px; }
  @media print {
    body { padding: 0; background: #fff; }
    .receipt-card { box-shadow: none; max-width: 100%; }
    img { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  }
</style>
</head>
<body>
<div class="receipt-card">
  <div class="header">
    $logoHtml
    <h1>${(vendorName ?: "Restaurant").esc()}</h1>
    ${vendorAddress?.let { "<p>${it.esc()}</p>" } ?: ""}
    ${vendorPhone?.let { "<p>${it.esc()}</p>" } ?: ""}
  </div>
  <hr class="divider">
  <div class="info-section">
    <div class="info-row"><span class="label">Order #</span><span class="value bold">#${order.id.takeLast(8).uppercase()}</span></div>
    <div class="info-row"><span class="label">Date</span><span class="value">$dateStr</span></div>
    <div class="info-row"><span class="label">Channel</span><span class="value">$channelLabel</span></div>
    <div class="info-row"><span class="label">Payment</span><span class="value">$paymentLabel</span></div>
    ${order.cashier_name?.let { """<div class="info-row"><span class="label">Cashier</span><span class="value">${it.esc()}</span></div>""" } ?: ""}
  </div>
  $clientHtml
  <hr class="divider">
  <table>
    <thead><tr><th>Item</th><th class="qty">Qty</th><th class="price">Price</th></tr></thead>
    <tbody>$itemsHtml</tbody>
  </table>
  <hr class="divider">
  <div class="totals">
    <div class="info-row"><span class="label">Subtotal</span><span class="value">${fmt(order.subtotal)} EGP</span></div>
    $taxHtml
    $deliveryFeeHtml
  </div>
  <div class="grand-total">
    <span class="label">Total</span>
    <span class="value">${fmt(order.total)} EGP</span>
  </div>
  $notesHtml
  <div class="footer">Thank you for your visit!</div>
</div>
</body>
</html>"""
}
