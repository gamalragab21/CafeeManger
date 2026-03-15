package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.AppNotification
import net.marllex.waselak.core.model.DeviceToken
import net.marllex.waselak.core.model.NotificationCount

interface NotificationRepository {
    suspend fun getNotifications(type: String? = null, unreadOnly: Boolean? = null, limit: Int = 50, offset: Int = 0): Result<List<AppNotification>>
    suspend fun getCount(): Result<NotificationCount>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun deleteNotification(id: String): Result<Unit>
    suspend fun registerDevice(token: String, platform: String, deviceName: String? = null): Result<DeviceToken>
    suspend fun unregisterDevice(token: String): Result<Unit>
}
