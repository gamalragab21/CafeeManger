package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TableResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val number: String,
    val capacity: Int = 4,
    val status: String = "AVAILABLE",
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class CreateTableRequest(
    val number: String,
    val capacity: Int = 4
)

@Serializable
data class UpdateTableRequest(
    val number: String? = null,
    val capacity: Int? = null
)

@Serializable
data class UpdateTableStatusRequest(
    val status: String
)
