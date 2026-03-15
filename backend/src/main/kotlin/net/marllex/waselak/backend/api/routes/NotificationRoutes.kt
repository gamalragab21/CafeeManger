package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.NotificationService
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class SuccessResponse(
    val success: Boolean = true,
    val message: String? = null,
)

@Serializable
data class NotificationDto(
    val id: String,
    val vendor_id: String,
    val user_id: String? = null,
    val type: String,
    val title: String,
    val body: String,
    val data: String? = null,
    val channel: String = "IN_APP",
    val priority: String = "NORMAL",
    val read: Boolean = false,
    val read_at: Long? = null,
    val action_url: String? = null,
    val platform: String? = null,      // null=all, ANDROID, DESKTOP, IOS
    val created_at: Long,
)

@Serializable
data class CreateNotificationDto(
    val user_id: String? = null,       // Null for broadcast to all vendor users
    val type: String,
    val title: String,
    val body: String,
    val data: String? = null,          // JSON payload
    val channel: String = "IN_APP",
    val priority: String = "NORMAL",
    val action_url: String? = null,
    val platform: String? = null,      // null=all, ANDROID, DESKTOP, IOS
)

@Serializable
data class RegisterDeviceDto(
    val token: String,
    val platform: String,              // ANDROID, IOS, WEB
    val device_name: String? = null,
)

@Serializable
data class DeviceTokenDto(
    val id: String,
    val user_id: String,
    val vendor_id: String,
    val token: String,
    val platform: String,
    val device_name: String? = null,
    val active: Boolean = true,
    val last_used_at: Long,
    val created_at: Long,
)

