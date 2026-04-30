package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.config.JwtConfig
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.domain.service.PinService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.java.KoinJavaComponent.inject
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

/**
 * "Manager override PIN" routes. Two separate concerns live here:
 *
 *  - `POST /api/v1/users/me/override-pin`     — a manager sets/changes their own PIN
 *    (requires re-entering their password so a stolen unlocked phone can't swap it).
 *
 *  - `POST /api/v1/auth/verify-override-pin`  — a cashier at the POS sends a PIN and
 *    receives a short-lived token that authorises the next discount. Rate-limited per
 *    vendor to kill brute-force.
 *
 *  - `POST /api/v1/users/{id}/reset-override-pin` — admin/owner nukes another manager's
 *    PIN. Doesn't reveal the PIN — just clears the hash so the manager sets a new one.
 *
 * Plus one request body type for each, all scoped to the vendor of the caller.
 */

@Serializable
private data class SetOverridePinRequest(val currentPassword: String, val pin: String)

@Serializable
private data class SetOverridePinResponse(val overridePinSet: Boolean)

@Serializable
private data class VerifyOverridePinRequest(val pin: String)

@Serializable
private data class VerifyOverridePinResponse(
    val token: String,
    val managerId: String,
    val managerName: String,
    val expiresInSeconds: Int,
)

@Serializable
private data class ResetOverridePinResponse(val overridePinSet: Boolean)

/**
 * Routes for the manager-owned override PIN. Mount under `authenticate("auth-jwt")`
 * so the suspension interceptor fires and the caller is always authenticated.
 */
internal fun Route.overridePinRoutes() {
    val pinService by inject<PinService>(PinService::class.java)
    val jwtConfig by inject<JwtConfig>(JwtConfig::class.java)

    // 1. Manager sets or changes their OWN PIN. Password-authed so an unlocked-device
    //    attacker can't overwrite it without also knowing the manager's password.
    post("/api/v1/users/me/override-pin") {
        val principal = currentUser()
        val body = call.receive<SetOverridePinRequest>()

        if (!pinService.isValidPin(body.pin)) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "PIN must be 4-6 digits"))
            return@post
        }
        // Only managers carry a PIN — sales reps, cashiers, delivery don't need one.
        if (principal.role.uppercase() != "MANAGER") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only managers can set an override PIN"))
            return@post
        }

        val userUuid = UUID.fromString(principal.userId)
        val passwordOk = transaction {
            UsersTable.selectAll()
                .where { UsersTable.id eq userUuid }
                .firstOrNull()
                ?.let { BCrypt.checkpw(body.currentPassword, it[UsersTable.passwordHash]) }
                ?: false
        }
        if (!passwordOk) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Wrong password"))
            return@post
        }

        val hash = pinService.hashPin(body.pin)
        transaction {
            UsersTable.update({ UsersTable.id eq userUuid }) {
                it[overridePinHash] = hash
                it[overridePinSetAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }
        }
        call.respond(HttpStatusCode.OK, SetOverridePinResponse(overridePinSet = true))
    }

    // 2. Cashier/terminal verifies a PIN and receives a short-lived token. Scoped to
    //    the caller's vendor — the PIN must belong to a MANAGER in the same vendor.
    post("/api/v1/auth/verify-override-pin") {
        val principal = currentUser()
        val body = call.receive<VerifyOverridePinRequest>()
        val pin = body.pin.trim()
        if (!pinService.isValidPin(pin)) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "PIN must be 4-6 digits"))
            return@post
        }

        // Look up every active manager of the vendor with a PIN set, and test the
        // supplied PIN against their bcrypt hash. We can't do this in SQL because
        // bcrypt hashes include their own salts — so at most a few hashes per vendor.
        val vendorUuid = UUID.fromString(principal.vendorId)
        val match = transaction {
            UsersTable.selectAll()
                .where {
                    (UsersTable.vendorId eq vendorUuid) and
                        (UsersTable.role eq "MANAGER") and
                        (UsersTable.active eq true) and
                        (UsersTable.overridePinHash.isNotNull())
                }
                .mapNotNull { row ->
                    val hash = row[UsersTable.overridePinHash] ?: return@mapNotNull null
                    if (pinService.verifyPin(pin, hash)) {
                        row[UsersTable.id].value.toString() to row[UsersTable.name]
                    } else null
                }
                .firstOrNull()
        }

        if (match == null) {
            // Don't leak whether ANY manager PIN exists; a single generic 403 is safer
            // than a "no manager has this PIN set" leak.
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid PIN"))
            return@post
        }

        val (managerId, managerName) = match
        val token = jwtConfig.generateOverrideToken(managerId, principal.vendorId)
        call.respond(HttpStatusCode.OK, VerifyOverridePinResponse(
            token = token,
            managerId = managerId,
            managerName = managerName,
            expiresInSeconds = 90,
        ))
    }

    // 3. Admin/owner resets another manager's PIN. Never reveals the PIN itself —
    //    just wipes the hash so the targeted manager has to set a new one.
    post("/api/v1/users/{id}/reset-override-pin") {
        val principal = currentUser()
        if (principal.role.uppercase() !in setOf("MANAGER", "OWNER")) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
            return@post
        }
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
        val targetUuid = try { UUID.fromString(id) } catch (_: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))
        }
        val vendorUuid = UUID.fromString(principal.vendorId)

        val updated = transaction {
            UsersTable.update({
                (UsersTable.id eq targetUuid) and (UsersTable.vendorId eq vendorUuid)
            }) {
                it[overridePinHash] = null
                it[overridePinSetAt] = null
                it[updatedAt] = Clock.System.now()
            }
        }
        if (updated == 0) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found in this vendor"))
            return@post
        }
        call.respond(HttpStatusCode.OK, ResetOverridePinResponse(overridePinSet = false))
    }
}

/** Small infix helper since we use `and` for where clauses above. */
private infix fun org.jetbrains.exposed.sql.Op<Boolean>.and(
    other: org.jetbrains.exposed.sql.Op<Boolean>
): org.jetbrains.exposed.sql.Op<Boolean> = org.jetbrains.exposed.sql.AndOp(listOf(this, other))
