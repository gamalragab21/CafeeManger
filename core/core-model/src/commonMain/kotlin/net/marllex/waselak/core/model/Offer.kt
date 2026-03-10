package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Offer(
    val id: String,
    val vendorId: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val discountType: String, // FIXED_PRICE or PERCENT
    val discountValue: Double,
    val active: Boolean = true,
    val expiresAt: Long? = null,
    val promoCode: String? = null,
    val maxUses: Int? = null,
    val usedCount: Int = 0,
    val startsAt: Long? = null,
    val displayOrder: Int = 0,
    val items: List<OfferItem> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

@Serializable
data class OfferItem(
    val id: String,
    val offerId: String,
    val itemId: String,
    val itemName: String,
    val itemPrice: Double,
    val quantity: Int = 1,
)
