package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.OrderItemsTable
import net.marllex.cafeemanger.backend.data.database.OrdersTable
import net.marllex.cafeemanger.backend.data.database.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class AnalyticsSummaryDto(
    val total_orders: Int,
    val total_revenue: Double,
    val average_order_value: Double,
    val orders_by_channel: Map<String, Int>,
    val orders_by_status: Map<String, Int>,
    val orders_by_payment_method: Map<String, Int>,
    val revenue_by_payment_method: Map<String, Double>,
    val top_items: List<TopItemDto>
)

@Serializable
data class TopItemDto(
    val item_name: String,
    val total_quantity: Int,
    val total_revenue: Double
)

@Serializable
data class DailyAnalyticsDto(
    val date: String,
    val total_orders: Int,
    val total_revenue: Double
)

@Serializable
data class DeliveryPerformanceDto(
    val delivery_user_id: String,
    val delivery_user_name: String,
    val order_count: Int,
    val total_revenue: Double,
    val total_tax: Double // NOTE: This field represents delivery fees, not actual tax
)

@Serializable
data class SettlementByPaymentMethodDto(
    val order_count: Int,
    val total_revenue: Double,
    val total_tax: Double, // NOTE: This field represents delivery fees, not actual tax
    val total_subtotal: Double
)

@Serializable
data class SettlementsDto(
    val by_payment_method: Map<String, SettlementByPaymentMethodDto>
)

private fun buildOrderQuery(
    vendorUUID: UUID,
    status: String?,
    channel: String?,
    cashierId: java.util.UUID?,
    deliveryUserId: java.util.UUID?,
    from: Long?,
    to: Long?
): Query {
    var query = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }
    status?.let { query = query.andWhere { OrdersTable.status eq it } }
    channel?.let { query = query.andWhere { OrdersTable.channel eq it } }
    cashierId?.let { query = query.andWhere { OrdersTable.cashierId eq it } }
    deliveryUserId?.let { query = query.andWhere { OrdersTable.deliveryUserId eq it } }
    from?.let { ts ->
        val instant = Instant.fromEpochMilliseconds(ts)
        query = query.andWhere { OrdersTable.createdAt greaterEq instant }
    }
    to?.let { ts ->
        val instant = Instant.fromEpochMilliseconds(ts)
        query = query.andWhere { OrdersTable.createdAt lessEq instant }
    }
    return query
}

private fun buildSummaryFromOrders(orders: List<ResultRow>, vendorUUID: UUID): AnalyticsSummaryDto {
    val totalOrders = orders.size
    val totalRevenue = orders.sumOf { it[OrdersTable.total].toDouble() }
    val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

    val ordersByChannel = orders.groupBy { it[OrdersTable.channel] }.mapValues { it.value.size }
    val ordersByStatus = orders.groupBy { it[OrdersTable.status] }.mapValues { it.value.size }
    val ordersByPaymentMethod = orders.groupBy { it[OrdersTable.paymentMethod] }.mapValues { it.value.size }
    val revenueByPaymentMethod = orders
        .groupBy { it[OrdersTable.paymentMethod] }
        .mapValues { rows -> rows.value.sumOf { it[OrdersTable.total].toDouble() } }

    val orderIds = orders.map { it[OrdersTable.id] }
    val topItems = if (orderIds.isEmpty()) emptyList() else {
        val orderItemRows = OrderItemsTable
            .select(
                OrderItemsTable.itemNameSnapshot,
                OrderItemsTable.quantity,
                OrderItemsTable.itemPriceSnapshot
            )
            .where { OrderItemsTable.orderId inList orderIds }
            .toList()
        orderItemRows
            .groupBy { it[OrderItemsTable.itemNameSnapshot] }
            .map { (name, rows) ->
                TopItemDto(
                    item_name = name,
                    total_quantity = rows.sumOf { it[OrderItemsTable.quantity] },
                    total_revenue = rows.sumOf {
                        it[OrderItemsTable.itemPriceSnapshot].toDouble() * it[OrderItemsTable.quantity]
                    }
                )
            }
            .sortedByDescending { it.total_quantity }
            .take(10)
    }

    return AnalyticsSummaryDto(
        total_orders = totalOrders,
        total_revenue = totalRevenue,
        average_order_value = averageOrderValue,
        orders_by_channel = ordersByChannel,
        orders_by_status = ordersByStatus,
        orders_by_payment_method = ordersByPaymentMethod,
        revenue_by_payment_method = revenueByPaymentMethod,
        top_items = topItems
    )
}

