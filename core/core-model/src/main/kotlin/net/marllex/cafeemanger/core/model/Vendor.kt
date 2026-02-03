package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Vendor(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val address: String,
    val contactPhone: String,
    val walletPhone: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null
)
