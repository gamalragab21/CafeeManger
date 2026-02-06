package net.marllex.cafeemanger.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class CreateCategoryRequest(
    val name: String,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    @SerialName("display_order") val displayOrder: Int? = null
)

@Serializable
data class ReorderCategoriesRequest(
    @SerialName("ordered_ids") val orderedIds: List<String>
)
