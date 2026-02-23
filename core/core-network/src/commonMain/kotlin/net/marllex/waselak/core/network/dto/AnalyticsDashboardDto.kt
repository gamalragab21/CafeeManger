package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════════
// Analytics Dashboard V2 — Network DTOs
// ══════════════════════════════════════════════════════════════════════

// ── Executive Summary ────────────────────────────────────────────────
@Serializable
data class PeriodMetricsResponse(
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("average_order_value") val averageOrderValue: Double,
    @SerialName("total_delivery_fees") val totalDeliveryFees: Double,
    @SerialName("total_discounts") val totalDiscounts: Double,
)

@Serializable
data class ExecutiveSummaryResponse(
    val current: PeriodMetricsResponse,
    val previous: PeriodMetricsResponse,
    @SerialName("revenue_change_percent") val revenueChangePercent: Double,
    @SerialName("orders_change_percent") val ordersChangePercent: Double,
    @SerialName("aov_change_percent") val aovChangePercent: Double,
    @SerialName("active_orders") val activeOrders: Int,
    @SerialName("attendance_today") val attendanceToday: Int,
)

// ── Revenue & Profit ─────────────────────────────────────────────────
@Serializable
data class PaymentMethodDetailResponse(
    val method: String,
    @SerialName("order_count") val orderCount: Int,
    val revenue: Double,
)

@Serializable
data class DailyRevenuePointResponse(
    val date: String,
    val revenue: Double,
)

@Serializable
data class RevenueProfitResponse(
    @SerialName("gross_revenue") val grossRevenue: Double,
    @SerialName("total_delivery_fees") val totalDeliveryFees: Double,
    @SerialName("net_revenue") val netRevenue: Double,
    @SerialName("payment_methods") val paymentMethods: List<PaymentMethodDetailResponse>,
    @SerialName("daily_trend") val dailyTrend: List<DailyRevenuePointResponse>,
)

// ── Orders Intelligence ──────────────────────────────────────────────
@Serializable
data class DailyOrderTrendPointResponse(
    val date: String,
    val total: Int,
    val completed: Int,
    val cancelled: Int,
)

@Serializable
data class ChannelBreakdownResponse(
    val channel: String,
    val count: Int,
    val percent: Double,
)

@Serializable
data class OrdersIntelligenceResponse(
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("completed_orders") val completedOrders: Int,
    @SerialName("cancelled_orders") val cancelledOrders: Int,
    @SerialName("refunded_orders") val refundedOrders: Int,
    @SerialName("orders_by_channel") val ordersByChannel: Map<String, Int>,
    @SerialName("daily_trend") val dailyTrend: List<DailyOrderTrendPointResponse>,
    @SerialName("channel_breakdown") val channelBreakdown: List<ChannelBreakdownResponse>,
)

// ── Peak Time Analysis ───────────────────────────────────────────────
@Serializable
data class HourlyDataResponse(
    val hour: Int,
    @SerialName("order_count") val orderCount: Int,
    val revenue: Double,
)

@Serializable
data class HeatmapPointResponse(
    @SerialName("day_of_week") val dayOfWeek: Int,
    val hour: Int,
    @SerialName("order_count") val orderCount: Int,
)

@Serializable
data class DayOfWeekResponse(
    @SerialName("day_of_week") val dayOfWeek: Int,
    val name: String,
    @SerialName("order_count") val orderCount: Int,
    val revenue: Double,
)

@Serializable
data class PeakTimeAnalysisResponse(
    @SerialName("busiest_hour") val busiestHour: Int,
    @SerialName("busiest_day") val busiestDay: String,
    @SerialName("hourly_data") val hourlyData: List<HourlyDataResponse>,
    val heatmap: List<HeatmapPointResponse>,
    @SerialName("day_of_week") val dayOfWeek: List<DayOfWeekResponse>,
)

