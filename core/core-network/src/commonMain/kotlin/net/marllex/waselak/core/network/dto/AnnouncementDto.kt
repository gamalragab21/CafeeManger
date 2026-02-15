package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_user_id") val targetUserId: String? = null,
    val title: String,
    val message: String,
    val priority: String,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateAnnouncementRequest(
    @SerialName("target_type") val targetType: String,
    @SerialName("target_user_id") val targetUserId: String? = null,
    val title: String,
    val message: String,
    val priority: String = "NORMAL",
)

@Serializable
data class UnreadCountResponse(val count: Int)
