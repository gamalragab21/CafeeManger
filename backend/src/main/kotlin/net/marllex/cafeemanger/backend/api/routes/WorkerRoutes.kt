package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ────────────────────────────────────────────────────────

@Serializable
data class WorkerDto(
    val id: String,
    val vendor_id: String,
    val worker_id: String,
    val full_name: String,
    val phone: String? = null,
    val description: String? = null,
    val role: String,
    val salary_type: String,
    val salary_amount: Double,
    val active: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateWorkerDto(
    val full_name: String,
    val phone: String? = null,
    val description: String? = null,
    val role: String,
    val salary_type: String, // DAILY, MONTHLY
    val salary_amount: Double = 0.0,
)

@Serializable
data class UpdateWorkerDto(
    val full_name: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val role: String? = null,
    val salary_type: String? = null,
    val salary_amount: Double? = null,
    val active: Boolean? = null,
)

@Serializable
data class WorkerRoleDto(
    val id: String,
    val vendor_id: String,
    val name: String,
    val description: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class CreateWorkerRoleDto(
    val name: String,
    val description: String? = null,
)

@Serializable
data class SalaryPaymentDto(
    val id: String,
    val vendor_id: String,
    val worker_id: String,
    val worker_name: String? = null,
    val period_type: String,
    val period_start: String,
    val period_end: String,
    val worked_days: Int,
    val worked_hours: Int? = null,
    val amount: Double,
    val paid: Boolean,
    val paid_at: Long? = null,
    val paid_by: String? = null,
    val note: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class MarkPaidDto(
    val note: String? = null,
)

@Serializable
data class BatchPayDto(
    val payment_ids: List<String>,
    val note: String? = null,
)

// ─── Routes ──────────────────────────────────────────────────────

fun Route.workerRoutes() {
    // ── Worker Roles (Predefined in Settings) ────────────────────
    route("/api/v1/worker-roles") {
        // GET all worker roles
        get {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val roles = transaction {
                WorkerRolesTable.selectAll()
                    .where { WorkerRolesTable.vendorId eq vendorUUID }
                    .orderBy(WorkerRolesTable.name)
                    .map { it.toWorkerRoleDto() }
            }
            call.respond(HttpStatusCode.OK, roles)
        }

        // CREATE worker role
        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateWorkerRoleDto>()
            require(request.name.isNotBlank()) { "Role name is required" }

            val role = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Check for duplicate
                val existing = WorkerRolesTable.selectAll()
                    .where {
                        (WorkerRolesTable.vendorId eq vendorUUID) and
                        (WorkerRolesTable.name eq request.name)
                    }.firstOrNull()
                if (existing != null) throw IllegalStateException("Role '${request.name}' already exists")

                val id = WorkerRolesTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[name] = request.name
                    it[description] = request.description
                    it[createdAt] = Clock.System.now()
                }
                WorkerRolesTable.selectAll().where { WorkerRolesTable.id eq id }.first().toWorkerRoleDto()
            }
            call.respond(HttpStatusCode.Created, role)
        }

        // DELETE worker role
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = WorkerRolesTable.deleteWhere {
                    (WorkerRolesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Role not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }

    // ── Workers CRUD ─────────────────────────────────────────────
    route("/api/v1/workers") {
        // GET all workers
        get {
            try {
                println("[Workers][GET] Start fetching all workers")

                val principal = requireRole("MANAGER","CASHIER")
                println("[Workers][GET] Principal: $principal")

                val vendorUUID = UUID.fromString(principal.vendorId)
                println("[Workers][GET] Vendor UUID: $vendorUUID")

                val activeOnly = call.parameters["active"]?.toBooleanStrictOrNull()
                println("[Workers][GET] Active filter parameter: $activeOnly")

                val workers = transaction {
                    println("[Workers][GET][Transaction] Start database transaction")

                    var query = WorkersTable.selectAll().where { WorkersTable.vendorId eq vendorUUID }
                    println("[Workers][GET][Transaction] Base query for vendor: ${query.fetchSize}")

                    activeOnly?.let { active ->
                        query = query.andWhere { WorkersTable.active eq active }
                        println("[Workers][GET][Transaction] Applied active filter: $active")
                    }

                    val results = query.orderBy(WorkersTable.fullName).map { it.toWorkerDto() }
                    println("[Workers][GET][Transaction] Workers fetched count: ${results.size}")
                    results
                }

                println("[Workers][GET] Finished fetching workers: $workers")
                call.respond(HttpStatusCode.OK, workers)
            } catch (e: Exception) {
                println("[Workers][GET][ERROR] ${e.message}")
                throw e
            }
        }

        // GET single worker
        get("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val worker = transaction {
                WorkersTable.selectAll()
                    .where {
                        (WorkersTable.id eq UUID.fromString(id)) and
                        (WorkersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toWorkerDto()
                    ?: throw NoSuchElementException("Worker not found")
            }
            call.respond(HttpStatusCode.OK, worker)
        }

        // CREATE worker
        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateWorkerDto>()
            require(request.full_name.isNotBlank()) { "Full name is required" }
            require(request.role.isNotBlank()) { "Role is required" }
            require(request.salary_type in listOf("DAILY", "MONTHLY")) {
                "Salary type must be DAILY or MONTHLY"
            }

            val worker = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                // Generate auto-incrementing worker ID
                val lastWorker = WorkersTable.selectAll()
                    .where { WorkersTable.vendorId eq vendorUUID }
                    .orderBy(WorkersTable.createdAt, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()

                val nextNum = if (lastWorker != null) {
                    val lastId = lastWorker[WorkersTable.workerId]
                    val num = lastId.substringAfter("-").toIntOrNull() ?: 0
                    num + 1
                } else 1

                val workerId = "WRK-%03d".format(nextNum)

                val id = WorkersTable.insertAndGetId {
                    it[WorkersTable.vendorId] = vendorUUID
                    it[WorkersTable.workerId] = workerId
                    it[fullName] = request.full_name
                    it[phone] = request.phone
                    it[description] = request.description
                    it[role] = request.role
                    it[salaryType] = request.salary_type
                    it[salaryAmount] = BigDecimal.valueOf(request.salary_amount)
                    it[active] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                WorkersTable.selectAll().where { WorkersTable.id eq id }.first().toWorkerDto()
            }
            call.respond(HttpStatusCode.Created, worker)
        }

        // UPDATE worker
        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateWorkerDto>()

            request.salary_type?.let { type ->
                require(type in listOf("DAILY", "MONTHLY")) {
                    "Salary type must be DAILY or MONTHLY"
                }
            }

            val updated = transaction {
                val workerUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                WorkersTable.update({
                    (WorkersTable.id eq workerUUID) and
                    (WorkersTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.full_name?.let { stmt[fullName] = it }
                    request.phone?.let { stmt[phone] = it }
                    request.description?.let { stmt[description] = it }
                    request.role?.let { stmt[role] = it }
                    request.salary_type?.let { stmt[salaryType] = it }
                    request.salary_amount?.let { stmt[salaryAmount] = BigDecimal.valueOf(it) }
                    request.active?.let { stmt[active] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                WorkersTable.selectAll()
                    .where { WorkersTable.id eq workerUUID }
                    .firstOrNull()?.toWorkerDto()
                    ?: throw NoSuchElementException("Worker not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE worker
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val workerUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Delete related records first
                AttendanceTable.deleteWhere { AttendanceTable.workerId eq workerUUID }
                SalaryPaymentsTable.deleteWhere { SalaryPaymentsTable.workerId eq workerUUID }

                val deleted = WorkersTable.deleteWhere {
                    (WorkersTable.id eq workerUUID) and
                    (WorkersTable.vendorId eq vendorUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Worker not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }

    // ── Salary Payments ──────────────────────────────────────────
    route("/api/v1/salary-payments") {
        // GET salary payments (filter by worker, period, paid status)
        get {
            val principal = requireRole("MANAGER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val workerIdParam = call.parameters["worker_id"]
            val paidParam = call.parameters["paid"]?.toBooleanStrictOrNull()
            val periodType = call.parameters["period_type"]

            val payments = transaction {
                var query = SalaryPaymentsTable
                    .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { SalaryPaymentsTable.vendorId eq vendorUUID }

                workerIdParam?.let { wId ->
                    query = query.andWhere { SalaryPaymentsTable.workerId eq UUID.fromString(wId) }
                }
                paidParam?.let { paid ->
                    query = query.andWhere { SalaryPaymentsTable.paid eq paid }
                }
                periodType?.let { pt ->
                    query = query.andWhere { SalaryPaymentsTable.periodType eq pt }
                }

                query.orderBy(SalaryPaymentsTable.createdAt, SortOrder.DESC)
                    .map { it.toSalaryPaymentDto() }
            }
            call.respond(HttpStatusCode.OK, payments)
        }

        // BATCH PAY multiple salary payments at once
        patch("/batch-pay") {
            val principal = requireRole("MANAGER")
            val request = call.receive<BatchPayDto>()
            require(request.payment_ids.isNotEmpty()) { "At least one payment ID is required" }

            val updated = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()
                val userId = UUID.fromString(principal.userId)

                request.payment_ids.map { idStr ->
                    val paymentUUID = UUID.fromString(idStr)

                    SalaryPaymentsTable.update({
                        (SalaryPaymentsTable.id eq paymentUUID) and
                        (SalaryPaymentsTable.vendorId eq vendorUUID)
                    }) {
                        it[paid] = true
                        it[paidAt] = now
                        it[paidBy] = userId
                        it[note] = request.note
                        it[updatedAt] = now
                    }

                    SalaryPaymentsTable
                        .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                        .selectAll()
                        .where { SalaryPaymentsTable.id eq paymentUUID }
                        .firstOrNull()?.toSalaryPaymentDto()
                }.filterNotNull()
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // MARK as paid / unpaid
        patch("/{id}/pay") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<MarkPaidDto>()

            val updated = transaction {
                val paymentUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                SalaryPaymentsTable.update({
                    (SalaryPaymentsTable.id eq paymentUUID) and
                    (SalaryPaymentsTable.vendorId eq vendorUUID)
                }) {
                    it[paid] = true
                    it[paidAt] = Clock.System.now()
                    it[paidBy] = UUID.fromString(principal.userId)
                    it[note] = request.note
                    it[updatedAt] = Clock.System.now()
                }

                SalaryPaymentsTable
                    .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { SalaryPaymentsTable.id eq paymentUUID }
                    .firstOrNull()?.toSalaryPaymentDto()
                    ?: throw NoSuchElementException("Payment not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // MARK as unpaid
        patch("/{id}/unpay") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val updated = transaction {
                val paymentUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                SalaryPaymentsTable.update({
                    (SalaryPaymentsTable.id eq paymentUUID) and
                    (SalaryPaymentsTable.vendorId eq vendorUUID)
                }) {
                    it[paid] = false
                    it[paidAt] = null
                    it[paidBy] = null
                    it[updatedAt] = Clock.System.now()
                }

                SalaryPaymentsTable
                    .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { SalaryPaymentsTable.id eq paymentUUID }
                    .firstOrNull()?.toSalaryPaymentDto()
                    ?: throw NoSuchElementException("Payment not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}

// ─── Mappers ─────────────────────────────────────────────────────

private fun ResultRow.toWorkerDto() = WorkerDto(
    id = this[WorkersTable.id].toString(),
    vendor_id = this[WorkersTable.vendorId].toString(),
    worker_id = this[WorkersTable.workerId],
    full_name = this[WorkersTable.fullName],
    phone = this[WorkersTable.phone],
    description = this[WorkersTable.description],
    role = this[WorkersTable.role],
    salary_type = this[WorkersTable.salaryType],
    salary_amount = this[WorkersTable.salaryAmount].toDouble(),
    active = this[WorkersTable.active],
    created_at = this[WorkersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[WorkersTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toWorkerRoleDto() = WorkerRoleDto(
    id = this[WorkerRolesTable.id].toString(),
    vendor_id = this[WorkerRolesTable.vendorId].toString(),
    name = this[WorkerRolesTable.name],
    description = this[WorkerRolesTable.description],
    created_at = this[WorkerRolesTable.createdAt].toEpochMilliseconds(),
)

private fun ResultRow.toSalaryPaymentDto() = SalaryPaymentDto(
    id = this[SalaryPaymentsTable.id].toString(),
    vendor_id = this[SalaryPaymentsTable.vendorId].toString(),
    worker_id = this[SalaryPaymentsTable.workerId].toString(),
    worker_name = this.getOrNull(WorkersTable.fullName),
    period_type = this[SalaryPaymentsTable.periodType],
    period_start = this[SalaryPaymentsTable.periodStart],
    period_end = this[SalaryPaymentsTable.periodEnd],
    worked_days = this[SalaryPaymentsTable.workedDays],
    worked_hours = this[SalaryPaymentsTable.workedHours],
    amount = this[SalaryPaymentsTable.amount].toDouble(),
    paid = this[SalaryPaymentsTable.paid],
    paid_at = this[SalaryPaymentsTable.paidAt]?.toEpochMilliseconds(),
    paid_by = this[SalaryPaymentsTable.paidBy]?.toString(),
    note = this[SalaryPaymentsTable.note],
    created_at = this[SalaryPaymentsTable.createdAt].toEpochMilliseconds(),
)
