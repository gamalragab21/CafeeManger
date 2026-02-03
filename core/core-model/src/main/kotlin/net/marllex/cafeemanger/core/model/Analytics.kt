package net.marllex.cafeemanger.core.model

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
    val item: Item,
    val quantitySold: Int,
    val revenue: Double
)

@Serializable
data class DailyAnalytics(
    val date: String,
    val orders: Int,
    val revenue: Double
)
