package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
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
    val enable_takeaway: Boolean = true,
    val enable_in_store: Boolean = false,
    val enable_pickup_later: Boolean = false,
    val business_type: String = "RESTAURANT",
    val tax_enabled: Boolean = false,
    val default_tax_percent: Double = 0.0,
    val stock_mode: String = "NONE",
    val is_suspended: Boolean = false,
    val suspension_reason: String? = null,
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
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    val business_type: String? = null,
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    val is_suspended: Boolean? = null,
    val suspension_reason: String? = null,
    val digital_menu_url: String? = null,
)

@Serializable
data class AdminCreateVendorRequest(
    val vendor_name: String,
    val vendor_address: String,
    val vendor_phone: String,
    val wallet_phone: String? = null,
    val default_delivery_fee: Double = 0.0,
    val store_type: String? = null,
    val logo_url: String? = null,
    val digital_menu_url: String? = null,
    // Channel flags — nullable = auto-configure from business_type
    val business_type: String = "RESTAURANT",
    val enable_tables: Boolean? = null,
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    // Tax & stock
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    // Manager user
    val manager_name: String,
    val manager_phone: String,
    val manager_email: String? = null,
    val manager_password: String,
)

@Serializable
data class AdminCreateVendorResponse(
    val vendor: AdminVendorResponse,
    val manager_id: String,
)

@Serializable
data class FeatureFlagsResponse(
    val vendor_id: String,
    // ═══ PUBLIC FEATURES ═══
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
    
    // ═══ INTERNAL CONFIGURATION (Admin only) ═══
    // System Limits
    val max_users_per_vendor: Int,
    val max_workers_per_vendor: Int,
    val max_orders_per_day: Int,
    val max_items_per_order: Int,
    // Storage & Data
    val max_storage_images_mb: Int,
    val data_retention_days: Int,
    // API Rate Limiting
    val api_rate_limit_per_minute: Int,
    val enable_api_rate_limit: Boolean,
    // Advanced Features
    val enable_advanced_analytics: Boolean,
    val enable_custom_reports: Boolean,
    val enable_data_export: Boolean,
    val enable_bulk_operations: Boolean,
    // Integration Features
    val enable_webhooks: Boolean,
    val enable_api_access: Boolean,
    val enable_third_party_integrations: Boolean,
    // Subscription & Billing
    val subscription_tier: String,
    val subscription_expires_at: Long?,
    val billing_cycle: String,
    // Security & Compliance
    val enable_two_factor_auth: Boolean,
    val enable_audit_logs: Boolean,
    val enable_data_encryption: Boolean,
    val require_strong_passwords: Boolean,
    // Support & Priority
    val support_priority: String,
    val enable_priority_support: Boolean,
    val enable_dedicated_account: Boolean,
    // Vendor Status
    val is_active: Boolean,
    val is_suspended: Boolean,
    val suspension_reason: String?,
    val notes: String?,
)

