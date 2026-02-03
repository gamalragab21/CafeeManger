package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ActivityLog(
    val id: String,
    val orderId: String,
    val userId: String,
    val userName: String? = null,
    val action: String,
    val payload: Map<String, String>? = null,
    val createdAt: Long
)
