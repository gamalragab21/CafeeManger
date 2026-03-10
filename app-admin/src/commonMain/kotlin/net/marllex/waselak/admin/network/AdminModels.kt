package net.marllex.waselak.admin.network

import kotlinx.serialization.Serializable

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
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    val is_suspended: Boolean? = null,
    val suspension_reason: String? = null
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
    val enable_dine_in: Boolean = true,
    val enable_delivery: Boolean = true,
    val enable_takeaway: Boolean = true,
    val enable_in_store: Boolean = false,
    val enable_pickup_later: Boolean = false,
    val tax_enabled: Boolean = false,
    val default_tax_percent: Double = 0.0,
    val stock_mode: String = "NONE",
    val default_delivery_fee: Double = 0.0,
    val plan_name: String? = null,
    val plan_display_name: String? = null,
    val subscription_status: String? = null,
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
