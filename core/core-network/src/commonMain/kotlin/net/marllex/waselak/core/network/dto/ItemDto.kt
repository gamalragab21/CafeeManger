package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    @SerialName("cost_price") val costPrice: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val available: Boolean = true,
    @SerialName("stock_behavior") val stockBehavior: String = "NONE",
    @SerialName("variant_groups") val variantGroups: List<VariantGroupResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class VariantGroupResponse(
    val id: String,
    val name: String,
    val required: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
    val options: List<VariantOptionResponse> = emptyList()
)

@Serializable
data class VariantOptionResponse(
    val id: String,
    val name: String,
    @SerialName("price_adjustment") val priceAdjustment: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class CreateVariantGroupRequest(
    val name: String,
    val required: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
    val options: List<CreateVariantOptionRequest> = emptyList()
)

@Serializable
data class CreateVariantOptionRequest(
    val name: String,
    @SerialName("price_adjustment") val priceAdjustment: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class CreateItemRequest(
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    @SerialName("cost_price") val costPrice: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val available: Boolean = true
)

@Serializable
data class UpdateItemRequest(
    @SerialName("category_id") val categoryId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val available: Boolean? = null
)
