package net.marllex.cafeemanger.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaxPlaceResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    @SerialName("tax_percent") val taxPercent: Double,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("display_order") val displayOrder: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class CreateTaxPlaceRequest(
    val name: String,
    @SerialName("tax_percent") val taxPercent: Double,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class UpdateTaxPlaceRequest(
    val name: String? = null,
    @SerialName("tax_percent") val taxPercent: Double? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("display_order") val displayOrder: Int? = null
)
