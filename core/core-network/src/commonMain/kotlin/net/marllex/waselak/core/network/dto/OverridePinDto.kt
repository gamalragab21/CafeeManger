package net.marllex.waselak.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Manager POS-override PIN DTOs. Mirror the backend's serializable request/response
 * types in `OverridePinRoutes.kt`. Used by:
 *  - Manager app: set/change own PIN in the "My Account" screen.
 *  - Cashier app: verify a manager's PIN before applying a discount.
 */

@Serializable
data class SetOverridePinRequest(
    val currentPassword: String,
    val pin: String,
)

@Serializable
data class SetOverridePinResponse(
    val overridePinSet: Boolean,
)

@Serializable
data class VerifyOverridePinRequest(
    val pin: String,
)

@Serializable
data class VerifyOverridePinResponse(
    val token: String,
    val managerId: String,
    val managerName: String,
    val expiresInSeconds: Int,
)

@Serializable
data class ResetOverridePinResponse(
    val overridePinSet: Boolean,
)
