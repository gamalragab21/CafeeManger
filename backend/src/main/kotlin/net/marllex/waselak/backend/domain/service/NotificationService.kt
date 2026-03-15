package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.Clock
import net.marllex.waselak.backend.data.database.NotificationsTable
import net.marllex.waselak.backend.data.database.VendorSubscriptionsTable
import net.marllex.waselak.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Centralized notification creation service.
 * Used by route hooks (order cancel/refund, stock alerts, PO receive)
 * and scheduled tasks (subscription expiry).
 */
class NotificationService {

    companion object {
        val VALID_TYPES = listOf(
            "ORDER_NEW", "ORDER_STATUS", "ORDER_CANCELLED", "ORDER_REFUNDED",
            "LOW_STOCK", "OUT_OF_STOCK", "EXPIRY_ALERT",
            "SCHEDULED_ORDER", "PRESCRIPTION",
            "PO_RECEIVED",
            "SUBSCRIPTION_EXPIRING", "SUBSCRIPTION_EXPIRED",
            "ADMIN_ANNOUNCEMENT", "SYSTEM_UPDATE",
            "ANNOUNCEMENT", "SYSTEM",
        )
    }

    /**
     * Create a notification for a specific vendor (optionally targeted at a specific user).
     * Must be called inside a transaction.
     */
    fun notify(
        vendorId: UUID,
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
            it[NotificationsTable.vendorId] = vendorId
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

    /**
     * Broadcast a notification to all users of a vendor (userId=null).
     * Must be called inside a transaction.
     */
    fun broadcast(
        vendorId: UUID,
        type: String,
        title: String,
        body: String,
        data: String? = null,
        priority: String = "NORMAL",
        actionUrl: String? = null,
        platform: String? = null,
    ) {
        notify(
            vendorId = vendorId,
            userId = null,
            type = type,
            title = title,
            body = body,
            data = data,
            priority = priority,
            actionUrl = actionUrl,
            platform = platform,
        )
    }

    /**
     * Send a notification to ALL active vendors (e.g., admin announcements, system updates).
     * Creates one broadcast notification per vendor.
     * Runs its own transaction.
     */
    fun notifyAllVendors(
        type: String,
        title: String,
        body: String,
        actionUrl: String? = null,
        platform: String? = null,
        priority: String = "NORMAL",
    ) {
        transaction {
            val activeVendorIds = VendorsTable.selectAll()
                .map { it[VendorsTable.id].value }

            for (vId in activeVendorIds) {
                broadcast(
                    vendorId = vId,
                    type = type,
                    title = title,
                    body = body,
                    priority = priority,
                    actionUrl = actionUrl,
                    platform = platform,
                )
            }
        }
    }

    /**
     * Send a notification to specific vendors (by vendor IDs).
     * Runs its own transaction.
     */
    fun notifyVendors(
        vendorIds: List<UUID>,
        type: String,
        title: String,
        body: String,
        actionUrl: String? = null,
        platform: String? = null,
        priority: String = "NORMAL",
    ) {
        transaction {
            for (vId in vendorIds) {
                broadcast(
                    vendorId = vId,
                    type = type,
                    title = title,
                    body = body,
                    priority = priority,
                    actionUrl = actionUrl,
                    platform = platform,
                )
            }
        }
    }
}
