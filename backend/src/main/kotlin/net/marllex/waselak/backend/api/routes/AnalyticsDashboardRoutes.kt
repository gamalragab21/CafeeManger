package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
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
    // ── "Total revenue" — every PAID order regardless of fulfillment
    //    status. Kept for backwards compatibility but the dashboard
    //    headline now uses `completed_revenue` (the same definition the
    //    shift summary uses) so the two screens agree. ──
    val total_revenue: Double,
    val net_revenue: Double = 0.0,
    val pending_revenue: Double = 0.0,
    val total_orders: Int,
    val average_order_value: Double,
    val total_delivery_fees: Double,
    val total_discounts: Double,
    val total_tax: Double = 0.0,
    // ── Accounting-correct figures ──────────────────────────────────
    // Earnings: PAID + COMPLETED. Matches what shift summary shows.
    val completed_revenue: Double = 0.0,
    // Count of fully closed orders (status = COMPLETED).
    val completed_orders: Int = 0,
    // Count of unpaid orders (regardless of status). Lets the
    // dashboard surface "money you still need to collect" alongside
    // its EGP total in `pending_revenue`.
    val pending_orders: Int = 0,
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
    val quantity: Double,
    val min_quantity: Double,
    val cost_price: Double,
    val unit: String,
    val status: String,
)

