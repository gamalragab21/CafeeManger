package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TaxPlace(
    val id: String,
    val vendorId: String,
    val name: String,
    val taxPercent: Double,
    val isDefault: Boolean,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