@Serializable
data class UpdateFeatureFlagsRequest(
    // ═══ PUBLIC FEATURES ═══
    // Manager App
    val manager_dashboard: Boolean? = null,
    val manager_orders: Boolean? = null,
    val manager_menu: Boolean? = null,
    val manager_staff: Boolean? = null,
    val manager_attendance: Boolean? = null,
    val manager_salary: Boolean? = null,
    val manager_inventory: Boolean? = null,
    val manager_tables: Boolean? = null,
    val manager_reports: Boolean? = null,
    val manager_chatbot: Boolean? = null,
    val manager_users: Boolean? = null,
    val manager_settings: Boolean? = null,
    // Cashier App
    val cashier_orders: Boolean? = null,
    val cashier_attendance: Boolean? = null,
    val cashier_dine_in: Boolean? = null,
    val cashier_takeaway: Boolean? = null,
    val cashier_delivery: Boolean? = null,
    // Delivery App
    val delivery_orders: Boolean? = null,
    val delivery_navigation: Boolean? = null,
    val delivery_earnings: Boolean? = null,
    // Global
    val qr_code_attendance: Boolean? = null,
    val pin_attendance: Boolean? = null,
    val multi_language: Boolean? = null,
    
    // ═══ INTERNAL CONFIGURATION (Admin only) ═══
    // System Limits
    val max_users_per_vendor: Int? = null,
    val max_workers_per_vendor: Int? = null,
    val max_orders_per_day: Int? = null,
    val max_items_per_order: Int? = null,
    // Storage & Data
    val max_storage_images_mb: Int? = null,
    val data_retention_days: Int? = null,
    // API Rate Limiting
    val api_rate_limit_per_minute: Int? = null,
    val enable_api_rate_limit: Boolean? = null,
    // Advanced Features
    val enable_advanced_analytics: Boolean? = null,
    val enable_custom_reports: Boolean? = null,
    val enable_data_export: Boolean? = null,
    val enable_bulk_operations: Boolean? = null,
    // Integration Features
    val enable_webhooks: Boolean? = null,
    val enable_api_access: Boolean? = null,
    val enable_third_party_integrations: Boolean? = null,
    // Subscription & Billing
    val subscription_tier: String? = null,
    val subscription_expires_at: Long? = null,
    val billing_cycle: String? = null,
    // Security & Compliance
    val enable_two_factor_auth: Boolean? = null,
    val enable_audit_logs: Boolean? = null,
    val enable_data_encryption: Boolean? = null,
    val require_strong_passwords: Boolean? = null,
    // Support & Priority
    val support_priority: String? = null,
    val enable_priority_support: Boolean? = null,
    val enable_dedicated_account: Boolean? = null,
    // Vendor Status
    val is_active: Boolean? = null,
    val is_suspended: Boolean? = null,
    val suspension_reason: String? = null,
    val notes: String? = null,
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
private fun mapVendorRow(row: ResultRow, usersCount: Int = 0) =
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
        enable_takeaway = row[VendorsTable.enableTakeaway],
        enable_in_store = row[VendorsTable.enableInStore],
        enable_pickup_later = row[VendorsTable.enablePickupLater],
        business_type = row[VendorsTable.businessType],
        tax_enabled = row[VendorsTable.taxEnabled],
        default_tax_percent = row[VendorsTable.defaultTaxPercent].toDouble(),
        stock_mode = row[VendorsTable.stockMode],
        is_suspended = row[VendorsTable.isSuspended],
        suspension_reason = row[VendorsTable.suspensionReason],
        digital_menu_url = row[VendorsTable.digitalMenuUrl],
        users_count = usersCount,
        created_at = row[VendorsTable.createdAt].toEpochMilliseconds(),
        updated_at = row[VendorsTable.updatedAt].toEpochMilliseconds(),
    )

