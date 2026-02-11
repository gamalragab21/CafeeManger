package net.marllex.cafeemanger.core.model

data class Announcement(
    val id: String,
    val vendorId: String,
    val senderId: String,
    val senderName: String?,
    val targetType: AnnouncementTarget,
    val targetUserId: String?,
    val title: String,
    val message: String,
    val priority: AnnouncementPriority,
    val read: Boolean,
    val createdAt: Long,
)

enum class AnnouncementTarget {
    ALL, CASHIERS, DELIVERY, SPECIFIC
}

enum class AnnouncementPriority {
    NORMAL, URGENT
}