@Serializable
data class NotificationCountDto(
    val total: Int = 0,
    val unread: Int = 0,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.notificationRoutes() {
    route("/api/v1/notifications") {

        // GET all notifications for current user
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val unreadOnly = (call.parameters["unread_only"] ?: call.parameters["unread"])?.toBoolean() ?: false
            val type = call.parameters["type"]
            val platformFilter = call.parameters["platform"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0

            val notifications = transaction {
                var query = NotificationsTable.selectAll().where {
                    (NotificationsTable.vendorId eq vendorUUID) and
                    (
                        (NotificationsTable.userId eq userUUID) or
                        (NotificationsTable.userId.isNull())  // Broadcast notifications
                    )
                }
                if (unreadOnly) {
                    query = query.andWhere { NotificationsTable.read eq false }
                }
                type?.let { t ->
                    query = query.andWhere { NotificationsTable.type eq t.uppercase() }
                }
                // Filter by platform: show notifications where platform is null (all) OR matches the filter
                platformFilter?.let { p ->
                    query = query.andWhere {
                        (NotificationsTable.platform.isNull()) or (NotificationsTable.platform eq p.uppercase())
                    }
                }

                query.orderBy(NotificationsTable.createdAt, SortOrder.DESC)
                    .limit(limit).offset(offset.toLong())
                    .map { it.toNotificationDto() }
            }
            call.respond(HttpStatusCode.OK, notifications)
        }

        // GET unread count
        get("/count") {
            val trace = call.routeTrace()
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)

            val count = transaction {
                val total = NotificationsTable.selectAll().where {
                    (NotificationsTable.vendorId eq vendorUUID) and
                    (
                        (NotificationsTable.userId eq userUUID) or
                        (NotificationsTable.userId.isNull())
                    )
                }.count().toInt()

                val unread = NotificationsTable.selectAll().where {
                    (NotificationsTable.vendorId eq vendorUUID) and
                    (
                        (NotificationsTable.userId eq userUUID) or
                        (NotificationsTable.userId.isNull())
                    ) and
                    (NotificationsTable.read eq false)
                }.count().toInt()

                NotificationCountDto(total = total, unread = unread)
            }
            call.respond(HttpStatusCode.OK, count)
        }

        // PATCH mark a notification as read
        patch("/{id}/read") {
            val trace = call.routeTrace()
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val now = Clock.System.now()
                NotificationsTable.update({
                    (NotificationsTable.id eq UUID.fromString(id)) and
                    (NotificationsTable.vendorId eq vendorUUID)
                }) {
                    it[read] = true
                    it[readAt] = now
                }
            }
            call.respond(HttpStatusCode.OK, SuccessResponse(message = "Notification marked as read"))
        }

        // PATCH mark all notifications as read
        patch("/read-all") {
            val trace = call.routeTrace()
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)

            val count = transaction {
                val now = Clock.System.now()
                NotificationsTable.update({
                    (NotificationsTable.vendorId eq vendorUUID) and
                    (
                        (NotificationsTable.userId eq userUUID) or
                        (NotificationsTable.userId.isNull())
                    ) and
                    (NotificationsTable.read eq false)
                }) {
                    it[read] = true
                    it[readAt] = now
                }
            }
            call.respond(HttpStatusCode.OK, SuccessResponse(message = "Marked $count notifications as read"))
        }

        // POST create a notification (internal, for managers/system)
        post {
            val trace = call.routeTrace()
            trace.step("Create notification")
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<CreateNotificationDto>()

            require(request.title.isNotBlank()) { "Title is required" }
            require(request.body.isNotBlank()) { "Body is required" }
            require(request.type in NotificationService.VALID_TYPES) { "Invalid notification type: ${request.type}" }

            val notification = transaction {
                val now = Clock.System.now()
                val id = NotificationsTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[userId] = request.user_id?.let { uid -> UUID.fromString(uid) }
                    it[type] = request.type
                    it[title] = request.title
                    it[body] = request.body
                    it[data] = request.data
                    it[channel] = request.channel
                    it[priority] = request.priority
                    it[read] = false
                    it[actionUrl] = request.action_url
                    it[platform] = request.platform
                    it[createdAt] = now
                }

                NotificationDto(
                    id = id.toString(),
                    vendor_id = vendorUUID.toString(),
                    user_id = request.user_id,
                    type = request.type,
                    title = request.title,
                    body = request.body,
                    data = request.data,
                    channel = request.channel,
                    priority = request.priority,
                    read = false,
                    action_url = request.action_url,
                    platform = request.platform,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Notification created", mapOf("type" to request.type))
            call.respond(HttpStatusCode.Created, notification)
        }

        // DELETE a notification
        delete("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val count = NotificationsTable.deleteWhere {
                    (NotificationsTable.id eq UUID.fromString(id)) and
                    (NotificationsTable.vendorId eq vendorUUID)
                }
                if (count == 0) throw NoSuchElementException("Notification not found")
            }
            call.respond(HttpStatusCode.OK, SuccessResponse(message = "Notification deleted"))
        }
    }

    // ─── Device Token Management ────────────────────────────────
    route("/api/v1/devices") {

        // POST register a device token for push notifications
        post("/register") {
            val trace = call.routeTrace()
            trace.step("Register device token")
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<RegisterDeviceDto>()

            require(request.token.isNotBlank()) { "Token is required" }
            require(request.platform in listOf("ANDROID", "IOS", "WEB")) {
                "Invalid platform: ${request.platform}"
            }

            val device = transaction {
                val now = Clock.System.now()

                // Check if token already registered for this user
                val existing = DeviceTokensTable.selectAll().where {
                    (DeviceTokensTable.userId eq userUUID) and
                    (DeviceTokensTable.token eq request.token)
                }.firstOrNull()

                if (existing != null) {
                    // Update last used
                    DeviceTokensTable.update({
                        DeviceTokensTable.id eq existing[DeviceTokensTable.id]
                    }) {
                        it[active] = true
                        it[lastUsedAt] = now
                        it[deviceName] = request.device_name
                    }
                    return@transaction DeviceTokenDto(
                        id = existing[DeviceTokensTable.id].toString(),
                        user_id = principal.userId,
                        vendor_id = vendorUUID.toString(),
                        token = request.token,
                        platform = request.platform,
                        device_name = request.device_name,
                        active = true,
                        last_used_at = now.toEpochMilliseconds(),
                        created_at = existing[DeviceTokensTable.createdAt].toEpochMilliseconds(),
                    )
                }

                val id = DeviceTokensTable.insertAndGetId {
                    it[userId] = userUUID
                    it[vendorId] = vendorUUID
                    it[token] = request.token
                    it[platform] = request.platform
                    it[deviceName] = request.device_name
                    it[active] = true
                    it[lastUsedAt] = now
                    it[createdAt] = now
                }

                DeviceTokenDto(
                    id = id.toString(),
                    user_id = principal.userId,
                    vendor_id = vendorUUID.toString(),
                    token = request.token,
                    platform = request.platform,
                    device_name = request.device_name,
                    active = true,
                    last_used_at = now.toEpochMilliseconds(),
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Device registered", mapOf("platform" to request.platform))
            call.respond(HttpStatusCode.Created, device)
        }

        // DELETE unregister a device token
        delete("/{token}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            val userUUID = UUID.fromString(principal.userId)
            val token = call.parameters["token"] ?: throw IllegalArgumentException("Token required")

            transaction {
                DeviceTokensTable.update({
                    (DeviceTokensTable.userId eq userUUID) and
                    (DeviceTokensTable.token eq token)
                }) {
                    it[active] = false
                }
            }
            call.respond(HttpStatusCode.OK, SuccessResponse(message = "Device token deactivated"))
        }
    }
}

// ─── Internal Helper: Create notification from system events ────
// (Kept for backward compatibility — prefer NotificationService for new code)

internal fun createSystemNotification(
    vendorUUID: UUID,
    userId: UUID? = null,
    type: String,
    title: String,
    body: String,
    data: String? = null,
    priority: String = "NORMAL",
    actionUrl: String? = null,
    platform: String? = null,
) {
    val now = Clock.System.now()
    NotificationsTable.insert {
        it[vendorId] = vendorUUID
        it[NotificationsTable.userId] = userId
        it[NotificationsTable.type] = type
        it[NotificationsTable.title] = title
        it[NotificationsTable.body] = body
        it[NotificationsTable.data] = data
        it[channel] = if (priority == "URGENT") "BOTH" else "IN_APP"
        it[NotificationsTable.priority] = priority
        it[read] = false
        it[NotificationsTable.actionUrl] = actionUrl
        it[NotificationsTable.platform] = platform
        it[createdAt] = now
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toNotificationDto() = NotificationDto(
    id = this[NotificationsTable.id].toString(),
    vendor_id = this[NotificationsTable.vendorId].toString(),
    user_id = this[NotificationsTable.userId]?.toString(),
    type = this[NotificationsTable.type],
    title = this[NotificationsTable.title],
    body = this[NotificationsTable.body],
    data = this[NotificationsTable.data],
    channel = this[NotificationsTable.channel],
    priority = this[NotificationsTable.priority],
    read = this[NotificationsTable.read],
    read_at = this[NotificationsTable.readAt]?.toEpochMilliseconds(),
    action_url = this[NotificationsTable.actionUrl],
    platform = this[NotificationsTable.platform],
    created_at = this[NotificationsTable.createdAt].toEpochMilliseconds(),
)
