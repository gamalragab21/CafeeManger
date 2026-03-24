package net.marllex.waselak.admin.network

import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsDto(
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

@Serializable
data class UpdateAppSettingsRequest(
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

@Serializable
data class LoginResponse(
    val token: String,
    val refresh_token: String,
    val admin_id: String,
    val name: String,
    val email: String
)

@Serializable
data class RefreshTokenResponse(
    val token: String,
    val refresh_token: String,
)

@Serializable
data class AdminProfile(
    val id: String,
    val name: String,
    val email: String,
    val active: Boolean,
    val last_login_at: Long? = null,
    val created_at: Long
)

@Serializable
data class PlanDto(
    val id: String = "",
    val name: String,
    val display_name: String,
    val price_egp: Int,
    val billing_cycle: String = "MONTHLY",
    val max_managers: Int,
    val max_cashiers: Int,
    val max_delivery: Int,
    val max_orders_per_month: Int,
    val max_menu_items: Int,
    val max_branches: Int,
    val stock_management: Boolean,
    val worker_attendance: Boolean,
    val delivery_module: Boolean,
    val overtime: Boolean = false,
    val salaries: Boolean = false,
    val customer_management: Boolean = false,
    val table_management: Boolean = false,
    val digital_receipt: Boolean = false,
    val worker_qrcode: Boolean = false,
    val loyalty_points: Boolean = false,
    val manual_discount: Boolean = false,
    val offers_management: Boolean = false,
    val cash_drawer: Boolean = false,
    val split_payment: Boolean = false,
    val customer_credit: Boolean = false,
    val suppliers: Boolean = false,
    val returns: Boolean = false,
    val prescriptions: Boolean = false,
    val drug_interactions: Boolean = false,
    val scheduled_orders: Boolean = false,
    val kds: Boolean = false,
    val notifications: Boolean = true,
    val analytics: String,
    val digital_menu: String,
    val active: Boolean = true
)

@Serializable
data class PlanUpdateDto(
    val display_name: String? = null,
    val price_egp: Int? = null,
    val max_managers: Int? = null,
    val max_cashiers: Int? = null,
    val max_delivery: Int? = null,
    val max_orders_per_month: Int? = null,
    val max_menu_items: Int? = null,
    val max_branches: Int? = null,
    val stock_management: Boolean? = null,
    val worker_attendance: Boolean? = null,
    val delivery_module: Boolean? = null,
    val overtime: Boolean? = null,
    val salaries: Boolean? = null,
    val customer_management: Boolean? = null,
    val table_management: Boolean? = null,
    val digital_receipt: Boolean? = null,
    val worker_qrcode: Boolean? = null,
    val loyalty_points: Boolean? = null,
    val manual_discount: Boolean? = null,
    val offers_management: Boolean? = null,
    val cash_drawer: Boolean? = null,
    val split_payment: Boolean? = null,
    val customer_credit: Boolean? = null,
    val suppliers: Boolean? = null,
    val returns: Boolean? = null,
    val prescriptions: Boolean? = null,
    val drug_interactions: Boolean? = null,
    val scheduled_orders: Boolean? = null,
    val kds: Boolean? = null,
    val notifications: Boolean? = null,
    val analytics: String? = null,
    val digital_menu: String? = null
)

@Serializable
data class VendorDto(
    val id: String,
    val name: String,
    val address: String,
    val contact_phone: String,
    val business_type: String = "RESTAURANT",
    val is_suspended: Boolean = false,
    val suspension_reason: String? = null,
    val users_count: Int = 0,
    val plan_name: String? = null,
    val plan_display_name: String? = null,
    val subscription_status: String? = null,
    val enable_tables: Boolean = true,
    val enable_kds: Boolean = true,
    val enable_dine_in: Boolean = true,
    val enable_delivery: Boolean = true,
    val enable_takeaway: Boolean = true,
    val enable_in_store: Boolean = false,
    val enable_pickup_later: Boolean = false,
    val tax_enabled: Boolean = false,
    val default_tax_percent: Double = 0.0,
    val stock_mode: String = "NONE",
    val logo_url: String? = null,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double = 0.0,
    val store_type: String? = null,
    val digital_menu_url: String? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0
)

@Serializable
data class CreateVendorRequest(
    val vendor_name: String,
    val vendor_address: String,
    val vendor_phone: String,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double = 0.0,
    val store_type: String? = null,
    val logo_url: String? = null,
    val digital_menu_url: String? = null,
    val business_type: String = "RESTAURANT",
    val enable_tables: Boolean? = null,
    val enable_kds: Boolean? = null,
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    val plan: String = "STARTER",
    val manager_name: String,
    val manager_phone: String,
    val manager_email: String? = null,
    val manager_password: String
)

@Serializable
data class CreateVendorResponse(
    val vendor_id: String,
    val manager_id: String,
    val vendor_name: String,
    val plan: String
)

@Serializable
data class UpdateVendorRequest(
    val name: String? = null,
    val address: String? = null,
    val contact_phone: String? = null,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double? = null,
    val store_type: String? = null,
    val logo_url: String? = null,
    val digital_menu_url: String? = null,
    val business_type: String? = null,
    val enable_tables: Boolean? = null,
    val enable_kds: Boolean? = null,
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    val is_suspended: Boolean? = null,
    val suspension_reason: String? = null,
    // Feature toggles
    val enable_digital_menu: Boolean? = null,
    val enable_recipe: Boolean? = null,
    val enable_split_payment: Boolean? = null,
    val enable_cash_drawer: Boolean? = null,
    val enable_returns: Boolean? = null,
    val enable_customer_credit: Boolean? = null,
    val enable_pre_orders: Boolean? = null,
    val enable_scheduled_orders: Boolean? = null,
    val enable_suppliers: Boolean? = null,
    val enable_drug_interactions: Boolean? = null,
    val enable_prescriptions: Boolean? = null,
    val enable_analytics: Boolean? = null,
    val enable_announcements: Boolean? = null,
    val loyalty_enabled: Boolean? = null,
    // Social links
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

@Serializable
data class VendorUsageDto(
    val plan: PlanUsageInfo,
    val usage: UsageInfo
)

@Serializable
data class PlanUsageInfo(
    val name: String,
    val display_name: String,
    val max_managers: Int,
    val max_cashiers: Int,
    val max_delivery: Int,
    val max_orders_per_month: Int,
    val max_menu_items: Int
)

@Serializable
data class UsageInfo(
    val managers: Int,
    val cashiers: Int,
    val delivery: Int,
    val monthly_orders: Int,
    val menu_items: Int
)

@Serializable
data class AnalyticsOverviewDto(
    val summary: AnalyticsSummary,
    val plan_distribution: PlanDistribution,
    val vendors: List<VendorAnalytics>
)

@Serializable
data class AnalyticsSummary(
    val total_vendors: Int,
    val active_vendors: Int,
    val suspended_vendors: Int,
    val orders_today: Int,
    val orders_this_month: Int,
    val revenue_today: Double,
    val revenue_this_month: Double,
    val total_users: Int,
    val active_users: Int,
    val total_workers: Int,
    val active_workers: Int
)

@Serializable
data class PlanDistribution(
    val starter: Int,
    val business: Int,
    val enterprise: Int
)

@Serializable
data class VendorAnalytics(
    val vendor_id: String,
    val vendor_name: String,
    val is_suspended: Boolean,
    val orders_today: Int,
    val revenue_this_month: Double,
    val total_workers: Int,
    val active_workers: Int
)

// ─── Logs Dashboard DTOs ────────────────────────────────────────

@Serializable
data class LogEntryDto(
    val id: String,
    val vendorId: String? = null,
    val userId: String? = null,
    val userRole: String? = null,
    val method: String,
    val path: String,
    val queryParams: String? = null,
    val statusCode: Int,
    val durationMs: Long,
    val clientIp: String? = null,
    val userAgent: String? = null,
    val requestBody: String? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val resource: String? = null,
    val action: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val traceLog: String? = null,
    val createdAt: String
)

@Serializable
data class PaginatedLogsDto(
    val logs: List<LogEntryDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

@Serializable
data class LogStatsDto(
    val totalRequests: Long,
    val errorCount: Long,
    val errorRate: Double,
    val avgDurationMs: Double,
    val statusBreakdown: Map<String, Long> = emptyMap()
)

@Serializable
data class EndpointStatDto(
    val method: String,
    val path: String,
    val count: Long,
    val avgDurationMs: Double,
    val errorCount: Long = 0
)

@Serializable
data class TimelinePointDto(
    val hour: String,
    val count: Long,
    val errorCount: Long = 0
)

@Serializable
data class LogVendorDto(
    val id: String,
    val name: String
)

@Serializable
data class LogUserDto(
    val id: String,
    val name: String,
    val role: String
)

@Serializable
data class ResourceStatDto(
    val resource: String,
    val count: Long,
    val avgDurationMs: Double,
    val errorCount: Long = 0
)

@Serializable
data class ActionStatDto(
    val action: String,
    val count: Long,
    val avgDurationMs: Double,
    val errorCount: Long = 0
)

@Serializable
data class LiveMonitoringDto(
    val requestsPerMinute: Double,
    val p95DurationMs: Long,
    val activeResources: List<String> = emptyList(),
    val recentErrors: List<MonitoringErrorDto> = emptyList()
)

@Serializable
data class MonitoringErrorDto(
    val method: String,
    val path: String,
    val statusCode: Int,
    val resource: String? = null,
    val action: String? = null,
    val durationMs: Long,
    val errorMessage: String? = null,
    val createdAt: String
)

// ─── Vendor Detail DTOs ─────────────────────────────────────────

@Serializable
data class VendorDetailDto(
    val vendor: VendorDetailInfo,
    val users: List<VendorUserDto>,
    val stats: VendorStatsDto,
    val plan_usage: VendorPlanUsageDto
)

@Serializable
data class VendorDetailInfo(
    val id: String,
    val name: String,
    val address: String,
    val contact_phone: String,
    val wallet_phone: String? = null,
    val business_type: String = "RESTAURANT",
    val store_type: String? = null,
    val is_suspended: Boolean = false,
    val suspension_reason: String? = null,
    val logo_url: String? = null,
    val digital_menu_url: String? = null,
    val enable_tables: Boolean = true,
    val enable_kds: Boolean = true,
    val enable_dine_in: Boolean = true,
    val enable_delivery: Boolean = true,
    val enable_takeaway: Boolean = true,
    val enable_in_store: Boolean = false,
    val enable_pickup_later: Boolean = false,
    val tax_enabled: Boolean = false,
    val default_tax_percent: Double = 0.0,
    val stock_mode: String = "NONE",
    val default_delivery_fee: Double = 0.0,
    val offline_mode_enabled: Boolean = false,
    val biometric_required: Boolean = false,
    // Feature toggles
    val enable_digital_menu: Boolean = true,
    val enable_recipe: Boolean = true,
    val enable_split_payment: Boolean = true,
    val enable_cash_drawer: Boolean = true,
    val enable_returns: Boolean = true,
    val enable_customer_credit: Boolean = false,
    val enable_pre_orders: Boolean = false,
    val enable_scheduled_orders: Boolean = false,
    val enable_suppliers: Boolean = true,
    val enable_drug_interactions: Boolean = false,
    val enable_prescriptions: Boolean = false,
    val enable_analytics: Boolean = true,
    val enable_announcements: Boolean = true,
    // Social links
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
    // Loyalty settings
    val loyalty_enabled: Boolean = false,
    val points_earn_rate: Double = 1.0,
    val points_redeem_rate: Double = 0.1,
    val min_points_redeem: Int = 100,
    // Discount settings
    val max_manual_discount_percent: Double = 100.0,
    val manual_discount_requires_pin: Boolean = false,
    val plan_name: String? = null,
    val plan_display_name: String? = null,
    val subscription_status: String? = null,
    val subscription_started_at: Long? = null,
    val subscription_expires_at: Long? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0
)

@Serializable
data class VendorUserDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val role: String,
    val active: Boolean = true,
    val created_at: Long = 0
)

@Serializable
data class VendorStatsDto(
    val orders: VendorOrderStatsDto,
    val revenue: VendorRevenueStatsDto,
    val tax: VendorTaxStatsDto
)

@Serializable
data class VendorOrderStatsDto(
    val total: Long = 0,
    val today: Long = 0,
    val this_month: Long = 0,
    val by_channel: Map<String, Long> = emptyMap(),
    val by_status: Map<String, Long> = emptyMap()
)

@Serializable
data class VendorRevenueStatsDto(
    val total: Double = 0.0,
    val today: Double = 0.0,
    val this_month: Double = 0.0,
    val by_payment_method: Map<String, PaymentMethodStatsDto> = emptyMap(),
    val by_channel: Map<String, Double> = emptyMap()
)

@Serializable
data class PaymentMethodStatsDto(
    val count: Long = 0,
    val amount: Double = 0.0
)

@Serializable
data class VendorTaxStatsDto(
    val total_collected: Double = 0.0
)

@Serializable
data class VendorPlanUsageDto(
    val plan: PlanUsageInfo,
    val usage: UsageInfo
)

// ══════════════════════════════════════════════════════════════════════
// Vendor Analytics DTOs (CMS wrapper of vendor dashboard analytics)
// ══════════════════════════════════════════════════════════════════════

// ── Executive Summary ────────────────────────────────────────────────
@Serializable
data class PeriodMetricsDto(
    val total_revenue: Double = 0.0,
    val net_revenue: Double = 0.0,
    val pending_revenue: Double = 0.0,
    val total_orders: Int = 0,
    val average_order_value: Double = 0.0,
    val total_delivery_fees: Double = 0.0,
    val total_discounts: Double = 0.0,
    val total_tax: Double = 0.0,
)

@Serializable
data class ExecutiveSummaryDto(
    val current: PeriodMetricsDto = PeriodMetricsDto(),
    val previous: PeriodMetricsDto = PeriodMetricsDto(),
    val revenue_change_percent: Double = 0.0,
    val orders_change_percent: Double = 0.0,
    val aov_change_percent: Double = 0.0,
    val active_orders: Int = 0,
    val attendance_today: Int = 0,
)

// ── Revenue & Profit ─────────────────────────────────────────────────
@Serializable
data class PaymentMethodDetailDto(
    val method: String = "",
    val order_count: Int = 0,
    val revenue: Double = 0.0,
)

@Serializable
data class DailyRevenuePointDto(
    val date: String = "",
    val revenue: Double = 0.0,
)

@Serializable
data class RevenueProfitDto(
    val gross_revenue: Double = 0.0,
    val total_delivery_fees: Double = 0.0,
    val net_revenue: Double = 0.0,
    val payment_methods: List<PaymentMethodDetailDto> = emptyList(),
    val daily_trend: List<DailyRevenuePointDto> = emptyList(),
)

// ── Orders Intelligence ──────────────────────────────────────────────
@Serializable
data class DailyOrderTrendPointDto(
    val date: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0,
)

@Serializable
data class ChannelBreakdownDto(
    val channel: String = "",
    val count: Int = 0,
    val percent: Double = 0.0,
)

@Serializable
data class OrdersIntelligenceDto(
    val total_orders: Int = 0,
    val completed_orders: Int = 0,
    val cancelled_orders: Int = 0,
    val refunded_orders: Int = 0,
    val orders_by_channel: Map<String, Int> = emptyMap(),
    val daily_trend: List<DailyOrderTrendPointDto> = emptyList(),
    val channel_breakdown: List<ChannelBreakdownDto> = emptyList(),
)

// ── Peak Time Analysis ───────────────────────────────────────────────
@Serializable
data class HourlyDataDto(
    val hour: Int = 0,
    val order_count: Int = 0,
    val revenue: Double = 0.0,
)

@Serializable
data class HeatmapPointDto(
    val day_of_week: Int = 0,
    val hour: Int = 0,
    val order_count: Int = 0,
)

@Serializable
data class DayOfWeekDto(
    val day_of_week: Int = 0,
    val name: String = "",
    val order_count: Int = 0,
    val revenue: Double = 0.0,
)

@Serializable
data class PeakTimeAnalysisDto(
    val busiest_hour: Int = 0,
    val busiest_day: String = "",
    val hourly_data: List<HourlyDataDto> = emptyList(),
    val heatmap: List<HeatmapPointDto> = emptyList(),
    val day_of_week: List<DayOfWeekDto> = emptyList(),
)

// ── Staff Performance ────────────────────────────────────────────────
@Serializable
data class CashierPerformanceDto(
    val cashier_id: String = "",
    val cashier_name: String = "",
    val revenue: Double = 0.0,
    val order_count: Int = 0,
    val average_order_value: Double = 0.0,
    val cancelled_orders: Int = 0,
    val cancellation_rate: Double = 0.0,
)

@Serializable
data class DeliveryPerformanceDto(
    val driver_id: String = "",
    val driver_name: String = "",
    val orders_completed: Int = 0,
    val fees_collected: Double = 0.0,
    val revenue: Double = 0.0,
    val avg_delivery_time_minutes: Double = 0.0,
    val late_delivery_percent: Double = 0.0,
)

// ── Product Intelligence ─────────────────────────────────────────────
@Serializable
data class ProductItemDto(
    val item_id: String = "",
    val item_name: String = "",
    val category_name: String = "",
    val quantity_sold: Int = 0,
    val revenue: Double = 0.0,
    val cost_price: Double = 0.0,
    val profit_margin: Double = 0.0,
)

@Serializable
data class CategoryRevenueDto(
    val category_id: String = "",
    val category_name: String = "",
    val revenue: Double = 0.0,
    val item_count: Int = 0,
)

@Serializable
data class ProductIntelligenceDto(
    val top_selling: List<ProductItemDto> = emptyList(),
    val most_profitable: List<ProductItemDto> = emptyList(),
    val least_selling: List<ProductItemDto> = emptyList(),
    val revenue_by_category: List<CategoryRevenueDto> = emptyList(),
    val low_margin_warnings: List<ProductItemDto> = emptyList(),
)

// ── Customer Intelligence ────────────────────────────────────────────
@Serializable
data class TopCustomerDto(
    val customer_id: String = "",
    val customer_name: String = "",
    val phone: String = "",
    val order_count: Int = 0,
    val total_spent: Double = 0.0,
)

@Serializable
data class CustomerIntelligenceDto(
    val total_customers: Int = 0,
    val new_customers_percent: Double = 0.0,
    val returning_customers_percent: Double = 0.0,
    val average_spend: Double = 0.0,
    val lifetime_value: Double = 0.0,
    val top_customers: List<TopCustomerDto> = emptyList(),
    val frequency_buckets: Map<String, Int> = emptyMap(),
)

// ── Alerts ───────────────────────────────────────────────────────────
@Serializable
data class AlertItemDto(
    val type: String = "",
    val severity: String = "",
    val title: String = "",
    val message: String = "",
    val value: Double = 0.0,
    val threshold: Double = 0.0,
)

@Serializable
data class AlertsResponseDto(
    val alerts: List<AlertItemDto> = emptyList(),
)

// ── Stock Overview ───────────────────────────────────────────────────
@Serializable
data class StockOverviewItemDto(
    val stock_id: String = "",
    val item_name: String = "",
    val quantity: Double = 0.0,
    val min_quantity: Double = 0.0,
    val cost_price: Double = 0.0,
    val unit: String = "",
    val status: String = "",
)

@Serializable
data class StockMovementDto(
    val date: String = "",
    val added: Double = 0.0,
    val deducted: Double = 0.0,
)

@Serializable
data class StockOverviewDto(
    val total_stock_value: Double = 0.0,
    val total_selling_value: Double = 0.0,
    val potential_profit: Double = 0.0,
    val total_items: Int = 0,
    val low_stock_items: List<StockOverviewItemDto> = emptyList(),
    val out_of_stock_items: List<StockOverviewItemDto> = emptyList(),
    val dead_stock_items: List<StockOverviewItemDto> = emptyList(),
    val movement_summary: List<StockMovementDto> = emptyList(),
)

// ── Offers Analytics ─────────────────────────────────────────────────
@Serializable
data class OfferPerformanceItemDto(
    val offer_id: String = "",
    val offer_name: String = "",
    val discount_type: String = "",
    val discount_value: Double = 0.0,
    val usage_count: Int = 0,
    val total_discount_given: Double = 0.0,
    val total_revenue_from_offer_orders: Double = 0.0,
    val promo_code: String? = null,
    val is_active: Boolean = false,
)

@Serializable
data class DailyOfferUsageDto(
    val date: String = "",
    val usage_count: Int = 0,
    val discount_amount: Double = 0.0,
)

@Serializable
data class OffersAnalyticsDto(
    val total_offers: Int = 0,
    val active_offers: Int = 0,
    val total_offer_uses: Int = 0,
    val total_discount_from_offers: Double = 0.0,
    val average_discount_per_use: Double = 0.0,
    val top_offers: List<OfferPerformanceItemDto> = emptyList(),
    val offer_usage_trend: List<DailyOfferUsageDto> = emptyList(),
)

// ── Discount Analytics ───────────────────────────────────────────────
@Serializable
data class DiscountBreakdownDto(
    val type: String = "",
    val count: Int = 0,
    val total_amount: Double = 0.0,
    val percent_of_total: Double = 0.0,
)

@Serializable
data class DailyDiscountTrendDto(
    val date: String = "",
    val manual_discount: Double = 0.0,
    val offer_discount: Double = 0.0,
    val points_discount: Double = 0.0,
)

@Serializable
data class DiscountAnalyticsDto(
    val total_orders_with_discount: Int = 0,
    val total_discount_given: Double = 0.0,
    val average_discount_per_order: Double = 0.0,
    val discount_rate: Double = 0.0,
    val breakdown: List<DiscountBreakdownDto> = emptyList(),
    val daily_trend: List<DailyDiscountTrendDto> = emptyList(),
)

// ── Loyalty Analytics ────────────────────────────────────────────────
@Serializable
data class DailyLoyaltyTrendDto(
    val date: String = "",
    val points_earned: Int = 0,
    val points_redeemed: Int = 0,
)

@Serializable
data class LoyaltyAnalyticsDto(
    val total_points_earned: Long = 0,
    val total_points_redeemed: Long = 0,
    val total_points_outstanding: Long = 0,
    val active_loyalty_customers: Int = 0,
    val redemption_rate: Double = 0.0,
    val points_to_revenue: Double = 0.0,
    val daily_trend: List<DailyLoyaltyTrendDto> = emptyList(),
)

// ══════════════════════════════════════════════════════════════════════
// CMS Orders, Customers, Workers DTOs
// ══════════════════════════════════════════════════════════════════════

@Serializable
data class CmsOrderListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 20,
    val total_pages: Int = 0,
    val orders: List<CmsOrderDto> = emptyList(),
)

@Serializable
data class CmsOrderDto(
    val id: String = "",
    val channel: String = "",
    val status: String = "",
    val payment_method: String = "",
    val payment_status: String = "",
    val subtotal: Double = 0.0,
    val delivery_fee: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val client_name: String = "",
    val client_phone: String = "",
    val notes: String = "",
    val created_at: Long = 0,
    val updated_at: Long = 0,
    val refunded_at: Long? = null,
    val refund_reason: String? = null,
    val points_earned: Int = 0,
    val points_redeemed: Int = 0,
)

@Serializable
data class CmsOrderDetailDto(
    val id: String = "",
    val channel: String = "",
    val status: String = "",
    val payment_method: String = "",
    val payment_status: String = "",
    val subtotal: Double = 0.0,
    val delivery_fee: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val client_name: String = "",
    val client_phone: String = "",
    val client_address: String? = null,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val notes: String = "",
    val created_at: Long = 0,
    val updated_at: Long = 0,
    val refunded_at: Long? = null,
    val refund_reason: String? = null,
    val points_earned: Int = 0,
    val points_redeemed: Int = 0,
    val items: List<CmsOrderItemDto> = emptyList(),
)

@Serializable
data class CmsOrderItemDto(
    val id: String = "",
    val item_id: String = "",
    val item_name: String = "",
    val item_price: Double = 0.0,
    val quantity: Int = 0,
    val note: String? = null,
    val variant_options: String? = null,
)

@Serializable
data class CmsCustomerListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 20,
    val total_pages: Int = 0,
    val customers: List<CmsCustomerDto> = emptyList(),
)

@Serializable
data class CmsCustomerDto(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val order_count: Int = 0,
    val total_spent: Double = 0.0,
    val points_balance: Int = 0,
    val last_order_at: Long? = null,
    val created_at: Long = 0,
)

@Serializable
data class CmsWorkerListResponse(
    val total: Int = 0,
    val workers: List<CmsWorkerDto> = emptyList(),
)

@Serializable
data class CmsWorkerDto(
    val id: String = "",
    val worker_id: String = "",
    val full_name: String = "",
    val phone: String = "",
    val role: String = "",
    val salary_type: String = "",
    val salary_amount: Double = 0.0,
    val active: Boolean = true,
    val created_at: Long = 0,
    val attendance_days_30d: Int = 0,
    val worked_minutes_30d: Int = 0,
    val checked_in_today: Boolean = false,
)

// ─── Platform Analytics ────────────────────────────────────────────

@Serializable
data class PlatformAnalyticsDto(
    val total_vendors: Int = 0,
    val active_vendors: Int = 0,
    val new_this_month: Int = 0,
    val mrr: Long = 0,
    val active_subscriptions: Int = 0,
    val expired_subscriptions: Int = 0,
    val orders_this_month: Int = 0,
    val revenue_this_month: Double = 0.0,
    val avg_revenue_per_vendor: Double = 0.0,
    val plan_revenue: List<PlanRevenueDto> = emptyList(),
    val top_vendors: List<TopVendorDto> = emptyList(),
)

@Serializable
data class PlanRevenueDto(
    val plan: String = "",
    val count: Int = 0,
    val revenue: Long = 0,
)

@Serializable
data class TopVendorDto(
    val vendor_id: String = "",
    val vendor_name: String = "",
    val revenue: Double = 0.0,
    val orders: Int = 0,
    val is_suspended: Boolean = false,
)

// ─── Platform Alerts ────────────────────────────────────────────

@Serializable
data class PlatformAlertDto(
    val type: String = "",
    val severity: String = "WARNING",
    val vendor_id: String = "",
    val vendor_name: String = "",
    val message: String = "",
)

// ─── Notifications ───────────────────────────────────────────────
@Serializable
data class AdminSendNotificationRequest(
    val vendor_ids: List<String>? = null,   // null = all active vendors
    val type: String,                        // ADMIN_ANNOUNCEMENT or SYSTEM_UPDATE
    val title: String,
    val body: String,
    val action_url: String? = null,
    val platform: String? = null,            // null=all, ANDROID, DESKTOP, IOS
    val priority: String = "NORMAL",
)

// ─── App Releases DTOs ──────────────────────────────────────────
@kotlinx.serialization.Serializable
data class AppReleaseDto(
    val id: String = "",
    val version_name: String = "",
    val version_code: Int = 0,
    val update_status: String = "OPTIONAL",
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int = 1,
    val drive_folder_id: String? = null,
    val released_date: String? = null,
    val is_active: Boolean = true,
    val released_at: Long = 0,
    val created_at: Long = 0,
)

@kotlinx.serialization.Serializable
data class CreateReleaseRequest(
    val version_name: String,
    val version_code: Int,
    val update_status: String = "OPTIONAL",
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int = 1,
    val drive_folder_id: String? = null,
    val released_date: String? = null,
)

@kotlinx.serialization.Serializable
data class UpdateReleaseRequest(
    val update_status: String? = null,
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int? = null,
    val drive_folder_id: String? = null,
    val is_active: Boolean? = null,
)
