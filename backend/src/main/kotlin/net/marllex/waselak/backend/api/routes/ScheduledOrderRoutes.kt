package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class ScheduledOrderDto(
    val id: String,
    val vendor_id: String,
    val customer_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val channel: String = "PICKUP_LATER",
    val scheduled_for: Long,
    val reminder_sent_at: Long? = null,
    val status: String = "SCHEDULED",
    val notes: String? = null,
    val payment_method: String = "CASH",
    val payment_status: String = "PENDING",
    val subtotal: Double = 0.0,
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val delivery_fee: Double = 0.0,
    val order_id: String? = null,         // Linked actual order once converted
    val created_by: String,
    val items: List<ScheduledOrderItemDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class ScheduledOrderItemDto(
    val id: String,
    val scheduled_order_id: String,
    val item_id: String,
    val item_name: String,
    val item_price: Double,
    val quantity: Int,
    val note: String? = null,
    val variant_options: String? = null,
    val created_at: Long,
)

@Serializable
data class CreateScheduledOrderDto(
    val customer_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val channel: String = "PICKUP_LATER",
    val scheduled_for: Long,              // Epoch ms of when order should be ready
    val notes: String? = null,
    val payment_method: String = "CASH",
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val delivery_fee: Double = 0.0,
    val items: List<CreateScheduledOrderItemDto>,
)

@Serializable
data class CreateScheduledOrderItemDto(
    val item_id: String,
    val quantity: Int,
    val note: String? = null,
    val variant_options: String? = null,
)

@Serializable
data class UpdateScheduledOrderStatusDto(
    val status: String,                   // CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED
    val notes: String? = null,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.scheduledOrderRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/scheduled-orders") {

        // GET all scheduled orders for vendor
        get {
            val trace = call.routeTrace()
            trace.step("List scheduled orders")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SCHEDULED_ORDERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val upcoming = call.parameters["upcoming"]?.toBoolean() ?: false
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50

            val orders = transaction {
                val now = Clock.System.now()
                var query = ScheduledOrdersTable.selectAll().where {
                    ScheduledOrdersTable.vendorId eq vendorUUID
                }
                status?.let { s ->
                    query = query.andWhere { ScheduledOrdersTable.status eq s.uppercase() }
                }
                if (upcoming) {
                    query = query.andWhere {
                        (ScheduledOrdersTable.scheduledFor greaterEq now) and
                        (ScheduledOrdersTable.status inList listOf("SCHEDULED", "CONFIRMED"))
                    }
                }

                query.orderBy(ScheduledOrdersTable.scheduledFor, SortOrder.ASC)
                    .limit(limit)
                    .map { row ->
                        val soId = row[ScheduledOrdersTable.id]
                        val items = ScheduledOrderItemsTable.selectAll().where {
                            ScheduledOrderItemsTable.scheduledOrderId eq soId
                        }.map { it.toScheduledOrderItemDto(soId.toString()) }
                        row.toScheduledOrderDto(items)
                    }
            }
            trace.step("Scheduled orders listed", mapOf("count" to orders.size.toString()))
            call.respond(HttpStatusCode.OK, orders)
        }

        // GET single scheduled order
        get("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SCHEDULED_ORDERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val order = transaction {
                val row = ScheduledOrdersTable.selectAll().where {
                    (ScheduledOrdersTable.id eq UUID.fromString(id)) and
                    (ScheduledOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Scheduled order not found")

                val items = ScheduledOrderItemsTable.selectAll().where {
                    ScheduledOrderItemsTable.scheduledOrderId eq row[ScheduledOrdersTable.id]
                }.map { it.toScheduledOrderItemDto(id) }

                row.toScheduledOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        // POST create a scheduled order
        post {
            val trace = call.routeTrace()
            trace.step("Create scheduled order")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SCHEDULED_ORDERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CreateScheduledOrderDto>()

            require(request.items.isNotEmpty()) { "At least one item is required" }
            require(request.scheduled_for > Clock.System.now().toEpochMilliseconds()) {
                "Scheduled time must be in the future"
            }

            val order = transaction {
                val now = Clock.System.now()

                // Calculate totals from items
                var subtotal = BigDecimal.ZERO
                val itemsData = request.items.map { soItem ->
                    val itemUUID = UUID.fromString(soItem.item_id)
                    val item = ItemsTable.selectAll().where { ItemsTable.id eq itemUUID }
                        .firstOrNull() ?: throw NoSuchElementException("Item ${soItem.item_id} not found")

                    val price = item[ItemsTable.price]
                    val lineTotal = price * BigDecimal(soItem.quantity)
                    subtotal += lineTotal

                    Triple(soItem, item, price)
                }

                val discountBD = BigDecimal(request.discount)
                val taxBD = BigDecimal(request.tax)
                val deliveryFeeBD = BigDecimal(request.delivery_fee)
                val total = subtotal - discountBD + taxBD + deliveryFeeBD

                val soId = ScheduledOrdersTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[customerId] = request.customer_id?.let { cid -> UUID.fromString(cid) }
                    it[clientName] = request.client_name
                    it[clientPhone] = request.client_phone
                    it[channel] = request.channel
                    it[scheduledFor] = Instant.fromEpochMilliseconds(request.scheduled_for)
                    it[status] = "SCHEDULED"
                    it[notes] = request.notes
                    it[paymentMethod] = request.payment_method
                    it[paymentStatus] = "PENDING"
                    it[ScheduledOrdersTable.subtotal] = subtotal
                    it[ScheduledOrdersTable.total] = total
                    it[ScheduledOrdersTable.discount] = discountBD
                    it[ScheduledOrdersTable.tax] = taxBD
                    it[ScheduledOrdersTable.deliveryFee] = deliveryFeeBD
                    it[createdBy] = userUUID
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                val createdItems = itemsData.map { (soItem, item, price) ->
                    val siId = ScheduledOrderItemsTable.insertAndGetId {
                        it[scheduledOrderId] = soId
                        it[itemId] = UUID.fromString(soItem.item_id)
                        it[itemName] = item[ItemsTable.name]
                        it[itemPrice] = price
                        it[quantity] = soItem.quantity
                        it[note] = soItem.note
                        it[variantOptions] = soItem.variant_options
                        it[createdAt] = now
                    }

                    ScheduledOrderItemDto(
                        id = siId.toString(),
                        scheduled_order_id = soId.toString(),
                        item_id = soItem.item_id,
                        item_name = item[ItemsTable.name],
                        item_price = price.toDouble(),
                        quantity = soItem.quantity,
                        note = soItem.note,
                        variant_options = soItem.variant_options,
                        created_at = now.toEpochMilliseconds(),
                    )
                }

                ScheduledOrderDto(
                    id = soId.toString(),
                    vendor_id = vendorUUID.toString(),
                    customer_id = request.customer_id,
                    client_name = request.client_name,
                    client_phone = request.client_phone,
                    channel = request.channel,
                    scheduled_for = request.scheduled_for,
                    status = "SCHEDULED",
                    notes = request.notes,
                    payment_method = request.payment_method,
                    payment_status = "PENDING",
                    subtotal = subtotal.toDouble(),
                    total = total.toDouble(),
                    discount = request.discount,
                    tax = request.tax,
                    delivery_fee = request.delivery_fee,
                    created_by = principal.userId,
                    items = createdItems,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Scheduled order created", mapOf("id" to order.id))
            call.respond(HttpStatusCode.Created, order)
        }

        // PATCH update scheduled order status
        patch("/{id}/status") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SCHEDULED_ORDERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateScheduledOrderStatusDto>()

            require(request.status in listOf("CONFIRMED", "PREPARING", "READY", "COMPLETED", "CANCELLED")) {
                "Invalid status: ${request.status}"
            }

            transaction {
                val soUUID = UUID.fromString(id)
                val now = Clock.System.now()

                ScheduledOrdersTable.selectAll().where {
                    (ScheduledOrdersTable.id eq soUUID) and
                    (ScheduledOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Scheduled order not found")

                ScheduledOrdersTable.update({ ScheduledOrdersTable.id eq soUUID }) {
                    it[status] = request.status
                    it[updatedAt] = now
                    if (request.notes != null) {
                        it[notes] = request.notes
                    }
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "status" to request.status))
        }

        // DELETE cancel a scheduled order
        delete("/{id}") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SCHEDULED_ORDERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val soUUID = UUID.fromString(id)
                val row = ScheduledOrdersTable.selectAll().where {
                    (ScheduledOrdersTable.id eq soUUID) and
                    (ScheduledOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Scheduled order not found")

                require(row[ScheduledOrdersTable.status] in listOf("SCHEDULED", "CONFIRMED")) {
                    "Can only cancel scheduled or confirmed orders"
                }

                ScheduledOrdersTable.update({ ScheduledOrdersTable.id eq soUUID }) {
                    it[status] = "CANCELLED"
                    it[updatedAt] = Clock.System.now()
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "status" to "CANCELLED"))
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toScheduledOrderDto(items: List<ScheduledOrderItemDto> = emptyList()) = ScheduledOrderDto(
    id = this[ScheduledOrdersTable.id].toString(),
    vendor_id = this[ScheduledOrdersTable.vendorId].toString(),
    customer_id = this[ScheduledOrdersTable.customerId]?.toString(),
    client_name = this[ScheduledOrdersTable.clientName],
    client_phone = this[ScheduledOrdersTable.clientPhone],
    channel = this[ScheduledOrdersTable.channel],
    scheduled_for = this[ScheduledOrdersTable.scheduledFor].toEpochMilliseconds(),
    reminder_sent_at = this[ScheduledOrdersTable.reminderSentAt]?.toEpochMilliseconds(),
    status = this[ScheduledOrdersTable.status],
    notes = this[ScheduledOrdersTable.notes],
    payment_method = this[ScheduledOrdersTable.paymentMethod],
    payment_status = this[ScheduledOrdersTable.paymentStatus],
    subtotal = this[ScheduledOrdersTable.subtotal].toDouble(),
    total = this[ScheduledOrdersTable.total].toDouble(),
    discount = this[ScheduledOrdersTable.discount].toDouble(),
    tax = this[ScheduledOrdersTable.tax].toDouble(),
    delivery_fee = this[ScheduledOrdersTable.deliveryFee].toDouble(),
    order_id = this[ScheduledOrdersTable.orderId]?.toString(),
    created_by = this[ScheduledOrdersTable.createdBy].toString(),
    items = items,
    created_at = this[ScheduledOrdersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[ScheduledOrdersTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toScheduledOrderItemDto(soId: String) = ScheduledOrderItemDto(
    id = this[ScheduledOrderItemsTable.id].toString(),
    scheduled_order_id = soId,
    item_id = this[ScheduledOrderItemsTable.itemId].toString(),
    item_name = this[ScheduledOrderItemsTable.itemName],
    item_price = this[ScheduledOrderItemsTable.itemPrice].toDouble(),
    quantity = this[ScheduledOrderItemsTable.quantity],
    note = this[ScheduledOrderItemsTable.note],
    variant_options = this[ScheduledOrderItemsTable.variantOptions],
    created_at = this[ScheduledOrderItemsTable.createdAt].toEpochMilliseconds(),
)
