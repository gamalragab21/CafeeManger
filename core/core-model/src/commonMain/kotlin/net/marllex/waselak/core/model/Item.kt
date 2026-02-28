package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: String,
    val vendorId: String,
    val categoryId: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val costPrice: Double? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
    val available: Boolean = true,
    val stockBehavior: String = "NONE",
    val variantGroups: List<VariantGroup> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
data class VariantGroup(
    val id: String,
    val name: String,
    val required: Boolean = false,
    val displayOrder: Int = 0,
    val options: List<VariantOption> = emptyList()
)

@Serializable
data class VariantOption(
    val id: String,
    val name: String,
    val priceAdjustment: Double = 0.0,
    val isDefault: Boolean = false,
    val displayOrder: Int = 0
)
