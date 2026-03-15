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
    val quantity: Double,
    val minQuantity: Double,
    val costPrice: Double,
    val unit: String,
    val status: String,
)

data class StockMovement(
    val date: String,
    val added: Double,
    val deducted: Double,
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

// ─── Offers Analytics ──────────────────────────────────────────
data class OfferPerformanceItem(
    val offerId: String,
    val offerName: String,
    val discountType: String,
    val discountValue: Double,
    val usageCount: Int,
    val totalDiscountGiven: Double,
    val totalRevenueFromOfferOrders: Double,
    val promoCode: String? = null,
    val isActive: Boolean,
)
data class DailyOfferUsage(val date: String, val usageCount: Int, val discountAmount: Double)
data class OffersAnalytics(
    val totalOffers: Int,
    val activeOffers: Int,
    val totalOfferUses: Int,
    val totalDiscountFromOffers: Double,
    val averageDiscountPerUse: Double,
    val topOffers: List<OfferPerformanceItem>,
    val offerUsageTrend: List<DailyOfferUsage>,
)

// ─── Discount Analytics ────────────────────────────────────────
data class DiscountBreakdown(
    val type: String,
    val count: Int,
    val totalAmount: Double,
    val percentOfTotal: Double,
)
data class DailyDiscount(val date: String, val manualDiscount: Double, val offerDiscount: Double, val pointsDiscount: Double)
data class DiscountAnalytics(
    val totalOrdersWithDiscount: Int,
    val totalDiscountGiven: Double,
    val averageDiscountPerOrder: Double,
    val discountRate: Double,
    val breakdown: List<DiscountBreakdown>,
    val dailyTrend: List<DailyDiscount>,
)

// ─── Loyalty Analytics ─────────────────────────────────────────
data class DailyLoyalty(val date: String, val pointsEarned: Int, val pointsRedeemed: Int)
data class LoyaltyAnalytics(
    val totalPointsEarned: Long,
    val totalPointsRedeemed: Long,
    val totalPointsOutstanding: Long,
    val activeLoyaltyCustomers: Int,
    val redemptionRate: Double,
    val pointsToRevenue: Double,
    val dailyTrend: List<DailyLoyalty>,
)

// ─── Supplier Analytics ──────────────────────────────────────
data class TopSupplier(
    val supplierId: String,
    val supplierName: String,
    val totalOrders: Int,
    val totalSpent: Double,
    val receivedOrders: Int,
    val pendingOrders: Int,
)

data class SupplierItem(
    val stockId: String,
    val itemName: String,
    val totalQuantity: Double,
    val totalCost: Double,
    val orderCount: Int,
    val unit: String,
)

data class MonthlyPurchase(
    val month: String,
    val total: Double,
    val orderCount: Int,
)

data class SupplierAnalytics(
    val totalSuppliers: Int,
    val activeSuppliers: Int,
    val totalPurchaseOrders: Int,
    val totalSpent: Double,
    val pendingOrders: Int,
    val receivedOrders: Int,
    val averageOrderValue: Double,
    val topSuppliers: List<TopSupplier>,
    val topItems: List<SupplierItem>,
    val monthlyTrend: List<MonthlyPurchase>,
)

// ─── Staff Costs Analytics ────────────────────────────────────
data class WorkerOvertimeSummary(
    val workerId: String,
    val workerName: String,
    val overtimeHours: Double,
    val overtimeAmount: Double,
)

data class StaffCostsAnalytics(
    val totalSalaries: Double,
    val totalOvertime: Double,
    val totalCompensation: Double,
    val paidAmount: Double,
    val unpaidAmount: Double,
    val overtimeHours: Double,
    val workersCount: Int,
    val overtimePercentage: Double,
    val topOvertimeWorkers: List<WorkerOvertimeSummary>,
)