// ── Cashier Performance V2 ──────────────────────────────────────────
@Serializable
data class CashierPerformanceV2Response(
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("cashier_name") val cashierName: String,
    val revenue: Double,
    @SerialName("order_count") val orderCount: Int,
    @SerialName("average_order_value") val averageOrderValue: Double,
    @SerialName("cancelled_orders") val cancelledOrders: Int,
    @SerialName("cancellation_rate") val cancellationRate: Double,
)

// ── Delivery Performance V2 ─────────────────────────────────────────
@Serializable
data class DeliveryPerformanceV2Response(
    @SerialName("driver_id") val driverId: String,
    @SerialName("driver_name") val driverName: String,
    @SerialName("orders_completed") val ordersCompleted: Int,
    @SerialName("fees_collected") val feesCollected: Double,
    val revenue: Double,
    @SerialName("avg_delivery_time_minutes") val avgDeliveryTimeMinutes: Double,
    @SerialName("late_delivery_percent") val lateDeliveryPercent: Double,
)

// ── Product Intelligence ────────────────────────────────────────────
@Serializable
data class ProductItemResponse(
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("quantity_sold") val quantitySold: Int,
    val revenue: Double,
    @SerialName("cost_price") val costPrice: Double,
    @SerialName("profit_margin") val profitMargin: Double,
)

@Serializable
data class CategoryRevenueResponse(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    val revenue: Double,
    @SerialName("item_count") val itemCount: Int,
)

@Serializable
data class ProductIntelligenceResponse(
    @SerialName("top_selling") val topSelling: List<ProductItemResponse>,
    @SerialName("most_profitable") val mostProfitable: List<ProductItemResponse>,
    @SerialName("least_selling") val leastSelling: List<ProductItemResponse>,
    @SerialName("revenue_by_category") val revenueByCategory: List<CategoryRevenueResponse>,
    @SerialName("low_margin_warnings") val lowMarginWarnings: List<ProductItemResponse>,
)

// ── Customer Intelligence ───────────────────────────────────────────
@Serializable
data class TopCustomerResponse(
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String,
    val phone: String,
    @SerialName("order_count") val orderCount: Int,
    @SerialName("total_spent") val totalSpent: Double,
)

@Serializable
data class CustomerIntelligenceResponse(
    @SerialName("total_customers") val totalCustomers: Int,
    @SerialName("new_customers_percent") val newCustomersPercent: Double,
    @SerialName("returning_customers_percent") val returningCustomersPercent: Double,
    @SerialName("average_spend") val averageSpend: Double,
    @SerialName("lifetime_value") val lifetimeValue: Double,
    @SerialName("top_customers") val topCustomers: List<TopCustomerResponse>,
    @SerialName("frequency_buckets") val frequencyBuckets: Map<String, Int>,
)

// ── Alerts ──────────────────────────────────────────────────────────
@Serializable
data class AlertResponse(
    val type: String,
    val severity: String,
    val title: String,
    val message: String,
    val value: Double,
    val threshold: Double,
)

@Serializable
data class AlertsResponse(
    val alerts: List<AlertResponse>,
)

// ── Stock Overview ──────────────────────────────────────────────────
@Serializable
data class StockOverviewItemResponse(
    @SerialName("stock_id") val stockId: String,
    @SerialName("item_name") val itemName: String,
    val quantity: Double,
    @SerialName("min_quantity") val minQuantity: Double,
    @SerialName("cost_price") val costPrice: Double,
    val unit: String,
    val status: String,
)

@Serializable
data class StockMovementResponse(
    val date: String,
    val added: Double,
    val deducted: Double,
)

@Serializable
data class StockOverviewResponse(
    @SerialName("total_stock_value") val totalStockValue: Double,
    @SerialName("total_selling_value") val totalSellingValue: Double,
    @SerialName("potential_profit") val potentialProfit: Double,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("low_stock_items") val lowStockItems: List<StockOverviewItemResponse>,
    @SerialName("out_of_stock_items") val outOfStockItems: List<StockOverviewItemResponse>,
    @SerialName("dead_stock_items") val deadStockItems: List<StockOverviewItemResponse>,
    @SerialName("movement_summary") val movementSummary: List<StockMovementResponse>,
)
