package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.OvertimeTable
import net.marllex.waselak.backend.data.database.WorkersTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

@Serializable
data class OvertimeDto(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_name") val workerName: String? = null,
    val date: String,
    val hours: Double,
    @SerialName("rate_per_hour") val ratePerHour: Double,
    val amount: Double,
    val note: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateOvertimeDto(
    @SerialName("worker_id") val workerId: String = "",
    val date: String,
    val hours: Double,
    @SerialName("rate_per_hour") val ratePerHour: Double = 0.0,
    val note: String? = null,
)

@Serializable
data class UpdateOvertimeDto(
    @SerialName("rate_per_hour") val ratePerHour: Double? = null,
    val note: String? = null,
)

fun Route.overtimeRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/overtime") {
        // GET - list overtime entries
        get {
            val trace = call.routeTrace()
            trace.step("List overtime entries started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val workerId = call.parameters["worker_id"]
            val fromDate = call.parameters["from_date"]
            val toDate = call.parameters["to_date"]
            trace.step("Querying overtime entries", mapOf("vendorId" to vendorUUID.toString(), "workerId" to (workerId ?: "null"), "fromDate" to (fromDate ?: "null"), "toDate" to (toDate ?: "null")))

            val entries = transaction {
                var query = OvertimeTable.innerJoin(
                    WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                ).selectAll().where {
                    OvertimeTable.vendorId eq vendorUUID
                }
                workerId?.let { query = query.andWhere { OvertimeTable.workerId eq UUID.fromString(it) } }
                fromDate?.let { query = query.andWhere { OvertimeTable.date greaterEq it } }
                toDate?.let { query = query.andWhere { OvertimeTable.date lessEq it } }

                query.orderBy(OvertimeTable.date, SortOrder.DESC)
                    .map { it.toOvertimeDto() }
            }
            trace.step("Overtime entries retrieved", mapOf("count" to entries.size.toString()))
            trace.step("List overtime entries completed")
            call.respond(HttpStatusCode.OK, entries)
        }

        // POST - create overtime entry
        post {
            val trace = call.routeTrace()
            trace.step("Create overtime entry started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CreateOvertimeDto>()

            require(request.hours > 0) { "Hours must be greater than 0" }
            require(request.ratePerHour >= 0) { "Rate per hour must be >= 0" }
            require(request.date.isNotBlank()) { "Date is required" }
            trace.step("Creating overtime entry", mapOf("workerId" to request.workerId, "date" to request.date, "hours" to request.hours.toString(), "ratePerHour" to request.ratePerHour.toString(), "note" to (request.note ?: "null")))

            val calculatedAmount = request.hours * request.ratePerHour

            val entry = transaction {
                // If worker_id is empty, resolve from authenticated user
                val workerUUID = if (request.workerId.isBlank()) {
                    val w = WorkersTable.selectAll().where {
                        (WorkersTable.userId eq userUUID) and (WorkersTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("No worker linked to current user")
                    w[WorkersTable.id].value
                } else {
                    UUID.fromString(request.workerId)
                }
                // Verify worker belongs to vendor
                val worker = WorkersTable.selectAll().where {
                    (WorkersTable.id eq workerUUID) and (WorkersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Worker not found")

                val id = OvertimeTable.insertAndGetId {
                    it[OvertimeTable.vendorId] = vendorUUID
                    it[OvertimeTable.workerId] = workerUUID
                    it[date] = request.date
                    it[hours] = request.hours.toBigDecimal()
                    it[ratePerHour] = request.ratePerHour.toBigDecimal()
                    it[amount] = calculatedAmount.toBigDecimal()
                    it[note] = request.note
                    it[createdBy] = userUUID
                    it[createdAt] = Clock.System.now()
                }

                OvertimeTable.innerJoin(
                    WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                ).selectAll().where {
                    OvertimeTable.id eq id
                }.first().toOvertimeDto()
            }
            trace.step("Overtime entry created", mapOf("id" to entry.id, "workerId" to entry.workerId, "date" to entry.date, "hours" to entry.hours.toString()))
            trace.step("Create overtime entry completed")
            call.respond(HttpStatusCode.Created, entry)
        }

        // PATCH - update overtime entry (MANAGER only — set rate / note)
        patch("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update overtime entry started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateOvertimeDto>()
            trace.step("Updating overtime entry", mapOf("overtimeId" to id, "ratePerHour" to (request.ratePerHour?.toString() ?: "null"), "note" to (request.note ?: "null")))

            val entry = transaction {
                val overtimeUUID = UUID.fromString(id)
                val existing = OvertimeTable.selectAll().where {
                    (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Overtime entry not found")

                val newRate = request.ratePerHour ?: existing[OvertimeTable.ratePerHour].toDouble()
                val hours = existing[OvertimeTable.hours].toDouble()
                val newAmount = hours * newRate

                OvertimeTable.update({ (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID) }) {
                    it[ratePerHour] = newRate.toBigDecimal()
                    it[amount] = newAmount.toBigDecimal()
                    request.note?.let { n -> it[note] = n }
                }

                OvertimeTable.innerJoin(
                    WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                ).selectAll().where {
                    OvertimeTable.id eq overtimeUUID
                }.first().toOvertimeDto()
            }
            trace.step("Overtime entry updated", mapOf("id" to entry.id, "workerId" to entry.workerId, "hours" to entry.hours.toString(), "ratePerHour" to entry.ratePerHour.toString()))
            trace.step("Update overtime entry completed")
            call.respond(HttpStatusCode.OK, entry)
        }

        // DELETE - delete overtime entry (MANAGER only)
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete overtime entry started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Deleting overtime entry", mapOf("overtimeId" to id, "vendorId" to vendorUUID.toString()))

            transaction {
                val deleted = OvertimeTable.deleteWhere {
                    (OvertimeTable.id eq UUID.fromString(id)) and
                    (OvertimeTable.vendorId eq vendorUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Overtime entry not found")
            }
            trace.step("Overtime entry deleted", mapOf("overtimeId" to id))
            trace.step("Delete overtime entry completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toOvertimeDto() = OvertimeDto(
    id = this[OvertimeTable.id].toString(),
    vendorId = this[OvertimeTable.vendorId].toString(),
    workerId = this[OvertimeTable.workerId].toString(),
    workerName = this[WorkersTable.fullName],
    date = this[OvertimeTable.date],
    hours = this[OvertimeTable.hours].toDouble(),
    ratePerHour = this[OvertimeTable.ratePerHour].toDouble(),
    amount = this[OvertimeTable.amount].toDouble(),
    note = this[OvertimeTable.note],
    createdBy = this[OvertimeTable.createdBy].toString(),
    createdAt = this[OvertimeTable.createdAt].toEpochMilliseconds(),
)
