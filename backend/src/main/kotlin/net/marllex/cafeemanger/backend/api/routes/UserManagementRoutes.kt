package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.data.database.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
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
    val active: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null
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
    val role: String? = null,
    val active: Boolean? = null,
    val password: String? = null
)

fun Route.userManagementRoutes() {
    route("/api/v1/users") {
        get {
            val principal = requireRole("MANAGER","CASHIER")
            val role = call.parameters["role"]

            val users = transaction {
                var query = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq UUID.fromString(principal.vendorId) }

                role?.let { query = query.andWhere { UsersTable.role eq it } }

                query.orderBy(UsersTable.name)
                    .map { it.toUserDto() }
            }
            call.respond(HttpStatusCode.OK, users)
        }

        get("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val user = transaction {
                UsersTable.selectAll()
                    .where {
                        (UsersTable.id eq UUID.fromString(id)) and
                        (UsersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toUserDto()
                    ?: throw NoSuchElementException("User not found")
            }
            call.respond(HttpStatusCode.OK, user)
        }

        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateUserDto>()
            require(request.name.isNotBlank()) { "Name is required" }
            require(request.phone.isNotBlank()) { "Phone is required" }
            require(request.password.length >= 6) { "Password must be at least 6 characters" }

            val validRoles = listOf("MANAGER", "CASHIER", "DELIVERY")
            require(request.role in validRoles) {
                "Invalid role. Must be one of: ${validRoles.joinToString()}"
            }

            val user = transaction {
                // Check for duplicate phone within vendor
                val existing = UsersTable.selectAll()
                    .where {
                        (UsersTable.vendorId eq UUID.fromString(principal.vendorId)) and
                        (UsersTable.phone eq request.phone)
                    }.firstOrNull()
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
                UsersTable.selectAll().where { UsersTable.id eq id }.first().toUserDto()
            }
            call.respond(HttpStatusCode.Created, user)
        }

        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateUserDto>()

            request.role?.let { role ->
                val validRoles = listOf("MANAGER", "CASHIER", "DELIVERY")
                require(role in validRoles) {
                    "Invalid role. Must be one of: ${validRoles.joinToString()}"
                }
            }

            val updated = transaction {
                // Prevent manager from deactivating themselves
                if (request.active == false && id == principal.userId) {
                    throw IllegalStateException("Cannot deactivate your own account")
                }

                UsersTable.update({
                    (UsersTable.id eq UUID.fromString(id)) and
                    (UsersTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.phone?.let { stmt[phone] = it }
                    request.email?.let { stmt[email] = it }
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
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            require(id != principal.userId) { "Cannot delete your own account" }

            transaction {
                val deleted = UsersTable.deleteWhere {
                    (UsersTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("User not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toUserDto() = UserDto(
    id = this[UsersTable.id].toString(),
    vendor_id = this[UsersTable.vendorId].toString(),
    role = this[UsersTable.role],
    name = this[UsersTable.name],
    phone = this[UsersTable.phone],
    email = this[UsersTable.email],
    active = this[UsersTable.active],
    created_at = this[UsersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[UsersTable.updatedAt].toEpochMilliseconds()
)
