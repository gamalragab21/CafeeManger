package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Stock(
    val id: String,
    val vendorId: String,
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int = 5,
    val costPrice: Double = 0.0,
    val unit: String = "pcs",
    val lastUpdatedAt: Long = System.currentTimeMillis(),
) {
    val totalValue: Double get() = costPrice * quantity
    val isLowStock: Boolean get() = quantity in 1..minQuantity
    val isOutOfStock: Boolean get() = quantity <= 0
}

@Serializable
data class StockTransaction(
    val id: String,
    val stockId: String,
    val type: StockTransactionType,
    val quantity: Int,
    val previousQuantity: Int,
    val orderId: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class StockTransactionType {
    ADD, DEDUCT, ADJUST
}

data class StockSummary(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val healthyStockCount: Int = 0,
)
