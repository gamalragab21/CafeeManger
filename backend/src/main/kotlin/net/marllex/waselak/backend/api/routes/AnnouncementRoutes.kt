package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.AnnouncementReadsTable
import net.marllex.waselak.backend.data.database.AnnouncementsTable
import net.marllex.waselak.backend.data.database.UsersTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateAnnouncementRequest(
    val target_type: String, // ALL, CASHIERS, DELIVERY, SPECIFIC
    val target_user_id: String? = null,
    val title: String,
    val message: String,
    val priority: String = "NORMAL", // NORMAL, URGENT
)

@Serializable
data class AnnouncementDto(
    val id: String,
    val vendor_id: String,
    val sender_id: String,
    val sender_name: String? = null,
    val target_type: String,
    val target_user_id: String? = null,
    val title: String,
    val message: String,
    val priority: String,
    val read: Boolean = false,
    val created_at: Long,
)

@Serializable
data class UnreadCountDto(val count: Int)

fun Route.announcementRoutes() {
    route("/api/v1/announcements") {

        // Create announcement (MANAGER only)
        post {
            val trace = call.routeTrace()
            trace.step("Create announcement started")
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateAnnouncementRequest>()
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Creating announcement", mapOf("title" to request.title, "targetType" to request.target_type, "priority" to request.priority, "vendorId" to vendorUUID.toString()))

            val announcement = transaction {
                val id = AnnouncementsTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[senderId] = UUID.fromString(principal.userId)
                    it[targetType] = request.target_type
                    request.target_user_id?.let { uid ->
                        it[targetUserId] = UUID.fromString(uid)
                    }
                    it[title] = request.title
                    it[message] = request.message
                    it[priority] = request.priority
                    it[createdAt] = Clock.System.now()
                }

                val row = AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.id eq id }
                    .first()

                val senderName = UsersTable.selectAll()
                    .where { UsersTable.id eq row[AnnouncementsTable.senderId] }
                    .firstOrNull()?.get(UsersTable.name)

                AnnouncementDto(
                    id = id.toString(),
                    vendor_id = vendorUUID.toString(),
                    sender_id = principal.userId,
                    sender_name = senderName,
                    target_type = row[AnnouncementsTable.targetType],
                    target_user_id = row[AnnouncementsTable.targetUserId]?.toString(),
                    title = row[AnnouncementsTable.title],
                    message = row[AnnouncementsTable.message],
                    priority = row[AnnouncementsTable.priority],
                    read = false,
                    created_at = row[AnnouncementsTable.createdAt].toEpochMilliseconds(),
                )
            }
            trace.step("Announcement created", mapOf("id" to announcement.id, "title" to announcement.title))
            trace.step("Create announcement completed")
            call.respond(HttpStatusCode.Created, announcement)
        }

        // List announcements (filtered by user's role)
        get {
            val trace = call.routeTrace()
            trace.step("List announcements started")
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            trace.step("Querying announcements", mapOf("vendorId" to vendorUUID.toString(), "role" to principal.role))

            val announcements = transaction {
                val rows = AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.vendorId eq vendorUUID }
                    .orderBy(AnnouncementsTable.createdAt, SortOrder.DESC)
                    .toList()

                // Filter by target
                val roleTarget = when (principal.role) {
                    "CASHIER" -> "CASHIERS"
                    "DELIVERY" -> "DELIVERY"
                    else -> null
                }

                val filtered = rows.filter { row ->
                    val target = row[AnnouncementsTable.targetType]
                    when (target) {
                        "ALL" -> true
                        "SPECIFIC" -> row[AnnouncementsTable.targetUserId]?.value == userUUID
                        else -> target == roleTarget || principal.role == "MANAGER"
                    }
                }

                // Get read status
                val readIds = AnnouncementReadsTable.selectAll()
                    .where { AnnouncementReadsTable.userId eq userUUID }
                    .map { it[AnnouncementReadsTable.announcementId].value }
                    .toSet()

                // Get sender names
                val senderIds = filtered.map { it[AnnouncementsTable.senderId] }.distinct()
                val senderNames = if (senderIds.isEmpty()) emptyMap() else {
                    UsersTable.selectAll()
                        .where { UsersTable.id inList senderIds }
                        .associate { it[UsersTable.id].value to it[UsersTable.name] }
                }

                filtered.map { row ->
                    val announcementId = row[AnnouncementsTable.id].value
                    AnnouncementDto(
                        id = announcementId.toString(),
                        vendor_id = vendorUUID.toString(),
                        sender_id = row[AnnouncementsTable.senderId].toString(),
                        sender_name = senderNames[row[AnnouncementsTable.senderId].value],
                        target_type = row[AnnouncementsTable.targetType],
                        target_user_id = row[AnnouncementsTable.targetUserId]?.toString(),
                        title = row[AnnouncementsTable.title],
                        message = row[AnnouncementsTable.message],
                        priority = row[AnnouncementsTable.priority],
                        read = announcementId in readIds,
                        created_at = row[AnnouncementsTable.createdAt].toEpochMilliseconds(),
                    )
                }
            }
            trace.step("Announcements retrieved", mapOf("count" to announcements.size.toString()))
            trace.step("List announcements completed")
            call.respond(HttpStatusCode.OK, announcements)
        }

        // Get unread count
        get("/unread-count") {
            val trace = call.routeTrace()
            trace.step("Get unread count started")
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            trace.step("Querying unread count", mapOf("vendorId" to vendorUUID.toString(), "userId" to userUUID.toString()))

            val count = transaction {
                val allAnnouncements = AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.vendorId eq vendorUUID }
                    .toList()

                val roleTarget = when (principal.role) {
                    "CASHIER" -> "CASHIERS"
                    "DELIVERY" -> "DELIVERY"
                    else -> null
                }

                val filtered = allAnnouncements.filter { row ->
                    val target = row[AnnouncementsTable.targetType]
                    when (target) {
                        "ALL" -> true
                        "SPECIFIC" -> row[AnnouncementsTable.targetUserId]?.value == userUUID
                        else -> target == roleTarget || principal.role == "MANAGER"
                    }
                }

                val readIds = AnnouncementReadsTable.selectAll()
                    .where { AnnouncementReadsTable.userId eq userUUID }
                    .map { it[AnnouncementReadsTable.announcementId].value }
                    .toSet()

                filtered.count { it[AnnouncementsTable.id].value !in readIds }
            }
            trace.step("Unread count result", mapOf("count" to count.toString()))
            trace.step("Get unread count completed")
            call.respond(HttpStatusCode.OK, UnreadCountDto(count))
        }

        // Mark as read
        post("/{id}/read") {
            val trace = call.routeTrace()
            trace.step("Mark announcement as read started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val announcementUUID = UUID.fromString(id)
            val userUUID = UUID.fromString(principal.userId)
            trace.step("Marking announcement as read", mapOf("announcementId" to id, "userId" to userUUID.toString()))

            transaction {
                // Check if not already read
                val existing = AnnouncementReadsTable.selectAll().where {
                    (AnnouncementReadsTable.announcementId eq announcementUUID) and
                    (AnnouncementReadsTable.userId eq userUUID)
                }.firstOrNull()

                if (existing == null) {
                    AnnouncementReadsTable.insert {
                        it[announcementId] = announcementUUID
                        it[userId] = userUUID
                        it[readAt] = Clock.System.now()
                    }
                }
            }
            trace.step("Mark as read completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // Delete announcement (MANAGER only)
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete announcement started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val announcementUUID = UUID.fromString(id)
            trace.step("Deleting announcement", mapOf("announcementId" to id, "vendorId" to principal.vendorId))

            transaction {
                // Delete reads first
                AnnouncementReadsTable.deleteWhere {
                    announcementId eq announcementUUID
                }
                // Delete announcement
                AnnouncementsTable.deleteWhere {
                    (AnnouncementsTable.id eq announcementUUID) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
            }
            trace.step("Announcement deleted", mapOf("announcementId" to id))
            trace.step("Delete announcement completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}
