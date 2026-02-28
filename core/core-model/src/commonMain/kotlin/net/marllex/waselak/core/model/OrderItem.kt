package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OrderItem(
    val id: String,
    val orderId: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val itemPriceSnapshot: Double,
    val quantity: Int,
    val note: String? = null,
    val variantOptionsSnapshot: String? = null,
    val createdAt: Long? = null
) {
    val totalPrice: Double
        get() = itemPriceSnapshot * quantity
}

@Serializable
data class VariantSelection(
    val groupName: String,
    val optionName: String,
    val priceAdjustment: Double
)

data class CartItem(
    val item: Item,
    val quantity: Int,
    val note: String? = null,
    val variantSelections: List<VariantSelection> = emptyList()
) {
    val unitPrice: Double
        get() = item.price + variantSelections.sumOf { it.priceAdjustment }
    val totalPrice: Double
        get() = unitPrice * quantity
}
