package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("item_id") val itemId: String? = null, // Nullable for independent stock items
    @SerialName("item_name") val itemName: String,
    val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int,
    @SerialName("cost_price") val costPrice: Double,
    val unit: String,
    @SerialName("is_menu_item") val isMenuItem: Boolean = true,
    @SerialName("alert_enabled") val alertEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CreateStockRequest(
    @SerialName("item_id") val itemId: String? = null, // Optional - null for independent items
    @SerialName("item_name") val itemName: String? = null, // Required if itemId is null
    val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int = 5,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    val unit: String = "pcs",
    @SerialName("alert_enabled") val alertEnabled: Boolean = true,
)

@Serializable
data class UpdateStockRequest(
    @SerialName("item_name") val itemName: String? = null,
    val quantity: Int? = null,
    @SerialName("min_quantity") val minQuantity: Int? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    val unit: String? = null,
    @SerialName("alert_enabled") val alertEnabled: Boolean? = null,
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
    @SerialName("item_name") val itemName: String? = null,
    val type: String,
    val quantity: Int,
    @SerialName("previous_quantity") val previousQuantity: Int,
    @SerialName("order_id") val orderId: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class StockAlertResponse(
    val id: String,
    @SerialName("item_name") val itemName: String,
    val quantity: Int,
    @SerialName("min_quantity") val minQuantity: Int,
    val unit: String,
    @SerialName("is_out_of_stock") val isOutOfStock: Boolean,
    @SerialName("is_menu_item") val isMenuItem: Boolean,
)

@Serializable
data class StockAnalyticsSummaryResponse(
    @SerialName("total_items") val totalItems: Int,
    @SerialName("total_value") val totalValue: Double,
    @SerialName("low_stock_count") val lowStockCount: Int,
    @SerialName("out_of_stock_count") val outOfStockCount: Int,
    @SerialName("healthy_count") val healthyCount: Int,
    @SerialName("menu_items_count") val menuItemsCount: Int,
    @SerialName("independent_items_count") val independentItemsCount: Int,
    @SerialName("total_transactions_today") val totalTransactionsToday: Int,
    @SerialName("total_added_today") val totalAddedToday: Int,
    @SerialName("total_deducted_today") val totalDeductedToday: Int,
)
