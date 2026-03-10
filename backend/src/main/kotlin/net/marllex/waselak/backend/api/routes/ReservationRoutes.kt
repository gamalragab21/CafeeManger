package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.ReservationsTable
import net.marllex.waselak.backend.data.database.TablesTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

@Serializable
data class ReservationDto(
    val id: String,
    val vendor_id: String,
    val table_id: String,
    val table_number: String? = null,
    val client_name: String,
    val client_phone: String? = null,
    val reservation_date: String,
    val reservation_time: String,
    val number_of_guests: Int = 1,
    val notes: String? = null,
    val status: String = "PENDING",
    val order_id: String? = null,
    val created_by: String,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateReservationDto(
    val table_id: String,
    val client_name: String,
    val client_phone: String? = null,
    val reservation_date: String,
    val reservation_time: String,
    val number_of_guests: Int = 1,
    val notes: String? = null,
    val order_id: String? = null,
)

@Serializable
data class UpdateReservationDto(
    val table_id: String? = null,
    val client_name: String? = null,
    val client_phone: String? = null,
    val reservation_date: String? = null,
    val reservation_time: String? = null,
    val number_of_guests: Int? = null,
    val notes: String? = null,
    val order_id: String? = null,
)

@Serializable
data class UpdateReservationStatusDto(val status: String)

fun Route.reservationRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/reservations") {

        // GET /api/v1/reservations?date=&table_id=&status=
        get {
            val trace = call.routeTrace()
            trace.step("List reservations started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")

            val date = call.parameters["date"]
            val tableId = call.parameters["table_id"]
            val status = call.parameters["status"]
            trace.step("Querying reservations", mapOf("date" to (date ?: "null"), "tableId" to (tableId ?: "null"), "status" to (status ?: "null")))

            val reservations = transaction {
                var query = ReservationsTable
                    .join(TablesTable, JoinType.LEFT, ReservationsTable.tableId, TablesTable.id)
                    .selectAll()
                    .where { ReservationsTable.vendorId eq UUID.fromString(principal.vendorId) }

                date?.let { query = query.andWhere { ReservationsTable.reservationDate eq it } }
                tableId?.let { query = query.andWhere { ReservationsTable.tableId eq UUID.fromString(it) } }
                status?.let { query = query.andWhere { ReservationsTable.status eq it } }

                query.orderBy(ReservationsTable.reservationDate)
                    .orderBy(ReservationsTable.reservationTime)
                    .map { it.toReservationDto() }
            }
            trace.step("Reservations retrieved", mapOf("count" to reservations.size.toString()))
            trace.step("List reservations completed")
            call.respond(HttpStatusCode.OK, reservations)
        }

        // GET /api/v1/reservations/{id}
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get reservation by ID started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Querying reservation", mapOf("reservationId" to id))

            val reservation = transaction {
                ReservationsTable
                    .join(TablesTable, JoinType.LEFT, ReservationsTable.tableId, TablesTable.id)
                    .selectAll()
                    .where {
                        (ReservationsTable.id eq UUID.fromString(id)) and
                        (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toReservationDto()
                    ?: throw NoSuchElementException("Reservation not found")
            }
            trace.step("Reservation found", mapOf("reservationId" to reservation.id, "clientName" to reservation.client_name, "status" to reservation.status))
            trace.step("Get reservation by ID completed")
            call.respond(HttpStatusCode.OK, reservation)
        }

        // POST /api/v1/reservations
        post {
            val trace = call.routeTrace()
            trace.step("Create reservation started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val request = call.receive<CreateReservationDto>()

            require(request.client_name.isNotBlank()) { "Client name is required" }
            require(request.reservation_date.isNotBlank()) { "Reservation date is required" }
            require(request.reservation_time.isNotBlank()) { "Reservation time is required" }
            trace.step("Creating reservation", mapOf("clientName" to request.client_name, "date" to request.reservation_date, "time" to request.reservation_time, "partySize" to request.number_of_guests.toString(), "tableId" to request.table_id))

            val reservation = transaction {
                // Validate table exists and belongs to vendor
                val table = TablesTable.selectAll()
                    .where {
                        (TablesTable.id eq UUID.fromString(request.table_id)) and
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Table not found")

                // Check table is available
                val tableStatus = table[TablesTable.status]
                require(tableStatus == "AVAILABLE") {
                    "Table is not available (current status: $tableStatus)"
                }

                // Insert reservation
                val reservationId = ReservationsTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[tableId] = UUID.fromString(request.table_id)
                    it[clientName] = request.client_name
                    it[clientPhone] = request.client_phone
                    it[reservationDate] = request.reservation_date
                    it[reservationTime] = request.reservation_time
                    it[numberOfGuests] = request.number_of_guests
                    it[notes] = request.notes
                    it[status] = "PENDING"
                    it[orderId] = request.order_id?.let { oid -> UUID.fromString(oid) }
                    it[createdBy] = UUID.fromString(principal.userId)
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Update table status to RESERVED
                TablesTable.update({
                    TablesTable.id eq UUID.fromString(request.table_id)
                }) {
                    it[status] = "RESERVED"
                    it[updatedAt] = Clock.System.now()
                }

                // Return the created reservation
                ReservationsTable
                    .join(TablesTable, JoinType.LEFT, ReservationsTable.tableId, TablesTable.id)
                    .selectAll()
                    .where { ReservationsTable.id eq reservationId }
                    .first().toReservationDto()
            }
            trace.step("Reservation created", mapOf("reservationId" to reservation.id, "clientName" to reservation.client_name, "tableId" to reservation.table_id))
            trace.step("Create reservation completed")
            call.respond(HttpStatusCode.Created, reservation)
        }

        // PUT /api/v1/reservations/{id}
        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update reservation started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateReservationDto>()
            trace.step("Updating reservation", mapOf("reservationId" to id, "clientName" to (request.client_name ?: "null"), "date" to (request.reservation_date ?: "null"), "time" to (request.reservation_time ?: "null"), "partySize" to (request.number_of_guests?.toString() ?: "null"), "tableId" to (request.table_id ?: "null")))

            val updated = transaction {
                // Verify reservation exists and belongs to vendor
                val existing = ReservationsTable.selectAll()
                    .where {
                        (ReservationsTable.id eq UUID.fromString(id)) and
                        (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Reservation not found")

                val oldTableId = existing[ReservationsTable.tableId]

                // If changing table, validate new table and update statuses
                if (request.table_id != null && request.table_id != oldTableId.toString()) {
                    val newTable = TablesTable.selectAll()
                        .where {
                            (TablesTable.id eq UUID.fromString(request.table_id)) and
                            (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                        }.firstOrNull() ?: throw NoSuchElementException("New table not found")

                    require(newTable[TablesTable.status] == "AVAILABLE") {
                        "New table is not available"
                    }

                    // Release old table
                    TablesTable.update({ TablesTable.id eq oldTableId }) {
                        it[status] = "AVAILABLE"
                        it[updatedAt] = Clock.System.now()
                    }

                    // Reserve new table
                    TablesTable.update({ TablesTable.id eq UUID.fromString(request.table_id) }) {
                        it[status] = "RESERVED"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                ReservationsTable.update({
                    (ReservationsTable.id eq UUID.fromString(id)) and
                    (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.table_id?.let { stmt[tableId] = UUID.fromString(it) }
                    request.client_name?.let { stmt[clientName] = it }
                    request.client_phone?.let { stmt[clientPhone] = it }
                    request.reservation_date?.let { stmt[reservationDate] = it }
                    request.reservation_time?.let { stmt[reservationTime] = it }
                    request.number_of_guests?.let { stmt[numberOfGuests] = it }
                    request.notes?.let { stmt[notes] = it }
                    request.order_id?.let { stmt[orderId] = UUID.fromString(it) }
                    stmt[updatedAt] = Clock.System.now()
                }

                ReservationsTable
                    .join(TablesTable, JoinType.LEFT, ReservationsTable.tableId, TablesTable.id)
                    .selectAll()
                    .where { ReservationsTable.id eq UUID.fromString(id) }
                    .first().toReservationDto()
            }
            trace.step("Reservation updated", mapOf("reservationId" to updated.id, "clientName" to updated.client_name, "tableId" to updated.table_id))
            trace.step("Update reservation completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // PATCH /api/v1/reservations/{id}/status
        patch("/{id}/status") {
            val trace = call.routeTrace()
            trace.step("Update reservation status started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateReservationStatusDto>()
            trace.step("Changing reservation status", mapOf("reservationId" to id, "newStatus" to request.status))

            val validStatuses = listOf("PENDING", "CONFIRMED", "CANCELLED", "COMPLETED")
            require(request.status in validStatuses) {
                "Invalid status. Must be one of: ${validStatuses.joinToString()}"
            }

            val updated = transaction {
                val existing = ReservationsTable.selectAll()
                    .where {
                        (ReservationsTable.id eq UUID.fromString(id)) and
                        (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Reservation not found")

                val tableId = existing[ReservationsTable.tableId]

                ReservationsTable.update({
                    (ReservationsTable.id eq UUID.fromString(id)) and
                    (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[status] = request.status
                    it[updatedAt] = Clock.System.now()
                }

                // If cancelled or completed, release the table
                if (request.status in listOf("CANCELLED", "COMPLETED")) {
                    TablesTable.update({ TablesTable.id eq tableId }) {
                        it[status] = "AVAILABLE"
                        it[updatedAt] = Clock.System.now()
                    }
                }

                ReservationsTable
                    .join(TablesTable, JoinType.LEFT, ReservationsTable.tableId, TablesTable.id)
                    .selectAll()
                    .where { ReservationsTable.id eq UUID.fromString(id) }
                    .first().toReservationDto()
            }
            trace.step("Reservation status updated", mapOf("reservationId" to updated.id, "status" to updated.status))
            trace.step("Update reservation status completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE /api/v1/reservations/{id}
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete reservation started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Deleting reservation", mapOf("reservationId" to id, "vendorId" to principal.vendorId))

            transaction {
                val existing = ReservationsTable.selectAll()
                    .where {
                        (ReservationsTable.id eq UUID.fromString(id)) and
                        (ReservationsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Reservation not found")

                val tableId = existing[ReservationsTable.tableId]
                val currentStatus = existing[ReservationsTable.status]

                // Delete the reservation
                ReservationsTable.deleteWhere {
                    (ReservationsTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }

                // Release the table if reservation was active
                if (currentStatus in listOf("PENDING", "CONFIRMED")) {
                    TablesTable.update({ TablesTable.id eq tableId }) {
                        it[status] = "AVAILABLE"
                        it[updatedAt] = Clock.System.now()
                    }
                }
            }
            trace.step("Reservation deleted", mapOf("reservationId" to id))
            trace.step("Delete reservation completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toReservationDto() = ReservationDto(
    id = this[ReservationsTable.id].toString(),
    vendor_id = this[ReservationsTable.vendorId].toString(),
    table_id = this[ReservationsTable.tableId].toString(),
    table_number = this.getOrNull(TablesTable.number),
    client_name = this[ReservationsTable.clientName],
    client_phone = this[ReservationsTable.clientPhone],
    reservation_date = this[ReservationsTable.reservationDate],
    reservation_time = this[ReservationsTable.reservationTime],
    number_of_guests = this[ReservationsTable.numberOfGuests],
    notes = this[ReservationsTable.notes],
    status = this[ReservationsTable.status],
    order_id = this[ReservationsTable.orderId]?.toString(),
    created_by = this[ReservationsTable.createdBy].toString(),
    created_at = this[ReservationsTable.createdAt].toEpochMilliseconds(),
    updated_at = this[ReservationsTable.updatedAt].toEpochMilliseconds(),
)
