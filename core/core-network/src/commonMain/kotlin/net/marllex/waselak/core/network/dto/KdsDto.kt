package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KdsOrderResponse(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: Int = 0,
    val channel: String,
    @SerialName("table_number") val tableNumber: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    val notes: String? = null,
    val items: List<KdsOrderItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("elapsed_minutes") val elapsedMinutes: Long = 0,
)

@Serializable
data class KdsOrderItemResponse(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("item_name") val itemName: String,
    val quantity: Int,
    val note: String? = null,
    @SerialName("variant_options") val variantOptions: String? = null,
    @SerialName("kitchen_status") val kitchenStatus: String = "PENDING",
    @SerialName("kitchen_station") val kitchenStation: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class UpdateKitchenStatusRequest(
    val status: String,
)

@Serializable
data class BulkUpdateKitchenStatusRequest(
    @SerialName("item_ids") val itemIds: List<String> = emptyList(),
    val status: String,
)

@Serializable
data class AssignStationRequest(
    val station: String?,
)

@Serializable
data class KdsSummaryResponse(
    @SerialName("total_items") val totalItems: Int = 0,
    val pending: Int = 0,
    val cooking: Int = 0,
    val ready: Int = 0,
    val served: Int = 0,
    @SerialName("avg_prep_time_minutes") val avgPrepTimeMinutes: Double = 0.0,
)
