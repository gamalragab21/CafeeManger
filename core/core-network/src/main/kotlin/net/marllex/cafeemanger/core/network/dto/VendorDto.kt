package net.marllex.cafeemanger.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VendorResponse(
    val id: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    val address: String,
    @SerialName("contact_phone") val contactPhone: String,
    @SerialName("wallet_phone") val walletPhone: String? = null,
    @SerialName("default_delivery_fee") val defaultDeliveryFee: Double = 0.0,
    @SerialName("store_type") val storeType: String? = null,
    @SerialName("enable_tables") val enableTables: Boolean = true,
    @SerialName("enable_dine_in") val enableDineIn: Boolean = true,
    @SerialName("enable_delivery") val enableDelivery: Boolean = true,
    @SerialName("digital_menu_url") val digitalMenuUrl: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class UpdateVendorRequest(
    val name: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val address: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("wallet_phone") val walletPhone: String? = null,
    @SerialName("default_delivery_fee") val defaultDeliveryFee: Double? = null,
    @SerialName("store_type") val storeType: String? = null,
    @SerialName("enable_tables") val enableTables: Boolean? = null,
    @SerialName("enable_dine_in") val enableDineIn: Boolean? = null,
    @SerialName("enable_delivery") val enableDelivery: Boolean? = null
)