fun Route.analyticsRoutes() {
    route("/api/v1/analytics") {
        get("/summary") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val summary = transaction {
                val orders = buildOrderQuery(vendorUUID, null, null, null, null, from, to).toList()
                buildSummaryFromOrders(orders, vendorUUID)
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        get("/filtered-summary") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val summary = transaction {
                val orders = buildOrderQuery(vendorUUID, status, channel, cashierId, deliveryUserId, from, to).toList()
                buildSummaryFromOrders(orders, vendorUUID)
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        get("/settlements") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val settlements = transaction {
                val orders = buildOrderQuery(vendorUUID, status, channel, cashierId, deliveryUserId, from, to).toList()
                val byPayment = orders
                    .groupBy { it[OrdersTable.paymentMethod] }
                    .mapValues { (_, rows) ->
                        SettlementByPaymentMethodDto(
                            order_count = rows.size,
                            total_revenue = rows.sumOf { it[OrdersTable.total].toDouble() },
                            total_tax = rows.sumOf { it[OrdersTable.tax].toDouble() },
                            total_subtotal = rows.sumOf { it[OrdersTable.subtotal].toDouble() }
                        )
                    }
                SettlementsDto(by_payment_method = byPayment)
            }
            call.respond(HttpStatusCode.OK, settlements)
        }

        get("/delivery-performance") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val performance = transaction {
                // Fetch all delivery users for this vendor so filters never appear empty
                val deliveryUsers = UsersTable
                    .selectAll()
                    .where {
                        (UsersTable.vendorId eq vendorUUID) and
                                (UsersTable.role eq "DELIVERY")
                    }
                    .associateBy { it[UsersTable.id] }

                val orders = buildOrderQuery(vendorUUID, status, "DELIVERY", cashierId, null, from, to)
                    .andWhere { OrdersTable.deliveryUserId.isNotNull() }
                    .toList()

                val groupedByDelivery = orders.groupBy { it[OrdersTable.deliveryUserId]!! }

                // Build performance list including users with zero orders
                deliveryUsers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByDelivery[userId].orEmpty()
                    DeliveryPerformanceDto(
                        delivery_user_id = userId.toString(),
                        delivery_user_name = userRow[UsersTable.name],
                        order_count = orderRows.size,
                        total_revenue = orderRows.sumOf { it[OrdersTable.total].toDouble() },
                        total_tax = orderRows.sumOf { it[OrdersTable.tax].toDouble() }
                    )
                }
            }
            call.respond(HttpStatusCode.OK, performance)
        }

        get("/cashier-performance") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val performance = transaction {
                // Fetch all cashiers for this vendor so filters never appear empty
                val cashiers = UsersTable
                    .selectAll()
                    .where {
                        (UsersTable.vendorId eq vendorUUID) and
                                (UsersTable.role eq "CASHIER")
                    }
                    .associateBy { it[UsersTable.id] }

                val orders = buildOrderQuery(vendorUUID, status, channel, null, deliveryUserId, from, to).toList()
                val groupedByCashier = orders.groupBy { it[OrdersTable.cashierId] }

                cashiers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByCashier[userId].orEmpty()
                    DeliveryPerformanceDto(
                        delivery_user_id = userId.toString(),
                        delivery_user_name = userRow[UsersTable.name],
                        order_count = orderRows.size,
                        total_revenue = orderRows.sumOf { it[OrdersTable.total].toDouble() },
                        total_tax = orderRows.sumOf { it[OrdersTable.tax].toDouble() }
                    )
                }
            }
            call.respond(HttpStatusCode.OK, performance)
        }

        get("/daily") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val days = call.parameters["days"]?.toIntOrNull() ?: 30

            val dailyData = transaction {
                OrdersTable
                    .select(
                        OrdersTable.createdAt.date(),
                        OrdersTable.id.count(),
                        OrdersTable.total.sum()
                    ).where { OrdersTable.vendorId eq vendorUUID }
                    .groupBy(OrdersTable.createdAt.date())
                    .orderBy(OrdersTable.createdAt.date(), SortOrder.DESC)
                    .limit(days)
                    .map {
                        DailyAnalyticsDto(
                            date = it[OrdersTable.createdAt.date()].toString(),
                            total_orders = it[OrdersTable.id.count()].toInt(),
                            total_revenue = it[OrdersTable.total.sum()]?.toDouble() ?: 0.0
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, dailyData)
        }

        get("/orders/count") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]

            val count = transaction {
                var query = OrdersTable.selectAll()
                    .where { OrdersTable.vendorId eq vendorUUID }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }
                channel?.let { query = query.andWhere { OrdersTable.channel eq it } }

                query.count().toInt()
            }
            call.respond(HttpStatusCode.OK, mapOf("count" to count))
        }

        get("/revenue") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val revenue = transaction {
                val completedOrders = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                                (OrdersTable.status eq "COMPLETED")
                    }

                val totalRevenue = completedOrders
                    .sumOf { it[OrdersTable.total].toDouble() }

                val todayStr = kotlinx.datetime.Clock.System.now().toString().substring(0, 10)
                val todayRevenue = completedOrders
                    .filter { it[OrdersTable.createdAt].toString().startsWith(todayStr) }
                    .sumOf { it[OrdersTable.total].toDouble() }

                mapOf(
                    "total_revenue" to totalRevenue,
                    "today_revenue" to todayRevenue,
                    "completed_orders" to completedOrders.count().toInt()
                )
            }
            call.respond(HttpStatusCode.OK, revenue)
        }
    }
}
