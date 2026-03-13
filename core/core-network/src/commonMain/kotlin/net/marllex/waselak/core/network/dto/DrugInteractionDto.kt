package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DrugInteractionResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("item_id_a") val itemIdA: String,
    @SerialName("item_name_a") val itemNameA: String? = null,
    @SerialName("item_id_b") val itemIdB: String,
    @SerialName("item_name_b") val itemNameB: String? = null,
    val severity: String = "MODERATE",
    val description: String,
    @SerialName("description_ar") val descriptionAr: String? = null,
    val recommendation: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateDrugInteractionRequest(
    @SerialName("item_id_a") val itemIdA: String,
    @SerialName("item_id_b") val itemIdB: String,
    val severity: String = "MODERATE",
    val description: String,
    @SerialName("description_ar") val descriptionAr: String? = null,
    val recommendation: String? = null,
)

@Serializable
data class CheckInteractionsRequest(
    @SerialName("item_ids") val itemIds: List<String>,
)

@Serializable
data class InteractionCheckResultResponse(
    @SerialName("has_interactions") val hasInteractions: Boolean,
    val interactions: List<DrugInteractionResponse> = emptyList(),
)
