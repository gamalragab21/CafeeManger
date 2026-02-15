package net.marllex.cafeemanger.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsSummary(
    val totalOrders: Int,
    val totalRevenue: Double,
    val averageOrderValue: Double,
    val ordersByChannel: Map<String, Int>,
    val ordersByStatus: Map<String, Int>,
    val ordersByPaymentMethod: Map<String, Int>,
    val revenueByPaymentMethod: Map<String, Double>,
    val topItems: List<TopItem>,
    val fromDate: Long,
    val toDate: Long
)

@Serializable
data class TopItem(
    @SerialName("item_name") val item: String,
    @SerialName("total_quantity") val quantitySold: Int,
    @SerialName("total_revenue") val revenue: Double
)

@Serializable
data class DailyAnalytics(
    val date: String,
    val orders: Int,
    val revenue: Double
)

@Serializable
data class SettlementByPaymentMethod(
    val orderCount: Int,
    val totalRevenue: Double,
    val totalTax: Double, // NOTE: This field represents delivery fees, not actual tax
    val totalSubtotal: Double
)

@Serializable
data class Settlements(
    val byPaymentMethod: Map<String, SettlementByPaymentMethod>
)

@Serializable
data class DeliveryPerformance(
    val deliveryUserId: String,
    val deliveryUserName: String,
    val orderCount: Int,
    val totalRevenue: Double,
    val totalTax: Double // NOTE: This field represents delivery fees, not actual tax
)