package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Unified Notification System domain models.
 * Supports in-app and push notifications for all app events.
 */

@Serializable
data class AppNotification(
    val id: String,
    val vendorId: String,
    val userId: String? = null,
    val type: String,                  // ORDER_NEW, ORDER_STATUS, LOW_STOCK, EXPIRY_ALERT, SCHEDULED_ORDER, PRESCRIPTION, ANNOUNCEMENT, SYSTEM
    val title: String,
    val body: String,
    val data: String? = null,          // JSON payload
    val channel: String = "IN_APP",    // IN_APP, PUSH, BOTH
    val priority: String = "NORMAL",   // LOW, NORMAL, HIGH, URGENT
    val read: Boolean = false,
    val readAt: Long? = null,
    val actionUrl: String? = null,     // Deep link path
    val createdAt: Long,
) {
    val isRead: Boolean get() = read
    val isUnread: Boolean get() = !read
    val isUrgent: Boolean get() = priority == "URGENT"
    val isHighPriority: Boolean get() = priority in listOf("HIGH", "URGENT")
}

@Serializable
data class NotificationCount(
    val total: Int = 0,
    val unread: Int = 0,
) {
    val hasUnread: Boolean get() = unread > 0
}

@Serializable
data class DeviceToken(
    val id: String,
    val userId: String,
    val vendorId: String,
    val token: String,
    val platform: String,              // ANDROID, IOS, WEB
    val deviceName: String? = null,
    val active: Boolean = true,
    val lastUsedAt: Long,
    val createdAt: Long,
)

enum class NotificationType {
    ORDER_NEW, ORDER_STATUS, LOW_STOCK, EXPIRY_ALERT,
    SCHEDULED_ORDER, PRESCRIPTION, ANNOUNCEMENT, SYSTEM;

    companion object {
        fun fromString(value: String): NotificationType =
            entries.firstOrNull { it.name == value.uppercase() } ?: SYSTEM
    }
}

enum class NotificationPriority {
    LOW, NORMAL, HIGH, URGENT;

    companion object {
        fun fromString(value: String): NotificationPriority =
            entries.firstOrNull { it.name == value.uppercase() } ?: NORMAL
    }
}
