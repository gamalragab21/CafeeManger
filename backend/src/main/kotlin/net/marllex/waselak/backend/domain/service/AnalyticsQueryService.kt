package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.*
import net.marllex.waselak.backend.api.routes.*
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Reusable analytics query service.
 * Extracts the SQL query logic from AnalyticsDashboardRoutes so it can be
 * called from both the vendor-facing analytics endpoints (vendor JWT) and
 * the CMS admin wrapper endpoints (admin JWT with vendorId path param).
 *
 * Every method takes [vendorId] as a parameter instead of extracting it
 * from the JWT principal.
 */
class AnalyticsQueryService {

    private val DAY_NAMES = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // ── 1. Executive Summary ─────────────────────────────────────────────

    fun getExecutiveSummary(vendorId: UUID, from: Instant, to: Instant): ExecutiveSummaryDto {
        val periodMs = to.toEpochMilliseconds() - from.toEpochMilliseconds()
        val prevFrom = Instant.fromEpochMilliseconds(from.toEpochMilliseconds() - periodMs)
        val prevTo = from

        return transaction {
            val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")

            fun metricsForPeriod(start: Instant, end: Instant): PeriodMetricsDto {
                val orders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorId) and
                            (OrdersTable.createdAt greaterEq start) and
                            (OrdersTable.createdAt lessEq end) and
                            (OrdersTable.status notInList excludedStatuses)
                }.toList()
                val paidOrders = orders.filter { it[OrdersTable.paymentStatus] == "PAID" }
                val pendingPaymentOrders = orders.filter { it[OrdersTable.paymentStatus] == "PENDING" }
                // "Completed + paid" — the accounting truth, same definition
                // the shift summary uses. The dashboard now surfaces THIS
                // value as `total_revenue` so that:
                //   • Already-deployed manager apps (which only know the
                //     legacy `total_revenue` / `total_orders` field names)
                //     immediately start displaying the correct, accounting-
                //     consistent figures without any client rebuild.
                //   • New clients that use the dedicated `completed_revenue`
                //     / `completed_orders` fields keep working — they get
                //     the same value through the explicit alias.
                // If you need the "all paid orders, including in-flight" set
                // (the old semantic) it's still exposed inside
                // `RevenueProfit` and `Settlements` queries below.
                val completedPaidOrders = paidOrders.filter { it[OrdersTable.status] == "COMPLETED" }

                val completedRevenue = completedPaidOrders.sumOf { it[OrdersTable.total].toDouble() }
                val pendingRevenue = pendingPaymentOrders.sumOf { it[OrdersTable.total].toDouble() }
                val completedOrders = completedPaidOrders.size
                val pendingOrders = pendingPaymentOrders.size
                // AOV based on completed orders so it reads consistently
                // against the headline revenue figure.
                val aov = if (completedOrders > 0) completedRevenue / completedOrders else 0.0
                val totalDeliveryFees = completedPaidOrders
                    .filter { it[OrdersTable.channel] == "DELIVERY" }
                    .sumOf { it[OrdersTable.deliveryFee].toDouble() }
                val totalDiscounts = completedPaidOrders.sumOf { it[OrdersTable.discount].toDouble() }
                val totalTax = completedPaidOrders.sumOf { it[OrdersTable.tax].toDouble() }
                val netRevenue = completedRevenue - totalDeliveryFees
                return PeriodMetricsDto(
                    // Legacy field names now carry the completed-only
                    // semantic — this is what makes the old manager app
                    // suddenly start showing real numbers.
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
                    pending_orders = pendingOrders,
                )
            }

            val current = metricsForPeriod(from, to)
            val previous = metricsForPeriod(prevFrom, prevTo)

            fun pctChange(cur: Double, prev: Double): Double =
                if (prev == 0.0) if (cur > 0) 100.0 else 0.0
                else ((cur - prev) / prev) * 100.0

