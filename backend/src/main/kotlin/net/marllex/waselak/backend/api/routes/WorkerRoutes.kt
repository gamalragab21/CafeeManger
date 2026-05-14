package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.log
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AuthService
import net.marllex.waselak.backend.domain.service.OrderService
import net.marllex.waselak.backend.domain.service.PinService
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.domain.service.QrCodeService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import kotlin.getValue

// ─── DTOs ────────────────────────────────────────────────────────

@Serializable
data class PinVerifyResponse(
    val success: Boolean,
    val message: String? = null,
)

@Serializable
data class WorkerDto(
    val id: String,
    val vendor_id: String,
    val user_id: String? = null,
    val worker_id: String,
    val full_name: String,
    val phone: String? = null,
    val description: String? = null,
    val photo_url: String? = null,
    val role: String,
    val salary_type: String,
    val salary_amount: Double,
    val active: Boolean = true,
    val is_login_enabled: Boolean = false,
    val has_pin: Boolean = false,
    val pin_sha256: String? = null,
    val qr_code_version: Int = 1,
    val pin_updated_at: Long? = null,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateWorkerDto(
    val full_name: String,
    val phone: String? = null,
    val description: String? = null,
    val photo_url: String? = null,
    val role: String,
    val salary_type: String, // DAILY, MONTHLY
    val salary_amount: Double = 0.0,
    val pin: String? = null, // OPTIONAL: 4-6 digit PIN (required only for PIN authentication)
    val is_login_enabled: Boolean = false,
    val password: String? = null,
    val login_role: String? = null, // CASHIER or DELIVERY (user role for login)
)

@Serializable
data class UpdateWorkerDto(
    val full_name: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val photo_url: String? = null,
    val role: String? = null,
    val salary_type: String? = null,
    val salary_amount: Double? = null,
    val active: Boolean? = null,
    val pin: String? = null, // Optional: Update PIN
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
    val overtime_hours: Double = 0.0,
    val overtime_amount: Double = 0.0,
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

@Serializable
data class UpdatePinDto(
    val pin: String, // New 4-6 digit PIN
)

@Serializable
data class QrCodeResponse(
    @SerialName("qr_code_data") val qr_code_data: String,
    @SerialName("qr_code_version") val qr_code_version: Int,
)

// ─── Routes ──────────────────────────────────────────────────────

fun Route.workerRoutes() {
    // Inject services at route level to avoid lazy initialization issues
    val pinService by KoinJavaComponent.inject<PinService>(
        clazz = PinService::class.java
    )
    val qrCodeService by KoinJavaComponent.inject<QrCodeService>(
        clazz = QrCodeService::class.java
    )
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)
    // ── Worker Roles (Predefined in Settings) ────────────────────
    route("/api/v1/worker-roles") {
        // GET all worker roles
        get {
            val trace = call.routeTrace()
            trace.step("List worker roles started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId, "role" to principal.role))
            val vendorUUID = UUID.fromString(principal.vendorId)

            val roles = transaction {
                WorkerRolesTable.selectAll()
                    .where { WorkerRolesTable.vendorId eq vendorUUID }
                    .orderBy(WorkerRolesTable.name)
                    .map { it.toWorkerRoleDto() }
            }
            trace.step("Worker roles fetched", mapOf("count" to roles.size.toString()))
            trace.step("List worker roles completed")
            call.respond(HttpStatusCode.OK, roles)
        }

        // CREATE worker role
        post {
            val trace = call.routeTrace()
            trace.step("Create worker role started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val request = call.receive<CreateWorkerRoleDto>()
            trace.step("Request parsed", mapOf("name" to request.name, "description" to (request.description ?: "null")))
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
                trace.step("Duplicate check passed")

                val id = WorkerRolesTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[name] = request.name
                    it[description] = request.description
                    it[createdAt] = Clock.System.now()
                }
                trace.step("Worker role created", mapOf("roleId" to id.toString()))
                WorkerRolesTable.selectAll().where { WorkerRolesTable.id eq id }.first().toWorkerRoleDto()
            }
            trace.step("Create worker role completed")
            call.respond(HttpStatusCode.Created, role)
        }

        // DELETE worker role
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete worker role started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Role ID parsed", mapOf("roleId" to id))

            transaction {
                val deleted = WorkerRolesTable.deleteWhere {
                    (WorkerRolesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                trace.step("Delete result", mapOf("deletedCount" to deleted.toString()))
                if (deleted == 0) throw NoSuchElementException("Role not found")
            }
            trace.step("Delete worker role completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }

    // ── Workers CRUD ─────────────────────────────────────────────
    route("/api/v1/workers") {
        // GET all workers
        get {
            val trace = call.routeTrace()
            trace.step("List workers started")
            val principal = requireRole("MANAGER","CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId, "role" to principal.role))

            val vendorUUID = UUID.fromString(principal.vendorId)
            val activeOnly = call.parameters["active"]?.toBooleanStrictOrNull()
            trace.step("Filters applied", mapOf("activeOnly" to (activeOnly?.toString() ?: "null")))

            val workers = transaction {
                var query = WorkersTable.selectAll().where { WorkersTable.vendorId eq vendorUUID }

                activeOnly?.let { active ->
                    query = query.andWhere { WorkersTable.active eq active }
                }

                val results = query.orderBy(WorkersTable.fullName).map { it.toWorkerDto() }
                trace.step("Workers fetched from database", mapOf("count" to results.size.toString()))
                results
            }

            trace.step("List workers completed", mapOf("totalReturned" to workers.size.toString()))
            call.respond(HttpStatusCode.OK, workers)
        }

        // GET single worker
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get worker started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))

            val worker = transaction {
                WorkersTable.selectAll()
                    .where {
                        (WorkersTable.id eq UUID.fromString(id)) and
                        (WorkersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toWorkerDto()
                    ?: throw NoSuchElementException("Worker not found")
            }
            trace.step("Worker found", mapOf("workerName" to worker.full_name, "workerRole" to worker.role))
            trace.step("Get worker completed")
            call.respond(HttpStatusCode.OK, worker)
        }

        // CREATE worker
        post {
            val trace = call.routeTrace()
            trace.step("Create worker started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val request = call.receive<CreateWorkerDto>()
            trace.step("Request parsed", mapOf(
                "name" to request.full_name,
                "role" to request.role,
                "phone" to (request.phone ?: "null"),
                "salaryType" to request.salary_type,
                "salaryAmount" to request.salary_amount.toString(),
                "isLoginEnabled" to request.is_login_enabled.toString(),
                "hasPin" to (!request.pin.isNullOrBlank()).toString()
            ))

            require(request.full_name.isNotBlank()) { "Full name is required" }
            require(request.role.isNotBlank()) { "Role is required" }
            require(request.salary_type in listOf("DAILY", "MONTHLY")) {
                "Salary type must be DAILY or MONTHLY"
            }
            // PIN is optional - only validate if provided
            if (!request.pin.isNullOrBlank()) {
                require(pinService.isValidPin(request.pin)) { "PIN must be 4-6 digits" }
            }
            trace.step("Validation passed")

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
                trace.step("Worker ID generated", mapOf("workerId" to workerId))

                // Hash PIN only if provided
                val pinHash = if (!request.pin.isNullOrBlank()) {
                    pinService.hashPin(request.pin)
                } else {
                    null
                }
                val pinSha = if (!request.pin.isNullOrBlank()) sha256Hex(request.pin) else null

                // If login enabled, create a User record first
                var linkedUserId: UUID? = null
                if (request.is_login_enabled && !request.password.isNullOrBlank() && !request.phone.isNullOrBlank()) {
                    val userRole = request.login_role ?: "CASHIER"
                    require(userRole in listOf("CASHIER", "DELIVERY")) { "Login role must be CASHIER or DELIVERY" }

                    val passwordHash = AuthService.hashPassword(request.password)
                    linkedUserId = UsersTable.insertAndGetId {
                        it[UsersTable.vendorId] = vendorUUID
                        it[UsersTable.role] = userRole
                        it[name] = request.full_name
                        it[UsersTable.phone] = request.phone
                        it[UsersTable.passwordHash] = passwordHash
                        it[UsersTable.active] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }.value
                    trace.step("Login user created", mapOf("linkedUserId" to linkedUserId.toString(), "loginRole" to userRole))
                }

                val id = WorkersTable.insertAndGetId {
                    it[WorkersTable.vendorId] = vendorUUID
                    it[WorkersTable.workerId] = workerId
                    it[fullName] = request.full_name
                    it[phone] = request.phone
                    it[description] = request.description
                    it[photoUrl] = request.photo_url
                    it[role] = request.role
                    it[salaryType] = request.salary_type
                    it[salaryAmount] = BigDecimal.valueOf(request.salary_amount)
                    it[active] = true
                    if (pinHash != null) {
                        it[WorkersTable.pinHash] = pinHash
                        it[WorkersTable.pinSha256] = pinSha
                        it[WorkersTable.pinUpdatedAt] = now
                    }
                    it[WorkersTable.qrCodeVersion] = 1
                    linkedUserId?.let { uid -> it[userId] = uid }
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                trace.step("Worker inserted", mapOf("workerId" to id.toString()))

                // Generate QR code data
                val qrData = qrCodeService.generateQrCodeData(
                    workerId = id.toString(),
                    name = request.full_name,
                    role = request.role,
                    version = 1
                )
                val qrDataJson = Json.encodeToString(qrData)

                // Update worker with QR code data
                WorkersTable.update({ WorkersTable.id eq id }) {
                    it[qrCodeData] = qrDataJson
                }
                trace.step("QR code generated and saved")

                WorkersTable.selectAll().where { WorkersTable.id eq id }.first().toWorkerDto()
            }
            trace.step("Create worker completed", mapOf("workerId" to worker.id, "workerCode" to worker.worker_id))
            call.respond(HttpStatusCode.Created, worker)
        }

        // UPDATE worker
        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update worker started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))
            val request = call.receive<UpdateWorkerDto>()
            trace.step("Request parsed", mapOf(
                "fullName" to (request.full_name ?: "null"),
                "role" to (request.role ?: "null"),
                "phone" to (request.phone ?: "null"),
                "salaryType" to (request.salary_type ?: "null"),
                "salaryAmount" to (request.salary_amount?.toString() ?: "null"),
                "active" to (request.active?.toString() ?: "null"),
                "hasPin" to (request.pin != null).toString()
            ))

            request.salary_type?.let { type ->
                require(type in listOf("DAILY", "MONTHLY")) {
                    "Salary type must be DAILY or MONTHLY"
                }
            }

            request.pin?.let { pin ->
                require(pinService.isValidPin(pin)) { "PIN must be 4-6 digits" }
            }
            trace.step("Validation passed")

            try {
                val updated = transaction {
                    val workerUUID = UUID.fromString(id)
                    val vendorUUID = UUID.fromString(principal.vendorId)
                    val now = Clock.System.now()

                    // Get current worker data for QR regeneration if needed
                    val currentWorker = WorkersTable.selectAll()
                        .where { (WorkersTable.id eq workerUUID) and (WorkersTable.vendorId eq vendorUUID) }
                        .firstOrNull() ?: throw NoSuchElementException("Worker not found")
                    trace.step("Current worker found", mapOf("currentName" to currentWorker[WorkersTable.fullName], "currentRole" to currentWorker[WorkersTable.role]))

                    val needsQrRegeneration = request.full_name != null || request.role != null

                    // Delete old photo file if being replaced
                    if (request.photo_url != null) {
                        val oldPhotoUrl = currentWorker[WorkersTable.photoUrl]
                        if (oldPhotoUrl != null && oldPhotoUrl != request.photo_url) {
                            deleteUploadedFile(oldPhotoUrl)
                            trace.step("Old photo deleted", mapOf("oldPhotoUrl" to oldPhotoUrl))
                        }
                    }

                    WorkersTable.update({
                        (WorkersTable.id eq workerUUID) and
                        (WorkersTable.vendorId eq vendorUUID)
                    }) { stmt ->
                        request.full_name?.let { stmt[fullName] = it }
                        request.phone?.let { stmt[phone] = it }
                        request.description?.let { stmt[description] = it }
                        request.photo_url?.let { stmt[photoUrl] = it }
                        request.role?.let { stmt[role] = it }
                        request.salary_type?.let { stmt[salaryType] = it }
                        request.salary_amount?.let { stmt[salaryAmount] = BigDecimal.valueOf(it) }
                        request.active?.let { stmt[active] = it }
                        request.pin?.let { pin ->
                            stmt[WorkersTable.pinHash] = pinService.hashPin(pin)
                            stmt[WorkersTable.pinSha256] = sha256Hex(pin)
                            stmt[WorkersTable.pinUpdatedAt] = now
                        }
                        stmt[updatedAt] = now
                    }
                    trace.step("Worker record updated")

                    // ── Mirror display fields into the linked users row ─────
                    //
                    // For workers with `is_login_enabled = true` (those who
                    // can also sign in as a cashier/delivery user) the
                    // workers and users tables hold two parallel copies of
                    // name/phone/photo. Older code only touched
                    // WorkersTable, so the manager would rename "Abdallah"
                    // and the manager UI would show the new name (it reads
                    // workers) while:
                    //   • the cashier app showed the OLD name (its "who am
                    //     I logged in as" header reads users.name), and
                    //   • the admin web dashboard's cashier list showed the
                    //     OLD name (also reads users.name).
                    //
                    // Sync the safe-to-mirror display columns whenever the
                    // request changes them. We do NOT mirror:
                    //   • workers.role — that's a free-text job title
                    //     (Cashier, Baker, Cheif…). users.role is the
                    //     auth role (CASHIER / MANAGER / DELIVERY) and
                    //     would break login if overwritten.
                    //   • workers.description / salary fields — not on
                    //     users at all.
                    //   • workers.pin* — users have their own password.
                    //
                    // active IS mirrored: deactivating the worker should
                    // also block the linked user from logging back in.
                    val linkedUserId = currentWorker[WorkersTable.userId]
                    val needsUserSync = linkedUserId != null && (
                        request.full_name != null ||
                            request.phone != null ||
                            request.photo_url != null ||
                            request.active != null
                    )
                    if (needsUserSync) {
                        UsersTable.update({ UsersTable.id eq linkedUserId!! }) { stmt ->
                            request.full_name?.let { stmt[name] = it }
                            request.phone?.let { stmt[phone] = it }
                            request.photo_url?.let { stmt[photoUrl] = it }
                            request.active?.let { stmt[active] = it }
                            stmt[updatedAt] = now
                        }
                        trace.step("Linked user row synced", mapOf("userId" to linkedUserId.toString()))
                    }

                    // Update UNPAID salary records when salary amount or type changes
                    if (request.salary_amount != null || request.salary_type != null) {
                        val newAmount = request.salary_amount?.let { BigDecimal.valueOf(it) }
                            ?: currentWorker[WorkersTable.salaryAmount]

                        // Update all unpaid salary records with the new amount
                        SalaryPaymentsTable.update({
                            (SalaryPaymentsTable.workerId eq workerUUID) and
                            (SalaryPaymentsTable.paid eq false)
                        }) { stmt ->
                            stmt[amount] = newAmount
                        }
                        trace.step("Unpaid salary records updated", mapOf("newAmount" to newAmount.toString()))
                    }

                    // Regenerate QR code if name or role changed
                    if (needsQrRegeneration) {
                        val updatedWorker = WorkersTable.selectAll()
                            .where { WorkersTable.id eq workerUUID }
                            .first()

                        val newVersion = currentWorker[WorkersTable.qrCodeVersion] + 1
                        val qrData = qrCodeService.generateQrCodeData(
                            workerId = workerUUID.toString(),
                            name = request.full_name ?: currentWorker[WorkersTable.fullName],
                            role = request.role ?: currentWorker[WorkersTable.role],
                            version = newVersion
                        )
                        val qrDataJson = Json.encodeToString(qrData)

                        WorkersTable.update({ WorkersTable.id eq workerUUID }) {
                            it[WorkersTable.qrCodeData] = qrDataJson
                            it[WorkersTable.qrCodeVersion] = newVersion
                        }
                        trace.step("QR code regenerated", mapOf("newVersion" to newVersion.toString()))
                    }

                    WorkersTable.selectAll()
                        .where { WorkersTable.id eq workerUUID }
                        .firstOrNull()?.toWorkerDto()
                        ?: throw NoSuchElementException("Worker not found")
                }
                trace.step("Update worker completed", mapOf("workerId" to updated.id))
                call.respond(HttpStatusCode.OK, updated)
            } catch (e: Exception) {
                call.application.log.error("Error updating worker: ${e.message}", e)
                throw IllegalStateException("Failed to update worker: ${e.message}")
            }
        }

        // DELETE worker
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete worker started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))

            transaction {
                val workerUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Delete ALL related records first (order matters for FK constraints)
                AttendanceAuthLogsTable.deleteWhere { AttendanceAuthLogsTable.workerId eq workerUUID }
                AttendanceTable.deleteWhere { AttendanceTable.workerId eq workerUUID }
                OvertimeTable.deleteWhere { OvertimeTable.workerId eq workerUUID }
                SalaryPaymentsTable.deleteWhere { SalaryPaymentsTable.workerId eq workerUUID }
                trace.step("Related records deleted (attendance, overtime, salary)")

                val deleted = WorkersTable.deleteWhere {
                    (WorkersTable.id eq workerUUID) and
                    (WorkersTable.vendorId eq vendorUUID)
                }
                trace.step("Worker delete result", mapOf("deletedCount" to deleted.toString()))
                if (deleted == 0) throw NoSuchElementException("Worker not found")
            }
            trace.step("Delete worker completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // UPDATE worker PIN (attendance feature)
        post("/{id}/pin") {
            val trace = call.routeTrace()
            trace.step("Set worker PIN started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "ATTENDANCE")
            trace.step("Feature check passed", mapOf("feature" to "ATTENDANCE"))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))
            val request = call.receive<UpdatePinDto>()
            trace.step("PIN request received", mapOf("pinLength" to request.pin.length.toString()))

            require(pinService.isValidPin(request.pin)) { "PIN must be 4-6 digits" }

            transaction {
                val workerUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val updated = WorkersTable.update({
                    (WorkersTable.id eq workerUUID) and
                    (WorkersTable.vendorId eq vendorUUID)
                }) {
                    it[WorkersTable.pinHash] = pinService.hashPin(request.pin)
                    it[WorkersTable.pinSha256] = sha256Hex(request.pin)
                    it[WorkersTable.pinUpdatedAt] = now
                    it[updatedAt] = now
                }
                trace.step("PIN update result", mapOf("updatedCount" to updated.toString()))

                if (updated == 0) throw NoSuchElementException("Worker not found")
            }
            trace.step("Set worker PIN completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // VERIFY manager PIN (for cashier-initiated actions like large discounts)
        // Checks the PIN against all manager-role workers in the vendor
        post("/verify-manager-pin") {
            val trace = call.routeTrace()
            trace.step("Verify manager PIN started")
            val principal = requireRole("MANAGER", "CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            val request = call.receive<UpdatePinDto>()

            val vendorId = principal.vendorId

            // Rate limiting using vendor-level key
            val rateLimitKey = "manager-pin-$vendorId"
            if (!pinService.canAttemptPin(rateLimitKey)) {
                val remaining = pinService.getRemainingLockoutTime(rateLimitKey)
                trace.step("Rate limited", mapOf("remainingSeconds" to remaining.toString()))
                call.respond(HttpStatusCode.TooManyRequests, PinVerifyResponse(
                    success = false,
                    message = "Too many attempts. Try again in $remaining seconds."
                ))
                return@post
            }

            // Find all manager workers with PINs set
            val managers = transaction {
                WorkersTable.selectAll()
                    .where {
                        (WorkersTable.vendorId eq UUID.fromString(vendorId)) and
                        (WorkersTable.active eq true) and
                        (WorkersTable.pinHash.isNotNull())
                    }
                    .filter { row ->
                        val role = row[WorkersTable.role].lowercase()
                        role == "manager" || role == "مدير"
                    }
                    .map { row ->
                        row[WorkersTable.id].toString() to row[WorkersTable.pinHash]!!
                    }
            }

            if (managers.isEmpty()) {
                trace.step("No managers with PINs found")
                call.respond(HttpStatusCode.BadRequest, PinVerifyResponse(
                    success = false,
                    message = "No managers have PINs set up"
                ))
                return@post
            }

            // Try to verify against any manager's PIN
            val matchedManager = managers.firstOrNull { (_, hash) ->
                pinService.verifyPin(request.pin, hash)
            }

            if (matchedManager != null) {
                pinService.resetRateLimit(rateLimitKey)
                trace.step("Manager PIN verified", mapOf("managerId" to matchedManager.first))
                call.respond(HttpStatusCode.OK, PinVerifyResponse(success = true))
            } else {
                trace.step("PIN verification failed against all managers")
                call.respond(HttpStatusCode.Unauthorized, PinVerifyResponse(
                    success = false,
                    message = "Invalid PIN"
                ))
            }
        }

        // GET worker QR code as PNG image
        get("/{id}/qr-code") {
            val trace = call.routeTrace()
            trace.step("Get worker QR code started")
            val principal = requireRole("MANAGER", "CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId, "role" to principal.role))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "WORKER_QRCODE")
            trace.step("Feature check passed", mapOf("feature" to "WORKER_QRCODE"))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))

            val imageBytes = transaction {
                val workerUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val worker = WorkersTable.selectAll()
                    .where {
                        (WorkersTable.id eq workerUUID) and
                        (WorkersTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Worker not found")
                trace.step("Worker found", mapOf("workerName" to worker[WorkersTable.fullName]))

                var qrDataJson = worker[WorkersTable.qrCodeData]

                // If worker doesn't have QR code, generate it now
                if (qrDataJson == null) {
                    trace.step("No existing QR code, generating new one")
                    val qrData = qrCodeService.generateQrCodeData(
                        workerId = workerUUID.toString(),
                        name = worker[WorkersTable.fullName],
                        role = worker[WorkersTable.role],
                        version = 1
                    )
                    qrDataJson = Json.encodeToString(qrData)

                    // Save QR code data to worker
                    WorkersTable.update({ WorkersTable.id eq workerUUID }) {
                        it[qrCodeData] = qrDataJson
                        it[qrCodeVersion] = 1
                    }
                    trace.step("QR code generated and saved", mapOf("version" to "1"))
                } else {
                    trace.step("Existing QR code found")
                }

                val qrData = Json.decodeFromString<net.marllex.waselak.backend.domain.service.QrCodeData>(qrDataJson)
                qrCodeService.generateQrCodeImage(qrData, size = 500) // Larger QR code
            }
            trace.step("QR code image rendered", mapOf("imageSize" to imageBytes.size.toString()))
            trace.step("Get worker QR code completed")
            call.respondBytes(imageBytes, ContentType.Image.PNG)
        }

        // REGENERATE worker QR code
        post("/{id}/qr-code/regenerate") {
            val trace = call.routeTrace()
            trace.step("Regenerate QR code started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "WORKER_QRCODE")
            trace.step("Feature check passed", mapOf("feature" to "WORKER_QRCODE"))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Worker ID parsed", mapOf("workerId" to id))

            try {
                val response = transaction {
                    val workerUUID = UUID.fromString(id)
                    val vendorUUID = UUID.fromString(principal.vendorId)

                    val worker = WorkersTable.selectAll()
                        .where {
                            (WorkersTable.id eq workerUUID) and
                            (WorkersTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: throw NoSuchElementException("Worker not found")
                    trace.step("Worker found", mapOf("workerName" to worker[WorkersTable.fullName]))

                    val currentVersion = worker[WorkersTable.qrCodeVersion]
                    val newVersion = currentVersion + 1
                    trace.step("Version bump", mapOf("oldVersion" to currentVersion.toString(), "newVersion" to newVersion.toString()))

                    val qrData = qrCodeService.generateQrCodeData(
                        workerId = workerUUID.toString(),
                        name = worker[WorkersTable.fullName],
                        role = worker[WorkersTable.role],
                        version = newVersion
                    )
                    val qrDataJson = Json.encodeToString(qrData)

                    val updated = WorkersTable.update({ WorkersTable.id eq workerUUID }) {
                        it[qrCodeData] = qrDataJson
                        it[qrCodeVersion] = newVersion
                        it[updatedAt] = Clock.System.now()
                    }
                    trace.step("QR code update result", mapOf("updatedCount" to updated.toString()))

                    if (updated == 0) {
                        throw IllegalStateException("Failed to update worker QR code")
                    }

                    QrCodeResponse(
                        qr_code_data = qrDataJson,
                        qr_code_version = newVersion
                    )
                }
                trace.step("Regenerate QR code completed", mapOf("newVersion" to response.qr_code_version.toString()))
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.application.log.error("Error regenerating QR code: ${e.message}", e)
                throw IllegalStateException("Failed to regenerate QR code: ${e.message}")
            }
        }
    }

    // ── Salary Payments ──────────────────────────────────────────
    route("/api/v1/salary-payments") {
        // GET salary payments (filter by worker, period, paid status)
        get {
            val trace = call.routeTrace()
            trace.step("List salary payments started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "SALARY")
            trace.step("Feature check passed", mapOf("feature" to "SALARY"))
            val vendorUUID = UUID.fromString(principal.vendorId)
            val workerIdParam = call.parameters["worker_id"]
            val paidParam = call.parameters["paid"]?.toBooleanStrictOrNull()
            val periodType = call.parameters["period_type"]
            trace.step("Filters applied", mapOf(
                "workerId" to (workerIdParam ?: "null"),
                "paid" to (paidParam?.toString() ?: "null"),
                "periodType" to (periodType ?: "null")
            ))

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

                val results = query.orderBy(SalaryPaymentsTable.createdAt, SortOrder.DESC)
                    .map { row ->
                        val dto = row.toSalaryPaymentDto()
                        // Enrich with live UNPAID overtime data (paid overtime was already paid separately)
                        val overtimeEntries = OvertimeTable.selectAll().where {
                            (OvertimeTable.workerId eq row[SalaryPaymentsTable.workerId]) and
                            (OvertimeTable.vendorId eq vendorUUID) and
                            (OvertimeTable.date greaterEq row[SalaryPaymentsTable.periodStart]) and
                            (OvertimeTable.date lessEq row[SalaryPaymentsTable.periodEnd]) and
                            (OvertimeTable.paid eq false)
                        }.toList()
                        if (overtimeEntries.isNotEmpty()) {
                            val otHours = overtimeEntries.sumOf { it[OvertimeTable.hours].toDouble() }
                            val otAmount = overtimeEntries.sumOf { it[OvertimeTable.amount].toDouble() }
                            dto.copy(overtime_hours = otHours, overtime_amount = otAmount)
                        } else {
                            dto.copy(overtime_hours = 0.0, overtime_amount = 0.0)
                        }
                    }
                trace.step("Salary payments fetched", mapOf("count" to results.size.toString()))
                results
            }
            trace.step("List salary payments completed")
            call.respond(HttpStatusCode.OK, payments)
        }

        // BATCH PAY multiple salary payments at once
        patch("/batch-pay") {
            val trace = call.routeTrace()
            trace.step("Batch pay salary payments started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "SALARY")
            trace.step("Feature check passed", mapOf("feature" to "SALARY"))
            val request = call.receive<BatchPayDto>()
            trace.step("Request parsed", mapOf(
                "paymentCount" to request.payment_ids.size.toString(),
                "hasNote" to (request.note != null).toString()
            ))
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
            trace.step("Batch pay completed", mapOf("paidCount" to updated.size.toString()))
            call.respond(HttpStatusCode.OK, updated)
        }

        // MARK as paid / unpaid
        patch("/{id}/pay") {
            val trace = call.routeTrace()
            trace.step("Mark salary payment as paid started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "SALARY")
            trace.step("Feature check passed", mapOf("feature" to "SALARY"))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Payment ID parsed", mapOf("paymentId" to id))
            val request = call.receive<MarkPaidDto>()
            trace.step("Request parsed", mapOf("hasNote" to (request.note != null).toString()))

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
                trace.step("Payment marked as paid")

                SalaryPaymentsTable
                    .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { SalaryPaymentsTable.id eq paymentUUID }
                    .firstOrNull()?.toSalaryPaymentDto()
                    ?: throw NoSuchElementException("Payment not found")
            }
            trace.step("Mark as paid completed", mapOf("paymentId" to updated.id, "workerName" to (updated.worker_name ?: "null")))
            call.respond(HttpStatusCode.OK, updated)
        }

        // MARK as unpaid
        patch("/{id}/unpay") {
            val trace = call.routeTrace()
            trace.step("Mark salary payment as unpaid started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(java.util.UUID.fromString(principal.vendorId), "SALARY")
            trace.step("Feature check passed", mapOf("feature" to "SALARY"))
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Payment ID parsed", mapOf("paymentId" to id))

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
                trace.step("Payment marked as unpaid")

                SalaryPaymentsTable
                    .innerJoin(WorkersTable, { SalaryPaymentsTable.workerId }, { WorkersTable.id })
                    .selectAll()
                    .where { SalaryPaymentsTable.id eq paymentUUID }
                    .firstOrNull()?.toSalaryPaymentDto()
                    ?: throw NoSuchElementException("Payment not found")
            }
            trace.step("Mark as unpaid completed", mapOf("paymentId" to updated.id, "workerName" to (updated.worker_name ?: "null")))
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

// ─── Mappers ─────────────────────────────────────────────────────

private fun ResultRow.toWorkerDto(host: String = "localhost:8080", scheme: String = "http") = WorkerDto(
    id = this[WorkersTable.id].toString(),
    vendor_id = this[WorkersTable.vendorId].toString(),
    user_id = this[WorkersTable.userId]?.toString(),
    worker_id = this[WorkersTable.workerId],
    full_name = this[WorkersTable.fullName],
    phone = this[WorkersTable.phone],
    description = this[WorkersTable.description],
    photo_url = rewriteUploadUrl(this[WorkersTable.photoUrl], host, scheme),
    role = this[WorkersTable.role],
    salary_type = this[WorkersTable.salaryType],
    salary_amount = this[WorkersTable.salaryAmount].toDouble(),
    active = this[WorkersTable.active],
    is_login_enabled = this[WorkersTable.userId] != null,
    has_pin = this[WorkersTable.pinHash] != null,
    pin_sha256 = this[WorkersTable.pinSha256],
    qr_code_version = this[WorkersTable.qrCodeVersion],
    pin_updated_at = this[WorkersTable.pinUpdatedAt]?.toEpochMilliseconds(),
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
    overtime_hours = this[SalaryPaymentsTable.overtimeHours].toDouble(),
    overtime_amount = this[SalaryPaymentsTable.overtimeAmount].toDouble(),
    paid = this[SalaryPaymentsTable.paid],
    paid_at = this[SalaryPaymentsTable.paidAt]?.toEpochMilliseconds(),
    paid_by = this[SalaryPaymentsTable.paidBy]?.toString(),
    note = this[SalaryPaymentsTable.note],
    created_at = this[SalaryPaymentsTable.createdAt].toEpochMilliseconds(),
)
