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

// ── Offers Analytics ──────────────────────────────────────────
@Serializable
data class OfferPerformanceItemResponse(
    @SerialName("offer_id") val offerId: String,
    @SerialName("offer_name") val offerName: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Double,
    @SerialName("usage_count") val usageCount: Int,
    @SerialName("total_discount_given") val totalDiscountGiven: Double,
    @SerialName("total_revenue_from_offer_orders") val totalRevenueFromOfferOrders: Double,
    @SerialName("promo_code") val promoCode: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class DailyOfferUsageResponse(
    val date: String,
    @SerialName("usage_count") val usageCount: Int,
    @SerialName("discount_amount") val discountAmount: Double,
)

@Serializable
data class OffersAnalyticsResponse(
    @SerialName("total_offers") val totalOffers: Int,
    @SerialName("active_offers") val activeOffers: Int,
    @SerialName("total_offer_uses") val totalOfferUses: Int,
    @SerialName("total_discount_from_offers") val totalDiscountFromOffers: Double,
    @SerialName("average_discount_per_use") val averageDiscountPerUse: Double,
    @SerialName("top_offers") val topOffers: List<OfferPerformanceItemResponse>,
    @SerialName("offer_usage_trend") val offerUsageTrend: List<DailyOfferUsageResponse>,
)

// ── Discount Analytics ────────────────────────────────────────
@Serializable
data class DiscountBreakdownResponse(
    val type: String,
    val count: Int,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("percent_of_total") val percentOfTotal: Double,
)

@Serializable
data class DailyDiscountResponse(
    val date: String,
    @SerialName("manual_discount") val manualDiscount: Double,
    @SerialName("offer_discount") val offerDiscount: Double,
    @SerialName("points_discount") val pointsDiscount: Double,
)

@Serializable
data class DiscountAnalyticsResponse(
    @SerialName("total_orders_with_discount") val totalOrdersWithDiscount: Int,
    @SerialName("total_discount_given") val totalDiscountGiven: Double,
    @SerialName("average_discount_per_order") val averageDiscountPerOrder: Double,
    @SerialName("discount_rate") val discountRate: Double,
    val breakdown: List<DiscountBreakdownResponse>,
    @SerialName("daily_trend") val dailyTrend: List<DailyDiscountResponse>,
)

// ── Loyalty Analytics ─────────────────────────────────────────
@Serializable
data class DailyLoyaltyResponse(
    val date: String,
    @SerialName("points_earned") val pointsEarned: Int,
    @SerialName("points_redeemed") val pointsRedeemed: Int,
)

@Serializable
data class LoyaltyAnalyticsResponse(
    @SerialName("total_points_earned") val totalPointsEarned: Long,
    @SerialName("total_points_redeemed") val totalPointsRedeemed: Long,
    @SerialName("total_points_outstanding") val totalPointsOutstanding: Long,
    @SerialName("active_loyalty_customers") val activeLoyaltyCustomers: Int,
    @SerialName("redemption_rate") val redemptionRate: Double,
    @SerialName("points_to_revenue") val pointsToRevenue: Double,
    @SerialName("daily_trend") val dailyTrend: List<DailyLoyaltyResponse>,
)

// ── Supplier Analytics ───────────────────────────────────────
@Serializable
data class TopSupplierResponse(
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("supplier_name") val supplierName: String,
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("total_spent") val totalSpent: Double,
    @SerialName("received_orders") val receivedOrders: Int,
    @SerialName("pending_orders") val pendingOrders: Int,
)

@Serializable
data class SupplierItemResponse(
    @SerialName("stock_id") val stockId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("total_quantity") val totalQuantity: Double,
    @SerialName("total_cost") val totalCost: Double,
    @SerialName("order_count") val orderCount: Int,
    val unit: String,
)

@Serializable
data class MonthlyPurchaseResponse(
    val month: String,
    val total: Double,
    @SerialName("order_count") val orderCount: Int,
)

@Serializable
data class SupplierAnalyticsResponse(
    @SerialName("total_suppliers") val totalSuppliers: Int,
    @SerialName("active_suppliers") val activeSuppliers: Int,
    @SerialName("total_purchase_orders") val totalPurchaseOrders: Int,
    @SerialName("total_spent") val totalSpent: Double,
    @SerialName("pending_orders") val pendingOrders: Int,
    @SerialName("received_orders") val receivedOrders: Int,
    @SerialName("average_order_value") val averageOrderValue: Double,
    @SerialName("top_suppliers") val topSuppliers: List<TopSupplierResponse>,
    @SerialName("top_items") val topItems: List<SupplierItemResponse>,
    @SerialName("monthly_trend") val monthlyTrend: List<MonthlyPurchaseResponse>,
)

// ── Staff Costs Analytics ─────────────────────────────────────
@Serializable
data class WorkerOvertimeSummaryResponse(
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_name") val workerName: String,
    @SerialName("overtime_hours") val overtimeHours: Double,
    @SerialName("overtime_amount") val overtimeAmount: Double,
)

@Serializable
data class StaffCostsAnalyticsResponse(
    @SerialName("total_salaries") val totalSalaries: Double,
    @SerialName("total_overtime") val totalOvertime: Double,
    @SerialName("total_compensation") val totalCompensation: Double,
    @SerialName("paid_amount") val paidAmount: Double,
    @SerialName("unpaid_amount") val unpaidAmount: Double,
    @SerialName("overtime_hours") val overtimeHours: Double,
    @SerialName("workers_count") val workersCount: Int,
    @SerialName("overtime_percentage") val overtimePercentage: Double,
    @SerialName("top_overtime_workers") val topOvertimeWorkers: List<WorkerOvertimeSummaryResponse>,
)

// ── Credit Analytics ────────────────────────────────────────────
@Serializable
data class CreditAnalyticsResponse(
    @SerialName("total_outstanding") val totalOutstanding: Double,
    @SerialName("total_credit_limit") val totalCreditLimit: Double,
    @SerialName("utilization_percent") val utilizationPercent: Double,
    @SerialName("total_credit_customers") val totalCreditCustomers: Int,
    @SerialName("total_charges") val totalCharges: Double,
    @SerialName("total_payments") val totalPayments: Double,
    @SerialName("credit_orders_count") val creditOrdersCount: Int,
    @SerialName("credit_orders_revenue") val creditOrdersRevenue: Double,
    @SerialName("top_debtors") val topDebtors: List<CreditDebtorResponse> = emptyList(),
)

@Serializable
data class CreditDebtorResponse(
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_phone") val customerPhone: String? = null,
    val balance: Double,
    @SerialName("credit_limit") val creditLimit: Double,
)

@Serializable
data class ReturnsAnalyticsResponse(
    @SerialName("total_returns") val totalReturns: Int = 0,
    @SerialName("total_refunds") val totalRefunds: Int = 0,
    @SerialName("total_exchanges") val totalExchanges: Int = 0,
    @SerialName("total_refunded_amount") val totalRefundedAmount: Double = 0.0,
    @SerialName("total_exchanged_amount") val totalExchangedAmount: Double = 0.0,
    @SerialName("total_returned_items") val totalReturnedItems: Int = 0,
    @SerialName("returned_items_breakdown") val returnedItemsBreakdown: List<ReturnsItemBreakdownResponse> = emptyList(),
    @SerialName("exchange_items") val exchangeItems: List<ExchangeItemBreakdownResponse> = emptyList(),
)

@Serializable
data class ReturnsItemBreakdownResponse(
    @SerialName("item_name") val itemName: String,
    @SerialName("total_quantity") val totalQuantity: Int = 0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
)

@Serializable
data class ExchangeItemBreakdownResponse(
    @SerialName("item_name") val itemName: String,
    val quantity: Int = 0,
    val price: Double = 0.0,
)