            val activeOrders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.status notInList listOf("COMPLETED", "CANCELED", "CANCELLED", "REFUNDED"))
            }.count().toInt()

            val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            val attendanceToday = AttendanceTable.selectAll().where {
                (AttendanceTable.vendorId eq vendorId) and
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
    }

    // ── 2. Revenue & Profit ──────────────────────────────────────────────

    fun getRevenueProfit(vendorId: UUID, from: Instant, to: Instant): RevenueProfitDto {
        return transaction {
            val excludedFromRevenue = listOf("CANCELED", "CANCELLED", "REFUNDED")
            val orders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to) and
                        (OrdersTable.status notInList excludedFromRevenue)
            }.toList()

            val paidOrders = orders.filter { it[OrdersTable.paymentStatus] == "PAID" }
            val gross = paidOrders.sumOf { it[OrdersTable.total].toDouble() }
            val deliveryFees = paidOrders
                .filter { it[OrdersTable.channel] == "DELIVERY" }
                .sumOf { it[OrdersTable.deliveryFee].toDouble() }
            val net = gross - deliveryFees

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
                total_delivery_fees = deliveryFees,
                net_revenue = net,
                payment_methods = paymentMethods,
                daily_trend = dailyTrend,
            )
        }
    }

    // ── 3. Orders Intelligence ───────────────────────────────────────────

    fun getOrdersIntelligence(vendorId: UUID, from: Instant, to: Instant): OrdersIntelligenceDto {
        return transaction {
            val orders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to)
            }.toList()

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
    }

    // ── 4. Peak Time Analysis ────────────────────────────────────────────

    fun getPeakTimes(vendorId: UUID, from: Instant, to: Instant): PeakTimeAnalysisDto {
        return transaction {
            val orders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
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

            val hourlyMap = (0..23).associateWith { h ->
                val hourOrders = timeData.filter { it.hour == h }
                HourlyDataDto(hour = h, order_count = hourOrders.size, revenue = hourOrders.sumOf { it.revenue })
            }
            val busiestHour = hourlyMap.maxByOrNull { it.value.order_count }?.key ?: 0

            val heatmap = mutableListOf<HeatmapPointDto>()
            for (day in 1..7) {
                for (hour in 0..23) {
                    val count = timeData.count { it.dayOfWeek == day && it.hour == hour }
                    heatmap.add(HeatmapPointDto(day_of_week = day, hour = hour, order_count = count))
                }
            }

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
    }

    // ── 5. Cashier Performance V2 ────────────────────────────────────────

    fun getCashierPerformance(vendorId: UUID, from: Instant, to: Instant): List<CashierPerformanceV2Dto> {
        return transaction {
            val cashiers = UsersTable.selectAll().where {
                (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "CASHIER")
            }.associateBy { it[UsersTable.id] }

            val orders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to)
            }.toList()

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
    }

    // ── 6. Delivery Performance V2 ───────────────────────────────────────

    fun getDeliveryPerformance(vendorId: UUID, from: Instant, to: Instant): List<DeliveryPerformanceV2Dto> {
        return transaction {
            val drivers = UsersTable.selectAll().where {
                (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "DELIVERY")
            }.associateBy { it[UsersTable.id] }

            val orders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
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
                    avg_delivery_time_minutes = 0.0,
                    late_delivery_percent = 0.0,
                )
            }
        }
    }

    // ── 7. Product Intelligence ──────────────────────────────────────────

    fun getProductIntelligence(vendorId: UUID, from: Instant, to: Instant, limit: Int = 20): ProductIntelligenceDto {
        return transaction {
            val orderIds = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
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

            val orderItems = OrderItemsTable.selectAll()
                .where { OrderItemsTable.orderId inList orderIds }
                .toList()

            val allItemIds = orderItems.map { it[OrderItemsTable.itemId] }.distinct()
            val itemCategoryMap = ItemsTable.selectAll()
                .where { ItemsTable.id inList allItemIds }
                .associate { it[ItemsTable.id].value to it[ItemsTable.categoryId].value }

            val categoryNames = CategoriesTable.selectAll()
                .where { CategoriesTable.vendorId eq vendorId }
                .associate { it[CategoriesTable.id].value to it[CategoriesTable.name] }

            val stockCostPrices = StockTable.selectAll()
                .where { StockTable.vendorId eq vendorId }
                .associate { (it[StockTable.itemId]?.value ?: it[StockTable.id].value) to it[StockTable.costPrice].toDouble() }

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
    }

    // ── 8. Customer Intelligence ─────────────────────────────────────────

    fun getCustomerIntelligence(vendorId: UUID, from: Instant, to: Instant): CustomerIntelligenceDto {
        return transaction {
            val customers = CustomersTable.selectAll().where {
                CustomersTable.vendorId eq vendorId
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
    }

    // ── 9. Alerts ────────────────────────────────────────────────────────

    fun getAlerts(vendorId: UUID, from: Instant, to: Instant): AlertsDto {
        return transaction {
            val alerts = mutableListOf<AlertDto>()

            // Revenue drop check
            val periodMs = to.toEpochMilliseconds() - from.toEpochMilliseconds()
            val prevFrom = Instant.fromEpochMilliseconds(from.toEpochMilliseconds() - periodMs)

            val currentRevenue = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to)
            }.sumOf { it[OrdersTable.total].toDouble() }

            val previousRevenue = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
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
                (OrdersTable.vendorId eq vendorId) and
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

            // Low stock & out of stock
            val stockItems = StockTable.selectAll().where {
                (StockTable.vendorId eq vendorId) and (StockTable.alertEnabled eq true)
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

            AlertsDto(alerts = alerts)
        }
    }

    // ── 10. Stock Overview ───────────────────────────────────────────────

    fun getStockOverview(vendorId: UUID): StockOverviewDto {
        return transaction {
            val stockItems = StockTable.selectAll().where {
                StockTable.vendorId eq vendorId
            }.toList()

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
            val deadStockItems = mutableListOf<StockOverviewItemDto>()

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
    }

    // ── 11. Offers Analytics ─────────────────────────────────────────────

    fun getOffersAnalytics(vendorId: UUID, from: Instant, to: Instant): OffersAnalyticsResponseDto {
        return transaction {
            val allOffers = OffersTable.selectAll().where {
                OffersTable.vendorId eq vendorId
            }.toList()
            val totalOffers = allOffers.size
            val activeOffers = allOffers.count { it[OffersTable.active] }

            val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")
            val offerOrders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.offerId.isNotNull()) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to) and
                        (OrdersTable.status notInList excludedStatuses)
            }.toList()

            val offerMap = allOffers.associateBy { it[OffersTable.id].value }

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
    }

    // ── 12. Discount Analytics ───────────────────────────────────────────

    fun getDiscountAnalytics(vendorId: UUID, from: Instant, to: Instant): DiscountAnalyticsResponseDto {
        return transaction {
            val excludedStatuses = listOf("CANCELED", "CANCELLED", "REFUNDED")

            val allOrders = OrdersTable.selectAll().where {
                (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq from) and
                        (OrdersTable.createdAt lessEq to) and
                        (OrdersTable.status notInList excludedStatuses)
            }.toList()
            val totalOrderCount = allOrders.size

            val discountedOrders = allOrders.filter {
                it[OrdersTable.discount].compareTo(java.math.BigDecimal.ZERO) > 0
            }
            val totalOrdersWithDiscount = discountedOrders.size
            val totalDiscountGiven = discountedOrders.sumOf { it[OrdersTable.discount].toDouble() }
            val avgDiscountPerOrder = if (totalOrdersWithDiscount > 0) totalDiscountGiven / totalOrdersWithDiscount else 0.0
            val discountRate = if (totalOrderCount > 0) (totalOrdersWithDiscount.toDouble() / totalOrderCount) * 100.0 else 0.0

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
                    type = "MANUAL", count = manualCount, total_amount = manualAmount,
                    percent_of_total = if (totalDiscountGiven > 0) (manualAmount / totalDiscountGiven) * 100.0 else 0.0,
                ),
                DiscountBreakdownDto(
                    type = "OFFER", count = offerCount, total_amount = offerAmount,
                    percent_of_total = if (totalDiscountGiven > 0) (offerAmount / totalDiscountGiven) * 100.0 else 0.0,
                ),
                DiscountBreakdownDto(
                    type = "POINTS", count = pointsCount, total_amount = pointsAmount,
                    percent_of_total = if (totalDiscountGiven > 0) (pointsAmount / totalDiscountGiven) * 100.0 else 0.0,
                ),
            )

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

            DiscountAnalyticsResponseDto(
                total_orders_with_discount = totalOrdersWithDiscount,
                total_discount_given = totalDiscountGiven,
                average_discount_per_order = avgDiscountPerOrder,
                discount_rate = discountRate,
                breakdown = breakdown,
                daily_trend = dailyTrend,
            )
        }
    }

    // ── 13. Loyalty Analytics ────────────────────────────────────────────

    fun getLoyaltyAnalytics(vendorId: UUID, from: Instant, to: Instant): LoyaltyAnalyticsResponseDto {
        return transaction {
            val pointsTxs = PointsTransactionsTable.selectAll().where {
                (PointsTransactionsTable.vendorId eq vendorId) and
                        (PointsTransactionsTable.createdAt greaterEq from) and
                        (PointsTransactionsTable.createdAt lessEq to)
            }.toList()

            val totalEarned = pointsTxs
                .filter { it[PointsTransactionsTable.type] == "EARN" }
                .sumOf { it[PointsTransactionsTable.points].toLong() }
            val totalRedeemed = pointsTxs
                .filter { it[PointsTransactionsTable.type] == "REDEEM" }
                .sumOf { it[PointsTransactionsTable.points].toLong() }

            val activeLoyaltyCustomers = CustomersTable.selectAll().where {
                (CustomersTable.vendorId eq vendorId) and
                        (CustomersTable.pointsBalance greater 0)
            }.count().toInt()

            val totalPointsOutstanding = CustomersTable.selectAll().where {
                CustomersTable.vendorId eq vendorId
            }.sumOf { it[CustomersTable.pointsBalance].toLong() }

            val redemptionRate = if (totalEarned > 0) (totalRedeemed.toDouble() / totalEarned) * 100.0 else 0.0

            val vendorRow = VendorsTable.selectAll().where {
                VendorsTable.id eq vendorId
            }.firstOrNull()
            val pointsRedeemRate = vendorRow?.get(VendorsTable.pointsRedeemRate)?.toDouble() ?: 0.1
            val pointsToRevenue = totalRedeemed * pointsRedeemRate

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
    }

    // ── 14. Staff Costs Analytics ──────────────────────────────────────

    fun getStaffCostsAnalytics(vendorId: UUID, from: Instant, to: Instant): StaffCostsAnalyticsDto {
        return transaction {
            // Salary payments in the period (by period_start falling within range)
            val fromDate = from.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            val toDate = to.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            val salaryPayments = SalaryPaymentsTable.selectAll().where {
                (SalaryPaymentsTable.vendorId eq vendorId) and
                        (SalaryPaymentsTable.periodStart greaterEq fromDate) and
                        (SalaryPaymentsTable.periodStart lessEq toDate)
            }.toList()

            val totalSalaries = salaryPayments.sumOf { it[SalaryPaymentsTable.amount].toDouble() }
            val totalOvertimeFromSalary = salaryPayments.sumOf { it[SalaryPaymentsTable.overtimeAmount].toDouble() }
            val totalOvertimeHoursFromSalary = salaryPayments.sumOf { it[SalaryPaymentsTable.overtimeHours].toDouble() }

            val paidPayments = salaryPayments.filter { it[SalaryPaymentsTable.paid] }
            val unpaidPayments = salaryPayments.filter { !it[SalaryPaymentsTable.paid] }

            val paidAmount = paidPayments.sumOf {
                it[SalaryPaymentsTable.amount].toDouble() + it[SalaryPaymentsTable.overtimeAmount].toDouble()
            }
            val unpaidAmount = unpaidPayments.sumOf {
                it[SalaryPaymentsTable.amount].toDouble() + it[SalaryPaymentsTable.overtimeAmount].toDouble()
            }

            val totalCompensation = totalSalaries + totalOvertimeFromSalary
            val overtimePercentage = if (totalCompensation > 0) (totalOvertimeFromSalary / totalCompensation) * 100.0 else 0.0

            val workerIds = salaryPayments.map { it[SalaryPaymentsTable.workerId] }.distinct()

            // Top overtime workers from overtime_entries table
            val overtimeEntries = OvertimeTable.selectAll().where {
                (OvertimeTable.vendorId eq vendorId) and
                        (OvertimeTable.date greaterEq fromDate) and
                        (OvertimeTable.date lessEq toDate)
            }.toList()

            val workerOvertimeMap = overtimeEntries.groupBy { it[OvertimeTable.workerId] }
            val topOvertimeWorkers = workerOvertimeMap.entries
                .map { (wId, entries) ->
                    val workerName = WorkersTable.selectAll()
                        .where { WorkersTable.id eq wId }
                        .firstOrNull()?.get(WorkersTable.fullName) ?: "Unknown"
                    WorkerOvertimeSummaryDto(
                        worker_id = wId.value.toString(),
                        worker_name = workerName,
                        overtime_hours = entries.sumOf { it[OvertimeTable.hours].toDouble() },
                        overtime_amount = entries.sumOf { it[OvertimeTable.amount].toDouble() },
                    )
                }
                .sortedByDescending { it.overtime_amount }
                .take(5)

            StaffCostsAnalyticsDto(
                total_salaries = totalSalaries,
                total_overtime = totalOvertimeFromSalary,
                total_compensation = totalCompensation,
                paid_amount = paidAmount,
                unpaid_amount = unpaidAmount,
                overtime_hours = totalOvertimeHoursFromSalary,
                workers_count = workerIds.size,
                overtime_percentage = overtimePercentage,
                top_overtime_workers = topOvertimeWorkers,
            )
        }
    }

    // ── 15. Supplier Analytics ────────────────────────────────────────────

    fun getSupplierAnalytics(vendorId: UUID, from: Instant, to: Instant): SupplierAnalyticsDto {
        return transaction {
            // All suppliers for this vendor
            val allSuppliers = SuppliersTable.selectAll().where {
                SuppliersTable.vendorId eq vendorId
            }.toList()

            val totalSuppliers = allSuppliers.size
            val activeSuppliers = allSuppliers.count { it[SuppliersTable.active] }

            // Purchase orders in the date range
            val purchaseOrders = PurchaseOrdersTable.selectAll().where {
                (PurchaseOrdersTable.vendorId eq vendorId) and
                        (PurchaseOrdersTable.createdAt greaterEq from) and
                        (PurchaseOrdersTable.createdAt lessEq to)
            }.toList()

            val totalPurchaseOrders = purchaseOrders.size
            val totalSpent = purchaseOrders
                .filter { it[PurchaseOrdersTable.status] != "CANCELLED" }
                .sumOf { it[PurchaseOrdersTable.total].toDouble() }
            val pendingOrders = purchaseOrders.count { it[PurchaseOrdersTable.status] in listOf("DRAFT", "SUBMITTED") }
            val receivedOrders = purchaseOrders.count { it[PurchaseOrdersTable.status] in listOf("RECEIVED", "PARTIALLY_RECEIVED") }
            val avgOrderValue = if (totalPurchaseOrders > 0) totalSpent / totalPurchaseOrders else 0.0

            // Top suppliers by total spent
            val poBySupplier = purchaseOrders
                .filter { it[PurchaseOrdersTable.status] != "CANCELLED" }
                .groupBy { it[PurchaseOrdersTable.supplierId] }

            val topSuppliers = poBySupplier.entries.map { (suppId, orders) ->
                val supplierName = allSuppliers.find { it[SuppliersTable.id] == suppId }
                    ?.get(SuppliersTable.name) ?: "Unknown"
                TopSupplierDto(
                    supplier_id = suppId.value.toString(),
                    supplier_name = supplierName,
                    total_orders = orders.size,
                    total_spent = orders.sumOf { it[PurchaseOrdersTable.total].toDouble() },
                    received_orders = orders.count { it[PurchaseOrdersTable.status] in listOf("RECEIVED", "PARTIALLY_RECEIVED") },
                    pending_orders = orders.count { it[PurchaseOrdersTable.status] in listOf("DRAFT", "SUBMITTED") },
                )
            }.sortedByDescending { it.total_spent }.take(10)

            // Top purchased items
            val poIds = purchaseOrders
                .filter { it[PurchaseOrdersTable.status] != "CANCELLED" }
                .map { it[PurchaseOrdersTable.id] }

            val topItems = if (poIds.isNotEmpty()) {
                val poItems = PurchaseOrderItemsTable.selectAll().where {
                    PurchaseOrderItemsTable.purchaseOrderId inList poIds
                }.toList()

                poItems.groupBy { it[PurchaseOrderItemsTable.stockId] }.entries.map { (stockId, items) ->
                    val stockName = StockTable.selectAll()
                        .where { StockTable.id eq stockId }
                        .firstOrNull()?.get(StockTable.itemName) ?: "Unknown"
                    SupplierItemDto(
                        stock_id = stockId.value.toString(),
                        item_name = stockName,
                        total_quantity = items.sumOf { it[PurchaseOrderItemsTable.requestedQuantity].toDouble() },
                        total_cost = items.sumOf { it[PurchaseOrderItemsTable.totalCost].toDouble() },
                        order_count = items.map { it[PurchaseOrderItemsTable.purchaseOrderId] }.distinct().size,
                        unit = items.first()[PurchaseOrderItemsTable.unit],
                    )
                }.sortedByDescending { it.total_cost }.take(10)
            } else emptyList()

            // Monthly purchase trend
            val tz = TimeZone.currentSystemDefault()
            val monthlyTrend = purchaseOrders
                .filter { it[PurchaseOrdersTable.status] != "CANCELLED" }
                .groupBy {
                    val ldt = it[PurchaseOrdersTable.createdAt].toLocalDateTime(tz)
                    "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}"
                }
                .map { (month, orders) ->
                    MonthlyPurchaseDto(
                        month = month,
                        total = orders.sumOf { it[PurchaseOrdersTable.total].toDouble() },
                        order_count = orders.size,
                    )
                }
                .sortedBy { it.month }

            SupplierAnalyticsDto(
                total_suppliers = totalSuppliers,
                active_suppliers = activeSuppliers,
                total_purchase_orders = totalPurchaseOrders,
                total_spent = totalSpent,
                pending_orders = pendingOrders,
                received_orders = receivedOrders,
                average_order_value = avgOrderValue,
                top_suppliers = topSuppliers,
                top_items = topItems,
                monthly_trend = monthlyTrend,
            )
        }
    }
}
