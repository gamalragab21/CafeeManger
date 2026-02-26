package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

@Serializable
data class VendorResponse(
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
    val enable_takeaway: Boolean = true,
    val enable_offline_mode: Boolean = false,
    val digital_menu_url: String? = null,
    val created_at: Long,
    val updated_at: Long? = null
)

@Serializable
data class UpdateVendorRequest(
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
    val enable_takeaway: Boolean? = null,
    val enable_offline_mode: Boolean? = null,
    val digital_menu_url: String? = null
)

fun Route.vendorRoutes() {
    route("/api/v1/vendors") {
        get("/me") {
            val principal = currentUser()
            val vendor = transaction {
                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .firstOrNull() ?: throw NoSuchElementException("Vendor not found")
            }

            call.respond(HttpStatusCode.OK, VendorResponse(
                id = vendor[VendorsTable.id].toString(),
                name = vendor[VendorsTable.name],
                logo_url = vendor[VendorsTable.logoUrl],
                address = vendor[VendorsTable.address],
                contact_phone = vendor[VendorsTable.contactPhone],
                wallet_phone = vendor[VendorsTable.walletPhone],
                default_delivery_fee = vendor[VendorsTable.defaultDeliveryFee].toDouble(),
                store_type = vendor[VendorsTable.storeType],
                enable_tables = vendor[VendorsTable.enableTables],
                enable_dine_in = vendor[VendorsTable.enableDineIn],
                enable_delivery = vendor[VendorsTable.enableDelivery],
                enable_takeaway = vendor[VendorsTable.enableTakeaway],
                enable_offline_mode = vendor[VendorsTable.enableOfflineMode],
                digital_menu_url = vendor[VendorsTable.digitalMenuUrl],
                created_at = vendor[VendorsTable.createdAt].toEpochMilliseconds(),
                updated_at = vendor[VendorsTable.updatedAt].toEpochMilliseconds()
            ))
        }

        put("/me") {
            val principal = requireRole("MANAGER")
            val request = call.receive<UpdateVendorRequest>()

            val updated = transaction {
                VendorsTable.update({ VendorsTable.id eq UUID.fromString(principal.vendorId) }) { stmt ->
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
                    request.enable_takeaway?.let { stmt[enableTakeaway] = it }
                    request.enable_offline_mode?.let { stmt[enableOfflineMode] = it }
                    request.digital_menu_url?.let { stmt[digitalMenuUrl] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .first()
            }

            call.respond(HttpStatusCode.OK, VendorResponse(
                id = updated[VendorsTable.id].toString(),
                name = updated[VendorsTable.name],
                logo_url = updated[VendorsTable.logoUrl],
                address = updated[VendorsTable.address],
                contact_phone = updated[VendorsTable.contactPhone],
                wallet_phone = updated[VendorsTable.walletPhone],
                default_delivery_fee = updated[VendorsTable.defaultDeliveryFee].toDouble(),
                store_type = updated[VendorsTable.storeType],
                enable_tables = updated[VendorsTable.enableTables],
                enable_dine_in = updated[VendorsTable.enableDineIn],
                enable_delivery = updated[VendorsTable.enableDelivery],
                enable_takeaway = updated[VendorsTable.enableTakeaway],
                enable_offline_mode = updated[VendorsTable.enableOfflineMode],
                digital_menu_url = updated[VendorsTable.digitalMenuUrl],
                created_at = updated[VendorsTable.createdAt].toEpochMilliseconds(),
                updated_at = updated[VendorsTable.updatedAt].toEpochMilliseconds()
            ))
        }
    }
}

// ─── Helper: map feature flags row to response ───────────────────
@Serializable
data class FeatureFlagsClientResponse(
    val vendor_id: String,
    // Manager App
    val manager_dashboard: Boolean,
    val manager_orders: Boolean,
    val manager_menu: Boolean,
    val manager_staff: Boolean,
    val manager_attendance: Boolean,
    val manager_salary: Boolean,
    val manager_inventory: Boolean,
    val manager_tables: Boolean,
    val manager_reports: Boolean,
    val manager_chatbot: Boolean,
    val manager_users: Boolean,
    val manager_settings: Boolean,
    // Cashier App
    val cashier_orders: Boolean,
    val cashier_attendance: Boolean,
    val cashier_dine_in: Boolean,
    val cashier_takeaway: Boolean,
    val cashier_delivery: Boolean,
    // Delivery App
    val delivery_orders: Boolean,
    val delivery_navigation: Boolean,
    val delivery_earnings: Boolean,
    // Global
    val qr_code_attendance: Boolean,
    val pin_attendance: Boolean,
    val multi_language: Boolean,
)