package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════
// DTOs — Analytics Dashboard V2
// ══════════════════════════════════════════════════════════════════════

// ── Executive Summary ────────────────────────────────────────────────
@Serializable
data class PeriodMetricsDto(
    val total_revenue: Double,
    val total_orders: Int,
    val average_order_value: Double,
    val total_tax: Double,
    val total_delivery_fees: Double,
    val total_discounts: Double,
)

@Serializable
data class ExecutiveSummaryDto(
    val current: PeriodMetricsDto,
    val previous: PeriodMetricsDto,
    val revenue_change_percent: Double,
    val orders_change_percent: Double,
    val aov_change_percent: Double,
    val active_orders: Int,
    val attendance_today: Int,
)

// ── Revenue & Profit ─────────────────────────────────────────────────
@Serializable
data class PaymentMethodDetailDto(
    val method: String,
    val order_count: Int,
    val revenue: Double,
)

@Serializable
data class DailyRevenuePointDto(
    val date: String,
    val revenue: Double,
)

@Serializable
data class RevenueProfitDto(
    val gross_revenue: Double,
    val total_tax: Double,
    val total_delivery_fees: Double,
    val net_revenue: Double,
    val payment_methods: List<PaymentMethodDetailDto>,
    val daily_trend: List<DailyRevenuePointDto>,
)

// ── Orders Intelligence ──────────────────────────────────────────────
@Serializable
data class DailyOrderTrendPointDto(
    val date: String,
    val total: Int,
    val completed: Int,
    val cancelled: Int,
)

@Serializable
data class ChannelBreakdownDto(
    val channel: String,
    val count: Int,
    val percent: Double,
)

@Serializable
data class OrdersIntelligenceDto(
    val total_orders: Int,
    val completed_orders: Int,
    val cancelled_orders: Int,
    val refunded_orders: Int,
    val orders_by_channel: Map<String, Int>,
    val daily_trend: List<DailyOrderTrendPointDto>,
    val channel_breakdown: List<ChannelBreakdownDto>,
)

// ── Peak Time Analysis ───────────────────────────────────────────────
@Serializable
data class HourlyDataDto(
    val hour: Int,
    val order_count: Int,
    val revenue: Double,
)

@Serializable
data class HeatmapPointDto(
    val day_of_week: Int, // 1=Mon..7=Sun
    val hour: Int,
    val order_count: Int,
)

@Serializable
data class DayOfWeekDto(
    val day_of_week: Int,
    val name: String,
    val order_count: Int,
    val revenue: Double,
)

@Serializable
data class PeakTimeAnalysisDto(
    val busiest_hour: Int,
    val busiest_day: String,
    val hourly_data: List<HourlyDataDto>,
    val heatmap: List<HeatmapPointDto>,
    val day_of_week: List<DayOfWeekDto>,
)

// ── Cashier Performance V2 ──────────────────────────────────────────
@Serializable
data class CashierPerformanceV2Dto(
    val cashier_id: String,
    val cashier_name: String,
    val revenue: Double,
    val order_count: Int,
    val average_order_value: Double,
    val cancelled_orders: Int,
    val cancellation_rate: Double,
)

// ── Delivery Performance V2 ─────────────────────────────────────────
@Serializable
data class DeliveryPerformanceV2Dto(
    val driver_id: String,
    val driver_name: String,
    val orders_completed: Int,
    val fees_collected: Double,
    val revenue: Double,
    val avg_delivery_time_minutes: Double,
    val late_delivery_percent: Double,
)

// ── Product Intelligence ────────────────────────────────────────────
@Serializable
data class ProductItemDto(
    val item_id: String,
    val item_name: String,
    val category_name: String,
    val quantity_sold: Int,
    val revenue: Double,
    val cost_price: Double,
    val profit_margin: Double,
)

@Serializable
data class CategoryRevenueDto(
    val category_id: String,
    val category_name: String,
    val revenue: Double,
    val item_count: Int,
)

@Serializable
data class ProductIntelligenceDto(
    val top_selling: List<ProductItemDto>,
    val most_profitable: List<ProductItemDto>,
    val least_selling: List<ProductItemDto>,
    val revenue_by_category: List<CategoryRevenueDto>,
    val low_margin_warnings: List<ProductItemDto>,
)

// ── Customer Intelligence ───────────────────────────────────────────
@Serializable
data class TopCustomerDto(
    val customer_id: String,
    val customer_name: String,
    val phone: String,
    val order_count: Int,
    val total_spent: Double,
)

