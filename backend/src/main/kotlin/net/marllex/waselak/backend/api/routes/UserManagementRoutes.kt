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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

@Serializable
data class UserDto(
    val id: String,
    val vendor_id: String,
    val role: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val photo_url: String? = null,
    val active: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null,
    /**
     * UUID of the [WorkersTable] row that's linked to this user, if any. Set when:
     *  - The user was created via the auto-link create-user flow (MANAGER/CASHIER/DELIVERY)
     *  - An existing user was paired up by the startup backfill
     * Lets the Permissions & Roles UI show "Salary: X · open salary screen" inline,
     * and the Workers screen show "Has login: ✓" on the corresponding row.
     */
    val linked_worker_id: String? = null,
    /** Worker's salary amount, surfaced for inline display in the Users list. */
    val linked_worker_salary: Double? = null,
)

@Serializable
data class CreateUserDto(
    val role: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val password: String
)

@Serializable
data class UpdateUserDto(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val photo_url: String? = null,
    val role: String? = null,
    val active: Boolean? = null,
    val password: String? = null
)

fun Route.userManagementRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/users") {
        get {
            val trace = call.routeTrace()
            trace.step("List users started")
            val principal = requireRole("MANAGER","CASHIER")
            val role = call.parameters["role"]
            trace.step("Querying users", mapOf("vendorId" to principal.vendorId, "roleFilter" to (role ?: "null")))

            val users = transaction {
                var query = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq UUID.fromString(principal.vendorId) }

                role?.let { query = query.andWhere { UsersTable.role eq it } }

                val userDtos = query.orderBy(UsersTable.name)
                    .map { it.toUserDto() }

                enrichUsersWithWorkerPhotos(userDtos)
            }
            trace.step("List users result", mapOf("count" to users.size.toString()))
            trace.step("List users completed")
            call.respond(HttpStatusCode.OK, users)
        }

        // Self-update profile (any authenticated user)
        put("/me") {
            val trace = call.routeTrace()
            trace.step("Self-update profile started")
            val principal = currentUser()
            val request = call.receive<UpdateUserDto>()
            trace.step("Updating self profile", mapOf(
                "userId" to principal.userId,
                "name" to (request.name ?: "null"),
                "phone" to (request.phone ?: "null"),
                "email" to (request.email ?: "null"),
                "photoChanged" to (request.photo_url != null).toString(),
                "passwordChanged" to (request.password != null).toString()
            ))

            val updated = transaction {
                // Delete old photo file if being replaced
                if (request.photo_url != null) {
                    val oldPhotoUrl = UsersTable.selectAll()
                        .where { UsersTable.id eq UUID.fromString(principal.userId) }
                        .firstOrNull()?.get(UsersTable.photoUrl)
                    if (oldPhotoUrl != null && oldPhotoUrl != request.photo_url) {
                        deleteUploadedFile(oldPhotoUrl)
                    }
                }
                UsersTable.update({
                    UsersTable.id eq UUID.fromString(principal.userId)
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.phone?.let { stmt[phone] = it }
                    request.email?.let { stmt[email] = it }
                    request.photo_url?.let { stmt[photoUrl] = it }
                    // Don't allow self role/active change via /me
                    request.password?.let { pwd ->
                        stmt[passwordHash] = BCrypt.hashpw(pwd, BCrypt.gensalt())
                    }
                    stmt[updatedAt] = Clock.System.now()
                }
                UsersTable.selectAll().where { UsersTable.id eq UUID.fromString(principal.userId) }
                    .firstOrNull()?.toUserDto() ?: throw NoSuchElementException("User not found")
            }
            trace.step("Self-update profile result", mapOf("userId" to updated.id))
            trace.step("Self-update profile completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get user by ID started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Looking up user", mapOf("userId" to id, "vendorId" to principal.vendorId))

            val user = transaction {
                val userDto = UsersTable.selectAll()
                    .where {
                        (UsersTable.id eq UUID.fromString(id)) and
                        (UsersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toUserDto()
                    ?: throw NoSuchElementException("User not found")

                enrichUsersWithWorkerPhotos(listOf(userDto)).first()
            }
            trace.step("Get user result", mapOf("userId" to user.id, "name" to user.name, "role" to user.role))
            trace.step("Get user by ID completed")
            call.respond(HttpStatusCode.OK, user)
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create user started")
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateUserDto>()
            trace.step("Creating user", mapOf(
                "name" to request.name,
                "role" to request.role,
                "phone" to request.phone,
                "email" to (request.email ?: "null"),
                "password" to "***"
            ))
            require(request.name.isNotBlank()) { "Name is required" }
            require(request.phone.isNotBlank()) { "Phone is required" }
            require(request.password.length >= 6) { "Password must be at least 6 characters" }

            val validRoles = listOf("MANAGER", "CASHIER", "DELIVERY", "KITCHEN")
            require(request.role in validRoles) {
                "Invalid role. Must be one of: ${validRoles.joinToString()}"
            }

            // ─── Plan limit check ─────────────────────────
            trace.step("Checking plan limits", mapOf("vendorId" to principal.vendorId, "role" to request.role))
            planService.checkUserCreation(UUID.fromString(principal.vendorId), request.role)
            trace.step("Plan limits check passed")

            val user = transaction {
                // Check for duplicate phone globally (login queries by phone across all vendors)
                val existing = UsersTable.selectAll()
                    .where { UsersTable.phone eq request.phone }
                    .firstOrNull()
                if (existing != null) throw IllegalStateException("A user with this phone already exists")

                val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

                val id = UsersTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[role] = request.role
                    it[name] = request.name
                    it[phone] = request.phone
                    it[email] = request.email
                    it[UsersTable.passwordHash] = passwordHash
                    it[active] = true
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Auto-create a paired Worker record so the new user can be paid + clock in.
                // Without this, managers/cashiers/delivery had login-only "ghost" accounts:
                // they could log in but the Salaries / Attendance screens didn't know they
                // existed. The Worker is created with salary 0 (owner sets it later in the
                // Salaries screen) and links back via WorkersTable.userId.
                //
                // KITCHEN role is intentionally skipped — kitchen staff already get their
                // Worker records via the Workers screen and rarely have logins.
                if (request.role in listOf("MANAGER", "CASHIER", "DELIVERY")) {
                    val vendorUUID = UUID.fromString(principal.vendorId)
                    val alreadyLinked = WorkersTable.selectAll()
                        .where { WorkersTable.userId eq id }
                        .any()
                    if (!alreadyLinked) {
                        val workerCount = WorkersTable.selectAll()
                            .where { WorkersTable.vendorId eq vendorUUID }
                            .count()
                        WorkersTable.insert {
                            it[WorkersTable.vendorId] = vendorUUID
                            it[WorkersTable.userId] = id
                            it[workerId] = "WRK-${(workerCount + 1).toString().padStart(3, '0')}"
                            it[fullName] = request.name
                            it[WorkersTable.phone] = request.phone
                            it[WorkersTable.role] = request.role
                            it[salaryType] = "MONTHLY"
                            // salaryAmount defaults to ZERO — owner sets it via the Salaries
                            // screen. Better than guessing.
                            it[WorkersTable.active] = true
                            it[WorkersTable.createdAt] = Clock.System.now()
                            it[WorkersTable.updatedAt] = Clock.System.now()
                        }
                    }
                }

                UsersTable.selectAll().where { UsersTable.id eq id }.first().toUserDto()
            }
            trace.step("Create user result", mapOf("userId" to user.id, "name" to user.name, "role" to user.role))
            trace.step("Create user completed")
            call.respond(HttpStatusCode.Created, user)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update user started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateUserDto>()
            trace.step("Updating user", mapOf(
                "userId" to id,
                "name" to (request.name ?: "null"),
                "phone" to (request.phone ?: "null"),
                "email" to (request.email ?: "null"),
                "role" to (request.role ?: "null"),
                "active" to (request.active?.toString() ?: "null"),
                "photoChanged" to (request.photo_url != null).toString(),
                "passwordChanged" to (request.password != null).toString()
            ))

            request.role?.let { role ->
                val validRoles = listOf("MANAGER", "CASHIER", "DELIVERY", "KITCHEN")
                require(role in validRoles) {
                    "Invalid role. Must be one of: ${validRoles.joinToString()}"
                }
            }

            val updated = transaction {
                // Prevent manager from deactivating themselves
                if (request.active == false && id == principal.userId) {
                    throw IllegalStateException("Cannot deactivate your own account")
                }

                // Delete old photo file if being replaced
                if (request.photo_url != null) {
                    val oldPhotoUrl = UsersTable.selectAll()
                        .where { UsersTable.id eq UUID.fromString(id) }
                        .firstOrNull()?.get(UsersTable.photoUrl)
                    if (oldPhotoUrl != null && oldPhotoUrl != request.photo_url) {
                        deleteUploadedFile(oldPhotoUrl)
                    }
                }

                trace.step("Executing user update in database", mapOf("userId" to id))
                UsersTable.update({
                    (UsersTable.id eq UUID.fromString(id)) and
                    (UsersTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.phone?.let { stmt[phone] = it }
                    request.email?.let { stmt[email] = it }
                    request.photo_url?.let { stmt[photoUrl] = it }
                    request.role?.let { stmt[role] = it }
                    request.active?.let { stmt[active] = it }
                    request.password?.let { pwd ->
                        stmt[passwordHash] = BCrypt.hashpw(pwd, BCrypt.gensalt())
                    }
                    stmt[updatedAt] = Clock.System.now()
                }

                UsersTable.selectAll().where { UsersTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toUserDto() ?: throw NoSuchElementException("User not found")
            }
            trace.step("Update user result", mapOf("userId" to updated.id, "name" to updated.name, "role" to updated.role))
            trace.step("Update user completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete user started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Deleting user", mapOf("userId" to id, "vendorId" to principal.vendorId))

            require(id != principal.userId) { "Cannot delete your own account" }

            transaction {
                val userUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Verify user exists and belongs to this vendor
                val userExists = UsersTable.selectAll().where {
                    (UsersTable.id eq userUUID) and (UsersTable.vendorId eq vendorUUID)
                }.count() > 0
                if (!userExists) throw NoSuchElementException("User not found")

                // Check if user has non-nullable FK references (orders as cashier, attendance as recorder)
                val hasOrders = OrdersTable.selectAll().where { OrdersTable.cashierId eq userUUID }.count() > 0
                val hasAttendanceRecords = AttendanceTable.selectAll().where { AttendanceTable.recordedBy eq userUUID }.count() > 0

                if (hasOrders || hasAttendanceRecords) {
                    trace.step("Soft deleting user (has FK references)", mapOf(
                        "userId" to id,
                        "hasOrders" to hasOrders.toString(),
                        "hasAttendanceRecords" to hasAttendanceRecords.toString()
                    ))
                    // Soft delete: deactivate instead of hard delete to preserve data integrity
                    UsersTable.update({ (UsersTable.id eq userUUID) and (UsersTable.vendorId eq vendorUUID) }) {
                        it[active] = false
                        it[updatedAt] = Clock.System.now()
                    }

                    // Unlink any worker connected to this user
                    WorkersTable.update({ WorkersTable.userId eq userUUID }) {
                        it[userId] = null
                    }

                    // Invalidate sessions
                    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userUUID }
                } else {
                    trace.step("Hard deleting user (no FK references)", mapOf("userId" to id))
                    // Safe to hard delete - clean up ALL dependent records first
                    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userUUID }
                    ActivityLogsTable.deleteWhere { ActivityLogsTable.userId eq userUUID }
                    AnnouncementReadsTable.deleteWhere { AnnouncementReadsTable.userId eq userUUID }

                    // Delete announcements sent by this user
                    val announcementIds = AnnouncementsTable.selectAll()
                        .where { AnnouncementsTable.senderId eq userUUID }
                        .map { it[AnnouncementsTable.id] }
                    if (announcementIds.isNotEmpty()) {
                        AnnouncementReadsTable.deleteWhere { AnnouncementReadsTable.announcementId inList announcementIds }
                        AnnouncementsTable.deleteWhere { AnnouncementsTable.senderId eq userUUID }
                    }
                    AnnouncementsTable.update({ AnnouncementsTable.targetUserId eq userUUID }) {
                        it[targetUserId] = null
                    }

                    AttendanceAuthLogsTable.deleteWhere { AttendanceAuthLogsTable.cashierId eq userUUID }

                    OrdersTable.update({ OrdersTable.deliveryUserId eq userUUID }) {
                        it[deliveryUserId] = null
                    }
                    OrdersTable.update({ OrdersTable.paymentConfirmedBy eq userUUID }) {
                        it[paymentConfirmedBy] = null
                    }

                    SalaryPaymentsTable.update({ SalaryPaymentsTable.paidBy eq userUUID }) {
                        it[paidBy] = null
                    }

                    WorkersTable.update({ WorkersTable.userId eq userUUID }) {
                        it[userId] = null
                    }

                    UsersTable.deleteWhere {
                        (UsersTable.id eq userUUID) and (UsersTable.vendorId eq vendorUUID)
                    }
                }
            }
            trace.step("Delete user completed", mapOf("userId" to id))
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toUserDto(host: String = "localhost:8080", scheme: String = "http") = UserDto(
    id = this[UsersTable.id].toString(),
    vendor_id = this[UsersTable.vendorId].toString(),
    role = this[UsersTable.role],
    name = this[UsersTable.name],
    phone = this[UsersTable.phone],
    email = this[UsersTable.email],
    photo_url = rewriteUploadUrl(this[UsersTable.photoUrl], host, scheme),
    active = this[UsersTable.active],
    created_at = this[UsersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[UsersTable.updatedAt].toEpochMilliseconds()
)

/**
 * Hydrate a list of [UserDto] with their linked worker's id, salary, and (if missing)
 * photo. Runs three batched queries instead of one-per-user; safe to call inside any
 * `transaction {}` block.
 *
 * The cross-link surfaces in two places:
 *  - The Permissions & Roles UI uses `linked_worker_id` + `linked_worker_salary` to
 *    show inline "Salary: X" and a deep-link to the salary screen.
 *  - The photo enrichment fallback (kept from the prior implementation) lets a
 *    manager who only set their photo on the Worker side still appear with a face
 *    in the Users list.
 */
private fun enrichUsersWithWorkerPhotos(users: List<UserDto>): List<UserDto> {
    if (users.isEmpty()) return users
    val userIds = users.map { it.id }.toSet()

    // Single pass over the workers belonging to these users — collects link, salary,
    // and photo together so we don't hit the DB three times.
    data class WorkerLink(val workerId: String, val salary: Double, val photo: String?)
    val byUser: Map<String, WorkerLink> = WorkersTable.selectAll()
        .where { WorkersTable.userId.isNotNull() }
        .mapNotNull { row ->
            val userIdStr = row[WorkersTable.userId]?.value?.toString() ?: return@mapNotNull null
            if (userIdStr !in userIds) return@mapNotNull null
            userIdStr to WorkerLink(
                workerId = row[WorkersTable.id].value.toString(),
                salary = row[WorkersTable.salaryAmount].toDouble(),
                photo = row[WorkersTable.photoUrl],
            )
        }
        .toMap()

    return users.map { dto ->
        val link = byUser[dto.id]
        dto.copy(
            photo_url = dto.photo_url ?: link?.photo,
            linked_worker_id = link?.workerId,
            linked_worker_salary = link?.salary,
        )
    }
}
