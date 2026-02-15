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
    val createdAt: Long? = null
) {
    val totalPrice: Double
        get() = itemPriceSnapshot * quantity
}

data class CartItem(
    val item: Item,
    val quantity: Int,
    val note: String? = null
) {
    val totalPrice: Double
        get() = item.price * quantity
}