@Serializable
data class CustomerIntelligenceDto(
    val total_customers: Int,
    val new_customers_percent: Double,
    val returning_customers_percent: Double,
    val average_spend: Double,
    val lifetime_value: Double,
    val top_customers: List<TopCustomerDto>,
    val frequency_buckets: Map<String, Int>,
)

// ── Alerts ──────────────────────────────────────────────────────────
@Serializable
data class AlertDto(
    val type: String,
    val severity: String,
    val title: String,
    val message: String,
    val value: Double,
    val threshold: Double,
)

@Serializable
data class AlertsDto(
    val alerts: List<AlertDto>,
)

// ── Stock Overview ──────────────────────────────────────────────────
@Serializable
data class StockOverviewItemDto(
    val stock_id: String,
    val item_name: String,
    val quantity: Int,
    val min_quantity: Int,
    val cost_price: Double,
    val unit: String,
    val status: String,
)

@Serializable
data class StockMovementDto(
    val date: String,
    val added: Int,
    val deducted: Int,
)

@Serializable
data class StockOverviewDto(
    val total_stock_value: Double,
    val total_selling_value: Double,
    val potential_profit: Double,
    val total_items: Int,
    val low_stock_items: List<StockOverviewItemDto>,
    val out_of_stock_items: List<StockOverviewItemDto>,
    val dead_stock_items: List<StockOverviewItemDto>,
    val movement_summary: List<StockMovementDto>,
)

// ══════════════════════════════════════════════════════════════════════
// Helper: Parse epoch millis from query param
// ══════════════════════════════════════════════════════════════════════

private fun parseDateRange(call: RoutingCall): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val from = call.parameters["from"]?.toLongOrNull()
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?: (now - 30.days())
    val to = call.parameters["to"]?.toLongOrNull()
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?: now
    return from to to
}

private fun Int.days(): kotlin.time.Duration = kotlin.time.Duration.parse("${this}d")

private val DAY_NAMES = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// ══════════════════════════════════════════════════════════════════════
// Routes
// ══════════════════════════════════════════════════════════════════════

