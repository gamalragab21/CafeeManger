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
    val imageUrl: String? = null,
    val available: Boolean = true,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
