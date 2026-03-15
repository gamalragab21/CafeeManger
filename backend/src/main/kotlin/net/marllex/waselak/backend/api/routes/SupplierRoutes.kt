package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
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

// ─── Supplier DTOs ──────────────────────────────────────────────

@Serializable
data class SupplierDto(
    val id: String,
    val vendor_id: String,
    val name: String,
    val contact_name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class CreateSupplierDto(
    val name: String,
    val contact_name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateSupplierDto(
    val name: String? = null,
    val contact_name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

// ─── Purchase Order DTOs ─────────────────────────────────────────

@Serializable
data class PurchaseOrderDto(
    val id: String,
    val vendor_id: String,
    val supplier_id: String,
    val supplier_name: String? = null,
    val order_number: String,
    val status: String = "DRAFT",
    val notes: String? = null,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val expected_delivery_date: String? = null,   // YYYY-MM-DD
    val received_at: Long? = null,
    val created_by: String,
    val items: List<PurchaseOrderItemDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class PurchaseOrderItemDto(
    val id: String,
    val purchase_order_id: String,
    val stock_id: String,
    val stock_name: String? = null,
    val requested_quantity: Double,
    val received_quantity: Double = 0.0,
    val unit_cost: Double = 0.0,
    val total_cost: Double = 0.0,
    val unit: String = "PIECE",
    val notes: String? = null,
    val created_at: Long,
)

@Serializable
data class CreatePurchaseOrderDto(
    val supplier_id: String,
    val notes: String? = null,
    val expected_delivery_date: String? = null,     // YYYY-MM-DD
    val items: List<CreatePurchaseOrderItemDto>,
)

@Serializable
data class CreatePurchaseOrderItemDto(
    val stock_id: String,
    val requested_quantity: Double,
    val unit_cost: Double = 0.0,
    val unit: String = "PIECE",
    val notes: String? = null,
)

@Serializable
data class ReceivePurchaseOrderDto(
    val items: List<ReceiveItemDto>,
    val notes: String? = null,
)

@Serializable
data class ReceiveItemDto(
    val purchase_order_item_id: String,
    val received_quantity: Double,
    val batch_number: String? = null,       // For batch tracking
    val expiry_date: String? = null,        // YYYY-MM-DD for batch expiry
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.supplierRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    // ─── Supplier CRUD ──────────────────────────────────────────
    route("/api/v1/suppliers") {

        // GET all suppliers
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val suppliers = transaction {
                SuppliersTable.selectAll().where {
                    SuppliersTable.vendorId eq vendorUUID
                }.orderBy(SuppliersTable.name, SortOrder.ASC)
                    .map { it.toSupplierDto() }
            }
            call.respond(HttpStatusCode.OK, suppliers)
        }

        // GET single supplier
        get("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val supplier = transaction {
                SuppliersTable.selectAll().where {
                    (SuppliersTable.id eq UUID.fromString(id)) and
                    (SuppliersTable.vendorId eq vendorUUID)
                }.firstOrNull()?.toSupplierDto()
                    ?: throw NoSuchElementException("Supplier not found")
            }
            call.respond(HttpStatusCode.OK, supplier)
        }

        // POST create supplier — MANAGER only
        post {
            val trace = call.routeTrace()
            trace.step("Create supplier")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<CreateSupplierDto>()

            require(request.name.isNotBlank()) { "Supplier name is required" }

            val supplier = transaction {
                val now = Clock.System.now()
                val id = SuppliersTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[name] = request.name
                    it[contactName] = request.contact_name
                    it[phone] = request.phone
                    it[email] = request.email
                    it[address] = request.address
                    it[notes] = request.notes
                    it[active] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                SupplierDto(
                    id = id.toString(),
                    vendor_id = vendorUUID.toString(),
                    name = request.name,
                    contact_name = request.contact_name,
                    phone = request.phone,
                    email = request.email,
                    address = request.address,
                    notes = request.notes,
                    active = true,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Supplier created", mapOf("id" to supplier.id))
            call.respond(HttpStatusCode.Created, supplier)
        }

        // PUT update supplier — MANAGER only
        put("/{id}") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateSupplierDto>()

            val supplier = transaction {
                val idUUID = UUID.fromString(id)
                val now = Clock.System.now()

                SuppliersTable.selectAll().where {
                    (SuppliersTable.id eq idUUID) and (SuppliersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Supplier not found")

                SuppliersTable.update({
                    (SuppliersTable.id eq idUUID) and (SuppliersTable.vendorId eq vendorUUID)
                }) {
                    request.name?.let { n -> it[name] = n }
                    request.contact_name?.let { cn -> it[contactName] = cn }
                    request.phone?.let { p -> it[phone] = p }
                    request.email?.let { e -> it[email] = e }
                    request.address?.let { a -> it[address] = a }
                    request.notes?.let { n -> it[notes] = n }
                    request.active?.let { a -> it[active] = a }
                    it[updatedAt] = now
                }

                SuppliersTable.selectAll().where { SuppliersTable.id eq idUUID }
                    .first().toSupplierDto()
            }
            call.respond(HttpStatusCode.OK, supplier)
        }

        // DELETE supplier — MANAGER only
        delete("/{id}") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val count = SuppliersTable.deleteWhere {
                    (SuppliersTable.id eq UUID.fromString(id)) and
                    (SuppliersTable.vendorId eq vendorUUID)
                }
                if (count == 0) throw NoSuchElementException("Supplier not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("deleted" to id))
        }
    }

    // ─── Purchase Order CRUD ────────────────────────────────────
    route("/api/v1/purchase-orders") {

        // GET all purchase orders
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val supplierId = call.parameters["supplier_id"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50

            val orders = transaction {
                var query = PurchaseOrdersTable.selectAll().where {
                    PurchaseOrdersTable.vendorId eq vendorUUID
                }
                status?.let { s ->
                    query = query.andWhere { PurchaseOrdersTable.status eq s.uppercase() }
                }
                supplierId?.let { sid ->
                    query = query.andWhere { PurchaseOrdersTable.supplierId eq UUID.fromString(sid) }
                }

                query.orderBy(PurchaseOrdersTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val poId = row[PurchaseOrdersTable.id]
                        val items = PurchaseOrderItemsTable.selectAll().where {
                            PurchaseOrderItemsTable.purchaseOrderId eq poId
                        }.map { it.toPurchaseOrderItemDto(poId.toString()) }
                        row.toPurchaseOrderDto(items)
                    }
            }
            call.respond(HttpStatusCode.OK, orders)
        }

        // GET single purchase order
        get("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val order = transaction {
                val row = PurchaseOrdersTable.selectAll().where {
                    (PurchaseOrdersTable.id eq UUID.fromString(id)) and
                    (PurchaseOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Purchase order not found")

                val items = PurchaseOrderItemsTable.selectAll().where {
                    PurchaseOrderItemsTable.purchaseOrderId eq row[PurchaseOrdersTable.id]
                }.map { it.toPurchaseOrderItemDto(id) }

                row.toPurchaseOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, order)
        }

        // POST create purchase order — MANAGER only
        post {
            val trace = call.routeTrace()
            trace.step("Create purchase order")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CreatePurchaseOrderDto>()

            require(request.items.isNotEmpty()) { "At least one item is required" }

            val order = transaction {
                val now = Clock.System.now()
                val supplierUUID = UUID.fromString(request.supplier_id)

                // Verify supplier exists
                SuppliersTable.selectAll().where {
                    (SuppliersTable.id eq supplierUUID) and (SuppliersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Supplier not found")

                // Generate PO number
                val existingCount = PurchaseOrdersTable.selectAll().where {
                    PurchaseOrdersTable.vendorId eq vendorUUID
                }.count()
                val poNumber = "PO-${String.format("%05d", existingCount + 1)}"

                // Calculate totals
                var subtotal = BigDecimal.ZERO
                val itemsData = request.items.map { poItem ->
                    val stockUUID = UUID.fromString(poItem.stock_id)
                    val stockRow = StockTable.selectAll().where {
                        (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item ${poItem.stock_id} not found")

                    val lineTotal = BigDecimal(poItem.unit_cost) * BigDecimal(poItem.requested_quantity)
                    subtotal += lineTotal

                    Triple(poItem, stockRow, lineTotal)
                }

                val poId = PurchaseOrdersTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[supplierId] = supplierUUID
                    it[orderNumber] = poNumber
                    it[status] = "DRAFT"
                    it[notes] = request.notes
                    it[PurchaseOrdersTable.subtotal] = subtotal
                    it[PurchaseOrdersTable.total] = subtotal  // No tax on PO by default
                    it[expectedDeliveryDate] = request.expected_delivery_date?.let { LocalDate.parse(it) }
                    it[createdBy] = userUUID
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                val createdItems = itemsData.map { (poItem, stockRow, lineTotal) ->
                    val piId = PurchaseOrderItemsTable.insertAndGetId {
                        it[purchaseOrderId] = poId
                        it[stockId] = UUID.fromString(poItem.stock_id)
                        it[requestedQuantity] = BigDecimal(poItem.requested_quantity)
                        it[receivedQuantity] = BigDecimal.ZERO
                        it[unitCost] = BigDecimal(poItem.unit_cost)
                        it[totalCost] = lineTotal
                        it[unit] = poItem.unit
                        it[notes] = poItem.notes
                        it[createdAt] = now
                    }

                    PurchaseOrderItemDto(
                        id = piId.toString(),
                        purchase_order_id = poId.toString(),
                        stock_id = poItem.stock_id,
                        stock_name = stockRow[StockTable.itemName],
                        requested_quantity = poItem.requested_quantity,
                        received_quantity = 0.0,
                        unit_cost = poItem.unit_cost,
                        total_cost = lineTotal.toDouble(),
                        unit = poItem.unit,
                        notes = poItem.notes,
                        created_at = now.toEpochMilliseconds(),
                    )
                }

                val supplierName = SuppliersTable.selectAll().where { SuppliersTable.id eq supplierUUID }
                    .firstOrNull()?.get(SuppliersTable.name)

                PurchaseOrderDto(
                    id = poId.toString(),
                    vendor_id = vendorUUID.toString(),
                    supplier_id = request.supplier_id,
                    supplier_name = supplierName,
                    order_number = poNumber,
                    status = "DRAFT",
                    notes = request.notes,
                    subtotal = subtotal.toDouble(),
                    total = subtotal.toDouble(),
                    expected_delivery_date = request.expected_delivery_date,
                    created_by = principal.userId,
                    items = createdItems,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Purchase order created", mapOf("poNumber" to order.order_number))
            call.respond(HttpStatusCode.Created, order)
        }

        // PATCH submit a purchase order (DRAFT -> SUBMITTED)
        patch("/{id}/submit") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val poUUID = UUID.fromString(id)
                val row = PurchaseOrdersTable.selectAll().where {
                    (PurchaseOrdersTable.id eq poUUID) and
                    (PurchaseOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Purchase order not found")

                require(row[PurchaseOrdersTable.status] == "DRAFT") { "Can only submit draft POs" }

                PurchaseOrdersTable.update({ PurchaseOrdersTable.id eq poUUID }) {
                    it[status] = "SUBMITTED"
                    it[updatedAt] = Clock.System.now()
                }
            }
            // Return full PO
            val poUUID = UUID.fromString(id)
            val po = transaction {
                val row = PurchaseOrdersTable.selectAll().where { PurchaseOrdersTable.id eq poUUID }
                    .first()
                val items = PurchaseOrderItemsTable.selectAll().where {
                    PurchaseOrderItemsTable.purchaseOrderId eq poUUID
                }.map { it.toPurchaseOrderItemDto(id) }
                row.toPurchaseOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, po)
        }

        // POST receive items for a purchase order (adds stock + creates batches)
        post("/{id}/receive") {
            val trace = call.routeTrace()
            trace.step("Receive purchase order items")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<ReceivePurchaseOrderDto>()

            require(request.items.isNotEmpty()) { "At least one item to receive" }

            transaction {
                val poUUID = UUID.fromString(id)
                val now = Clock.System.now()

                val poRow = PurchaseOrdersTable.selectAll().where {
                    (PurchaseOrdersTable.id eq poUUID) and
                    (PurchaseOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Purchase order not found")

                require(poRow[PurchaseOrdersTable.status] in listOf("SUBMITTED", "PARTIALLY_RECEIVED")) {
                    "Can only receive items for submitted or partially received POs"
                }

                for (receiveItem in request.items) {
                    val poItemUUID = UUID.fromString(receiveItem.purchase_order_item_id)
                    val poItemRow = PurchaseOrderItemsTable.selectAll().where {
                        (PurchaseOrderItemsTable.id eq poItemUUID) and
                        (PurchaseOrderItemsTable.purchaseOrderId eq poUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("PO item not found: ${receiveItem.purchase_order_item_id}")

                    val receivedQty = BigDecimal(receiveItem.received_quantity)
                    val stockId = poItemRow[PurchaseOrderItemsTable.stockId]
                    val unitCost = poItemRow[PurchaseOrderItemsTable.unitCost]

                    // Update PO item received quantity
                    val existingReceived = poItemRow[PurchaseOrderItemsTable.receivedQuantity]
                    PurchaseOrderItemsTable.update({ PurchaseOrderItemsTable.id eq poItemUUID }) {
                        it[PurchaseOrderItemsTable.receivedQuantity] = existingReceived + receivedQty
                    }

                    // Add to stock
                    val stockRow = StockTable.selectAll().where { StockTable.id eq stockId }
                        .firstOrNull() ?: continue
                    val currentQty = stockRow[StockTable.quantity]
                    val newQty = currentQty + receivedQty

                    StockTable.update({ StockTable.id eq stockId }) {
                        it[quantity] = newQty
                        it[costPrice] = unitCost  // Update cost price to latest
                        it[updatedAt] = now
                    }

                    // Create stock batch if batch tracking info provided
                    if (receiveItem.batch_number != null || receiveItem.expiry_date != null) {
                        StockBatchesTable.insert {
                            it[StockBatchesTable.stockId] = stockId
                            it[StockBatchesTable.vendorId] = vendorUUID
                            it[batchNumber] = receiveItem.batch_number
                            it[StockBatchesTable.quantity] = receivedQty
                            it[initialQuantity] = receivedQty
                            it[StockBatchesTable.costPrice] = unitCost
                            it[expiryDate] = receiveItem.expiry_date?.let { LocalDate.parse(it) }
                            it[receivedAt] = now
                            it[status] = "ACTIVE"
                            it[createdAt] = now
                        }
                    }

                    // Record stock transaction
                    StockTransactionsTable.insert {
                        it[StockTransactionsTable.stockId] = stockId
                        it[type] = "PURCHASE"
                        it[StockTransactionsTable.quantity] = receivedQty
                        it[previousQuantity] = currentQty
                        it[note] = "PO ${poRow[PurchaseOrdersTable.orderNumber]} received"
                        it[createdAt] = now
                    }
                }

                // Check if all items fully received
                val allItems = PurchaseOrderItemsTable.selectAll().where {
                    PurchaseOrderItemsTable.purchaseOrderId eq poUUID
                }.toList()
                val allFullyReceived = allItems.all { item ->
                    item[PurchaseOrderItemsTable.receivedQuantity] >= item[PurchaseOrderItemsTable.requestedQuantity]
                }
                val anyReceived = allItems.any { item ->
                    item[PurchaseOrderItemsTable.receivedQuantity] > BigDecimal.ZERO
                }

                val newStatus = when {
                    allFullyReceived -> "RECEIVED"
                    anyReceived -> "PARTIALLY_RECEIVED"
                    else -> poRow[PurchaseOrdersTable.status]
                }

                PurchaseOrdersTable.update({ PurchaseOrdersTable.id eq poUUID }) {
                    it[status] = newStatus
                    it[updatedAt] = now
                    if (newStatus == "RECEIVED") {
                        it[receivedAt] = now
                    }
                    if (request.notes != null) {
                        it[notes] = request.notes
                    }
                }

                // Notification: PO_RECEIVED when fully received
                if (newStatus == "RECEIVED") {
                    val poNumber = poRow[PurchaseOrdersTable.orderNumber]
                    createSystemNotification(
                        vendorUUID = vendorUUID,
                        type = "PO_RECEIVED",
                        title = "Purchase Order Received",
                        body = "PO #$poNumber has been fully received",
                        data = """{"purchaseOrderId":"$id"}""",
                        actionUrl = "/suppliers",
                    )
                }
            }
            trace.step("Purchase order items received")
            // Return full PO
            val poUUID2 = UUID.fromString(id)
            val po = transaction {
                val row = PurchaseOrdersTable.selectAll().where { PurchaseOrdersTable.id eq poUUID2 }
                    .first()
                val items = PurchaseOrderItemsTable.selectAll().where {
                    PurchaseOrderItemsTable.purchaseOrderId eq poUUID2
                }.map { it.toPurchaseOrderItemDto(id) }
                row.toPurchaseOrderDto(items)
            }
            call.respond(HttpStatusCode.OK, po)
        }

        // DELETE cancel a purchase order -- MANAGER only
        delete("/{id}") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SUPPLIERS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val poUUID = UUID.fromString(id)
                val row = PurchaseOrdersTable.selectAll().where {
                    (PurchaseOrdersTable.id eq poUUID) and
                    (PurchaseOrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Purchase order not found")

                require(row[PurchaseOrdersTable.status] in listOf("DRAFT", "SUBMITTED")) {
                    "Can only cancel draft or submitted POs"
                }

                PurchaseOrdersTable.update({ PurchaseOrdersTable.id eq poUUID }) {
                    it[status] = "CANCELLED"
                    it[updatedAt] = Clock.System.now()
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toSupplierDto() = SupplierDto(
    id = this[SuppliersTable.id].toString(),
    vendor_id = this[SuppliersTable.vendorId].toString(),
    name = this[SuppliersTable.name],
    contact_name = this[SuppliersTable.contactName],
    phone = this[SuppliersTable.phone],
    email = this[SuppliersTable.email],
    address = this[SuppliersTable.address],
    notes = this[SuppliersTable.notes],
    active = this[SuppliersTable.active],
    created_at = this[SuppliersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[SuppliersTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toPurchaseOrderDto(items: List<PurchaseOrderItemDto> = emptyList()): PurchaseOrderDto {
    val supplierUUID = this[PurchaseOrdersTable.supplierId]
    val supplierName = SuppliersTable.selectAll().where { SuppliersTable.id eq supplierUUID }
        .firstOrNull()?.get(SuppliersTable.name)

    return PurchaseOrderDto(
        id = this[PurchaseOrdersTable.id].toString(),
        vendor_id = this[PurchaseOrdersTable.vendorId].toString(),
        supplier_id = supplierUUID.toString(),
        supplier_name = supplierName,
        order_number = this[PurchaseOrdersTable.orderNumber],
        status = this[PurchaseOrdersTable.status],
        notes = this[PurchaseOrdersTable.notes],
        subtotal = this[PurchaseOrdersTable.subtotal].toDouble(),
        tax = this[PurchaseOrdersTable.tax].toDouble(),
        total = this[PurchaseOrdersTable.total].toDouble(),
        expected_delivery_date = this[PurchaseOrdersTable.expectedDeliveryDate]?.toString(),
        received_at = this[PurchaseOrdersTable.receivedAt]?.toEpochMilliseconds(),
        created_by = this[PurchaseOrdersTable.createdBy].toString(),
        items = items,
        created_at = this[PurchaseOrdersTable.createdAt].toEpochMilliseconds(),
        updated_at = this[PurchaseOrdersTable.updatedAt].toEpochMilliseconds(),
    )
}

private fun ResultRow.toPurchaseOrderItemDto(poId: String): PurchaseOrderItemDto {
    val stockId = this[PurchaseOrderItemsTable.stockId]
    val stockName = StockTable.selectAll().where { StockTable.id eq stockId }
        .firstOrNull()?.get(StockTable.itemName)

    return PurchaseOrderItemDto(
        id = this[PurchaseOrderItemsTable.id].toString(),
        purchase_order_id = poId,
        stock_id = stockId.toString(),
        stock_name = stockName,
        requested_quantity = this[PurchaseOrderItemsTable.requestedQuantity].toDouble(),
        received_quantity = this[PurchaseOrderItemsTable.receivedQuantity].toDouble(),
        unit_cost = this[PurchaseOrderItemsTable.unitCost].toDouble(),
        total_cost = this[PurchaseOrderItemsTable.totalCost].toDouble(),
        unit = this[PurchaseOrderItemsTable.unit],
        notes = this[PurchaseOrderItemsTable.notes],
        created_at = this[PurchaseOrderItemsTable.createdAt].toEpochMilliseconds(),
    )
}
