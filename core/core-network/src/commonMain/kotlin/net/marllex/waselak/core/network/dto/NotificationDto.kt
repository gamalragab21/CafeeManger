package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("user_id") val userId: String? = null,
    val type: String,
    val title: String,
    val body: String,
    val data: String? = null,
    val channel: String = "IN_APP",
    val priority: String = "NORMAL",
    val read: Boolean = false,
    @SerialName("read_at") val readAt: Long? = null,
    @SerialName("action_url") val actionUrl: String? = null,
    val platform: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateNotificationRequest(
    @SerialName("user_id") val userId: String? = null,
    val type: String,
    val title: String,
    val body: String,
    val data: String? = null,
    val channel: String = "IN_APP",
    val priority: String = "NORMAL",
    @SerialName("action_url") val actionUrl: String? = null,
    val platform: String? = null,
)

@Serializable
data class NotificationCountResponse(
    val total: Int = 0,
    val unread: Int = 0,
)

@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class DeviceTokenResponse(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("vendor_id") val vendorId: String,
    val token: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String? = null,
    val active: Boolean = true,
    @SerialName("last_used_at") val lastUsedAt: Long,
    @SerialName("created_at") val createdAt: Long,
)