// ─── Routes ──────────────────────────────────────────────────────
fun Route.adminRoutes() {
    route("/api/v1/admin") {

        // POST /api/v1/admin/vendors — create new vendor with manager
        post("/vendors") {
            if (!requireAdminPassword()) return@post

            val request = call.receive<AdminCreateVendorRequest>()
            require(request.vendor_name.isNotBlank()) { "Vendor name is required" }
            require(request.manager_name.isNotBlank()) { "Manager name is required" }
            require(request.manager_phone.isNotBlank()) { "Manager phone is required" }
            require(request.manager_password.length >= 6) { "Password must be at least 6 characters" }

            // Auto-configure channel flags based on business_type
            val bt = request.business_type.uppercase()
            val enableTables = request.enable_tables ?: (bt == "RESTAURANT" || bt == "CAFE")
            val enableDineIn = request.enable_dine_in ?: (bt == "RESTAURANT" || bt == "CAFE")
            val enableDelivery = request.enable_delivery ?: (bt != "RETAIL")
            val enableTakeaway = request.enable_takeaway ?: true
            val enableInStore = request.enable_in_store ?: (bt == "RETAIL" || bt == "GROCERY")
            val enablePickupLater = request.enable_pickup_later ?: (bt == "RETAIL" || bt == "GROCERY")
            val taxEnabled = request.tax_enabled ?: (bt == "RETAIL" || bt == "GROCERY")
            val defaultTaxPercent = request.default_tax_percent ?: if (bt == "RETAIL" || bt == "GROCERY") 14.0 else 0.0
            val stockMode = request.stock_mode ?: if (bt == "RETAIL" || bt == "GROCERY") "ENFORCE" else "NONE"

            val result = transaction {
                // Check manager phone uniqueness
                val existingUser = UsersTable.selectAll()
                    .where { UsersTable.phone eq request.manager_phone }
                    .firstOrNull()
                if (existingUser != null) {
                    throw IllegalStateException("A user with this phone number already exists")
                }

                // Create vendor
                val vendorId = VendorsTable.insertAndGetId {
                    it[name] = request.vendor_name
                    it[address] = request.vendor_address
                    it[contactPhone] = request.vendor_phone
                    it[walletPhone] = request.wallet_phone
                    it[defaultDeliveryFee] = java.math.BigDecimal.valueOf(request.default_delivery_fee)
                    it[storeType] = request.store_type
                    it[logoUrl] = request.logo_url
                    it[digitalMenuUrl] = request.digital_menu_url
                    it[businessType] = bt
                    it[VendorsTable.enableTables] = enableTables
                    it[VendorsTable.enableDineIn] = enableDineIn
                    it[VendorsTable.enableDelivery] = enableDelivery
                    it[VendorsTable.enableTakeaway] = enableTakeaway
                    it[VendorsTable.enableInStore] = enableInStore
                    it[VendorsTable.enablePickupLater] = enablePickupLater
                    it[VendorsTable.taxEnabled] = taxEnabled
                    it[VendorsTable.defaultTaxPercent] = java.math.BigDecimal.valueOf(defaultTaxPercent)
                    it[VendorsTable.stockMode] = stockMode
                    it[isSuspended] = false
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Create manager user
                val passwordHash = net.marllex.waselak.backend.domain.service.AuthService.hashPassword(request.manager_password)
                val managerId = UsersTable.insertAndGetId {
                    it[UsersTable.vendorId] = vendorId.value
                    it[role] = "MANAGER"
                    it[UsersTable.name] = request.manager_name
                    it[phone] = request.manager_phone
                    it[email] = request.manager_email
                    it[UsersTable.passwordHash] = passwordHash
                    it[active] = true
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Return vendor info
                val vendorRow = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorId.value }
                    .first()

                AdminCreateVendorResponse(
                    vendor = mapVendorRow(vendorRow, 1),
                    manager_id = managerId.toString()
                )
            }

            call.respond(HttpStatusCode.Created, result)
        }

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
                    request.enable_takeaway?.let { stmt[enableTakeaway] = it }
                    request.enable_in_store?.let { stmt[enableInStore] = it }
                    request.enable_pickup_later?.let { stmt[enablePickupLater] = it }
                    request.business_type?.let { stmt[businessType] = it }
                    request.tax_enabled?.let { stmt[taxEnabled] = it }
                    request.default_tax_percent?.let { stmt[defaultTaxPercent] = java.math.BigDecimal.valueOf(it) }
                    request.stock_mode?.let { stmt[stockMode] = it }
                    request.is_suspended?.let { stmt[isSuspended] = it }
                    request.suspension_reason?.let { stmt[suspensionReason] = it }
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

        // DELETE /api/v1/admin/vendors/{id} — delete vendor and ALL associated data
        delete("/vendors/{id}") {
            if (!requireAdminPassword()) return@delete

            val vendorId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }

            val deleted = transaction {
                val exists = VendorsTable.selectAll()
                    .where { VendorsTable.id eq vendorUuid }
                    .firstOrNull() != null
                if (!exists) return@transaction false

                // 1. Get all order IDs for this vendor
                val orderIds = OrdersTable.selectAll()
                    .where { OrdersTable.vendorId eq vendorUuid }
                    .map { it[OrdersTable.id].value }

                // 2. Delete order-dependent records
                if (orderIds.isNotEmpty()) {
                    ActivityLogsTable.deleteWhere { ActivityLogsTable.orderId inList orderIds }
                    OrderItemsTable.deleteWhere { OrderItemsTable.orderId inList orderIds }
                    StockTransactionsTable.deleteWhere { StockTransactionsTable.orderId inList orderIds }
                }

                // 3. Delete stock transactions by stock items
                val stockIds = StockTable.selectAll()
                    .where { StockTable.vendorId eq vendorUuid }
                    .map { it[StockTable.id].value }
                if (stockIds.isNotEmpty()) {
                    StockTransactionsTable.deleteWhere { StockTransactionsTable.stockId inList stockIds }
                }

                // 4. Delete worker-dependent records
                val workerIds = WorkersTable.selectAll()
                    .where { WorkersTable.vendorId eq vendorUuid }
                    .map { it[WorkersTable.id].value }
                if (workerIds.isNotEmpty()) {
                    AttendanceAuthLogsTable.deleteWhere { AttendanceAuthLogsTable.workerId inList workerIds }
                    AttendanceTable.deleteWhere { AttendanceTable.workerId inList workerIds }
                    SalaryPaymentsTable.deleteWhere { SalaryPaymentsTable.workerId inList workerIds }
                }

                // 5. Delete announcement reads, then announcements
                val announcementIds = AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.vendorId eq vendorUuid }
                    .map { it[AnnouncementsTable.id].value }
                if (announcementIds.isNotEmpty()) {
                    AnnouncementReadsTable.deleteWhere { AnnouncementReadsTable.announcementId inList announcementIds }
                }
                AnnouncementsTable.deleteWhere { AnnouncementsTable.vendorId eq vendorUuid }

                // 6. Delete customer addresses, then customers
                val customerIds = CustomersTable.selectAll()
                    .where { CustomersTable.vendorId eq vendorUuid }
                    .map { it[CustomersTable.id].value }
                if (customerIds.isNotEmpty()) {
                    CustomerAddressesTable.deleteWhere { CustomerAddressesTable.customerId inList customerIds }
                }
                CustomersTable.deleteWhere { CustomersTable.vendorId eq vendorUuid }

                // 7. Delete vendor-level tables
                StockTable.deleteWhere { StockTable.vendorId eq vendorUuid }
                OrdersTable.deleteWhere { OrdersTable.vendorId eq vendorUuid }
                ItemsTable.deleteWhere { ItemsTable.vendorId eq vendorUuid }
                WorkerRolesTable.deleteWhere { WorkerRolesTable.vendorId eq vendorUuid }
                WorkersTable.deleteWhere { WorkersTable.vendorId eq vendorUuid }
                TaxPlacesTable.deleteWhere { TaxPlacesTable.vendorId eq vendorUuid }
                TablesTable.deleteWhere { TablesTable.vendorId eq vendorUuid }
                CategoriesTable.deleteWhere { CategoriesTable.vendorId eq vendorUuid }

                // 8. Delete users and their refresh tokens
                val userIds = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUuid }
                    .map { it[UsersTable.id].value }
                if (userIds.isNotEmpty()) {
                    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId inList userIds }
                }
                UsersTable.deleteWhere { UsersTable.vendorId eq vendorUuid }

                // 9. Finally delete the vendor
                VendorsTable.deleteWhere { VendorsTable.id eq vendorUuid }

                true
            }

            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Vendor and all associated data deleted"))
            }
        }
    }
}