package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OfferResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Double,
    val active: Boolean = true,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("promo_code") val promoCode: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("used_count") val usedCount: Int = 0,
    @SerialName("starts_at") val startsAt: Long? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    val items: List<OfferItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class OfferItemResponse(
    val id: String,
    @SerialName("offer_id") val offerId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("item_price") val itemPrice: Double,
    val quantity: Int = 1,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class CreateOfferRequest(
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Double,
    val active: Boolean = true,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("promo_code") val promoCode: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("starts_at") val startsAt: Long? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    val items: List<CreateOfferItemRequest> = emptyList(),
)

@Serializable
data class CreateOfferItemRequest(
    @SerialName("item_id") val itemId: String,
    val quantity: Int = 1,
)

@Serializable
data class UpdateOfferRequest(
    val name: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("discount_type") val discountType: String? = null,
    @SerialName("discount_value") val discountValue: Double? = null,
    val active: Boolean? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("promo_code") val promoCode: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("starts_at") val startsAt: Long? = null,
    @SerialName("display_order") val displayOrder: Int? = null,
    val items: List<CreateOfferItemRequest>? = null,
)
