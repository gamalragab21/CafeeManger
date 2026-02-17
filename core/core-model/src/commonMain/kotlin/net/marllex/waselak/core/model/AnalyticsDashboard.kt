package net.marllex.waselak.core.model

// ══════════════════════════════════════════════════════════════════════
// Analytics Dashboard V2 — Domain Models
// ══════════════════════════════════════════════════════════════════════

// ── Executive Summary ────────────────────────────────────────────────
data class PeriodMetrics(
    val totalRevenue: Double,
    val totalOrders: Int,
    val averageOrderValue: Double,
    val totalDeliveryFees: Double,
    val totalDiscounts: Double,
)

data class ExecutiveSummary(
    val current: PeriodMetrics,
    val previous: PeriodMetrics,
    val revenueChangePercent: Double,
    val ordersChangePercent: Double,
    val aovChangePercent: Double,
    val activeOrders: Int,
    val attendanceToday: Int,
)

// ── Revenue & Profit ─────────────────────────────────────────────────
data class PaymentMethodDetail(
    val method: String,
    val orderCount: Int,
    val revenue: Double,
)

data class DailyRevenuePoint(
    val date: String,
    val revenue: Double,
)

data class RevenueProfit(
    val grossRevenue: Double,
    val totalDeliveryFees: Double,
    val netRevenue: Double,
    val paymentMethods: List<PaymentMethodDetail>,
    val dailyTrend: List<DailyRevenuePoint>,
)

// ── Orders Intelligence ──────────────────────────────────────────────
data class DailyOrderTrendPoint(
    val date: String,
    val total: Int,
    val completed: Int,
    val cancelled: Int,
)

data class ChannelBreakdown(
    val channel: String,
    val count: Int,
    val percent: Double,
)

data class OrdersIntelligence(
    val totalOrders: Int,
    val completedOrders: Int,
    val cancelledOrders: Int,
    val refundedOrders: Int,
    val ordersByChannel: Map<String, Int>,
    val dailyTrend: List<DailyOrderTrendPoint>,
    val channelBreakdown: List<ChannelBreakdown>,
)

// ── Peak Time Analysis ───────────────────────────────────────────────
data class HourlyData(
    val hour: Int,
    val orderCount: Int,
    val revenue: Double,
)

data class HeatmapPoint(
    val dayOfWeek: Int,
    val hour: Int,
    val orderCount: Int,
)

data class DayOfWeekData(
    val dayOfWeek: Int,
    val name: String,
    val orderCount: Int,
    val revenue: Double,
)

data class PeakTimeAnalysis(
    val busiestHour: Int,
    val busiestDay: String,
    val hourlyData: List<HourlyData>,
    val heatmap: List<HeatmapPoint>,
    val dayOfWeek: List<DayOfWeekData>,
)

// ── Cashier Performance V2 ──────────────────────────────────────────
data class CashierPerformanceV2(
    val cashierId: String,
    val cashierName: String,
    val revenue: Double,
    val orderCount: Int,
    val averageOrderValue: Double,
    val cancelledOrders: Int,
    val cancellationRate: Double,
)

// ── Delivery Performance V2 ─────────────────────────────────────────
data class DeliveryPerformanceV2(
    val driverId: String,
    val driverName: String,
    val ordersCompleted: Int,
    val feesCollected: Double,
    val revenue: Double,
    val avgDeliveryTimeMinutes: Double,
    val lateDeliveryPercent: Double,
)

// ── Product Intelligence ────────────────────────────────────────────
data class ProductItem(
    val itemId: String,
    val itemName: String,
    val categoryName: String,
    val quantitySold: Int,
    val revenue: Double,
    val costPrice: Double,
    val profitMargin: Double,
)

data class CategoryRevenue(
    val categoryId: String,
    val categoryName: String,
    val revenue: Double,
    val itemCount: Int,
)

data class ProductIntelligence(
    val topSelling: List<ProductItem>,
    val mostProfitable: List<ProductItem>,
    val leastSelling: List<ProductItem>,
    val revenueByCategory: List<CategoryRevenue>,
    val lowMarginWarnings: List<ProductItem>,
)

// ── Customer Intelligence ───────────────────────────────────────────
data class TopCustomer(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val orderCount: Int,
    val totalSpent: Double,
)

data class CustomerIntelligence(
    val totalCustomers: Int,
    val newCustomersPercent: Double,
    val returningCustomersPercent: Double,
    val averageSpend: Double,
    val lifetimeValue: Double,
    val topCustomers: List<TopCustomer>,
    val frequencyBuckets: Map<String, Int>,
)

// ── Alerts ──────────────────────────────────────────────────────────
data class AnalyticsAlert(
    val type: String,
    val severity: String,
    val title: String,
    val message: String,
    val value: Double,
    val threshold: Double,
)

// ── Stock Overview ──────────────────────────────────────────────────
data class StockOverviewItem(
    val stockId: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int,
    val costPrice: Double,
    val unit: String,
    val status: String,
)

data class StockMovement(
    val date: String,
    val added: Int,
    val deducted: Int,
)

data class StockOverview(
    val totalStockValue: Double,
    val totalSellingValue: Double,
    val potentialProfit: Double,
    val totalItems: Int,
    val lowStockItems: List<StockOverviewItem>,
    val outOfStockItems: List<StockOverviewItem>,
    val deadStockItems: List<StockOverviewItem>,
    val movementSummary: List<StockMovement>,
)
