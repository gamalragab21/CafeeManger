package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
    @SerialName("app_type") val appType: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val role: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: Long? = null
)
