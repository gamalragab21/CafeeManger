package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_name") val itemName: String,
    val quantity: Double,
    @SerialName("min_quantity") val minQuantity: Double,
    @SerialName("cost_price") val costPrice: Double,
    val unit: String,
    @SerialName("base_unit") val baseUnit: String = "PIECE",
    @SerialName("conversion_rate") val conversionRate: Double = 1.0,
    @SerialName("is_menu_item") val isMenuItem: Boolean = true,
    @SerialName("alert_enabled") val alertEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CreateStockRequest(
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_name") val itemName: String? = null,
    val quantity: Double,
    @SerialName("min_quantity") val minQuantity: Double = 5.0,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    val unit: String = "PIECE",
    @SerialName("base_unit") val baseUnit: String = "PIECE",
    @SerialName("conversion_rate") val conversionRate: Double = 1.0,
    @SerialName("alert_enabled") val alertEnabled: Boolean = true,
)

@Serializable
data class UpdateStockRequest(
    @SerialName("item_name") val itemName: String? = null,
    val quantity: Double? = null,
    @SerialName("min_quantity") val minQuantity: Double? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    val unit: String? = null,
    @SerialName("alert_enabled") val alertEnabled: Boolean? = null,
)

@Serializable
data class AdjustQuantityRequest(
    val quantity: Double,
    val note: String? = null,
)

@Serializable
data class StockTransactionResponse(
    val id: String,
    @SerialName("stock_id") val stockId: String,
    @SerialName("item_name") val itemName: String? = null,
    val type: String,
    val quantity: Double,
    @SerialName("previous_quantity") val previousQuantity: Double,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("recipe_id") val recipeId: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class StockAlertResponse(
    val id: String,
    @SerialName("item_name") val itemName: String,
    val quantity: Double,
    @SerialName("min_quantity") val minQuantity: Double,
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
    @SerialName("recipe_items_count") val recipeItemsCount: Int = 0,
    @SerialName("total_transactions_today") val totalTransactionsToday: Int,
    @SerialName("total_added_today") val totalAddedToday: Double,
    @SerialName("total_deducted_today") val totalDeductedToday: Double,
)
