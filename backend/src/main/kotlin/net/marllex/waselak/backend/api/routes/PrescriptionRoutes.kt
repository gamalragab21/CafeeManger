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
data class PrescriptionDto(
    val id: String,
    val vendor_id: String,
    val customer_id: String? = null,
    val order_id: String? = null,
    val doctor_name: String? = null,
    val doctor_phone: String? = null,
    val patient_name: String,
    val patient_phone: String? = null,
    val patient_age: Int? = null,
    val diagnosis: String? = null,
    val notes: String? = null,
    val image_url: String? = null,
    val status: String = "PENDING",
    val expires_at: Long? = null,
    val dispensed_at: Long? = null,
    val dispensed_by: String? = null,
    val created_by: String,
    val items: List<PrescriptionItemDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class PrescriptionItemDto(
    val id: String,
    val prescription_id: String,
    val item_id: String,
    val item_name: String? = null,
    val quantity: Int,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    val dispensed_quantity: Int = 0,
    val status: String = "PENDING",
    val substitute_item_id: String? = null,
    val substitute_item_name: String? = null,
    val created_at: Long,
)

@Serializable
data class CreatePrescriptionDto(
    val customer_id: String? = null,
    val doctor_name: String? = null,
    val doctor_phone: String? = null,
    val patient_name: String,
    val patient_phone: String? = null,
    val patient_age: Int? = null,
    val diagnosis: String? = null,
    val notes: String? = null,
    val image_url: String? = null,
    val expires_at: Long? = null,
    val items: List<CreatePrescriptionItemDto>,
)

@Serializable
data class CreatePrescriptionItemDto(
    val item_id: String,
    val quantity: Int,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
)

@Serializable
data class DispensePrescriptionDto(
    val items: List<DispenseItemDto>? = null,  // If null, dispense all
    val notes: String? = null,
    val create_order: Boolean = true,          // Whether to auto-create an order
)

@Serializable
data class DispenseItemDto(
    val prescription_item_id: String,
    val dispensed_quantity: Int,
    val substitute_item_id: String? = null,    // If substituting with another medicine
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.prescriptionRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/prescriptions") {

        // GET all prescriptions for vendor
        get {
            val trace = call.routeTrace()
            trace.step("List prescriptions started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "PRESCRIPTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val status = call.parameters["status"]
            val patientPhone = call.parameters["patient_phone"]
            val customerId = call.parameters["customer_id"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50

            val prescriptions = transaction {
                var query = PrescriptionsTable.selectAll().where {
                    PrescriptionsTable.vendorId eq vendorUUID
                }
                status?.let { s ->
                    query = query.andWhere { PrescriptionsTable.status eq s.uppercase() }
                }
                patientPhone?.let { phone ->
                    query = query.andWhere { PrescriptionsTable.patientPhone eq phone }
                }
                customerId?.let { cid ->
                    query = query.andWhere { PrescriptionsTable.customerId eq UUID.fromString(cid) }
                }

                query.orderBy(PrescriptionsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val rxId = row[PrescriptionsTable.id]
                        val items = PrescriptionItemsTable.selectAll().where {
                            PrescriptionItemsTable.prescriptionId eq rxId
                        }.map { it.toPrescriptionItemDto() }
                        row.toPrescriptionDto(items)
                    }
            }
            trace.step("Prescriptions listed", mapOf("count" to prescriptions.size.toString()))
            call.respond(HttpStatusCode.OK, prescriptions)
        }

        // GET single prescription
        get("/{id}") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "PRESCRIPTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val prescription = transaction {
                val row = PrescriptionsTable.selectAll().where {
                    (PrescriptionsTable.id eq UUID.fromString(id)) and
                    (PrescriptionsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Prescription not found")

                val items = PrescriptionItemsTable.selectAll().where {
                    PrescriptionItemsTable.prescriptionId eq row[PrescriptionsTable.id]
                }.map { it.toPrescriptionItemDto() }

                row.toPrescriptionDto(items)
            }
            call.respond(HttpStatusCode.OK, prescription)
        }

        // POST create a new prescription
        post {
            val trace = call.routeTrace()
            trace.step("Create prescription started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "PRESCRIPTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CreatePrescriptionDto>()

            require(request.patient_name.isNotBlank()) { "Patient name is required" }
            require(request.items.isNotEmpty()) { "At least one medicine item is required" }

            val prescription = transaction {
                val now = Clock.System.now()

                // Check for drug interactions among the prescription items
                val itemIds = request.items.map { UUID.fromString(it.item_id) }
                val interactions = mutableListOf<String>()

                for (i in itemIds.indices) {
                    for (j in i + 1 until itemIds.size) {
                        val interaction = DrugInteractionsTable.selectAll().where {
                            (DrugInteractionsTable.vendorId eq vendorUUID) and
                            (DrugInteractionsTable.active eq true) and
                            (
                                ((DrugInteractionsTable.itemIdA eq itemIds[i]) and (DrugInteractionsTable.itemIdB eq itemIds[j])) or
                                ((DrugInteractionsTable.itemIdA eq itemIds[j]) and (DrugInteractionsTable.itemIdB eq itemIds[i]))
                            )
                        }.firstOrNull()

                        if (interaction != null) {
                            val severity = interaction[DrugInteractionsTable.severity]
                            val desc = interaction[DrugInteractionsTable.description]
                            interactions.add("[$severity] $desc")
                        }
                    }
                }

                // Create prescription
                val rxId = PrescriptionsTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[customerId] = request.customer_id?.let { cid -> UUID.fromString(cid) }
                    it[doctorName] = request.doctor_name
                    it[doctorPhone] = request.doctor_phone
                    it[patientName] = request.patient_name
                    it[patientPhone] = request.patient_phone
                    it[patientAge] = request.patient_age
                    it[diagnosis] = request.diagnosis
                    it[notes] = if (interactions.isNotEmpty()) {
                        val warningText = "⚠️ Drug Interactions:\n${interactions.joinToString("\n")}"
                        if (request.notes != null) "${request.notes}\n\n$warningText" else warningText
                    } else request.notes
                    it[imageUrl] = request.image_url
                    it[status] = "PENDING"
                    it[expiresAt] = request.expires_at?.let { ts ->
                        kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    }
                    it[createdBy] = userUUID
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                // Create items
                val createdItems = request.items.map { rxItem ->
                    val itemUUID = UUID.fromString(rxItem.item_id)
                    val itemName = ItemsTable.selectAll().where { ItemsTable.id eq itemUUID }
                        .firstOrNull()?.get(ItemsTable.name)

                    val piId = PrescriptionItemsTable.insertAndGetId {
                        it[prescriptionId] = rxId
                        it[itemId] = itemUUID
                        it[quantity] = rxItem.quantity
                        it[dosage] = rxItem.dosage
                        it[frequency] = rxItem.frequency
                        it[duration] = rxItem.duration
                        it[instructions] = rxItem.instructions
                        it[dispensedQuantity] = 0
                        it[status] = "PENDING"
                        it[createdAt] = now
                    }

                    PrescriptionItemDto(
                        id = piId.toString(),
                        prescription_id = rxId.toString(),
                        item_id = rxItem.item_id,
                        item_name = itemName,
                        quantity = rxItem.quantity,
                        dosage = rxItem.dosage,
                        frequency = rxItem.frequency,
                        duration = rxItem.duration,
                        instructions = rxItem.instructions,
                        dispensed_quantity = 0,
                        status = "PENDING",
                        created_at = now.toEpochMilliseconds(),
                    )
                }

                val result = mutableMapOf<String, Any?>(
                    "prescription" to PrescriptionDto(
                        id = rxId.toString(),
                        vendor_id = vendorUUID.toString(),
                        customer_id = request.customer_id,
                        doctor_name = request.doctor_name,
                        doctor_phone = request.doctor_phone,
                        patient_name = request.patient_name,
                        patient_phone = request.patient_phone,
                        patient_age = request.patient_age,
                        diagnosis = request.diagnosis,
                        notes = request.notes,
                        image_url = request.image_url,
                        status = "PENDING",
                        expires_at = request.expires_at,
                        created_by = principal.userId,
                        items = createdItems,
                        created_at = now.toEpochMilliseconds(),
                        updated_at = now.toEpochMilliseconds(),
                    ),
                )
                if (interactions.isNotEmpty()) {
                    result["drug_interactions"] = interactions
                }
                result
            }
            trace.step("Prescription created")
            call.respond(HttpStatusCode.Created, prescription)
        }

        // PATCH dispense a prescription (mark items as dispensed)
        patch("/{id}/dispense") {
            val trace = call.routeTrace()
            trace.step("Dispense prescription started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "PRESCRIPTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<DispensePrescriptionDto>()

            val result = transaction {
                val rxUUID = UUID.fromString(id)
                val now = Clock.System.now()

                val rxRow = PrescriptionsTable.selectAll().where {
                    (PrescriptionsTable.id eq rxUUID) and
                    (PrescriptionsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Prescription not found")

                require(rxRow[PrescriptionsTable.status] in listOf("PENDING", "PARTIALLY_DISPENSED")) {
                    "Can only dispense pending or partially dispensed prescriptions"
                }

                val rxItems = PrescriptionItemsTable.selectAll().where {
                    PrescriptionItemsTable.prescriptionId eq rxUUID
                }.toList()

                if (request.items != null) {
                    // Dispense specific items
                    for (di in request.items) {
                        val piUUID = UUID.fromString(di.prescription_item_id)
                        val piRow = rxItems.find { it[PrescriptionItemsTable.id].toString() == di.prescription_item_id }
                            ?: throw NoSuchElementException("Prescription item not found: ${di.prescription_item_id}")

                        val itemIdToUse: UUID = if (di.substitute_item_id != null) {
                            UUID.fromString(di.substitute_item_id)
                        } else {
                            piRow[PrescriptionItemsTable.itemId].value
                        }

                        PrescriptionItemsTable.update({ PrescriptionItemsTable.id eq piUUID }) {
                            it[dispensedQuantity] = di.dispensed_quantity
                            it[status] = if (di.dispensed_quantity >= piRow[PrescriptionItemsTable.quantity]) "DISPENSED" else "PENDING"
                            if (di.substitute_item_id != null) {
                                it[substituteItemId] = UUID.fromString(di.substitute_item_id)
                                it[status] = "SUBSTITUTED"
                            }
                        }

                        // Deduct stock for dispensed items
                        val stockRow = StockTable.selectAll().where {
                            (StockTable.itemId eq itemIdToUse) and (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull()

                        if (stockRow != null) {
                            val deductAmt = java.math.BigDecimal(di.dispensed_quantity)
                            val currentQty = stockRow[StockTable.quantity]
                            StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                it[quantity] = currentQty - deductAmt
                                it[updatedAt] = now
                            }

                            // FIFO batch deduction
                            val batchesAffected = fifoDeductBatches(
                                stockUUID = stockRow[StockTable.id].value,
                                vendorUUID = vendorUUID,
                                amount = deductAmt,
                                transactionType = "SALE_DIRECT",
                                note = "Prescription dispensing"
                            )
                            if (batchesAffected.isEmpty()) {
                                StockTransactionsTable.insert {
                                    it[stockId] = stockRow[StockTable.id]
                                    it[type] = "SALE_DIRECT"
                                    it[StockTransactionsTable.quantity] = deductAmt
                                    it[previousQuantity] = currentQty
                                    it[note] = "Prescription dispensing"
                                    it[createdAt] = now
                                }
                            }
                        }
                    }
                } else {
                    // Dispense all items
                    for (piRow in rxItems) {
                        if (piRow[PrescriptionItemsTable.status] != "PENDING") continue
                        val qty = piRow[PrescriptionItemsTable.quantity]

                        PrescriptionItemsTable.update({ PrescriptionItemsTable.id eq piRow[PrescriptionItemsTable.id] }) {
                            it[dispensedQuantity] = qty
                            it[status] = "DISPENSED"
                        }

                        // Deduct stock
                        val itemId = piRow[PrescriptionItemsTable.itemId]
                        val stockRow = StockTable.selectAll().where {
                            (StockTable.itemId eq itemId) and (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull()

                        if (stockRow != null) {
                            val deductAmt = java.math.BigDecimal(qty)
                            val currentQty = stockRow[StockTable.quantity]
                            StockTable.update({ StockTable.id eq stockRow[StockTable.id] }) {
                                it[quantity] = currentQty - deductAmt
                                it[updatedAt] = now
                            }

                            val batchesAffected = fifoDeductBatches(
                                stockUUID = stockRow[StockTable.id].value,
                                vendorUUID = vendorUUID,
                                amount = deductAmt,
                                transactionType = "SALE_DIRECT",
                                note = "Prescription dispensing"
                            )
                            if (batchesAffected.isEmpty()) {
                                StockTransactionsTable.insert {
                                    it[stockId] = stockRow[StockTable.id]
                                    it[type] = "SALE_DIRECT"
                                    it[StockTransactionsTable.quantity] = deductAmt
                                    it[previousQuantity] = currentQty
                                    it[note] = "Prescription dispensing"
                                    it[createdAt] = now
                                }
                            }
                        }
                    }
                }

                // Determine new prescription status
                val updatedItems = PrescriptionItemsTable.selectAll().where {
                    PrescriptionItemsTable.prescriptionId eq rxUUID
                }.toList()
                val allDispensed = updatedItems.all { it[PrescriptionItemsTable.status] in listOf("DISPENSED", "SUBSTITUTED", "UNAVAILABLE") }
                val anyDispensed = updatedItems.any { it[PrescriptionItemsTable.status] in listOf("DISPENSED", "SUBSTITUTED") }

                val newStatus = when {
                    allDispensed -> "DISPENSED"
                    anyDispensed -> "PARTIALLY_DISPENSED"
                    else -> "PENDING"
                }

                PrescriptionsTable.update({ PrescriptionsTable.id eq rxUUID }) {
                    it[status] = newStatus
                    if (newStatus == "DISPENSED") {
                        it[dispensedAt] = now
                        it[dispensedBy] = userUUID
                    }
                    it[updatedAt] = now
                    if (request.notes != null) {
                        it[notes] = request.notes
                    }
                }

                // Return updated prescription
                val updatedRow = PrescriptionsTable.selectAll().where {
                    PrescriptionsTable.id eq rxUUID
                }.first()
                val items = PrescriptionItemsTable.selectAll().where {
                    PrescriptionItemsTable.prescriptionId eq rxUUID
                }.map { it.toPrescriptionItemDto() }

                updatedRow.toPrescriptionDto(items)
            }
            trace.step("Prescription dispensed", mapOf("id" to id, "status" to result.status))
            call.respond(HttpStatusCode.OK, result)
        }

        // PATCH cancel a prescription
        patch("/{id}/cancel") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "PRESCRIPTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val rxUUID = UUID.fromString(id)
                val now = Clock.System.now()

                val rxRow = PrescriptionsTable.selectAll().where {
                    (PrescriptionsTable.id eq rxUUID) and
                    (PrescriptionsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Prescription not found")

                require(rxRow[PrescriptionsTable.status] == "PENDING") {
                    "Can only cancel pending prescriptions"
                }

                PrescriptionsTable.update({ PrescriptionsTable.id eq rxUUID }) {
                    it[status] = "CANCELLED"
                    it[updatedAt] = now
                }

                PrescriptionItemsTable.update({ PrescriptionItemsTable.prescriptionId eq rxUUID }) {
                    it[status] = "UNAVAILABLE"
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "status" to "CANCELLED"))
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toPrescriptionDto(items: List<PrescriptionItemDto> = emptyList()) = PrescriptionDto(
    id = this[PrescriptionsTable.id].toString(),
    vendor_id = this[PrescriptionsTable.vendorId].toString(),
    customer_id = this[PrescriptionsTable.customerId]?.toString(),
    order_id = this[PrescriptionsTable.orderId]?.toString(),
    doctor_name = this[PrescriptionsTable.doctorName],
    doctor_phone = this[PrescriptionsTable.doctorPhone],
    patient_name = this[PrescriptionsTable.patientName],
    patient_phone = this[PrescriptionsTable.patientPhone],
    patient_age = this[PrescriptionsTable.patientAge],
    diagnosis = this[PrescriptionsTable.diagnosis],
    notes = this[PrescriptionsTable.notes],
    image_url = this[PrescriptionsTable.imageUrl],
    status = this[PrescriptionsTable.status],
    expires_at = this[PrescriptionsTable.expiresAt]?.toEpochMilliseconds(),
    dispensed_at = this[PrescriptionsTable.dispensedAt]?.toEpochMilliseconds(),
    dispensed_by = this[PrescriptionsTable.dispensedBy]?.toString(),
    created_by = this[PrescriptionsTable.createdBy].toString(),
    items = items,
    created_at = this[PrescriptionsTable.createdAt].toEpochMilliseconds(),
    updated_at = this[PrescriptionsTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toPrescriptionItemDto(): PrescriptionItemDto {
    val itemId = this[PrescriptionItemsTable.itemId]
    val itemName = ItemsTable.selectAll().where { ItemsTable.id eq itemId }
        .firstOrNull()?.get(ItemsTable.name)

    val subId = this[PrescriptionItemsTable.substituteItemId]
    val subName = subId?.let { sid ->
        ItemsTable.selectAll().where { ItemsTable.id eq sid }
            .firstOrNull()?.get(ItemsTable.name)
    }

    return PrescriptionItemDto(
        id = this[PrescriptionItemsTable.id].toString(),
        prescription_id = this[PrescriptionItemsTable.prescriptionId].toString(),
        item_id = itemId.toString(),
        item_name = itemName,
        quantity = this[PrescriptionItemsTable.quantity],
        dosage = this[PrescriptionItemsTable.dosage],
        frequency = this[PrescriptionItemsTable.frequency],
        duration = this[PrescriptionItemsTable.duration],
        instructions = this[PrescriptionItemsTable.instructions],
        dispensed_quantity = this[PrescriptionItemsTable.dispensedQuantity],
        status = this[PrescriptionItemsTable.status],
        substitute_item_id = subId?.toString(),
        substitute_item_name = subName,
        created_at = this[PrescriptionItemsTable.createdAt].toEpochMilliseconds(),
    )
}
