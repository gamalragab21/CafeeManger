package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.data.database.UsersTable
import net.marllex.cafeemanger.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

// ─── Hardcoded admin password ────────────────────────────────────
private const val ADMIN_PASSWORD = "123456"

// ─── DTOs ────────────────────────────────────────────────────────
@Serializable
data class AdminVendorResponse(
    val id: String,
    val name: String,
    val logo_url: String? = null,
    val address: String,
    val contact_phone: String,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double = 0.0,
    val store_type: String? = null,
    val enable_tables: Boolean = true,
    val enable_dine_in: Boolean = true,
    val enable_delivery: Boolean = true,
    val digital_menu_url: String? = null,
    val users_count: Int = 0,
    val created_at: Long,
    val updated_at: Long? = null,
)

@Serializable
data class AdminUpdateVendorRequest(
    val name: String? = null,
    val logo_url: String? = null,
    val address: String? = null,
    val contact_phone: String? = null,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double? = null,
    val store_type: String? = null,
    val enable_tables: Boolean? = null,
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val digital_menu_url: String? = null,
)

// ─── Helper: validate admin password from header ─────────────────
private suspend fun RoutingContext.requireAdminPassword(): Boolean {
    val password = call.request.header("X-Admin-Password")
    if (password != ADMIN_PASSWORD) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid admin password"))
        return false
    }
    return true
}

// ─── Helper: map a DB row to AdminVendorResponse ─────────────────
private fun mapVendorRow(row: org.jetbrains.exposed.sql.ResultRow, usersCount: Int = 0) =
    AdminVendorResponse(
        id = row[VendorsTable.id].toString(),
        name = row[VendorsTable.name],
        logo_url = row[VendorsTable.logoUrl],
        address = row[VendorsTable.address],
        contact_phone = row[VendorsTable.contactPhone],
        wallet_phone = row[VendorsTable.walletPhone],
        default_delivery_fee = row[VendorsTable.defaultDeliveryFee].toDouble(),
        store_type = row[VendorsTable.storeType],
        enable_tables = row[VendorsTable.enableTables],
        enable_dine_in = row[VendorsTable.enableDineIn],
        enable_delivery = row[VendorsTable.enableDelivery],
        digital_menu_url = row[VendorsTable.digitalMenuUrl],
        users_count = usersCount,
        created_at = row[VendorsTable.createdAt].toEpochMilliseconds(),
        updated_at = row[VendorsTable.updatedAt].toEpochMilliseconds(),
    )

// ─── Routes ──────────────────────────────────────────────────────
fun Route.adminRoutes() {
    route("/api/v1/admin") {

        // GET /api/v1/admin/vendors — list all stores
        get("/vendors") {
            if (!requireAdminPassword()) return@get

            val vendors = transaction {
                VendorsTable.selectAll().map { row ->
                    val vendorId = row[VendorsTable.id]
                    val usersCount = UsersTable.selectAll()
                        .where { UsersTable.vendorId eq vendorId }
                        .count().toInt()
                    mapVendorRow(row, usersCount)
                }
            }

            call.respond(HttpStatusCode.OK, vendors)
        }

        // GET /api/v1/admin/vendors/{id} — get single store by ID
        get("/vendors/{id}") {
            if (!requireAdminPassword()) return@get

            val vendorId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }

            val vendor = transaction {
                val row = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorUuid }
                    .firstOrNull() ?: return@transaction null

                val usersCount = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUuid }
                    .count().toInt()

                mapVendorRow(row, usersCount)
            }

            if (vendor == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                call.respond(HttpStatusCode.OK, vendor)
            }
        }

        // PUT /api/v1/admin/vendors/{id} — update any store by ID
        put("/vendors/{id}") {
            if (!requireAdminPassword()) return@put

            val vendorId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }

            val request = call.receive<AdminUpdateVendorRequest>()

            val updated = transaction {
                // Check vendor exists
                val exists = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorUuid }
                    .firstOrNull() != null
                if (!exists) return@transaction null

                VendorsTable.update({ VendorsTable.id eq vendorUuid }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.logo_url?.let { stmt[logoUrl] = it }
                    request.address?.let { stmt[address] = it }
                    request.contact_phone?.let { stmt[contactPhone] = it }
                    request.wallet_phone?.let { stmt[walletPhone] = it }
                    request.default_delivery_fee?.let { stmt[defaultDeliveryFee] = java.math.BigDecimal.valueOf(it) }
                    request.store_type?.let { stmt[storeType] = it }
                    request.enable_tables?.let { stmt[enableTables] = it }
                    request.enable_dine_in?.let { stmt[enableDineIn] = it }
                    request.enable_delivery?.let { stmt[enableDelivery] = it }
                    request.digital_menu_url?.let { stmt[digitalMenuUrl] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                val row = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorUuid }
                    .first()

                val usersCount = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUuid }
                    .count().toInt()

                mapVendorRow(row, usersCount)
            }

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                call.respond(HttpStatusCode.OK, updated)
            }
        }
    }
}
