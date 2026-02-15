package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Stock(
    val id: String,
    val vendorId: String,
    val itemId: String? = null, // Nullable for independent stock items
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int = 5,
    val costPrice: Double = 0.0,
    val unit: String = "pcs",
    val isMenuItem: Boolean = true, // true = linked to menu, false = independent stock
    val alertEnabled: Boolean = true, // Enable low stock alerts
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
    val itemName: String? = null, // For display in transaction list
    val type: StockTransactionType,
    val quantity: Int,
    val previousQuantity: Int,
    val orderId: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val newQuantity: Int get() = when (type) {
        StockTransactionType.ADD -> previousQuantity + quantity
        StockTransactionType.DEDUCT -> (previousQuantity - quantity).coerceAtLeast(0)
        StockTransactionType.ADJUST -> quantity
    }
}

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
    val menuItemsCount: Int = 0,
    val independentItemsCount: Int = 0,
    val totalTransactionsToday: Int = 0,
    val totalAddedToday: Int = 0,
    val totalDeductedToday: Int = 0,
)

@Serializable
data class StockAlert(
    val id: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int,
    val unit: String,
    val isOutOfStock: Boolean,
    val isMenuItem: Boolean,
)
