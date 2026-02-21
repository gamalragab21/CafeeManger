package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val role: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val password: String
)

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val active: Boolean? = null,
    val role: String? = null,
    val password: String? = null
)

@Serializable
data class ApiSuccessResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    @SerialName("status_code") val statusCode: Int? = null
)