fun Route.analyticsDashboardRoutes() {
    route("/api/v1/analytics/dashboard") {

        // ── 1. Executive Summary ─────────────────────────────────────
        get("/executive-summary") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)
            val periodMs = to.toEpochMilliseconds() - from.toEpochMilliseconds()
            val prevFrom = Instant.fromEpochMilliseconds(from.toEpochMilliseconds() - periodMs)
            val prevTo = from

            val result = transaction {
                fun metricsForPeriod(start: Instant, end: Instant): PeriodMetricsDto {
                    val orders = OrdersTable.selectAll().where {
                        (OrdersTable.vendorId eq vendorUUID) and
                                (OrdersTable.createdAt greaterEq start) and
                                (OrdersTable.createdAt lessEq end)
                    }.toList()
                    val totalRevenue = orders.sumOf { it[OrdersTable.total].toDouble() }
                    val totalOrders = orders.size
                    val aov = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
                    val totalTax = orders.sumOf { it[OrdersTable.tax].toDouble() }
                    val totalDeliveryFees = orders.sumOf { it[OrdersTable.deliveryFee].toDouble() }
                    return PeriodMetricsDto(
                        total_revenue = totalRevenue,
                        total_orders = totalOrders,
                        average_order_value = aov,
                        total_tax = totalTax,
                        total_delivery_fees = totalDeliveryFees,
                        total_discounts = 0.0,
                    )
                }

                val current = metricsForPeriod(from, to)
                val previous = metricsForPeriod(prevFrom, prevTo)

                fun pctChange(cur: Double, prev: Double): Double =
                    if (prev == 0.0) if (cur > 0) 100.0 else 0.0
                    else ((cur - prev) / prev) * 100.0

                val activeOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.status notInList listOf("COMPLETED", "CANCELED", "CANCELLED"))
                }.count().toInt()

                val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                val attendanceToday = AttendanceTable.selectAll().where {
                    (AttendanceTable.vendorId eq vendorUUID) and
                            (AttendanceTable.date eq todayStr)
                }.count().toInt()

                ExecutiveSummaryDto(
                    current = current,
                    previous = previous,
                    revenue_change_percent = pctChange(current.total_revenue, previous.total_revenue),
                    orders_change_percent = pctChange(current.total_orders.toDouble(), previous.total_orders.toDouble()),
                    aov_change_percent = pctChange(current.average_order_value, previous.average_order_value),
                    active_orders = activeOrders,
                    attendance_today = attendanceToday,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 2. Revenue & Profit ──────────────────────────────────────
        get("/revenue-profit") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()

                val gross = orders.sumOf { it[OrdersTable.total].toDouble() }
                val tax = orders.sumOf { it[OrdersTable.tax].toDouble() }
                val deliveryFees = orders.sumOf { it[OrdersTable.deliveryFee].toDouble() }
                val net = orders.sumOf { it[OrdersTable.subtotal].toDouble() }

                val paymentMethods = orders
                    .groupBy { it[OrdersTable.paymentMethod] }
                    .map { (method, rows) ->
                        PaymentMethodDetailDto(
                            method = method,
                            order_count = rows.size,
                            revenue = rows.sumOf { it[OrdersTable.total].toDouble() },
                        )
                    }

                val tz = TimeZone.currentSystemDefault()
                val dailyTrend = orders
                    .groupBy { it[OrdersTable.createdAt].toLocalDateTime(tz).date.toString() }
                    .map { (date, rows) ->
                        DailyRevenuePointDto(
                            date = date,
                            revenue = rows.sumOf { it[OrdersTable.total].toDouble() },
                        )
                    }
                    .sortedBy { it.date }

                RevenueProfitDto(
                    gross_revenue = gross,
                    total_tax = tax,
                    total_delivery_fees = deliveryFees,
                    net_revenue = net,
                    payment_methods = paymentMethods,
                    daily_trend = dailyTrend,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 3. Orders Intelligence ───────────────────────────────────
        get("/orders-intelligence") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()

                val total = orders.size
                val completed = orders.count { it[OrdersTable.status] == "COMPLETED" }
                val cancelled = orders.count { it[OrdersTable.status] in listOf("CANCELED", "CANCELLED") }
                val refunded = 0 // No REFUNDED status in DB

                val ordersByChannel = orders.groupBy { it[OrdersTable.channel] }.mapValues { it.value.size }

                val tz = TimeZone.currentSystemDefault()
                val dailyTrend = orders
                    .groupBy { it[OrdersTable.createdAt].toLocalDateTime(tz).date.toString() }
                    .map { (date, rows) ->
                        DailyOrderTrendPointDto(
                            date = date,
                            total = rows.size,
                            completed = rows.count { it[OrdersTable.status] == "COMPLETED" },
                            cancelled = rows.count { it[OrdersTable.status] in listOf("CANCELED", "CANCELLED") },
                        )
                    }
                    .sortedBy { it.date }

                val channelBreakdown = ordersByChannel.map { (channel, count) ->
                    ChannelBreakdownDto(
                        channel = channel,
                        count = count,
                        percent = if (total > 0) (count.toDouble() / total) * 100.0 else 0.0,
                    )
                }

                OrdersIntelligenceDto(
                    total_orders = total,
                    completed_orders = completed,
                    cancelled_orders = cancelled,
                    refunded_orders = refunded,
                    orders_by_channel = ordersByChannel,
                    daily_trend = dailyTrend,
                    channel_breakdown = channelBreakdown,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 4. Peak Time Analysis ────────────────────────────────────
        get("/peak-times") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()

                val tz = TimeZone.currentSystemDefault()

                data class OrderTimeInfo(val hour: Int, val dayOfWeek: Int, val revenue: Double)

                val timeData = orders.map { row ->
                    val ldt = row[OrdersTable.createdAt].toLocalDateTime(tz)
                    OrderTimeInfo(
                        hour = ldt.hour,
                        dayOfWeek = ldt.dayOfWeek.isoDayNumber,
                        revenue = row[OrdersTable.total].toDouble(),
                    )
                }

                // Hourly
                val hourlyMap = (0..23).associateWith { h ->
                    val hourOrders = timeData.filter { it.hour == h }
                    HourlyDataDto(hour = h, order_count = hourOrders.size, revenue = hourOrders.sumOf { it.revenue })
                }
                val busiestHour = hourlyMap.maxByOrNull { it.value.order_count }?.key ?: 0

                // Heatmap
                val heatmap = mutableListOf<HeatmapPointDto>()
                for (day in 1..7) {
                    for (hour in 0..23) {
                        val count = timeData.count { it.dayOfWeek == day && it.hour == hour }
                        heatmap.add(HeatmapPointDto(day_of_week = day, hour = hour, order_count = count))
                    }
                }

                // Day of week
                val dayOfWeekData = (1..7).map { day ->
                    val dayOrders = timeData.filter { it.dayOfWeek == day }
                    DayOfWeekDto(
                        day_of_week = day,
                        name = DAY_NAMES[day],
                        order_count = dayOrders.size,
                        revenue = dayOrders.sumOf { it.revenue },
                    )
                }
                val busiestDay = dayOfWeekData.maxByOrNull { it.order_count }?.name ?: "Mon"

                PeakTimeAnalysisDto(
                    busiest_hour = busiestHour,
                    busiest_day = busiestDay,
                    hourly_data = hourlyMap.values.toList(),
                    heatmap = heatmap,
                    day_of_week = dayOfWeekData,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 5. Cashier Performance V2 ────────────────────────────────
        get("/cashier-performance-v2") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val cashiers = UsersTable.selectAll().where {
                    (UsersTable.vendorId eq vendorUUID) and (UsersTable.role eq "CASHIER")
                }.associateBy { it[UsersTable.id] }

                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()

                val groupedByCashier = orders.groupBy { it[OrdersTable.cashierId] }

                cashiers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByCashier[userId].orEmpty()
                    val totalRevenue = orderRows.sumOf { it[OrdersTable.total].toDouble() }
                    val totalOrders = orderRows.size
                    val aov = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
                    val cancelledOrders = orderRows.count { it[OrdersTable.status] in listOf("CANCELED", "CANCELLED") }
                    val cancelRate = if (totalOrders > 0) (cancelledOrders.toDouble() / totalOrders) * 100.0 else 0.0

                    CashierPerformanceV2Dto(
                        cashier_id = userId.toString(),
                        cashier_name = userRow[UsersTable.name],
                        revenue = totalRevenue,
                        order_count = totalOrders,
                        average_order_value = aov,
                        cancelled_orders = cancelledOrders,
                        cancellation_rate = cancelRate,
                    )
                }
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 6. Delivery Performance V2 ───────────────────────────────
        get("/delivery-performance-v2") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val drivers = UsersTable.selectAll().where {
                    (UsersTable.vendorId eq vendorUUID) and (UsersTable.role eq "DELIVERY")
                }.associateBy { it[UsersTable.id] }

                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.channel eq "DELIVERY") and
                            (OrdersTable.deliveryUserId.isNotNull()) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()

                val groupedByDriver = orders.groupBy { it[OrdersTable.deliveryUserId]!! }

                drivers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByDriver[userId].orEmpty()
                    val completedOrders = orderRows.count { it[OrdersTable.status] == "COMPLETED" }
                    val feesCollected = orderRows.sumOf { it[OrdersTable.deliveryFee].toDouble() }
                    val revenue = orderRows.sumOf { it[OrdersTable.total].toDouble() }

                    DeliveryPerformanceV2Dto(
                        driver_id = userId.toString(),
                        driver_name = userRow[UsersTable.name],
                        orders_completed = completedOrders,
                        fees_collected = feesCollected,
                        revenue = revenue,
                        avg_delivery_time_minutes = 0.0, // No timestamp columns
                        late_delivery_percent = 0.0, // No timestamp columns
                    )
                }
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 7. Product Intelligence ──────────────────────────────────
        get("/product-intelligence") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20

            val result = transaction {
                val orderIds = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.map { it[OrdersTable.id] }

                if (orderIds.isEmpty()) {
                    return@transaction ProductIntelligenceDto(
                        top_selling = emptyList(),
                        most_profitable = emptyList(),
                        least_selling = emptyList(),
                        revenue_by_category = emptyList(),
                        low_margin_warnings = emptyList(),
                    )
                }

                // Get all order items with their item and category info
                val orderItems = OrderItemsTable.selectAll()
                    .where { OrderItemsTable.orderId inList orderIds }
                    .toList()

                // Build item => category lookup
                val allItemIds = orderItems.map { it[OrderItemsTable.itemId] }.distinct()
                val itemCategoryMap = ItemsTable.selectAll()
                    .where { ItemsTable.id inList allItemIds }
                    .associate { it[ItemsTable.id].value to it[ItemsTable.categoryId].value }

                val categoryNames = CategoriesTable.selectAll()
                    .where { CategoriesTable.vendorId eq vendorUUID }
                    .associate { it[CategoriesTable.id].value to it[CategoriesTable.name] }

                // Stock cost prices
                val stockCostPrices = StockTable.selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .associate { (it[StockTable.itemId]?.value ?: it[StockTable.id].value) to it[StockTable.costPrice].toDouble() }

                // Aggregate items
                data class ItemAgg(
                    val itemId: UUID,
                    val name: String,
                    var qty: Int = 0,
                    var revenue: Double = 0.0,
                )

                val itemMap = mutableMapOf<UUID, ItemAgg>()
                for (row in orderItems) {
                    val id = row[OrderItemsTable.itemId].value
                    val agg = itemMap.getOrPut(id) {
                        ItemAgg(id, row[OrderItemsTable.itemNameSnapshot])
                    }
                    agg.qty += row[OrderItemsTable.quantity]
                    agg.revenue += row[OrderItemsTable.itemPriceSnapshot].toDouble() * row[OrderItemsTable.quantity]
                }

                fun ItemAgg.toDto(): ProductItemDto {
                    val catId = itemCategoryMap[itemId]
                    val catName = if (catId != null) categoryNames[catId] ?: "Unknown" else "Unknown"
                    val costPrice = stockCostPrices[itemId] ?: 0.0
                    val unitPrice = if (qty > 0) revenue / qty else 0.0
                    val margin = if (unitPrice > 0 && costPrice > 0) ((unitPrice - costPrice) / unitPrice) * 100.0 else 0.0
                    return ProductItemDto(
                        item_id = itemId.toString(),
                        item_name = name,
                        category_name = catName,
                        quantity_sold = qty,
                        revenue = revenue,
                        cost_price = costPrice,
                        profit_margin = margin,
                    )
                }

                val allItems = itemMap.values.toList()
                val topSelling = allItems.sortedByDescending { it.qty }.take(limit).map { it.toDto() }
                val mostProfitable = allItems.sortedByDescending { it.revenue }.take(limit).map { it.toDto() }
                val leastSelling = allItems.sortedBy { it.qty }.take(limit).map { it.toDto() }
                val lowMarginWarnings = allItems.map { it.toDto() }.filter { it.profit_margin in 0.01..15.0 }

                // Revenue by category
                val revByCategory = orderItems
                    .groupBy { itemCategoryMap[it[OrderItemsTable.itemId].value] }
                    .mapNotNull { (catId, rows) ->
                        catId ?: return@mapNotNull null
                        val catName = categoryNames[catId] ?: "Unknown"
                        val rev = rows.sumOf { it[OrderItemsTable.itemPriceSnapshot].toDouble() * it[OrderItemsTable.quantity] }
                        val itemCount = rows.map { it[OrderItemsTable.itemId].value }.distinct().size
                        CategoryRevenueDto(
                            category_id = catId.toString(),
                            category_name = catName,
                            revenue = rev,
                            item_count = itemCount,
                        )
                    }

                ProductIntelligenceDto(
                    top_selling = topSelling,
                    most_profitable = mostProfitable,
                    least_selling = leastSelling,
                    revenue_by_category = revByCategory,
                    low_margin_warnings = lowMarginWarnings,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 8. Customer Intelligence ─────────────────────────────────
        get("/customer-intelligence") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val customers = CustomersTable.selectAll().where {
                    CustomersTable.vendorId eq vendorUUID
                }.toList()

                val totalCustomers = customers.size
                if (totalCustomers == 0) {
                    return@transaction CustomerIntelligenceDto(
                        total_customers = 0,
                        new_customers_percent = 0.0,
                        returning_customers_percent = 0.0,
                        average_spend = 0.0,
                        lifetime_value = 0.0,
                        top_customers = emptyList(),
                        frequency_buckets = emptyMap(),
                    )
                }

                // New vs returning: new = orderCount <= 1, returning = orderCount > 1
                val newCustomers = customers.count { it[CustomersTable.orderCount] <= 1 }
                val returningCustomers = totalCustomers - newCustomers
                val newPct = (newCustomers.toDouble() / totalCustomers) * 100.0
                val returnPct = (returningCustomers.toDouble() / totalCustomers) * 100.0

                val totalSpent = customers.sumOf { it[CustomersTable.totalSpent].toDouble() }
                val avgSpend = totalSpent / totalCustomers
                val ltv = if (returningCustomers > 0) {
                    customers.filter { it[CustomersTable.orderCount] > 1 }.sumOf { it[CustomersTable.totalSpent].toDouble() } / returningCustomers
                } else avgSpend

                val topCustomers = customers
                    .sortedByDescending { it[CustomersTable.totalSpent].toDouble() }
                    .take(10)
                    .map {
                        TopCustomerDto(
                            customer_id = it[CustomersTable.id].toString(),
                            customer_name = it[CustomersTable.name] ?: "Unknown",
                            phone = it[CustomersTable.phone],
                            order_count = it[CustomersTable.orderCount],
                            total_spent = it[CustomersTable.totalSpent].toDouble(),
                        )
                    }

                // Frequency buckets
                val freq = mutableMapOf("1_order" to 0, "2_5_orders" to 0, "6_10_orders" to 0, "11_plus_orders" to 0)
                for (c in customers) {
                    val oc = c[CustomersTable.orderCount]
                    when {
                        oc <= 1 -> freq["1_order"] = freq["1_order"]!! + 1
                        oc in 2..5 -> freq["2_5_orders"] = freq["2_5_orders"]!! + 1
                        oc in 6..10 -> freq["6_10_orders"] = freq["6_10_orders"]!! + 1
                        else -> freq["11_plus_orders"] = freq["11_plus_orders"]!! + 1
                    }
                }

                CustomerIntelligenceDto(
                    total_customers = totalCustomers,
                    new_customers_percent = newPct,
                    returning_customers_percent = returnPct,
                    average_spend = avgSpend,
                    lifetime_value = ltv,
                    top_customers = topCustomers,
                    frequency_buckets = freq,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 9. Alerts ────────────────────────────────────────────────
        get("/alerts") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val result = transaction {
                val alerts = mutableListOf<AlertDto>()

                // Revenue drop check: compare current to previous period
                val periodMs = to.toEpochMilliseconds() - from.toEpochMilliseconds()
                val prevFrom = Instant.fromEpochMilliseconds(from.toEpochMilliseconds() - periodMs)

                val currentRevenue = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.sumOf { it[OrdersTable.total].toDouble() }

                val previousRevenue = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq prevFrom) and
                            (OrdersTable.createdAt less from)
                }.sumOf { it[OrdersTable.total].toDouble() }

                if (previousRevenue > 0 && currentRevenue < previousRevenue * 0.7) {
                    val dropPct = ((previousRevenue - currentRevenue) / previousRevenue) * 100.0
                    alerts.add(
                        AlertDto(
                            type = "REVENUE_DROP",
                            severity = "WARNING",
                            title = "Revenue Drop Detected",
                            message = "Revenue dropped by ${String.format("%.1f", dropPct)}% compared to previous period",
                            value = currentRevenue,
                            threshold = previousRevenue * 0.7,
                        )
                    )
                }

                // High cancellation
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()
                val totalOrders = orders.size
                val cancelledOrders = orders.count { it[OrdersTable.status] in listOf("CANCELED", "CANCELLED") }
                if (totalOrders > 5) {
                    val cancelRate = (cancelledOrders.toDouble() / totalOrders) * 100.0
                    if (cancelRate > 20.0) {
                        alerts.add(
                            AlertDto(
                                type = "HIGH_CANCELLATION",
                                severity = "CRITICAL",
                                title = "High Cancellation Rate",
                                message = "Cancellation rate is ${String.format("%.1f", cancelRate)}% ($cancelledOrders/$totalOrders orders)",
                                value = cancelRate,
                                threshold = 20.0,
                            )
                        )
                    }
                }

                // Low stock & out of stock
                val stockItems = StockTable.selectAll().where {
                    (StockTable.vendorId eq vendorUUID) and (StockTable.alertEnabled eq true)
                }.toList()

                val outOfStock = stockItems.filter { it[StockTable.quantity] == 0 }
                val lowStock = stockItems.filter {
                    it[StockTable.quantity] > 0 && it[StockTable.quantity] <= it[StockTable.minQuantity]
                }

                if (outOfStock.isNotEmpty()) {
                    alerts.add(
                        AlertDto(
                            type = "OUT_OF_STOCK",
                            severity = "CRITICAL",
                            title = "Items Out of Stock",
                            message = "${outOfStock.size} item(s) are out of stock: ${outOfStock.take(3).joinToString { it[StockTable.itemName] }}",
                            value = outOfStock.size.toDouble(),
                            threshold = 0.0,
                        )
                    )
                }

                if (lowStock.isNotEmpty()) {
                    alerts.add(
                        AlertDto(
                            type = "LOW_STOCK",
                            severity = "WARNING",
                            title = "Low Stock Warning",
                            message = "${lowStock.size} item(s) below minimum: ${lowStock.take(3).joinToString { it[StockTable.itemName] }}",
                            value = lowStock.size.toDouble(),
                            threshold = 0.0,
                        )
                    )
                }

                AlertsDto(alerts = alerts)
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 10. Stock Overview ───────────────────────────────────────
        get("/stock-overview") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val result = transaction {
                val stockItems = StockTable.selectAll().where {
                    StockTable.vendorId eq vendorUUID
                }.toList()

                // Item selling prices
                val menuItemIds = stockItems.mapNotNull { it[StockTable.itemId] }
                val sellingPrices = if (menuItemIds.isNotEmpty()) {
                    ItemsTable.selectAll()
                        .where { ItemsTable.id inList menuItemIds }
                        .associate { it[ItemsTable.id] to it[ItemsTable.price].toDouble() }
                } else emptyMap()

                var totalStockValue = 0.0
                var totalSellingValue = 0.0
                val lowStockItems = mutableListOf<StockOverviewItemDto>()
                val outOfStockItems = mutableListOf<StockOverviewItemDto>()
                val deadStockItems = mutableListOf<StockOverviewItemDto>() // quantity > 0, no transactions in 30 days

                // Get recent transaction stock IDs (last 30 days)
                val thirtyDaysAgo = Clock.System.now() - kotlin.time.Duration.parse("30d")
                val recentTxStockIds = StockTransactionsTable.selectAll().where {
                    StockTransactionsTable.createdAt greaterEq thirtyDaysAgo
                }.map { it[StockTransactionsTable.stockId] }.toSet()

                for (stock in stockItems) {
                    val qty = stock[StockTable.quantity]
                    val costPrice = stock[StockTable.costPrice].toDouble()
                    val itemId = stock[StockTable.itemId]
                    val sellingPrice = if (itemId != null) sellingPrices[itemId] ?: 0.0 else 0.0

                    totalStockValue += qty * costPrice
                    totalSellingValue += qty * sellingPrice

                    val status = when {
                        qty == 0 -> "OUT_OF_STOCK"
                        qty <= stock[StockTable.minQuantity] -> "LOW_STOCK"
                        else -> "HEALTHY"
                    }

                    val dto = StockOverviewItemDto(
                        stock_id = stock[StockTable.id].toString(),
                        item_name = stock[StockTable.itemName],
                        quantity = qty,
                        min_quantity = stock[StockTable.minQuantity],
                        cost_price = costPrice,
                        unit = stock[StockTable.unit],
                        status = status,
                    )

                    when (status) {
                        "OUT_OF_STOCK" -> outOfStockItems.add(dto)
                        "LOW_STOCK" -> lowStockItems.add(dto)
                    }

                    if (qty > 0 && stock[StockTable.id] !in recentTxStockIds) {
                        deadStockItems.add(dto.copy(status = "DEAD_STOCK"))
                    }
                }

                // Movement summary (last 14 days)
                val fourteenDaysAgo = Clock.System.now() - kotlin.time.Duration.parse("14d")
                val recentTransactions = StockTransactionsTable.selectAll().where {
                    StockTransactionsTable.createdAt greaterEq fourteenDaysAgo
                }.toList()

                val tz = TimeZone.currentSystemDefault()
                val movementSummary = recentTransactions
                    .groupBy { it[StockTransactionsTable.createdAt].toLocalDateTime(tz).date.toString() }
                    .map { (date, txs) ->
                        StockMovementDto(
                            date = date,
                            added = txs.filter { it[StockTransactionsTable.type] == "ADD" }.sumOf { it[StockTransactionsTable.quantity] },
                            deducted = txs.filter { it[StockTransactionsTable.type] == "DEDUCT" }.sumOf { it[StockTransactionsTable.quantity] },
                        )
                    }
                    .sortedBy { it.date }

                StockOverviewDto(
                    total_stock_value = totalStockValue,
                    total_selling_value = totalSellingValue,
                    potential_profit = totalSellingValue - totalStockValue,
                    total_items = stockItems.size,
                    low_stock_items = lowStockItems,
                    out_of_stock_items = outOfStockItems,
                    dead_stock_items = deadStockItems,
                    movement_summary = movementSummary,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
