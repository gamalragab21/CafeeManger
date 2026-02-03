package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val vendorId: String,
    val name: String,
    val displayOrder: Int = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