@Serializable
data class StockMovementDto(
    val date: String,
    val added: Double,
    val deducted: Double,
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

// ── Offers Analytics ───────────────────────────────────────────────
@Serializable
data class OfferPerformanceItemDto(
    val offer_id: String,
    val offer_name: String,
    val discount_type: String,
    val discount_value: Double,
    val usage_count: Int,
    val total_discount_given: Double,
    val total_revenue_from_offer_orders: Double,
    val promo_code: String? = null,
    val is_active: Boolean,
)

@Serializable
data class DailyOfferUsageDto(
    val date: String,
    val usage_count: Int,
    val discount_amount: Double,
)

@Serializable
data class OffersAnalyticsResponseDto(
    val total_offers: Int,
    val active_offers: Int,
    val total_offer_uses: Int,
    val total_discount_from_offers: Double,
    val average_discount_per_use: Double,
    val top_offers: List<OfferPerformanceItemDto>,
    val offer_usage_trend: List<DailyOfferUsageDto>,
)

// ── Discount Analytics ─────────────────────────────────────────────
@Serializable
data class DiscountBreakdownDto(
    val type: String,  // MANUAL, OFFER, POINTS
    val count: Int,
    val total_amount: Double,
    val percent_of_total: Double,
)

@Serializable
data class DailyDiscountTrendDto(
    val date: String,
    val manual_discount: Double,
    val offer_discount: Double,
    val points_discount: Double,
)

@Serializable
data class DiscountAnalyticsResponseDto(
    val total_orders_with_discount: Int,
    val total_discount_given: Double,
    val average_discount_per_order: Double,
    val discount_rate: Double,
    val breakdown: List<DiscountBreakdownDto>,
    val daily_trend: List<DailyDiscountTrendDto>,
)

// ── Loyalty Analytics ──────────────────────────────────────────────
@Serializable
data class DailyLoyaltyTrendDto(
    val date: String,
    val points_earned: Int,
    val points_redeemed: Int,
)

@Serializable
data class LoyaltyAnalyticsResponseDto(
    val total_points_earned: Long,
    val total_points_redeemed: Long,
    val total_points_outstanding: Long,
    val active_loyalty_customers: Int,
    val redemption_rate: Double,
    val points_to_revenue: Double,
    val daily_trend: List<DailyLoyaltyTrendDto>,
)

// ── Supplier Analytics ───────────────────────────────────────────
@Serializable
data class TopSupplierDto(
    val supplier_id: String,
    val supplier_name: String,
    val total_orders: Int,
    val total_spent: Double,
    val received_orders: Int,
    val pending_orders: Int,
)

@Serializable
data class SupplierItemDto(
    val stock_id: String,
    val item_name: String,
    val total_quantity: Double,
    val total_cost: Double,
    val order_count: Int,
    val unit: String,
)

@Serializable
data class MonthlyPurchaseDto(
    val month: String,
    val total: Double,
    val order_count: Int,
)

@Serializable
data class SupplierAnalyticsDto(
    val total_suppliers: Int,
    val active_suppliers: Int,
    val total_purchase_orders: Int,
    val total_spent: Double,
    val pending_orders: Int,
    val received_orders: Int,
    val average_order_value: Double,
    val top_suppliers: List<TopSupplierDto>,
    val top_items: List<SupplierItemDto>,
    val monthly_trend: List<MonthlyPurchaseDto>,
)

// ── Staff Costs Analytics ─────────────────────────────────────────
@Serializable
data class WorkerOvertimeSummaryDto(
    val worker_id: String,
    val worker_name: String,
    val overtime_hours: Double,
    val overtime_amount: Double,
)

@Serializable
data class StaffCostsAnalyticsDto(
    val total_salaries: Double,
    val total_overtime: Double,
    val total_compensation: Double,
    val paid_amount: Double,
    val unpaid_amount: Double,
    val overtime_hours: Double,
    val workers_count: Int,
    val overtime_percentage: Double,
    val top_overtime_workers: List<WorkerOvertimeSummaryDto>,
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
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)
    route("/api/v1/analytics/dashboard") {

        // ── 1. Executive Summary ─────────────────────────────────────
        get("/executive-summary") {
            val trace = call.routeTrace()
            trace.step("Executive summary started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            val periodMs = to.toEpochMilliseconds() - from.toEpochMilliseconds()
            val prevFrom = Instant.fromEpochMilliseconds(from.toEpochMilliseconds() - periodMs)
            val prevTo = from
            trace.step("Date range parsed", mapOf(
                "from" to from.toString(),
                "to" to to.toString(),
                "prevFrom" to prevFrom.toString(),
                "prevTo" to prevTo.toString()
            ))

            val result = transaction {
                val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")

                fun metricsForPeriod(start: Instant, end: Instant): PeriodMetricsDto {
                    val orders = OrdersTable.selectAll().where {
                        (OrdersTable.vendorId eq vendorUUID) and
                                (OrdersTable.createdAt greaterEq start) and
                                (OrdersTable.createdAt lessEq end) and
                                (OrdersTable.status notInList excludedStatuses)
                    }.toList()
                    val paidOrders = orders.filter { it[OrdersTable.paymentStatus] == "PAID" }
                    val completedPaidOrders = paidOrders.filter { it[OrdersTable.status] == "COMPLETED" }
                    // Orders that are NOT yet finalised. The dashboard
                    // surfaces this set as "Pending" because the merchant
                    // base reads "still pending" to mean "kitchen is
                    // still cooking", NOT "customer hasn't paid". Most
                    // Wimpy-style shops collect cash upfront, so the
                    // payment-PENDING bucket is almost always empty —
                    // showing it as the headline "Pending Payment" gives
                    // a misleading 0 every time. Instead this bucket
                    // tells the merchant how many tickets are still in
                    // motion + their total value (the in-pipeline money).
                    val pendingOrdersList = orders.filter { it[OrdersTable.status] != "COMPLETED" }

                    val completedRevenue = completedPaidOrders.sumOf { it[OrdersTable.total].toDouble() }
                    // Renamed semantic: `pendingRevenue` now means "total
                    // value of orders not yet completed" — covers both
                    // unpaid and paid-but-still-cooking. This is what the
                    // merchant means by "still pending".
                    val pendingRevenue = pendingOrdersList.sumOf { it[OrdersTable.total].toDouble() }
                    val completedOrders = completedPaidOrders.size
                    val pendingOrdersCount = pendingOrdersList.size
                    val aov = if (completedOrders > 0) completedRevenue / completedOrders else 0.0
                    val totalDeliveryFees = completedPaidOrders
                        .filter { it[OrdersTable.channel] == "DELIVERY" }
                        .sumOf { it[OrdersTable.deliveryFee].toDouble() }
                    val totalDiscounts = completedPaidOrders.sumOf { it[OrdersTable.discount].toDouble() }
                    val totalTax = completedPaidOrders.sumOf { it[OrdersTable.tax].toDouble() }
                    val netRevenue = completedRevenue - totalDeliveryFees
                    return PeriodMetricsDto(
                        total_revenue = completedRevenue,
                        net_revenue = netRevenue,
                        pending_revenue = pendingRevenue,
                        total_orders = completedOrders,
                        average_order_value = aov,
                        total_delivery_fees = totalDeliveryFees,
                        total_discounts = totalDiscounts,
                        total_tax = totalTax,
                        completed_revenue = completedRevenue,
                        completed_orders = completedOrders,
                        pending_orders = pendingOrdersCount,
                    )
                }

                val current = metricsForPeriod(from, to)
                trace.step("Current period metrics computed", mapOf(
                    "totalOrders" to current.total_orders.toString(),
                    "totalRevenue" to current.total_revenue.toString()
                ))
                val previous = metricsForPeriod(prevFrom, prevTo)
                trace.step("Previous period metrics computed", mapOf(
                    "totalOrders" to previous.total_orders.toString(),
                    "totalRevenue" to previous.total_revenue.toString()
                ))

                fun pctChange(cur: Double, prev: Double): Double =
                    if (prev == 0.0) if (cur > 0) 100.0 else 0.0
                    else ((cur - prev) / prev) * 100.0

                val activeOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.status notInList listOf("COMPLETED", "CANCELED", "CANCELLED", "REFUNDED"))
                }.count().toInt()
                trace.step("Active orders counted", mapOf("activeOrders" to activeOrders.toString()))

                val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                val attendanceToday = AttendanceTable.selectAll().where {
                    (AttendanceTable.vendorId eq vendorUUID) and
                            (AttendanceTable.date eq todayStr)
                }.count().toInt()
                trace.step("Attendance fetched", mapOf("attendanceToday" to attendanceToday.toString()))

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
            trace.step("Executive summary completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 2. Revenue & Profit ──────────────────────────────────────
        get("/revenue-profit") {
            val trace = call.routeTrace()
            trace.step("Revenue & profit fetch started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val excludedFromRevenue = listOf("CANCELED", "CANCELLED", "REFUNDED")
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to) and
                            (OrdersTable.status notInList excludedFromRevenue)
                }.toList()
                trace.step("Orders fetched for revenue", mapOf("orderCount" to orders.size.toString()))

                // Revenue only from PAID orders
                val paidOrders = orders.filter { it[OrdersTable.paymentStatus] == "PAID" }
                val gross = paidOrders.sumOf { it[OrdersTable.total].toDouble() }
                val deliveryFees = paidOrders
                    .filter { it[OrdersTable.channel] == "DELIVERY" }
                    .sumOf { it[OrdersTable.deliveryFee].toDouble() }
                val net = gross - deliveryFees

                // For SPLIT orders, distribute revenue to actual payment methods from OrderPaymentsTable
                val splitOrderIds = orders.filter { it[OrdersTable.paymentMethod] == "SPLIT" }
                    .map { it[OrdersTable.id].value }
                val splitPayments = if (splitOrderIds.isNotEmpty()) {
                    OrderPaymentsTable.selectAll().where {
                        OrderPaymentsTable.orderId inList splitOrderIds
                    }.toList()
                } else emptyList()

                // Build payment method breakdown: non-split orders + split sub-payments
                val methodMap = mutableMapOf<String, Pair<Int, Double>>() // method -> (orderCount, revenue)
                orders.filter { it[OrdersTable.paymentMethod] != "SPLIT" }.forEach { row ->
                    val method = row[OrdersTable.paymentMethod]
                    val (count, rev) = methodMap.getOrDefault(method, Pair(0, 0.0))
                    methodMap[method] = Pair(count + 1, rev + row[OrdersTable.total].toDouble())
                }
                // Add split sub-payments to their actual methods
                splitPayments.forEach { payment ->
                    val method = payment[OrderPaymentsTable.paymentMethod]
                    val amount = payment[OrderPaymentsTable.amount].toDouble()
                    val (count, rev) = methodMap.getOrDefault(method, Pair(0, 0.0))
                    methodMap[method] = Pair(count + 1, rev + amount)
                }
                val paymentMethods = methodMap.map { (method, pair) ->
                    PaymentMethodDetailDto(
                        method = method,
                        order_count = pair.first,
                        revenue = pair.second,
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

                trace.step("Revenue computed", mapOf(
                    "grossRevenue" to gross.toString(),
                    "netRevenue" to net.toString(),
                    "deliveryFees" to deliveryFees.toString(),
                    "paymentMethodCount" to paymentMethods.size.toString(),
                    "dailyTrendDays" to dailyTrend.size.toString()
                ))

                RevenueProfitDto(
                    gross_revenue = gross,
                    total_delivery_fees = deliveryFees,
                    net_revenue = net,
                    payment_methods = paymentMethods,
                    daily_trend = dailyTrend,
                )
            }
            trace.step("Revenue & profit fetch completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 3. Orders Intelligence ───────────────────────────────────
        get("/orders-intelligence") {
            val trace = call.routeTrace()
            trace.step("Orders intelligence started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()
                trace.step("Orders fetched", mapOf("orderCount" to orders.size.toString()))

                val total = orders.size
                val completed = orders.count { it[OrdersTable.status] == "COMPLETED" }
                val cancelled = orders.count { it[OrdersTable.status] in listOf("CANCELED", "CANCELLED") }
                val refunded = orders.count { it[OrdersTable.status] == "REFUNDED" }

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

                trace.step("Order stats computed", mapOf(
                    "totalOrders" to total.toString(),
                    "completedOrders" to completed.toString(),
                    "cancelledOrders" to cancelled.toString(),
                    "refundedOrders" to refunded.toString(),
                    "channelCount" to ordersByChannel.size.toString()
                ))

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
            trace.step("Orders intelligence completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 4. Peak Time Analysis ────────────────────────────────────
        get("/peak-times") {
            val trace = call.routeTrace()
            trace.step("Peak time analysis started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()
                trace.step("Orders fetched for peak times", mapOf("orderCount" to orders.size.toString()))

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

                trace.step("Peak times computed", mapOf(
                    "busiestHour" to busiestHour.toString(),
                    "busiestDay" to busiestDay
                ))

                PeakTimeAnalysisDto(
                    busiest_hour = busiestHour,
                    busiest_day = busiestDay,
                    hourly_data = hourlyMap.values.toList(),
                    heatmap = heatmap,
                    day_of_week = dayOfWeekData,
                )
            }
            trace.step("Peak time analysis completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 5. Cashier Performance V2 ────────────────────────────────
        get("/cashier-performance-v2") {
            val trace = call.routeTrace()
            trace.step("Cashier performance V2 started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val cashiers = UsersTable.selectAll().where {
                    (UsersTable.vendorId eq vendorUUID) and (UsersTable.role eq "CASHIER")
                }.associateBy { it[UsersTable.id] }
                trace.step("Cashiers fetched", mapOf("cashierCount" to cashiers.size.toString()))

                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()
                trace.step("Orders fetched for cashier perf", mapOf("orderCount" to orders.size.toString()))

                val groupedByCashier = orders.groupBy { it[OrdersTable.cashierId] }

                cashiers.values.map { userRow ->
                    val userId = userRow[UsersTable.id]
                    val orderRows = groupedByCashier[userId].orEmpty()
                    val revenueRows = orderRows.filter { it[OrdersTable.status] !in listOf("CANCELED", "CANCELLED", "REFUNDED") }
                    val totalRevenue = revenueRows.sumOf { it[OrdersTable.total].toDouble() }
                    val totalOrders = orderRows.size
                    val aov = if (revenueRows.isNotEmpty()) totalRevenue / revenueRows.size else 0.0
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
            trace.step("Cashier performance V2 result", mapOf("cashierCount" to result.size.toString()))
            trace.step("Cashier performance V2 completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 6. Delivery Performance V2 ───────────────────────────────
        get("/delivery-performance-v2") {
            val trace = call.routeTrace()
            trace.step("Delivery performance V2 started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val drivers = UsersTable.selectAll().where {
                    (UsersTable.vendorId eq vendorUUID) and (UsersTable.role eq "DELIVERY")
                }.associateBy { it[UsersTable.id] }
                trace.step("Delivery drivers fetched", mapOf("driverCount" to drivers.size.toString()))

                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.channel eq "DELIVERY") and
                            (OrdersTable.deliveryUserId.isNotNull()) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.toList()
                trace.step("Delivery orders fetched", mapOf("orderCount" to orders.size.toString()))

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
            trace.step("Delivery performance V2 result", mapOf("driverCount" to result.size.toString()))
            trace.step("Delivery performance V2 completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 7. Product Intelligence ──────────────────────────────────
        get("/product-intelligence") {
            val trace = call.routeTrace()
            trace.step("Product intelligence started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            trace.step("Params parsed", mapOf("from" to from.toString(), "to" to to.toString(), "limit" to limit.toString()))

            val result = transaction {
                val orderIds = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                }.map { it[OrdersTable.id] }
                trace.step("Order IDs fetched", mapOf("orderIdCount" to orderIds.size.toString()))

                if (orderIds.isEmpty()) {
                    trace.step("No orders found, returning empty product intelligence")
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
                trace.step("Product items aggregated", mapOf("uniqueItemCount" to allItems.size.toString()))
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

                trace.step("Product intelligence computed", mapOf(
                    "topSellingCount" to topSelling.size.toString(),
                    "mostProfitableCount" to mostProfitable.size.toString(),
                    "lowMarginCount" to lowMarginWarnings.size.toString(),
                    "categoryCount" to revByCategory.size.toString()
                ))

                ProductIntelligenceDto(
                    top_selling = topSelling,
                    most_profitable = mostProfitable,
                    least_selling = leastSelling,
                    revenue_by_category = revByCategory,
                    low_margin_warnings = lowMarginWarnings,
                )
            }
            trace.step("Product intelligence completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 8. Customer Intelligence ─────────────────────────────────
        get("/customer-intelligence") {
            val trace = call.routeTrace()
            trace.step("Customer intelligence started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val customers = CustomersTable.selectAll().where {
                    CustomersTable.vendorId eq vendorUUID
                }.toList()

                val totalCustomers = customers.size
                trace.step("Customers fetched", mapOf("totalCustomers" to totalCustomers.toString()))
                if (totalCustomers == 0) {
                    trace.step("No customers found, returning empty intelligence")
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

                trace.step("Customer stats computed", mapOf(
                    "newCustomersPct" to newPct.toString(),
                    "returningCustomersPct" to returnPct.toString(),
                    "averageSpend" to avgSpend.toString(),
                    "lifetimeValue" to ltv.toString(),
                    "topCustomerCount" to topCustomers.size.toString()
                ))

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
            trace.step("Customer intelligence completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 9. Alerts ────────────────────────────────────────────────
        get("/alerts") {
            val trace = call.routeTrace()
            trace.step("Alerts fetch started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

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
                trace.step("Revenue comparison fetched", mapOf(
                    "currentRevenue" to currentRevenue.toString(),
                    "previousRevenue" to previousRevenue.toString()
                ))

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

                // High refund rate
                val refundedOrders = orders.count { it[OrdersTable.status] == "REFUNDED" }
                if (totalOrders > 5) {
                    val refundRate = (refundedOrders.toDouble() / totalOrders) * 100.0
                    if (refundRate > 10.0) {
                        alerts.add(
                            AlertDto(
                                type = "HIGH_REFUND_RATE",
                                severity = "WARNING",
                                title = "High Refund Rate",
                                message = "Refund rate is ${String.format("%.1f", refundRate)}% ($refundedOrders/$totalOrders orders)",
                                value = refundRate,
                                threshold = 10.0,
                            )
                        )
                    }
                }

                trace.step("Order alerts checked", mapOf(
                    "totalOrders" to totalOrders.toString(),
                    "cancelledOrders" to cancelledOrders.toString()
                ))

                // Low stock & out of stock
                val stockItems = StockTable.selectAll().where {
                    (StockTable.vendorId eq vendorUUID) and (StockTable.alertEnabled eq true)
                }.toList()

                val outOfStock = stockItems.filter { it[StockTable.quantity].compareTo(java.math.BigDecimal.ZERO) == 0 }
                val lowStock = stockItems.filter {
                    it[StockTable.quantity].compareTo(java.math.BigDecimal.ZERO) > 0 && it[StockTable.quantity] <= it[StockTable.minQuantity]
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

                trace.step("Stock alerts checked", mapOf(
                    "outOfStockCount" to outOfStock.size.toString(),
                    "lowStockCount" to lowStock.size.toString()
                ))

                AlertsDto(alerts = alerts)
            }
            trace.step("Alerts result", mapOf("alertCount" to result.alerts.size.toString()))
            trace.step("Alerts fetch completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 10. Stock Overview ───────────────────────────────────────
        get("/stock-overview") {
            val trace = call.routeTrace()
            trace.step("Stock overview started")
            val principal = requireRole("MANAGER")
            // Analytics is available on all plans
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))

            val result = transaction {
                val stockItems = StockTable.selectAll().where {
                    StockTable.vendorId eq vendorUUID
                }.toList()
                trace.step("Stock items fetched", mapOf("stockItemCount" to stockItems.size.toString()))

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
                    val qtyBd = stock[StockTable.quantity]
                    val qty = qtyBd.toDouble()
                    val costPrice = stock[StockTable.costPrice].toDouble()
                    val itemId = stock[StockTable.itemId]
                    val sellingPrice = if (itemId != null) sellingPrices[itemId] ?: 0.0 else 0.0

                    totalStockValue += qty * costPrice
                    totalSellingValue += qty * sellingPrice

                    val minQty = stock[StockTable.minQuantity].toDouble()
                    val status = when {
                        qty <= 0.0 -> "OUT_OF_STOCK"
                        qty <= minQty -> "LOW_STOCK"
                        else -> "HEALTHY"
                    }

                    val dto = StockOverviewItemDto(
                        stock_id = stock[StockTable.id].toString(),
                        item_name = stock[StockTable.itemName],
                        quantity = qty,
                        min_quantity = minQty,
                        cost_price = costPrice,
                        unit = stock[StockTable.unit],
                        status = status,
                    )

                    when (status) {
                        "OUT_OF_STOCK" -> outOfStockItems.add(dto)
                        "LOW_STOCK" -> lowStockItems.add(dto)
                    }

                    if (qty > 0.0 && stock[StockTable.id] !in recentTxStockIds) {
                        deadStockItems.add(dto.copy(status = "DEAD_STOCK"))
                    }
                }

                // Movement summary (last 14 days)
                val fourteenDaysAgo = Clock.System.now() - kotlin.time.Duration.parse("14d")
                val recentTransactions = StockTransactionsTable.selectAll().where {
                    StockTransactionsTable.createdAt greaterEq fourteenDaysAgo
                }.toList()

                val tz = TimeZone.currentSystemDefault()
                val addTypes = setOf("ADD", "PURCHASE")
                val deductTypes = setOf("DEDUCT", "SALE_DIRECT", "SALE_RECIPE")
                val movementSummary = recentTransactions
                    .groupBy { it[StockTransactionsTable.createdAt].toLocalDateTime(tz).date.toString() }
                    .map { (date, txs) ->
                        StockMovementDto(
                            date = date,
                            added = txs.filter { it[StockTransactionsTable.type] in addTypes }.sumOf { it[StockTransactionsTable.quantity].toDouble() },
                            deducted = txs.filter { it[StockTransactionsTable.type] in deductTypes }.sumOf { it[StockTransactionsTable.quantity].toDouble() },
                        )
                    }
                    .sortedBy { it.date }

                trace.step("Stock overview computed", mapOf(
                    "totalStockValue" to totalStockValue.toString(),
                    "totalSellingValue" to totalSellingValue.toString(),
                    "potentialProfit" to (totalSellingValue - totalStockValue).toString(),
                    "lowStockCount" to lowStockItems.size.toString(),
                    "outOfStockCount" to outOfStockItems.size.toString(),
                    "deadStockCount" to deadStockItems.size.toString()
                ))

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
            trace.step("Stock overview completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 11. Offers Analytics ────────────────────────────────────────
        get("/offers-analytics") {
            val trace = call.routeTrace()
            trace.step("Offers analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorUUID, "ANALYTICS")
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                // Total and active offers for this vendor
                val allOffers = OffersTable.selectAll().where {
                    OffersTable.vendorId eq vendorUUID
                }.toList()
                val totalOffers = allOffers.size
                val activeOffers = allOffers.count { it[OffersTable.active] }
                trace.step("Offers fetched", mapOf(
                    "totalOffers" to totalOffers.toString(),
                    "activeOffers" to activeOffers.toString()
                ))

                // Orders with an offer applied within date range (exclude cancelled/refunded)
                val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")
                val offerOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.offerId.isNotNull()) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to) and
                            (OrdersTable.status notInList excludedStatuses)
                }.toList()
                trace.step("Offer orders fetched", mapOf("offerOrderCount" to offerOrders.size.toString()))

                // Build offer lookup
                val offerMap = allOffers.associateBy { it[OffersTable.id].value }

                // Aggregate by offer
                data class OfferAgg(
                    val offerId: UUID,
                    var usageCount: Int = 0,
                    var totalDiscount: Double = 0.0,
                    var totalRevenue: Double = 0.0,
                )

                val offerAggMap = mutableMapOf<UUID, OfferAgg>()
                for (order in offerOrders) {
                    val oid = order[OrdersTable.offerId]!!.value
                    val agg = offerAggMap.getOrPut(oid) { OfferAgg(offerId = oid) }
                    agg.usageCount++
                    agg.totalDiscount += order[OrdersTable.discount].toDouble()
                    agg.totalRevenue += order[OrdersTable.total].toDouble()
                }

                val totalOfferUses = offerOrders.size
                val totalDiscountFromOffers = offerOrders.sumOf { it[OrdersTable.discount].toDouble() }
                val avgDiscountPerUse = if (totalOfferUses > 0) totalDiscountFromOffers / totalOfferUses else 0.0

                // Top offers sorted by usage
                val topOffers = offerAggMap.values
                    .sortedByDescending { it.usageCount }
                    .take(20)
                    .map { agg ->
                        val offerRow = offerMap[agg.offerId]
                        OfferPerformanceItemDto(
                            offer_id = agg.offerId.toString(),
                            offer_name = offerRow?.get(OffersTable.name) ?: "Unknown",
                            discount_type = offerRow?.get(OffersTable.discountType) ?: "FIXED_PRICE",
                            discount_value = offerRow?.get(OffersTable.discountValue)?.toDouble() ?: 0.0,
                            usage_count = agg.usageCount,
                            total_discount_given = agg.totalDiscount,
                            total_revenue_from_offer_orders = agg.totalRevenue,
                            promo_code = offerRow?.get(OffersTable.promoCode),
                            is_active = offerRow?.get(OffersTable.active) ?: false,
                        )
                    }

                // Daily usage trend
                val tz = TimeZone.currentSystemDefault()
                val offerUsageTrend = offerOrders
                    .groupBy { it[OrdersTable.createdAt].toLocalDateTime(tz).date.toString() }
                    .map { (date, rows) ->
                        DailyOfferUsageDto(
                            date = date,
                            usage_count = rows.size,
                            discount_amount = rows.sumOf { it[OrdersTable.discount].toDouble() },
                        )
                    }
                    .sortedBy { it.date }

                trace.step("Offers analytics computed", mapOf(
                    "totalOfferUses" to totalOfferUses.toString(),
                    "totalDiscountFromOffers" to totalDiscountFromOffers.toString(),
                    "topOffersCount" to topOffers.size.toString(),
                    "trendDays" to offerUsageTrend.size.toString()
                ))

                OffersAnalyticsResponseDto(
                    total_offers = totalOffers,
                    active_offers = activeOffers,
                    total_offer_uses = totalOfferUses,
                    total_discount_from_offers = totalDiscountFromOffers,
                    average_discount_per_use = avgDiscountPerUse,
                    top_offers = topOffers,
                    offer_usage_trend = offerUsageTrend,
                )
            }
            trace.step("Offers analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 12. Discount Analytics ──────────────────────────────────────
        get("/discount-analytics") {
            val trace = call.routeTrace()
            trace.step("Discount analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorUUID, "ANALYTICS")
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")

                // All orders in date range (for discount rate denominator)
                val allOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                            (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to) and
                            (OrdersTable.status notInList excludedStatuses)
                }.toList()
                val totalOrderCount = allOrders.size
                trace.step("All orders fetched", mapOf("totalOrderCount" to totalOrderCount.toString()))

                // Orders with discount > 0
                val discountedOrders = allOrders.filter {
                    it[OrdersTable.discount].compareTo(java.math.BigDecimal.ZERO) > 0
                }
                val totalOrdersWithDiscount = discountedOrders.size
                val totalDiscountGiven = discountedOrders.sumOf { it[OrdersTable.discount].toDouble() }
                val avgDiscountPerOrder = if (totalOrdersWithDiscount > 0) totalDiscountGiven / totalOrdersWithDiscount else 0.0
                val discountRate = if (totalOrderCount > 0) (totalOrdersWithDiscount.toDouble() / totalOrderCount) * 100.0 else 0.0

                trace.step("Discount stats computed", mapOf(
                    "totalOrdersWithDiscount" to totalOrdersWithDiscount.toString(),
                    "totalDiscountGiven" to totalDiscountGiven.toString(),
                    "discountRate" to discountRate.toString()
                ))

                // Classify: OFFER (offerId != null), POINTS (pointsRedeemed > 0), MANUAL (else)
                var offerCount = 0; var offerAmount = 0.0
                var pointsCount = 0; var pointsAmount = 0.0
                var manualCount = 0; var manualAmount = 0.0

                for (order in discountedOrders) {
                    val discountVal = order[OrdersTable.discount].toDouble()
                    when {
                        order[OrdersTable.offerId] != null -> {
                            offerCount++; offerAmount += discountVal
                        }
                        order[OrdersTable.pointsRedeemed] > 0 -> {
                            pointsCount++; pointsAmount += discountVal
                        }
                        else -> {
                            manualCount++; manualAmount += discountVal
                        }
                    }
                }

                val breakdown = listOf(
                    DiscountBreakdownDto(
                        type = "MANUAL",
                        count = manualCount,
                        total_amount = manualAmount,
                        percent_of_total = if (totalDiscountGiven > 0) (manualAmount / totalDiscountGiven) * 100.0 else 0.0,
                    ),
                    DiscountBreakdownDto(
                        type = "OFFER",
                        count = offerCount,
                        total_amount = offerAmount,
                        percent_of_total = if (totalDiscountGiven > 0) (offerAmount / totalDiscountGiven) * 100.0 else 0.0,
                    ),
                    DiscountBreakdownDto(
                        type = "POINTS",
                        count = pointsCount,
                        total_amount = pointsAmount,
                        percent_of_total = if (totalDiscountGiven > 0) (pointsAmount / totalDiscountGiven) * 100.0 else 0.0,
                    ),
                )

                // Daily trend grouped by type
                val tz = TimeZone.currentSystemDefault()
                data class DailyDiscountAcc(
                    var manual: Double = 0.0,
                    var offer: Double = 0.0,
                    var points: Double = 0.0,
                )

                val dailyMap = mutableMapOf<String, DailyDiscountAcc>()
                for (order in discountedOrders) {
                    val date = order[OrdersTable.createdAt].toLocalDateTime(tz).date.toString()
                    val acc = dailyMap.getOrPut(date) { DailyDiscountAcc() }
                    val discountVal = order[OrdersTable.discount].toDouble()
                    when {
                        order[OrdersTable.offerId] != null -> acc.offer += discountVal
                        order[OrdersTable.pointsRedeemed] > 0 -> acc.points += discountVal
                        else -> acc.manual += discountVal
                    }
                }

                val dailyTrend = dailyMap.entries
                    .sortedBy { it.key }
                    .map { (date, acc) ->
                        DailyDiscountTrendDto(
                            date = date,
                            manual_discount = acc.manual,
                            offer_discount = acc.offer,
                            points_discount = acc.points,
                        )
                    }

                trace.step("Discount analytics computed", mapOf(
                    "manualCount" to manualCount.toString(),
                    "offerCount" to offerCount.toString(),
                    "pointsCount" to pointsCount.toString(),
                    "trendDays" to dailyTrend.size.toString()
                ))

                DiscountAnalyticsResponseDto(
                    total_orders_with_discount = totalOrdersWithDiscount,
                    total_discount_given = totalDiscountGiven,
                    average_discount_per_order = avgDiscountPerOrder,
                    discount_rate = discountRate,
                    breakdown = breakdown,
                    daily_trend = dailyTrend,
                )
            }
            trace.step("Discount analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 13. Loyalty Analytics ───────────────────────────────────────
        get("/loyalty-analytics") {
            val trace = call.routeTrace()
            trace.step("Loyalty analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            planService.checkFeature(vendorUUID, "ANALYTICS")
            trace.step("Vendor resolved", mapOf("vendorId" to vendorUUID.toString()))
            val (from, to) = parseDateRange(call)
            trace.step("Date range parsed", mapOf("from" to from.toString(), "to" to to.toString()))

            val result = transaction {
                // Points transactions within date range for this vendor
                val pointsTxs = PointsTransactionsTable.selectAll().where {
                    (PointsTransactionsTable.vendorId eq vendorUUID) and
                            (PointsTransactionsTable.createdAt greaterEq from) and
                            (PointsTransactionsTable.createdAt lessEq to)
                }.toList()
                trace.step("Points transactions fetched", mapOf("txCount" to pointsTxs.size.toString()))

                val totalEarned = pointsTxs
                    .filter { it[PointsTransactionsTable.type] == "EARN" }
                    .sumOf { it[PointsTransactionsTable.points].toLong() }
                val totalRedeemed = pointsTxs
                    .filter { it[PointsTransactionsTable.type] == "REDEEM" }
                    .sumOf { it[PointsTransactionsTable.points].toLong() }

                // Active loyalty customers: customers with pointsBalance > 0
                val activeLoyaltyCustomers = CustomersTable.selectAll().where {
                    (CustomersTable.vendorId eq vendorUUID) and
                            (CustomersTable.pointsBalance greater 0)
                }.count().toInt()
                trace.step("Active loyalty customers counted", mapOf("count" to activeLoyaltyCustomers.toString()))

                // Outstanding points = sum of all customer balances
                val totalPointsOutstanding = CustomersTable.selectAll().where {
                    CustomersTable.vendorId eq vendorUUID
                }.sumOf { it[CustomersTable.pointsBalance].toLong() }

                // Redemption rate
                val redemptionRate = if (totalEarned > 0) (totalRedeemed.toDouble() / totalEarned) * 100.0 else 0.0

                // Points to revenue: redeemed points * pointsRedeemRate from vendor settings
                val vendorRow = VendorsTable.selectAll().where {
                    VendorsTable.id eq vendorUUID
                }.firstOrNull()
                val pointsRedeemRate = vendorRow?.get(VendorsTable.pointsRedeemRate)?.toDouble() ?: 0.1
                val pointsToRevenue = totalRedeemed * pointsRedeemRate

                trace.step("Loyalty metrics computed", mapOf(
                    "totalEarned" to totalEarned.toString(),
                    "totalRedeemed" to totalRedeemed.toString(),
                    "totalOutstanding" to totalPointsOutstanding.toString(),
                    "redemptionRate" to redemptionRate.toString(),
                    "pointsToRevenue" to pointsToRevenue.toString()
                ))

                // Daily trend
                val tz = TimeZone.currentSystemDefault()
                data class DailyLoyaltyAcc(
                    var earned: Int = 0,
                    var redeemed: Int = 0,
                )

                val dailyMap = mutableMapOf<String, DailyLoyaltyAcc>()
                for (tx in pointsTxs) {
                    val date = tx[PointsTransactionsTable.createdAt].toLocalDateTime(tz).date.toString()
                    val acc = dailyMap.getOrPut(date) { DailyLoyaltyAcc() }
                    when (tx[PointsTransactionsTable.type]) {
                        "EARN" -> acc.earned += tx[PointsTransactionsTable.points]
                        "REDEEM" -> acc.redeemed += tx[PointsTransactionsTable.points]
                    }
                }

                val dailyTrend = dailyMap.entries
                    .sortedBy { it.key }
                    .map { (date, acc) ->
                        DailyLoyaltyTrendDto(
                            date = date,
                            points_earned = acc.earned,
                            points_redeemed = acc.redeemed,
                        )
                    }

                trace.step("Loyalty daily trend computed", mapOf("trendDays" to dailyTrend.size.toString()))

                LoyaltyAnalyticsResponseDto(
                    total_points_earned = totalEarned,
                    total_points_redeemed = totalRedeemed,
                    total_points_outstanding = totalPointsOutstanding,
                    active_loyalty_customers = activeLoyaltyCustomers,
                    redemption_rate = redemptionRate,
                    points_to_revenue = pointsToRevenue,
                    daily_trend = dailyTrend,
                )
            }
            trace.step("Loyalty analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 14. Staff Costs Analytics ─────────────────────────────────
        get("/staff-costs") {
            val trace = call.routeTrace()
            trace.step("Staff costs analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val analyticsService by KoinJavaComponent.inject<net.marllex.waselak.backend.domain.service.AnalyticsQueryService>(
                clazz = net.marllex.waselak.backend.domain.service.AnalyticsQueryService::class.java
            )
            val result = analyticsService.getStaffCostsAnalytics(vendorUUID, from, to)
            trace.step("Staff costs analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // ── 15. Supplier Analytics ──────────────────────────────────────
        get("/supplier-analytics") {
            val trace = call.routeTrace()
            trace.step("Supplier analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val (from, to) = parseDateRange(call)

            val analyticsService by KoinJavaComponent.inject<net.marllex.waselak.backend.domain.service.AnalyticsQueryService>(
                clazz = net.marllex.waselak.backend.domain.service.AnalyticsQueryService::class.java
            )
            val result = analyticsService.getSupplierAnalytics(vendorUUID, from, to)
            trace.step("Supplier analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // Credit Analytics (pharmacy)
        // Returns & Exchange Analytics
        get("/returns-analytics") {
            val trace = call.routeTrace()
            trace.step("Returns analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val result = transaction {
                var query = ProductReturnsTable.selectAll().where {
                    (ProductReturnsTable.vendorId eq vendorUUID) and
                    (ProductReturnsTable.status eq "COMPLETED")
                }
                from?.let { f ->
                    query = query.andWhere { ProductReturnsTable.createdAt greaterEq kotlinx.datetime.Instant.fromEpochMilliseconds(f) }
                }
                to?.let { t ->
                    query = query.andWhere { ProductReturnsTable.createdAt lessEq kotlinx.datetime.Instant.fromEpochMilliseconds(t) }
                }
                val returns = query.toList()

                val refunds = returns.filter { it[ProductReturnsTable.returnType] == "RETURN" }
                val exchanges = returns.filter { it[ProductReturnsTable.returnType] == "EXCHANGE" }

                // Get return items details
                val allReturnIds = returns.map { it[ProductReturnsTable.id] }
                val returnItems = if (allReturnIds.isNotEmpty()) {
                    ReturnItemsTable.selectAll().where {
                        ReturnItemsTable.returnId inList allReturnIds
                    }.toList()
                } else emptyList()

                // Group returned items by item name
                val itemBreakdown = returnItems.groupBy { it[ReturnItemsTable.itemId] }.map { (itemId, items) ->
                    val itemName = ItemsTable.selectAll().where { ItemsTable.id eq itemId }
                        .firstOrNull()?.get(ItemsTable.name) ?: "Unknown"
                    val totalQty = items.sumOf { it[ReturnItemsTable.quantity] }
                    val totalAmount = items.sumOf { it[ReturnItemsTable.refundAmount].toDouble() }
                    ReturnsItemBreakdownDto(
                        item_name = itemName,
                        total_quantity = totalQty,
                        total_amount = Math.round(totalAmount * 100.0) / 100.0,
                    )
                }.sortedByDescending { it.total_quantity }

                // Exchange items
                val exchangeItems = exchanges.mapNotNull { ret ->
                    val exchId = ret[ProductReturnsTable.exchangeItemId] ?: return@mapNotNull null
                    val exchName = ItemsTable.selectAll().where { ItemsTable.id eq exchId }
                        .firstOrNull()?.get(ItemsTable.name) ?: "Unknown"
                    val exchPrice = ItemsTable.selectAll().where { ItemsTable.id eq exchId }
                        .firstOrNull()?.get(ItemsTable.price)?.toDouble() ?: 0.0
                    ExchangeItemBreakdownDto(
                        item_name = exchName,
                        quantity = ret[ProductReturnsTable.exchangeQuantity],
                        price = exchPrice,
                    )
                }

                ReturnsAnalyticsDto(
                    total_returns = returns.size,
                    total_refunds = refunds.size,
                    total_exchanges = exchanges.size,
                    total_refunded_amount = Math.round(refunds.sumOf { it[ProductReturnsTable.refundAmount].toDouble() } * 100.0) / 100.0,
                    total_exchanged_amount = Math.round(exchanges.sumOf { it[ProductReturnsTable.refundAmount].toDouble() } * 100.0) / 100.0,
                    total_returned_items = returnItems.sumOf { it[ReturnItemsTable.quantity] },
                    returned_items_breakdown = itemBreakdown,
                    exchange_items = exchangeItems,
                )
            }
            trace.step("Returns analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }

        get("/credit-analytics") {
            val trace = call.routeTrace()
            trace.step("Credit analytics started")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()

            val result = transaction {
                // Total outstanding credit
                val allCredits = CustomerCreditsTable.selectAll().where {
                    CustomerCreditsTable.vendorId eq vendorUUID
                }.toList()

                val totalOutstanding = allCredits.sumOf { it[CustomerCreditsTable.balance].toDouble() }
                val totalCreditLimit = allCredits.sumOf { it[CustomerCreditsTable.creditLimit].toDouble() }
                val totalCreditCustomers = allCredits.count { it[CustomerCreditsTable.balance] > java.math.BigDecimal.ZERO }
                val utilizationPercent = if (totalCreditLimit > 0) (totalOutstanding / totalCreditLimit) * 100 else 0.0

                // Credit transactions in period
                var txQuery = CreditTransactionsTable.selectAll().where {
                    CreditTransactionsTable.vendorId eq vendorUUID
                }
                from?.let { f ->
                    txQuery = txQuery.andWhere { CreditTransactionsTable.createdAt greaterEq kotlinx.datetime.Instant.fromEpochMilliseconds(f) }
                }
                to?.let { t ->
                    txQuery = txQuery.andWhere { CreditTransactionsTable.createdAt lessEq kotlinx.datetime.Instant.fromEpochMilliseconds(t) }
                }
                val transactions = txQuery.toList()

                val totalCharges = transactions.filter { it[CreditTransactionsTable.type] == "CHARGE" }
                    .sumOf { it[CreditTransactionsTable.amount].toDouble() }
                val totalPayments = transactions.filter { it[CreditTransactionsTable.type] == "PAYMENT" }
                    .sumOf { it[CreditTransactionsTable.amount].toDouble() }

                // Top debtors
                val topDebtors = allCredits
                    .filter { it[CustomerCreditsTable.balance] > java.math.BigDecimal.ZERO }
                    .sortedByDescending { it[CustomerCreditsTable.balance].toDouble() }
                    .take(10)
                    .map { creditRow ->
                        val customerUUID = creditRow[CustomerCreditsTable.customerId]
                        val customer = CustomersTable.selectAll().where {
                            CustomersTable.id eq customerUUID
                        }.firstOrNull()

                        CreditDebtorDto(
                            customer_name = customer?.get(CustomersTable.name) ?: "Unknown",
                            customer_phone = customer?.get(CustomersTable.phone),
                            balance = creditRow[CustomerCreditsTable.balance].toDouble(),
                            credit_limit = creditRow[CustomerCreditsTable.creditLimit].toDouble(),
                        )
                    }

                // Credit orders count (orders with payment_method = CREDIT)
                var creditOrdersQuery = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and (OrdersTable.paymentMethod eq "CREDIT")
                }
                from?.let { f ->
                    creditOrdersQuery = creditOrdersQuery.andWhere { OrdersTable.createdAt greaterEq kotlinx.datetime.Instant.fromEpochMilliseconds(f) }
                }
                to?.let { t ->
                    creditOrdersQuery = creditOrdersQuery.andWhere { OrdersTable.createdAt lessEq kotlinx.datetime.Instant.fromEpochMilliseconds(t) }
                }
                val creditOrders = creditOrdersQuery.toList()
                val creditOrdersCount = creditOrders.size
                val creditOrdersRevenue = creditOrders.sumOf { it[OrdersTable.total].toDouble() }

                CreditAnalyticsDto(
                    total_outstanding = totalOutstanding,
                    total_credit_limit = totalCreditLimit,
                    utilization_percent = utilizationPercent,
                    total_credit_customers = totalCreditCustomers,
                    total_charges = totalCharges,
                    total_payments = totalPayments,
                    credit_orders_count = creditOrdersCount,
                    credit_orders_revenue = creditOrdersRevenue,
                    top_debtors = topDebtors,
                )
            }
            trace.step("Credit analytics completed")
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

@Serializable
data class CreditAnalyticsDto(
    val total_outstanding: Double,
    val total_credit_limit: Double,
    val utilization_percent: Double,
    val total_credit_customers: Int,
    val total_charges: Double,
    val total_payments: Double,
    val credit_orders_count: Int,
    val credit_orders_revenue: Double,
    val top_debtors: List<CreditDebtorDto>,
)

@Serializable
data class CreditDebtorDto(
    val customer_name: String,
    val customer_phone: String? = null,
    val balance: Double,
    val credit_limit: Double,
)

@kotlinx.serialization.Serializable
data class ReturnsAnalyticsDto(
    val total_returns: Int,
    val total_refunds: Int,
    val total_exchanges: Int,
    val total_refunded_amount: Double,
    val total_exchanged_amount: Double,
    val total_returned_items: Int,
    val returned_items_breakdown: List<ReturnsItemBreakdownDto> = emptyList(),
    val exchange_items: List<ExchangeItemBreakdownDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ReturnsItemBreakdownDto(
    val item_name: String,
    val total_quantity: Int,
    val total_amount: Double,
)

@kotlinx.serialization.Serializable
data class ExchangeItemBreakdownDto(
    val item_name: String,
    val quantity: Int,
    val price: Double,
)
