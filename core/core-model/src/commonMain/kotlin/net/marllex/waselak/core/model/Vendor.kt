package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Vendor(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val address: String,
    val contactPhone: String,
    val walletPhone: String? = null,
    val defaultDeliveryFee: Double = 0.0,
    val storeType: String? = null,
    val enableTables: Boolean = true,
    val enableDineIn: Boolean = true,
    val enableDelivery: Boolean = true,
    val enableTakeaway: Boolean = true,
    val enableInStore: Boolean = false,
    val enablePickupLater: Boolean = false,
    val digitalMenuUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null
)
