package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.NotificationRepository
import net.marllex.waselak.core.model.AppNotification
import net.marllex.waselak.core.model.DeviceToken
import net.marllex.waselak.core.model.NotificationCount
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.RegisterDeviceRequest
import net.marllex.waselak.core.network.mapper.toDomain

class NotificationRepositoryImpl(
    private val api: WaselakApiClient,
) : NotificationRepository {

    override suspend fun getNotifications(type: String?, unreadOnly: Boolean?, limit: Int, offset: Int): Result<List<AppNotification>> = runCatching {
        api.getNotifications(type, unreadOnly, limit, offset).map { it.toDomain() }
    }

    override suspend fun getCount(): Result<NotificationCount> = runCatching {
        api.getNotificationCount().toDomain()
    }

    override suspend fun markRead(id: String): Result<Unit> = runCatching {
        api.markNotificationRead(id)
    }

    override suspend fun markAllRead(): Result<Unit> = runCatching {
        api.markAllNotificationsRead()
    }

    override suspend fun deleteNotification(id: String): Result<Unit> = runCatching {
        api.deleteNotification(id)
    }

    override suspend fun registerDevice(token: String, platform: String, deviceName: String?): Result<DeviceToken> = runCatching {
        api.registerDevice(RegisterDeviceRequest(token, platform, deviceName)).toDomain()
    }

    override suspend fun unregisterDevice(token: String): Result<Unit> = runCatching {
        api.unregisterDevice(token)
    }
}
