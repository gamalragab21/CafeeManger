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
import net.marllex.waselak.backend.data.database.SalaryPaymentsTable
import net.marllex.waselak.backend.data.database.WorkersTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.time.YearMonth
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
    val paid: Boolean = false,
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

@Serializable
data class BatchPayOvertimeDto(
    val ids: List<String>,
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
            // Sync overtime totals to the salary payment covering this date
            syncOvertimeToSalaryPayment(vendorUUID, UUID.fromString(entry.workerId), entry.date)
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
            // Sync overtime totals to the salary payment covering this date
            syncOvertimeToSalaryPayment(vendorUUID, UUID.fromString(entry.workerId), entry.date)
            trace.step("Overtime entry updated", mapOf("id" to entry.id, "workerId" to entry.workerId, "hours" to entry.hours.toString(), "ratePerHour" to entry.ratePerHour.toString()))
            trace.step("Update overtime entry completed")
            call.respond(HttpStatusCode.OK, entry)
        }

        // PATCH - mark overtime as paid (MANAGER only)
        patch("/{id}/pay") {
            val trace = call.routeTrace()
            trace.step("Mark overtime as paid started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val entry = transaction {
                val overtimeUUID = UUID.fromString(id)
                OvertimeTable.update({
                    (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                }) {
                    it[paid] = true
                }
                OvertimeTable.innerJoin(
                    WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                ).selectAll().where {
                    OvertimeTable.id eq overtimeUUID
                }.firstOrNull()?.toOvertimeDto()
                    ?: throw NoSuchElementException("Overtime entry not found")
            }
            syncOvertimeToSalaryPayment(vendorUUID, UUID.fromString(entry.workerId), entry.date)
            trace.step("Mark overtime as paid completed", mapOf("id" to entry.id))
            call.respond(HttpStatusCode.OK, entry)
        }

        // PATCH - mark overtime as unpaid (MANAGER only)
        patch("/{id}/unpay") {
            val trace = call.routeTrace()
            trace.step("Mark overtime as unpaid started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val entry = transaction {
                val overtimeUUID = UUID.fromString(id)
                OvertimeTable.update({
                    (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                }) {
                    it[paid] = false
                }
                OvertimeTable.innerJoin(
                    WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                ).selectAll().where {
                    OvertimeTable.id eq overtimeUUID
                }.firstOrNull()?.toOvertimeDto()
                    ?: throw NoSuchElementException("Overtime entry not found")
            }
            syncOvertimeToSalaryPayment(vendorUUID, UUID.fromString(entry.workerId), entry.date)
            trace.step("Mark overtime as unpaid completed", mapOf("id" to entry.id))
            call.respond(HttpStatusCode.OK, entry)
        }

        // PATCH - batch pay overtime entries (MANAGER only)
        patch("/batch-pay") {
            val trace = call.routeTrace()
            trace.step("Batch pay overtime started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OVERTIME")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<BatchPayOvertimeDto>()
            require(request.ids.isNotEmpty()) { "At least one overtime ID is required" }

            val entries = transaction {
                request.ids.map { idStr ->
                    val overtimeUUID = UUID.fromString(idStr)
                    OvertimeTable.update({
                        (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                    }) {
                        it[paid] = true
                    }
                    OvertimeTable.innerJoin(
                        WorkersTable, { OvertimeTable.workerId }, { WorkersTable.id }
                    ).selectAll().where {
                        OvertimeTable.id eq overtimeUUID
                    }.first().toOvertimeDto()
                }
            }
            // Re-sync salary for each affected worker+date
            entries.map { UUID.fromString(it.workerId) to it.date }.distinct().forEach { (wId, d) ->
                syncOvertimeToSalaryPayment(vendorUUID, wId, d)
            }
            trace.step("Batch pay overtime completed", mapOf("count" to entries.size.toString()))
            call.respond(HttpStatusCode.OK, entries)
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

            // Capture workerId and date before deleting so we can sync the salary payment
            val (deletedWorkerId, deletedDate) = transaction {
                val overtimeUUID = UUID.fromString(id)
                val existing = OvertimeTable.selectAll().where {
                    (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Overtime entry not found")

                val wId = existing[OvertimeTable.workerId].value
                val d = existing[OvertimeTable.date]

                OvertimeTable.deleteWhere {
                    (OvertimeTable.id eq overtimeUUID) and (OvertimeTable.vendorId eq vendorUUID)
                }
                Pair(wId, d)
            }
            // Sync overtime totals to the salary payment covering this date
            syncOvertimeToSalaryPayment(vendorUUID, deletedWorkerId, deletedDate)
            trace.step("Overtime entry deleted", mapOf("overtimeId" to id))
            trace.step("Delete overtime entry completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

/**
 * Aggregates all overtime entries for a worker within the salary period covering [date],
 * then updates the salary payment's overtimeHours and overtimeAmount.
 * If no salary payment exists for the date, one is auto-created based on the worker's salary type.
 */
private fun syncOvertimeToSalaryPayment(vendorId: UUID, workerId: UUID, date: String) {
    transaction {
        // Find the salary payment whose period covers this date
        var salaryPayment = SalaryPaymentsTable.selectAll().where {
            (SalaryPaymentsTable.vendorId eq vendorId) and
            (SalaryPaymentsTable.workerId eq workerId) and
            (SalaryPaymentsTable.periodStart lessEq date) and
            (SalaryPaymentsTable.periodEnd greaterEq date)
        }.firstOrNull()

        // Auto-create salary payment if none exists
        if (salaryPayment == null) {
            val worker = WorkersTable.selectAll().where {
                (WorkersTable.id eq workerId) and (WorkersTable.vendorId eq vendorId)
            }.firstOrNull() ?: return@transaction

            val salaryType = worker[WorkersTable.salaryType]
            val salaryAmount = worker[WorkersTable.salaryAmount]
            val now = Clock.System.now()

            val (pType, pStart, pEnd) = when (salaryType) {
                "DAILY" -> Triple("DAY", date, date)
                "MONTHLY" -> {
                    val month = date.substring(0, 7)
                    val monthStart = "$month-01"
                    val ym = YearMonth.parse(month)
                    val monthEnd = "$month-${ym.lengthOfMonth().toString().padStart(2, '0')}"
                    Triple("MONTH", monthStart, monthEnd)
                }
                else -> return@transaction
            }

            SalaryPaymentsTable.insertAndGetId {
                it[SalaryPaymentsTable.vendorId] = vendorId
                it[SalaryPaymentsTable.workerId] = workerId
                it[SalaryPaymentsTable.periodType] = pType
                it[periodStart] = pStart
                it[periodEnd] = pEnd
                it[workedDays] = 0
                it[amount] = salaryAmount
                it[paid] = false
                it[createdAt] = now
                it[updatedAt] = now
            }

            salaryPayment = SalaryPaymentsTable.selectAll().where {
                (SalaryPaymentsTable.vendorId eq vendorId) and
                (SalaryPaymentsTable.workerId eq workerId) and
                (SalaryPaymentsTable.periodStart lessEq date) and
                (SalaryPaymentsTable.periodEnd greaterEq date)
            }.first()
        }

        val salaryId = salaryPayment[SalaryPaymentsTable.id]
        val periodStart = salaryPayment[SalaryPaymentsTable.periodStart]
        val periodEnd = salaryPayment[SalaryPaymentsTable.periodEnd]

        // Sum only UNPAID overtime entries (paid ones were already paid separately)
        val overtimeEntries = OvertimeTable.selectAll().where {
            (OvertimeTable.vendorId eq vendorId) and
            (OvertimeTable.workerId eq workerId) and
            (OvertimeTable.date greaterEq periodStart) and
            (OvertimeTable.date lessEq periodEnd) and
            (OvertimeTable.paid eq false)
        }.toList()

        val totalOtHours = overtimeEntries.sumOf { it[OvertimeTable.hours].toDouble() }
        val totalOtAmount = overtimeEntries.sumOf { it[OvertimeTable.amount].toDouble() }

        SalaryPaymentsTable.update({ SalaryPaymentsTable.id eq salaryId }) {
            it[overtimeHours] = totalOtHours.toBigDecimal()
            it[overtimeAmount] = totalOtAmount.toBigDecimal()
            it[updatedAt] = Clock.System.now()
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
    paid = this[OvertimeTable.paid],
    createdBy = this[OvertimeTable.createdBy].toString(),
    createdAt = this[OvertimeTable.createdAt].toEpochMilliseconds(),
)
