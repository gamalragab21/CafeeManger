package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.OrderItemsTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
import net.marllex.waselak.backend.data.database.OrdersTable
import net.marllex.waselak.backend.data.database.UsersTable
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
    val total_delivery_fees: Double
)

@Serializable
data class SettlementByPaymentMethodDto(
    val order_count: Int,
    val total_revenue: Double,
    val total_delivery_fees: Double,
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
    val excludedStatuses = setOf("CANCELED", "REFUNDED")
    val revenueOrders = orders.filter { it[OrdersTable.status] !in excludedStatuses }
    val totalRevenue = revenueOrders.sumOf { it[OrdersTable.total].toDouble() }
    val averageOrderValue = if (revenueOrders.isNotEmpty()) totalRevenue / revenueOrders.size else 0.0

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
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)
    route("/api/v1/analytics") {
        get("/summary") {
            val trace = call.routeTrace()
            trace.step("Analytics summary started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            trace.step("Date range parsed", mapOf("from" to (from?.toString() ?: "null"), "to" to (to?.toString() ?: "null")))

            val summary = transaction {
                val orders = buildOrderQuery(vendorUUID, null, null, null, null, from, to).toList()
                trace.step("Orders fetched", mapOf("orderCount" to orders.size.toString()))
                buildSummaryFromOrders(orders, vendorUUID)
            }
            trace.step("Summary built", mapOf("totalOrders" to summary.total_orders.toString(), "totalRevenue" to summary.total_revenue.toString()))
            trace.step("Analytics summary completed")
            call.respond(HttpStatusCode.OK, summary)
        }

        get("/filtered-summary") {
            val trace = call.routeTrace()
            trace.step("Filtered analytics summary started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            trace.step("Filters parsed", mapOf(
                "status" to (status ?: "null"),
                "channel" to (channel ?: "null"),
                "cashierId" to (cashierId?.toString() ?: "null"),
                "deliveryUserId" to (deliveryUserId?.toString() ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null")
            ))

            val summary = transaction {
                val orders = buildOrderQuery(vendorUUID, status, channel, cashierId, deliveryUserId, from, to).toList()
                trace.step("Filtered orders fetched", mapOf("orderCount" to orders.size.toString()))
                buildSummaryFromOrders(orders, vendorUUID)
            }
            trace.step("Filtered summary built", mapOf("totalOrders" to summary.total_orders.toString(), "totalRevenue" to summary.total_revenue.toString()))
            trace.step("Filtered analytics summary completed")
            call.respond(HttpStatusCode.OK, summary)
        }

        get("/settlements") {
            val trace = call.routeTrace()
            trace.step("Settlements fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            trace.step("Filters parsed", mapOf(
                "status" to (status ?: "null"),
                "channel" to (channel ?: "null"),
                "cashierId" to (cashierId?.toString() ?: "null"),
                "deliveryUserId" to (deliveryUserId?.toString() ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null")
            ))

            val settlements = transaction {
                val orders = buildOrderQuery(vendorUUID, status, channel, cashierId, deliveryUserId, from, to).toList()
                trace.step("Orders fetched for settlements", mapOf("orderCount" to orders.size.toString()))
                val byPayment = orders
                    .groupBy { it[OrdersTable.paymentMethod] }
                    .mapValues { (_, rows) ->
                        SettlementByPaymentMethodDto(
                            order_count = rows.size,
                            total_revenue = rows.sumOf { it[OrdersTable.total].toDouble() },
                            total_delivery_fees = rows.sumOf { it[OrdersTable.deliveryFee].toDouble() + it[OrdersTable.tax].toDouble() },
                            total_subtotal = rows.sumOf { it[OrdersTable.subtotal].toDouble() }
                        )
                    }
                trace.step("Settlements grouped by payment method", mapOf("paymentMethodCount" to byPayment.size.toString()))
                SettlementsDto(by_payment_method = byPayment)
            }
            trace.step("Settlements fetch completed")
            call.respond(HttpStatusCode.OK, settlements)
        }

        get("/delivery-performance") {
            val trace = call.routeTrace()
            trace.step("Delivery performance fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val status = call.parameters["status"]
            val cashierId = call.parameters["cashier_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            trace.step("Filters parsed", mapOf(
                "status" to (status ?: "null"),
                "cashierId" to (cashierId?.toString() ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null")
            ))

            val performance = transaction {
                // Fetch all delivery users for this vendor so filters never appear empty
                val deliveryUsers = UsersTable
                    .selectAll()
                    .where {
                        (UsersTable.vendorId eq vendorUUID) and
                                (UsersTable.role eq "DELIVERY")
                    }
                    .associateBy { it[UsersTable.id] }
                trace.step("Delivery users fetched", mapOf("deliveryUserCount" to deliveryUsers.size.toString()))

                val orders = buildOrderQuery(vendorUUID, status, "DELIVERY", cashierId, null, from, to)
                    .andWhere { OrdersTable.deliveryUserId.isNotNull() }
                    .toList()
                trace.step("Delivery orders fetched", mapOf("orderCount" to orders.size.toString()))

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
                        total_delivery_fees = orderRows.sumOf { it[OrdersTable.deliveryFee].toDouble() + it[OrdersTable.tax].toDouble() }
                    )
                }
            }
            trace.step("Delivery performance result", mapOf("driverCount" to performance.size.toString()))
            trace.step("Delivery performance fetch completed")
            call.respond(HttpStatusCode.OK, performance)
        }

        get("/cashier-performance") {
            val trace = call.routeTrace()
            trace.step("Cashier performance fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            val deliveryUserId = call.parameters["delivery_user_id"]?.let { java.util.UUID.fromString(it) }
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            trace.step("Filters parsed", mapOf(
                "status" to (status ?: "null"),
                "channel" to (channel ?: "null"),
                "deliveryUserId" to (deliveryUserId?.toString() ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null")
            ))

            val performance = transaction {
                // Fetch all cashiers for this vendor so filters never appear empty
                val cashiers = UsersTable
                    .selectAll()
                    .where {
                        (UsersTable.vendorId eq vendorUUID) and
                                (UsersTable.role eq "CASHIER")
                    }
                    .associateBy { it[UsersTable.id] }
                trace.step("Cashiers fetched", mapOf("cashierCount" to cashiers.size.toString()))

                val orders = buildOrderQuery(vendorUUID, status, channel, null, deliveryUserId, from, to).toList()
                trace.step("Orders fetched for cashier performance", mapOf("orderCount" to orders.size.toString()))
                val groupedByCashier = orders.groupBy { it[OrdersTable.cashierId] }

                cashiers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByCashier[userId].orEmpty()
                    DeliveryPerformanceDto(
                        delivery_user_id = userId.toString(),
                        delivery_user_name = userRow[UsersTable.name],
                        order_count = orderRows.size,
                        total_revenue = orderRows.sumOf { it[OrdersTable.total].toDouble() },
                        total_delivery_fees = orderRows.sumOf { it[OrdersTable.deliveryFee].toDouble() + it[OrdersTable.tax].toDouble() }
                    )
                }
            }
            trace.step("Cashier performance result", mapOf("cashierCount" to performance.size.toString()))
            trace.step("Cashier performance fetch completed")
            call.respond(HttpStatusCode.OK, performance)
        }

        get("/daily") {
            val trace = call.routeTrace()
            trace.step("Daily analytics fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val days = call.parameters["days"]?.toIntOrNull() ?: 30
            trace.step("Period parsed", mapOf("days" to days.toString()))

            val dailyData = transaction {
                val result = OrdersTable
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
                trace.step("Daily data fetched", mapOf("dayCount" to result.size.toString()))
                result
            }
            trace.step("Daily analytics fetch completed")
            call.respond(HttpStatusCode.OK, dailyData)
        }

        get("/orders/count") {
            val trace = call.routeTrace()
            trace.step("Order count fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")
            val status = call.parameters["status"]
            val channel = call.parameters["channel"]
            trace.step("Filters parsed", mapOf(
                "status" to (status ?: "null"),
                "channel" to (channel ?: "null")
            ))

            val count = transaction {
                var query = OrdersTable.selectAll()
                    .where { OrdersTable.vendorId eq vendorUUID }

                status?.let { query = query.andWhere { OrdersTable.status eq it } }
                channel?.let { query = query.andWhere { OrdersTable.channel eq it } }

                val result = query.count().toInt()
                trace.step("Order count result", mapOf("count" to result.toString()))
                result
            }
            trace.step("Order count fetch completed")
            call.respond(HttpStatusCode.OK, mapOf("count" to count))
        }

        get("/revenue") {
            val trace = call.routeTrace()
            trace.step("Revenue stats fetch started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            planService.checkFeature(vendorUUID, "ANALYTICS")

            val revenue = transaction {
                val completedOrders = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                                (OrdersTable.status eq "COMPLETED")
                    }
                trace.step("Completed orders fetched")

                val totalRevenue = completedOrders
                    .sumOf { it[OrdersTable.total].toDouble() }

                val todayStr = kotlinx.datetime.Clock.System.now().toString().substring(0, 10)
                val todayRevenue = completedOrders
                    .filter { it[OrdersTable.createdAt].toString().startsWith(todayStr) }
                    .sumOf { it[OrdersTable.total].toDouble() }

                trace.step("Revenue calculated", mapOf(
                    "totalRevenue" to totalRevenue.toString(),
                    "todayRevenue" to todayRevenue.toString(),
                    "todayDate" to todayStr
                ))

                mapOf(
                    "total_revenue" to totalRevenue,
                    "today_revenue" to todayRevenue,
                    "completed_orders" to completedOrders.count().toInt()
                )
            }
            trace.step("Revenue stats fetch completed")
            call.respond(HttpStatusCode.OK, revenue)
        }

        // Doctor statistics (pharmacy)
        get("/doctors") {
            val trace = call.routeTrace()
            trace.step("Doctor stats started")
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val stats = transaction {
                var query = OrdersTable
                    .innerJoin(OrderItemsTable)
                    .selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                        (OrdersTable.doctorName.isNotNull()) and
                        (OrdersTable.doctorName neq "")
                    }
                from?.let { f ->
                    query = query.andWhere { OrdersTable.createdAt greaterEq Instant.fromEpochMilliseconds(f) }
                }
                to?.let { t ->
                    query = query.andWhere { OrdersTable.createdAt lessEq Instant.fromEpochMilliseconds(t) }
                }

                val rows = query.toList()
                val grouped = rows.groupBy { it[OrdersTable.doctorName]!! }

                grouped.map { (doctorName, doctorRows) ->
                    val orderIds = doctorRows.map { it[OrdersTable.id] }.distinct()
                    val totalItems = doctorRows.sumOf { it[OrderItemsTable.quantity] }
                    val totalRevenue = orderIds.map { oid ->
                        doctorRows.first { it[OrdersTable.id] == oid }[OrdersTable.total].toDouble()
                    }.distinct().sum()

                    DoctorStatsDto(
                        doctor_name = doctorName,
                        prescription_count = orderIds.size,
                        total_items = totalItems,
                        total_revenue = totalRevenue,
                    )
                }.sortedByDescending { it.total_revenue }
            }
            trace.step("Doctor stats completed", mapOf("count" to stats.size.toString()))
            call.respond(HttpStatusCode.OK, stats)
        }
    }
}

@Serializable
data class DoctorStatsDto(
    val doctor_name: String,
    val prescription_count: Int,
    val total_items: Int,
    val total_revenue: Double,
)
