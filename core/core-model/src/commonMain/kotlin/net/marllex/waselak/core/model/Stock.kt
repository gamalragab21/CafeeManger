package net.marllex.waselak.core.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class Stock(
    val id: String,
    val vendorId: String,
    val itemId: String? = null,
    val itemName: String,
    val quantity: Double,
    val minQuantity: Double = 5.0,
    val costPrice: Double = 0.0,
    val unit: String = "PIECE",
    val baseUnit: String = "PIECE",
    val conversionRate: Double = 1.0,
    val isMenuItem: Boolean = true,
    val alertEnabled: Boolean = true,
    val lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val totalValue: Double get() = costPrice * quantity
    val isLowStock: Boolean get() = quantity > 0 && quantity <= minQuantity
    val isOutOfStock: Boolean get() = quantity <= 0
}

@Serializable
data class StockTransaction(
    val id: String,
    val stockId: String,
    val itemName: String? = null,
    val type: StockTransactionType,
    val quantity: Double,
    val previousQuantity: Double,
    val orderId: String? = null,
    val recipeId: String? = null,
    val note: String? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val newQuantity: Double get() = when (type) {
        StockTransactionType.ADD, StockTransactionType.PURCHASE, StockTransactionType.RETURN -> previousQuantity + quantity
        StockTransactionType.DEDUCT, StockTransactionType.SALE_DIRECT, StockTransactionType.SALE_RECIPE,
        StockTransactionType.WASTE, StockTransactionType.TRANSFER -> (previousQuantity - quantity).coerceAtLeast(0.0)
        StockTransactionType.ADJUST -> quantity
    }
}

@Serializable
enum class StockTransactionType {
    ADD, DEDUCT, ADJUST, PURCHASE, SALE_DIRECT, SALE_RECIPE, RETURN, WASTE, TRANSFER
}

data class StockSummary(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val healthyStockCount: Int = 0,
    val menuItemsCount: Int = 0,
    val independentItemsCount: Int = 0,
    val recipeItemsCount: Int = 0,
    val totalTransactionsToday: Int = 0,
    val totalAddedToday: Double = 0.0,
    val totalDeductedToday: Double = 0.0,
)

@Serializable
data class StockAlert(
    val id: String,
    val itemName: String,
    val quantity: Double,
    val minQuantity: Double,
    val unit: String,
    val isOutOfStock: Boolean,
    val isMenuItem: Boolean,
)
