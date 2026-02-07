package net.marllex.cafeemanger.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int,
    @SerialName("cost_price") val costPrice: Double,
    val unit: String,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CreateStockRequest(
    @SerialName("item_id") val itemId: String,
    val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int = 5,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    val unit: String = "pcs",
)

@Serializable
data class UpdateStockRequest(
    val quantity: Int? = null,
    @SerialName("min_quantity") val minQuantity: Int? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    val unit: String? = null,
)

@Serializable
data class AdjustQuantityRequest(
    val quantity: Int,
    val note: String? = null,
)

@Serializable
data class StockTransactionResponse(
    val id: String,
    @SerialName("stock_id") val stockId: String,
    val type: String,
    val quantity: Int,
    @SerialName("previous_quantity") val previousQuantity: Int,
    @SerialName("order_id") val orderId: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)
