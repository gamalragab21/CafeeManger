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
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class KdsOrderDto(
    val order_id: String,
    val order_number: Int,
    val channel: String,
    val table_number: String? = null,
    val client_name: String? = null,
    val notes: String? = null,
    val items: List<KdsOrderItemDto>,
    val created_at: Long,
    val elapsed_minutes: Long = 0,
)

@Serializable
data class KdsOrderItemDto(
    val id: String,
    val order_id: String,
    val item_name: String,
    val quantity: Int,
    val note: String? = null,
    val variant_options: String? = null,
    val kitchen_status: String,    // PENDING, COOKING, READY, SERVED
    val kitchen_station: String? = null,
    val created_at: Long,
)

@Serializable
data class UpdateKitchenStatusDto(
    val status: String,   // COOKING, READY, SERVED
)

@Serializable
data class BulkUpdateKitchenStatusDto(
    val item_ids: List<String>,
    val status: String,
)

@Serializable
data class KdsSummaryDto(
    val total_items: Int = 0,
    val pending: Int = 0,
    val cooking: Int = 0,
    val ready: Int = 0,
    val served: Int = 0,
    val avg_prep_time_minutes: Double = 0.0,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.kdsRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/kds") {

        // GET active kitchen queue -- orders with at least one PENDING/COOKING item
        get("/orders") {
            val trace = call.routeTrace()
            trace.step("KDS order queue started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "KDS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val station = call.parameters["station"]  // Optional filter by kitchen station
            val statusFilter = call.parameters["status"] // Optional: PENDING, COOKING, READY

            val orders = transaction {
                val now = Clock.System.now()

                // Find orders that are active (CREATED, IN_PROGRESS, SERVED states, not COMPLETED/CANCELLED)
                val activeStatuses = listOf("CREATED", "IN_PROGRESS", "PREPARING", "READY", "SERVED")
                val activeOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.status inList activeStatuses)
                }.orderBy(OrdersTable.createdAt, SortOrder.ASC).toList()

                activeOrders.mapNotNull { order ->
                    val orderId = order[OrdersTable.id]

                    // Get items for this order, optionally filtered by station and status
                    var itemQuery = OrderItemsTable.selectAll().where {
                        OrderItemsTable.orderId eq orderId
                    }
                    station?.let { s ->
                        itemQuery = itemQuery.andWhere {
                            OrderItemsTable.kitchenStation eq s.uppercase()
                        }
                    }
                    statusFilter?.let { sf ->
                        itemQuery = itemQuery.andWhere {
                            OrderItemsTable.kitchenStatus eq sf.uppercase()
                        }
                    }

                    val items = itemQuery.toList()

                    // Skip orders with no matching items
                    if (items.isEmpty()) return@mapNotNull null

                    // Skip if all items in this order are SERVED
                    val allServed = items.all { it[OrderItemsTable.kitchenStatus] == "SERVED" }
                    if (allServed && statusFilter == null) return@mapNotNull null

                    // Get table number if applicable
                    val tableNumber = order[OrdersTable.tableId]?.let { tableId ->
                        TablesTable.selectAll().where { TablesTable.id eq tableId }
                            .firstOrNull()?.get(TablesTable.number)
                    }

                    val orderCreatedAt = order[OrdersTable.createdAt]
                    val elapsedMinutes = (now - orderCreatedAt).inWholeMinutes

                    KdsOrderDto(
                        order_id = orderId.toString(),
                        order_number = activeOrders.indexOf(order) + 1,
                        channel = order[OrdersTable.channel],
                        table_number = tableNumber,
                        client_name = order[OrdersTable.clientName],
                        notes = order[OrdersTable.notes],
                        items = items.map { item ->
                            KdsOrderItemDto(
                                id = item[OrderItemsTable.id].toString(),
                                order_id = orderId.toString(),
                                item_name = item[OrderItemsTable.itemNameSnapshot],
                                quantity = item[OrderItemsTable.quantity],
                                note = item[OrderItemsTable.note],
                                variant_options = item[OrderItemsTable.variantOptionsSnapshot],
                                kitchen_status = item[OrderItemsTable.kitchenStatus],
                                kitchen_station = item[OrderItemsTable.kitchenStation],
                                created_at = item[OrderItemsTable.createdAt].toEpochMilliseconds(),
                            )
                        },
                        created_at = orderCreatedAt.toEpochMilliseconds(),
                        elapsed_minutes = elapsedMinutes,
                    )
                }
            }
            trace.step("KDS queue loaded", mapOf("orderCount" to orders.size.toString()))
            call.respond(HttpStatusCode.OK, orders)
        }

        // PATCH update single item kitchen status
        patch("/items/{itemId}/status") {
            val trace = call.routeTrace()
            trace.step("Update kitchen status started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "KDS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val itemId = call.parameters["itemId"]
                ?: throw IllegalArgumentException("Item ID required")
            val request = call.receive<UpdateKitchenStatusDto>()

            require(request.status in listOf("PENDING", "COOKING", "READY", "SERVED")) {
                "Invalid kitchen status: ${request.status}"
            }

            val updatedItem = transaction {
                val itemUUID = UUID.fromString(itemId)

                // Verify item belongs to an order owned by this vendor
                val item = OrderItemsTable.selectAll().where {
                    OrderItemsTable.id eq itemUUID
                }.firstOrNull() ?: throw NoSuchElementException("Order item not found")

                val orderId = item[OrderItemsTable.orderId]
                val order = OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderId) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found for this vendor")

                // Update kitchen status
                OrderItemsTable.update({ OrderItemsTable.id eq itemUUID }) {
                    it[kitchenStatus] = request.status
                }

                // If all items in this order are now READY, update order status to READY
                val allItems = OrderItemsTable.selectAll().where {
                    OrderItemsTable.orderId eq orderId
                }.toList()
                val allReady = allItems.all { oi ->
                    val id = oi[OrderItemsTable.id].value
                    if (id == itemUUID.let { itemUUID }) request.status == "READY" || request.status == "SERVED"
                    else oi[OrderItemsTable.kitchenStatus] in listOf("READY", "SERVED")
                }
                if (allReady) {
                    OrdersTable.update({ OrdersTable.id eq orderId }) {
                        it[status] = "READY"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                // If all items are SERVED, and order is DINE_IN, update to SERVED
                val allServed = allItems.all { oi ->
                    val id = oi[OrderItemsTable.id].value
                    if (id == itemUUID.let { itemUUID }) request.status == "SERVED"
                    else oi[OrderItemsTable.kitchenStatus] == "SERVED"
                }
                if (allServed && order[OrdersTable.channel] == "DINE_IN") {
                    OrdersTable.update({ OrdersTable.id eq orderId }) {
                        it[status] = "SERVED"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                KdsOrderItemDto(
                    id = itemId,
                    order_id = orderId.toString(),
                    item_name = item[OrderItemsTable.itemNameSnapshot],
                    quantity = item[OrderItemsTable.quantity],
                    note = item[OrderItemsTable.note],
                    variant_options = item[OrderItemsTable.variantOptionsSnapshot],
                    kitchen_status = request.status,
                    kitchen_station = item[OrderItemsTable.kitchenStation],
                    created_at = item[OrderItemsTable.createdAt].toEpochMilliseconds(),
                )
            }
            trace.step("Kitchen status updated", mapOf("itemId" to itemId, "status" to request.status))
            call.respond(HttpStatusCode.OK, updatedItem)
        }

        // PATCH bulk update -- mark all items in an order as READY/SERVED
        patch("/orders/{orderId}/bulk-status") {
            val trace = call.routeTrace()
            trace.step("Bulk KDS status update started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "KDS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val orderId = call.parameters["orderId"]
                ?: throw IllegalArgumentException("Order ID required")
            val request = call.receive<BulkUpdateKitchenStatusDto>()

            require(request.status in listOf("COOKING", "READY", "SERVED")) {
                "Invalid kitchen status: ${request.status}"
            }

            val updatedCount = transaction {
                val orderUUID = UUID.fromString(orderId)
                val now = Clock.System.now()

                // Verify order belongs to vendor
                OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderUUID) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val count = if (request.item_ids.isNotEmpty()) {
                    // Update specific items
                    val itemUUIDs = request.item_ids.map { UUID.fromString(it) }
                    OrderItemsTable.update({
                        (OrderItemsTable.orderId eq orderUUID) and
                        (OrderItemsTable.id inList itemUUIDs)
                    }) {
                        it[kitchenStatus] = request.status
                    }
                } else {
                    // Update all items in the order
                    OrderItemsTable.update({
                        OrderItemsTable.orderId eq orderUUID
                    }) {
                        it[kitchenStatus] = request.status
                    }
                }

                // Auto-update order status based on items
                if (request.status == "READY") {
                    OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                        it[status] = "READY"
                        it[updatedAt] = now
                    }
                } else if (request.status == "SERVED") {
                    OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                        it[status] = "SERVED"
                        it[updatedAt] = now
                    }
                }

                count
            }
            trace.step("Bulk KDS update", mapOf("orderId" to orderId, "updatedItems" to updatedCount.toString()))
            call.respond(HttpStatusCode.OK, mapOf("updated" to updatedCount, "status" to request.status))
        }

        // GET KDS summary/stats
        get("/summary") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "KDS")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val activeStatuses = listOf("CREATED", "IN_PROGRESS", "PREPARING", "READY", "SERVED")
                val activeOrderIds = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.status inList activeStatuses)
                }.map { it[OrdersTable.id] }

                if (activeOrderIds.isEmpty()) {
                    return@transaction KdsSummaryDto()
                }

                val items = OrderItemsTable.selectAll().where {
                    OrderItemsTable.orderId inList activeOrderIds
                }.toList()

                KdsSummaryDto(
                    total_items = items.size,
                    pending = items.count { it[OrderItemsTable.kitchenStatus] == "PENDING" },
                    cooking = items.count { it[OrderItemsTable.kitchenStatus] == "COOKING" },
                    ready = items.count { it[OrderItemsTable.kitchenStatus] == "READY" },
                    served = items.count { it[OrderItemsTable.kitchenStatus] == "SERVED" },
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        // PATCH assign kitchen station to order items
        patch("/items/{itemId}/station") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER", "CASHIER", "KITCHEN")
            planService.checkFeature(UUID.fromString(principal.vendorId), "KDS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val itemId = call.parameters["itemId"]
                ?: throw IllegalArgumentException("Item ID required")

            @Serializable
            data class AssignStationDto(val station: String?)
            val request = call.receive<AssignStationDto>()

            transaction {
                val itemUUID = UUID.fromString(itemId)

                // Verify item belongs to vendor's order
                val item = OrderItemsTable.selectAll().where {
                    OrderItemsTable.id eq itemUUID
                }.firstOrNull() ?: throw NoSuchElementException("Order item not found")

                OrdersTable.selectAll().where {
                    (OrdersTable.id eq item[OrderItemsTable.orderId]) and
                    (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found for vendor")

                OrderItemsTable.update({ OrderItemsTable.id eq itemUUID }) {
                    it[kitchenStation] = request.station
                }
            }
            trace.step("Station assigned", mapOf("itemId" to itemId, "station" to (request.station ?: "null")))
            call.respond(HttpStatusCode.OK, mapOf("item_id" to itemId, "station" to request.station))
        }
    }
}
