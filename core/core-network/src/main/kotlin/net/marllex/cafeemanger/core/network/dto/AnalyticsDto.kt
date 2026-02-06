package net.marllex.cafeemanger.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsSummaryResponse(
    @SerialName("total_orders") val totalOrders: Int,
    @SerialName("total_revenue") val totalRevenue: Double,
    @SerialName("average_order_value") val averageOrderValue: Double,
    @SerialName("orders_by_channel") val ordersByChannel: Map<String, Int>,
    @SerialName("orders_by_status") val ordersByStatus: Map<String, Int>,
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
