package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsSummaryResponse(
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("average_order_value") val averageOrderValue: Double,
    @SerialName("orders_by_channel") val ordersByChannel: Map<String, Int>,
    @SerialName("orders_by_status") val ordersByStatus: Map<String, Int>,
    @SerialName("orders_by_payment_method") val ordersByPaymentMethod: Map<String, Int>,
    @SerialName("revenue_by_payment_method") val revenueByPaymentMethod: Map<String, Double>,
    @SerialName("top_items") val topItems: List<TopItemResponse>
)

@Serializable
data class TopItemResponse(
    @SerialName("item_name")  val item: String,
    @SerialName("total_quantity") val quantitySold: Int,
    @SerialName("total_revenue")  val revenue: Double
)


@Serializable
data class DailyAnalyticsResponse(
    val date: String,
    @SerialName("total_orders") val orders: Int,
    @SerialName("total_revenue") val revenue: Double
)

@Serializable
data class SettlementByPaymentMethodResponse(
    @SerialName("order_count") val orderCount: Int,
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("total_delivery_fees") val totalDeliveryFees: Double,
    @SerialName("total_subtotal") val totalSubtotal: Double
)

@Serializable
data class SettlementsResponse(
    @SerialName("by_payment_method") val byPaymentMethod: Map<String, SettlementByPaymentMethodResponse>
)

@Serializable
data class DeliveryPerformanceResponse(
    @SerialName("delivery_user_id") val deliveryUserId: String,
    @SerialName("delivery_user_name") val deliveryUserName: String,
    @SerialName("order_count") val orderCount: Int,
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("total_delivery_fees") val totalDeliveryFees: Double
)

@Serializable
data class ShiftSummaryResponse(
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("cash_revenue") val cashRevenue: Double,
    @SerialName("wallet_revenue") val walletRevenue: Double,
    @SerialName("card_revenue") val cardRevenue: Double,
    @SerialName("cash_orders") val cashOrders: Int,
    @SerialName("wallet_orders") val walletOrders: Int,
    @SerialName("card_orders") val cardOrders: Int,
    @SerialName("cancelled_total") val cancelledTotal: Double,
    @SerialName("cancelled_count") val cancelledCount: Int,
    @SerialName("refunded_total") val refundedTotal: Double,
    @SerialName("refunded_count") val refundedCount: Int,
)

@Serializable
data class DoctorStatsResponse(
    @SerialName("doctor_name") val doctorName: String,
    @SerialName("prescription_count") val prescriptionCount: Int,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("total_revenue") val totalRevenue: Double,
)

@Serializable
data class CheckUpdateResponse(
    @SerialName("has_update") val hasUpdate: Boolean = false,
    @SerialName("latest_version") val latestVersion: String = "",
    @SerialName("latest_version_code") val latestVersionCode: Int = 0,
    @SerialName("current_version") val currentVersion: String = "",
    @SerialName("current_version_code") val currentVersionCode: Int = 0,
    @SerialName("update_status") val updateStatus: String = "UP_TO_DATE",
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("release_notes_ar") val releaseNotesAr: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("released_at") val releasedAt: Long? = null,
)
