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
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null
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
