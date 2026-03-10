package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val vendorId: String,
    val role: UserRole,
    val name: String,
    val phone: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val active: Boolean = true,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
enum class UserRole {
    MANAGER,
    CASHIER,
    DELIVERY
}
