package net.marllex.waselak.core.network.dto

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
    @SerialName("enable_takeaway") val enableTakeaway: Boolean = true,
    @SerialName("enable_in_store") val enableInStore: Boolean = false,
    @SerialName("enable_pickup_later") val enablePickupLater: Boolean = false,
    @SerialName("business_type") val businessType: String = "RESTAURANT",
    @SerialName("tax_enabled") val taxEnabled: Boolean = false,
    @SerialName("default_tax_percent") val defaultTaxPercent: Double = 0.0,
    @SerialName("stock_mode") val stockMode: String = "NONE",
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
    @SerialName("enable_delivery") val enableDelivery: Boolean? = null,
    @SerialName("enable_takeaway") val enableTakeaway: Boolean? = null,
    @SerialName("enable_in_store") val enableInStore: Boolean? = null,
    @SerialName("enable_pickup_later") val enablePickupLater: Boolean? = null,
    @SerialName("business_type") val businessType: String? = null,
    @SerialName("tax_enabled") val taxEnabled: Boolean? = null,
    @SerialName("default_tax_percent") val defaultTaxPercent: Double? = null,
    @SerialName("stock_mode") val stockMode: String? = null,
)
