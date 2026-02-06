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