package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.currentUser
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

// ─── DTOs ────────────────────────────────────────────────────────

@Serializable
data class AttendanceDto(
    val id: String,
    val vendor_id: String,
    val worker_id: String,
    val worker_name: String? = null,
    val worker_role: String? = null,
    val date: String,
    val check_in: Long,
    val check_out: Long? = null,
    val worked_minutes: Int? = null,
    val recorded_by: String,
    val note: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class CheckInDto(
    val worker_id: String,
    val note: String? = null,
)

@Serializable
data class CheckOutDto(
    val note: String? = null,
)

@Serializable
data class AttendanceSummaryDto(
    val worker_id: String,
    val worker_name: String,
    val worker_role: String,
    val total_days: Int,
    val total_worked_minutes: Int,
    val present_today: Boolean,
)

// ─── Routes ──────────────────────────────────────────────────────

fun Route.attendanceRoutes() {
    route("/api/v1/attendance") {

        // GET attendance records (Manager: all workers; Cashier: today only)
        get {
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val workerIdParam = call.parameters["worker_id"]
            val dateParam = call.parameters["date"]
            val fromDate = call.parameters["from_date"]
            val toDate = call.parameters["to_date"]

            val records = transaction {
                var query = AttendanceTable
                    .innerJoin(WorkersTable, { AttendanceTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { AttendanceTable.vendorId eq vendorUUID }

                workerIdParam?.let { wId ->
                    query = query.andWhere { AttendanceTable.workerId eq UUID.fromString(wId) }
                }
                dateParam?.let { d ->
                    query = query.andWhere { AttendanceTable.date eq d }
                }
                fromDate?.let { f ->
                    query = query.andWhere { AttendanceTable.date greaterEq f }
                }
                toDate?.let { t ->
                    query = query.andWhere { AttendanceTable.date lessEq t }
                }

                query.orderBy(AttendanceTable.date, SortOrder.DESC)
                    .orderBy(AttendanceTable.checkIn, SortOrder.DESC)
                    .map { it.toAttendanceDto() }
            }
            call.respond(HttpStatusCode.OK, records)
        }

        // GET today's attendance summary (who is present / absent)
        get("/today") {
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val today = Clock.System.now().toString().substring(0, 10) // YYYY-MM-DD

            val summary = transaction {
                val activeWorkers = WorkersTable.selectAll()
                    .where {
                        (WorkersTable.vendorId eq vendorUUID) and
                        (WorkersTable.active eq true)
                    }.toList()

                val todayRecords = AttendanceTable.selectAll()
                    .where {
                        (AttendanceTable.vendorId eq vendorUUID) and
                        (AttendanceTable.date eq today)
                    }.toList()

                val presentWorkerIds = todayRecords.map { it[AttendanceTable.workerId].toString() }.toSet()

                activeWorkers.map { worker ->
                    val wId = worker[WorkersTable.id].toString()
                    val attendance = todayRecords.find { it[AttendanceTable.workerId].toString() == wId }

                    AttendanceSummaryDto(
                        worker_id = wId,
                        worker_name = worker[WorkersTable.fullName],
                        worker_role = worker[WorkersTable.role],
                        total_days = 0, // Not relevant for today view
                        total_worked_minutes = attendance?.get(AttendanceTable.workedMinutes) ?: 0,
                        present_today = wId in presentWorkerIds,
                    )
                }
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        // GET attendance history summary for a worker
        get("/summary/{workerId}") {
            val principal = requireRole("MANAGER")
            val workerId = call.parameters["workerId"] ?: throw IllegalArgumentException("Worker ID required")
            val fromDate = call.parameters["from_date"]
            val toDate = call.parameters["to_date"]

            val summary = transaction {
                val workerUUID = UUID.fromString(workerId)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val worker = WorkersTable.selectAll()
                    .where {
                        (WorkersTable.id eq workerUUID) and
                        (WorkersTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Worker not found")

                var query = AttendanceTable.selectAll()
                    .where { AttendanceTable.workerId eq workerUUID }

                fromDate?.let { f ->
                    query = query.andWhere { AttendanceTable.date greaterEq f }
                }
                toDate?.let { t ->
                    query = query.andWhere { AttendanceTable.date lessEq t }
                }

                val records = query.toList()
                val today = Clock.System.now().toString().substring(0, 10)
                val isTodayPresent = records.any { it[AttendanceTable.date] == today }

                AttendanceSummaryDto(
                    worker_id = workerId,
                    worker_name = worker[WorkersTable.fullName],
                    worker_role = worker[WorkersTable.role],
                    total_days = records.size,
                    total_worked_minutes = records.sumOf { it[AttendanceTable.workedMinutes] ?: 0 },
                    present_today = isTodayPresent,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        // CHECK IN (Cashier or Manager can record)
        post("/check-in") {
            val principal = requireRole("CASHIER", "MANAGER")
            val request = call.receive<CheckInDto>()

            val record = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val workerUUID = UUID.fromString(request.worker_id)
                val now = Clock.System.now()
                val today = now.toString().substring(0, 10)

                // Verify worker exists and is active
                val worker = WorkersTable.selectAll()
                    .where {
                        (WorkersTable.id eq workerUUID) and
                        (WorkersTable.vendorId eq vendorUUID) and
                        (WorkersTable.active eq true)
                    }.firstOrNull() ?: throw NoSuchElementException("Active worker not found")

                // Check if already checked in today
                val existing = AttendanceTable.selectAll()
                    .where {
                        (AttendanceTable.workerId eq workerUUID) and
                        (AttendanceTable.date eq today)
                    }.firstOrNull()

                if (existing != null) throw IllegalStateException("Worker already checked in today")

                val id = AttendanceTable.insertAndGetId {
                    it[AttendanceTable.vendorId] = vendorUUID
                    it[AttendanceTable.workerId] = workerUUID
                    it[date] = today
                    it[checkIn] = now
                    it[recordedBy] = UUID.fromString(principal.userId)
                    it[note] = request.note
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                // ── Auto-generate salary record based on worker salary type ──
                val salaryType = worker[WorkersTable.salaryType]
                val salaryAmount = worker[WorkersTable.salaryAmount]

                when (salaryType) {
                    "DAILY" -> {
                        // Create a daily salary record if not already exists
                        val existingSalary = SalaryPaymentsTable.selectAll()
                            .where {
                                (SalaryPaymentsTable.workerId eq workerUUID) and
                                (SalaryPaymentsTable.periodType eq "DAY") and
                                (SalaryPaymentsTable.periodStart eq today)
                            }.firstOrNull()

                        if (existingSalary == null) {
                            SalaryPaymentsTable.insertAndGetId {
                                it[SalaryPaymentsTable.vendorId] = vendorUUID
                                it[SalaryPaymentsTable.workerId] = workerUUID
                                it[SalaryPaymentsTable.periodType] = "DAY"
                                it[periodStart] = today
                                it[periodEnd] = today
                                it[workedDays] = 1
                                it[amount] = salaryAmount
                                it[paid] = false
                                it[createdAt] = now
                                it[updatedAt] = now
                            }
                        }
                    }
                    "MONTHLY" -> {
                        // Create a monthly salary record on first check-in of the month
                        val month = today.substring(0, 7) // "YYYY-MM"
                        val monthStart = "$month-01"
                        val ym = YearMonth.parse(month)
                        val monthEnd = "$month-${ym.lengthOfMonth().toString().padStart(2, '0')}"

                        val existingMonthly = SalaryPaymentsTable.selectAll()
                            .where {
                                (SalaryPaymentsTable.workerId eq workerUUID) and
                                (SalaryPaymentsTable.periodType eq "MONTH") and
                                (SalaryPaymentsTable.periodStart eq monthStart)
                            }.firstOrNull()

                        if (existingMonthly == null) {
                            SalaryPaymentsTable.insertAndGetId {
                                it[SalaryPaymentsTable.vendorId] = vendorUUID
                                it[SalaryPaymentsTable.workerId] = workerUUID
                                it[SalaryPaymentsTable.periodType] = "MONTH"
                                it[periodStart] = monthStart
                                it[periodEnd] = monthEnd
                                it[workedDays] = 1
                                it[amount] = salaryAmount
                                it[paid] = false
                                it[createdAt] = now
                                it[updatedAt] = now
                            }
                        }
                    }
                }

                AttendanceTable
                    .innerJoin(WorkersTable, { AttendanceTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { AttendanceTable.id eq id }
                    .first().toAttendanceDto()
            }
            call.respond(HttpStatusCode.Created, record)
        }

        // CHECK OUT
        post("/check-out/{attendanceId}") {
            val principal = requireRole("CASHIER", "MANAGER")
            val attendanceId = call.parameters["attendanceId"]
                ?: throw IllegalArgumentException("Attendance ID required")
            val request = call.receive<CheckOutDto>()

            val record = transaction {
                val attnUUID = UUID.fromString(attendanceId)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val existing = AttendanceTable.selectAll()
                    .where {
                        (AttendanceTable.id eq attnUUID) and
                        (AttendanceTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Attendance record not found")

                if (existing[AttendanceTable.checkOut] != null) {
                    throw IllegalStateException("Worker already checked out")
                }

                val checkInTime = existing[AttendanceTable.checkIn]
                val workedMinutes = ((now.toEpochMilliseconds() - checkInTime.toEpochMilliseconds()) / 60_000).toInt()

                AttendanceTable.update({ AttendanceTable.id eq attnUUID }) {
                    it[checkOut] = now
                    it[AttendanceTable.workedMinutes] = workedMinutes
                    if (request.note != null) it[note] = request.note
                    it[updatedAt] = now
                }

                AttendanceTable
                    .innerJoin(WorkersTable, { AttendanceTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { AttendanceTable.id eq attnUUID }
                    .first().toAttendanceDto()
            }
            call.respond(HttpStatusCode.OK, record)
        }

        // DELETE attendance record (Manager only)
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = AttendanceTable.deleteWhere {
                    (AttendanceTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Attendance record not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

// ─── Mappers ─────────────────────────────────────────────────────

private fun ResultRow.toAttendanceDto() = AttendanceDto(
    id = this[AttendanceTable.id].toString(),
    vendor_id = this[AttendanceTable.vendorId].toString(),
    worker_id = this[AttendanceTable.workerId].toString(),
    worker_name = this.getOrNull(WorkersTable.fullName),
    worker_role = this.getOrNull(WorkersTable.role),
    date = this[AttendanceTable.date],
    check_in = this[AttendanceTable.checkIn].toEpochMilliseconds(),
    check_out = this[AttendanceTable.checkOut]?.toEpochMilliseconds(),
    worked_minutes = this[AttendanceTable.workedMinutes],
    recorded_by = this[AttendanceTable.recordedBy].toString(),
    note = this[AttendanceTable.note],
    created_at = this[AttendanceTable.createdAt].toEpochMilliseconds(),
)
