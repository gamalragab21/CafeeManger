package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.OvertimeTable
import net.marllex.waselak.backend.data.database.WorkersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
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
    @SerialName("worker_id") val workerId: String,
    val date: String,
    val hours: Double,
    @SerialName("rate_per_hour") val ratePerHour: Double,
    val note: String? = null,
)

fun Route.overtimeRoutes() {
    route("/api/v1/overtime") {
        // GET - list overtime entries
        get {
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val workerId = call.parameters["worker_id"]
            val fromDate = call.parameters["from_date"]
            val toDate = call.parameters["to_date"]

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
            call.respond(HttpStatusCode.OK, entries)
        }

        // POST - create overtime entry
        post {
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CreateOvertimeDto>()

            require(request.hours > 0) { "Hours must be greater than 0" }
            require(request.ratePerHour > 0) { "Rate per hour must be greater than 0" }
            require(request.date.isNotBlank()) { "Date is required" }

            val calculatedAmount = request.hours * request.ratePerHour

            val entry = transaction {
                val workerUUID = UUID.fromString(request.workerId)
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
            call.respond(HttpStatusCode.Created, entry)
        }

        // DELETE - delete overtime entry (MANAGER only)
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = OvertimeTable.deleteWhere {
                    (OvertimeTable.id eq UUID.fromString(id)) and
                    (OvertimeTable.vendorId eq vendorUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Overtime entry not found")
            }
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
