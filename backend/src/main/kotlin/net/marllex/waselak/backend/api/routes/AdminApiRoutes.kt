package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AdminAuthService
import net.marllex.waselak.backend.domain.service.AuthService
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.domain.service.RequestLogService
import net.marllex.waselak.backend.plugins.AdminPrincipal
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

// ─── Request/Response DTOs ────────────────────────────────────────
@Serializable
data class AdminLoginRequest(val email: String, val password: String)

@Serializable
data class AdminChangePasswordRequest(val current_password: String, val new_password: String)

@Serializable
data class AdminUpdatePlanRequest(
    val display_name: String? = null,
    val price_egp: Int? = null,
    val max_managers: Int? = null,
    val max_cashiers: Int? = null,
    val max_delivery: Int? = null,
    val max_orders_per_month: Int? = null,
    val max_menu_items: Int? = null,
    val max_branches: Int? = null,
    val stock_management: Boolean? = null,
    val worker_attendance: Boolean? = null,
    val delivery_module: Boolean? = null,
    val overtime: Boolean? = null,
    val salaries: Boolean? = null,
    val customer_management: Boolean? = null,
    val table_management: Boolean? = null,
    val digital_receipt: Boolean? = null,
    val worker_qrcode: Boolean? = null,
    val analytics: String? = null,
    val digital_menu: String? = null,
)

// ─── Routes ───────────────────────────────────────────────────────
fun Route.adminApiRoutes() {
    val adminAuthService by KoinJavaComponent.inject<AdminAuthService>(clazz = AdminAuthService::class.java)
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)
    val requestLogService by KoinJavaComponent.inject<RequestLogService>(clazz = RequestLogService::class.java)

    route("/api/v1/cms") {

        // ─── Public: Login ────────────────────────────────────────
        post("/auth/login") {
            val trace = call.routeTrace()
            trace.step("Admin API login started")
            val request = call.receive<AdminLoginRequest>()
            trace.step("Received login request", mapOf("email" to request.email))
            val result = adminAuthService.login(request.email, request.password)
            if (result != null) {
                trace.step("Login successful", mapOf("adminId" to result.id, "name" to result.name))
                val json = buildJsonObject {
                    put("token", result.token)
                    put("refresh_token", result.refreshToken)
                    put("admin_id", result.id)
                    put("name", result.name)
                    put("email", result.email)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            } else {
                trace.step("Login failed - invalid credentials", mapOf("email" to request.email))
                call.respondText(
                    """{"error":"Invalid email or password"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
            }
            trace.step("Admin API login completed")
        }

        // ─── Public: Refresh Token ───────────────────────────────
        post("/auth/refresh") {
            val trace = call.routeTrace()
            trace.step("Token refresh started")
            val body = call.receive<JsonObject>()
            val refreshToken = body["refresh_token"]?.jsonPrimitive?.contentOrNull
            if (refreshToken.isNullOrBlank()) {
                trace.step("Missing refresh_token in request")
                call.respondText(
                    """{"error":"refresh_token is required"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest
                )
                return@post
            }
            trace.step("Attempting token refresh")
            val result = adminAuthService.refreshToken(refreshToken)
            if (result != null) {
                trace.step("Token refresh successful")
                val json = buildJsonObject {
                    put("token", result.token)
                    put("refresh_token", result.refreshToken)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            } else {
                trace.step("Token refresh failed - invalid or expired token")
                call.respondText(
                    """{"error":"Invalid or expired refresh token"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
            }
            trace.step("Token refresh completed")
        }

        // ─── Protected admin endpoints (JWT Bearer) ──────────────
        authenticate("admin-jwt-bearer") {
            // ─── Profile ──────────────────────────────────────────
            get("/auth/profile") {
                val trace = call.routeTrace()
                trace.step("Get admin profile started")
                val principal = call.principal<AdminPrincipal>()!!
                trace.step("Fetching admin profile", mapOf("adminId" to principal.adminId))
                val admin = transaction {
                    AdminUsersTable.selectAll()
                        .where { AdminUsersTable.id eq UUID.fromString(principal.adminId) }
                        .firstOrNull()
                }
                if (admin != null) {
                    trace.step("Admin profile found", mapOf("adminId" to principal.adminId, "name" to admin[AdminUsersTable.name]))
                    val json = buildJsonObject {
                        put("id", admin[AdminUsersTable.id].value.toString())
                        put("name", admin[AdminUsersTable.name])
                        put("email", admin[AdminUsersTable.email])
                        put("active", admin[AdminUsersTable.active])
                        admin[AdminUsersTable.lastLoginAt]?.let { put("last_login_at", it.toEpochMilliseconds()) }
                        put("created_at", admin[AdminUsersTable.createdAt].toEpochMilliseconds())
                    }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Admin not found", mapOf("adminId" to principal.adminId))
                    call.respondText("""{"error":"Admin not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("Get admin profile completed")
            }

            // ─── Change Password ──────────────────────────────────
            put("/auth/password") {
                val trace = call.routeTrace()
                trace.step("Change admin password started")
                val principal = call.principal<AdminPrincipal>()!!
                trace.step("Admin identified", mapOf("adminId" to principal.adminId))
                val request = call.receive<AdminChangePasswordRequest>()
                require(request.new_password.length >= 6) { "New password must be at least 6 characters" }

                trace.step("Verifying current password and updating")
                val updated = transaction {
                    val admin = AdminUsersTable.selectAll()
                        .where { AdminUsersTable.id eq UUID.fromString(principal.adminId) }
                        .firstOrNull() ?: return@transaction false

                    if (!BCrypt.checkpw(request.current_password, admin[AdminUsersTable.passwordHash])) {
                        return@transaction false
                    }

                    AdminUsersTable.update({ AdminUsersTable.id eq UUID.fromString(principal.adminId) }) {
                        it[passwordHash] = AuthService.hashPassword(request.new_password)
                        it[updatedAt] = Clock.System.now()
                    }
                    true
                }

                if (updated) {
                    trace.step("Password changed successfully", mapOf("adminId" to principal.adminId))
                    val json = buildJsonObject { put("success", true); put("message", "Password changed successfully") }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Password change failed - incorrect current password", mapOf("adminId" to principal.adminId))
                    call.respondText("""{"error":"Current password is incorrect"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Change admin password completed")
            }

            // ─── Plans: List ──────────────────────────────────────
            get("/plans") {
                val trace = call.routeTrace()
                trace.step("List CMS plans started")
                val plans = planService.listActivePlans()
                trace.step("Plans fetched", mapOf("count" to plans.size.toString()))
                val json = buildJsonArray {
                    plans.forEach { plan ->
                        addJsonObject {
                            put("id", plan[SubscriptionPlansTable.id].value.toString())
                            put("name", plan[SubscriptionPlansTable.name])
                            put("display_name", plan[SubscriptionPlansTable.displayName])
                            put("price_egp", plan[SubscriptionPlansTable.priceEgp])
                            put("billing_cycle", plan[SubscriptionPlansTable.billingCycle])
                            put("max_managers", plan[SubscriptionPlansTable.maxManagers])
                            put("max_cashiers", plan[SubscriptionPlansTable.maxCashiers])
                            put("max_delivery", plan[SubscriptionPlansTable.maxDelivery])
                            put("max_orders_per_month", plan[SubscriptionPlansTable.maxOrdersPerMonth])
                            put("max_menu_items", plan[SubscriptionPlansTable.maxMenuItems])
                            put("max_branches", plan[SubscriptionPlansTable.maxBranches])
                            put("stock_management", plan[SubscriptionPlansTable.stockManagement])
                            put("worker_attendance", plan[SubscriptionPlansTable.workerAttendance])
                            put("delivery_module", plan[SubscriptionPlansTable.deliveryModule])
                            put("overtime", plan[SubscriptionPlansTable.overtime])
                            put("salaries", plan[SubscriptionPlansTable.salaries])
                            put("customer_management", plan[SubscriptionPlansTable.customerManagement])
                            put("table_management", plan[SubscriptionPlansTable.tableManagement])
                            put("digital_receipt", plan[SubscriptionPlansTable.digitalReceipt])
                            put("worker_qrcode", plan[SubscriptionPlansTable.workerQrcode])
                            put("analytics", plan[SubscriptionPlansTable.analytics])
                            put("digital_menu", plan[SubscriptionPlansTable.digitalMenu])
                            put("active", plan[SubscriptionPlansTable.active])
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("List CMS plans completed")
            }

            // ─── Plans: Update ────────────────────────────────────
            put("/plans/{name}") {
                val trace = call.routeTrace()
                trace.step("Update plan started")
                val planName = call.parameters["name"]
                    ?: return@put call.respondText("""{"error":"Missing plan name"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                trace.step("Parsed plan name", mapOf("planName" to planName))
                val request = call.receive<AdminUpdatePlanRequest>()
                trace.step("Received plan update request")

                trace.step("Updating plan in database")
                val updated = transaction {
                    val plan = SubscriptionPlansTable.selectAll()
                        .where { SubscriptionPlansTable.name eq planName.uppercase() }
                        .firstOrNull() ?: return@transaction null

                    SubscriptionPlansTable.update({ SubscriptionPlansTable.name eq planName.uppercase() }) { stmt ->
                        request.display_name?.let { stmt[displayName] = it }
                        request.price_egp?.let { stmt[priceEgp] = it }
                        request.max_managers?.let { stmt[maxManagers] = it }
                        request.max_cashiers?.let { stmt[maxCashiers] = it }
                        request.max_delivery?.let { stmt[maxDelivery] = it }
                        request.max_orders_per_month?.let { stmt[maxOrdersPerMonth] = it }
                        request.max_menu_items?.let { stmt[maxMenuItems] = it }
                        request.max_branches?.let { stmt[maxBranches] = it }
                        request.stock_management?.let { stmt[stockManagement] = it }
                        request.worker_attendance?.let { stmt[workerAttendance] = it }
                        request.delivery_module?.let { stmt[deliveryModule] = it }
                        request.overtime?.let { stmt[overtime] = it }
                        request.salaries?.let { stmt[salaries] = it }
                        request.customer_management?.let { stmt[customerManagement] = it }
                        request.table_management?.let { stmt[tableManagement] = it }
                        request.digital_receipt?.let { stmt[digitalReceipt] = it }
                        request.worker_qrcode?.let { stmt[workerQrcode] = it }
                        request.analytics?.let { stmt[analytics] = it }
                        request.digital_menu?.let { stmt[digitalMenu] = it }
                        stmt[updatedAt] = Clock.System.now()
                    }

                    SubscriptionPlansTable.selectAll()
                        .where { SubscriptionPlansTable.name eq planName.uppercase() }
                        .firstOrNull()
                }

                if (updated != null) {
                    trace.step("Plan updated", mapOf("planName" to planName))
                    val json = buildJsonObject {
                        put("name", updated[SubscriptionPlansTable.name])
                        put("display_name", updated[SubscriptionPlansTable.displayName])
                        put("price_egp", updated[SubscriptionPlansTable.priceEgp])
                        put("max_managers", updated[SubscriptionPlansTable.maxManagers])
                        put("max_cashiers", updated[SubscriptionPlansTable.maxCashiers])
                        put("max_delivery", updated[SubscriptionPlansTable.maxDelivery])
                        put("max_orders_per_month", updated[SubscriptionPlansTable.maxOrdersPerMonth])
                        put("max_menu_items", updated[SubscriptionPlansTable.maxMenuItems])
                        put("max_branches", updated[SubscriptionPlansTable.maxBranches])
                        put("stock_management", updated[SubscriptionPlansTable.stockManagement])
                        put("worker_attendance", updated[SubscriptionPlansTable.workerAttendance])
                        put("delivery_module", updated[SubscriptionPlansTable.deliveryModule])
                        put("overtime", updated[SubscriptionPlansTable.overtime])
                        put("salaries", updated[SubscriptionPlansTable.salaries])
                        put("customer_management", updated[SubscriptionPlansTable.customerManagement])
                        put("table_management", updated[SubscriptionPlansTable.tableManagement])
                        put("digital_receipt", updated[SubscriptionPlansTable.digitalReceipt])
                        put("worker_qrcode", updated[SubscriptionPlansTable.workerQrcode])
                        put("analytics", updated[SubscriptionPlansTable.analytics])
                        put("digital_menu", updated[SubscriptionPlansTable.digitalMenu])
                    }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Plan not found", mapOf("planName" to planName))
                    call.respondText("""{"error":"Plan not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("Update plan completed")
            }

            // ─── Vendors: List ────────────────────────────────────
            get("/vendors") {
                val trace = call.routeTrace()
                trace.step("List CMS vendors started")
                val vendors = transaction {
                    VendorsTable.selectAll().map { row ->
                        val vendorId = row[VendorsTable.id].value
                        val usersCount = UsersTable.selectAll()
                            .where { UsersTable.vendorId eq vendorId }
                            .count().toInt()
                        // Get subscription info
                        val sub = VendorSubscriptionsTable.selectAll()
                            .where { VendorSubscriptionsTable.vendorId eq vendorId }
                            .firstOrNull()
                        val plan = sub?.let { s ->
                            SubscriptionPlansTable.selectAll()
                                .where { SubscriptionPlansTable.id eq s[VendorSubscriptionsTable.planId] }
                                .firstOrNull()
                        }

                        buildJsonObject {
                            put("id", vendorId.toString())
                            put("name", row[VendorsTable.name])
                            put("address", row[VendorsTable.address])
                            put("contact_phone", row[VendorsTable.contactPhone])
                            put("business_type", row[VendorsTable.businessType])
                            put("is_suspended", row[VendorsTable.isSuspended])
                            row[VendorsTable.suspensionReason]?.let { put("suspension_reason", it) }
                            put("users_count", usersCount)
                            plan?.let {
                                put("plan_name", it[SubscriptionPlansTable.name])
                                put("plan_display_name", it[SubscriptionPlansTable.displayName])
                            }
                            sub?.let { put("subscription_status", it[VendorSubscriptionsTable.status]) }
                            put("enable_tables", row[VendorsTable.enableTables])
                            put("enable_dine_in", row[VendorsTable.enableDineIn])
                            put("enable_delivery", row[VendorsTable.enableDelivery])
                            put("enable_takeaway", row[VendorsTable.enableTakeaway])
                            put("enable_in_store", row[VendorsTable.enableInStore])
                            put("enable_pickup_later", row[VendorsTable.enablePickupLater])
                            put("tax_enabled", row[VendorsTable.taxEnabled])
                            put("default_tax_percent", row[VendorsTable.defaultTaxPercent].toDouble())
                            put("stock_mode", row[VendorsTable.stockMode])
                            row[VendorsTable.logoUrl]?.let { put("logo_url", it) }
                            row[VendorsTable.walletPhone]?.let { put("wallet_phone", it) }
                            put("default_delivery_fee", row[VendorsTable.defaultDeliveryFee].toDouble())
                            row[VendorsTable.storeType]?.let { put("store_type", it) }
                            row[VendorsTable.digitalMenuUrl]?.let { put("digital_menu_url", it) }
                            put("created_at", row[VendorsTable.createdAt].toEpochMilliseconds())
                            put("updated_at", row[VendorsTable.updatedAt].toEpochMilliseconds())
                        }
                    }
                }
                trace.step("Vendors fetched", mapOf("count" to vendors.size.toString()))
                val json = buildJsonArray { vendors.forEach { add(it) } }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("List CMS vendors completed")
            }

            // ─── Vendors: Create ──────────────────────────────────
            post("/vendors") {
                val trace = call.routeTrace()
                trace.step("CMS create vendor started")
                val request = call.receive<AdminCreateVendorRequest>()
                trace.step("Received create vendor request", mapOf(
                    "vendorName" to request.vendor_name,
                    "managerName" to request.manager_name,
                    "managerPhone" to request.manager_phone,
                    "businessType" to request.business_type,
                    "plan" to request.plan
                ))
                require(request.vendor_name.isNotBlank()) { "Vendor name is required" }
                require(request.manager_name.isNotBlank()) { "Manager name is required" }
                require(request.manager_phone.isNotBlank()) { "Manager phone is required" }
                require(request.manager_password.length >= 6) { "Password must be at least 6 characters" }

                val validPlans = listOf("STARTER", "BUSINESS", "ENTERPRISE")
                require(request.plan.uppercase() in validPlans) { "Invalid plan. Must be one of: ${validPlans.joinToString()}" }

                val bt = request.business_type.uppercase()
                val isRetailLike = bt in listOf("RETAIL", "GROCERY", "SUPERMARKET", "PHARMACY")
                val isDineIn = bt in listOf("RESTAURANT", "CAFE", "BAKERY", "JUICE_BAR")
                val enableTables = request.enable_tables ?: isDineIn
                val enableDineIn = request.enable_dine_in ?: isDineIn
                val enableDelivery = request.enable_delivery ?: (bt != "RETAIL")
                val enableTakeaway = request.enable_takeaway ?: true
                val enableInStore = request.enable_in_store ?: isRetailLike
                val enablePickupLater = request.enable_pickup_later ?: isRetailLike
                val taxEnabled = request.tax_enabled ?: isRetailLike
                val defaultTaxPercent = request.default_tax_percent ?: if (isRetailLike) 14.0 else 0.0
                val stockMode = request.stock_mode ?: if (isRetailLike) "ENFORCE" else "NONE"

                trace.step("Creating vendor and manager in transaction")
                val result = transaction {
                    val existingUser = UsersTable.selectAll()
                        .where { UsersTable.phone eq request.manager_phone }
                        .firstOrNull()
                    if (existingUser != null) {
                        throw IllegalStateException("A user with this phone number already exists")
                    }

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

                    planService.assignPlanToVendor(vendorId.value, request.plan)

                    val passwordHash = AuthService.hashPassword(request.manager_password)
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

                    buildJsonObject {
                        put("vendor_id", vendorId.value.toString())
                        put("manager_id", managerId.value.toString())
                        put("vendor_name", request.vendor_name)
                        put("plan", request.plan)
                    }
                }
                trace.step("Vendor created via CMS", mapOf(
                    "vendorName" to request.vendor_name,
                    "plan" to request.plan
                ))
                call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.Created)
                trace.step("CMS create vendor completed")
            }

            // ─── Vendors: Update ──────────────────────────────────
            put("/vendors/{id}") {
                val trace = call.routeTrace()
                trace.step("CMS update vendor started")
                val vendorId = call.parameters["id"]
                    ?: return@put call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@put call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

                val request = call.receive<AdminUpdateVendorRequest>()
                trace.step("Received update request", mapOf(
                    "name" to (request.name ?: "null"),
                    "isSuspended" to (request.is_suspended?.toString() ?: "null")
                ))
                trace.step("Updating vendor in database")
                val updated = transaction {
                    val exists = VendorsTable.selectAll()
                        .where { VendorsTable.id eq vendorUuid }
                        .firstOrNull() != null
                    if (!exists) return@transaction false

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
                    true
                }
                if (updated) {
                    trace.step("Vendor updated via CMS", mapOf("vendorId" to vendorId))
                    val json = buildJsonObject { put("success", true); put("message", "Vendor updated") }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Vendor not found for CMS update", mapOf("vendorId" to vendorId))
                    call.respondText("""{"error":"Vendor not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("CMS update vendor completed")
            }

            // ─── Vendors: Suspend/Unsuspend ───────────────────────
            put("/vendors/{id}/suspend") {
                val trace = call.routeTrace()
                trace.step("Suspend/unsuspend vendor started")
                val vendorId = call.parameters["id"]
                    ?: return@put call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@put call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

                @Serializable data class SuspendRequest(val suspended: Boolean, val reason: String? = null)
                val request = call.receive<SuspendRequest>()
                trace.step("Received suspend request", mapOf(
                    "suspended" to request.suspended.toString(),
                    "reason" to (request.reason ?: "null")
                ))

                trace.step("Updating vendor suspension status")
                val updated = transaction {
                    VendorsTable.update({ VendorsTable.id eq vendorUuid }) {
                        it[isSuspended] = request.suspended
                        it[suspensionReason] = if (request.suspended) request.reason else null
                        it[updatedAt] = Clock.System.now()
                    }
                }

                if (updated > 0) {
                    val status = if (request.suspended) "suspended" else "activated"
                    trace.step("Vendor $status", mapOf("vendorId" to vendorId))
                    val json = buildJsonObject { put("success", true); put("message", "Vendor $status") }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Vendor not found for suspension", mapOf("vendorId" to vendorId))
                    call.respondText("""{"error":"Vendor not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("Suspend/unsuspend vendor completed")
            }

            // ─── Vendors: Delete ──────────────────────────────────
            delete("/vendors/{id}") {
                val trace = call.routeTrace()
                trace.step("CMS delete vendor started")
                val vendorId = call.parameters["id"]
                    ?: return@delete call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@delete call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

                trace.step("Deleting vendor and all associated data")
                val deleted = transaction {
                    val exists = VendorsTable.selectAll()
                        .where { VendorsTable.id eq vendorUuid }
                        .firstOrNull() != null
                    if (!exists) return@transaction false

                    // 1. Delete order-dependent records
                    val orderIds = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUuid }.map { it[OrdersTable.id].value }
                    if (orderIds.isNotEmpty()) {
                        ActivityLogsTable.deleteWhere { ActivityLogsTable.orderId inList orderIds }
                        OrderItemsTable.deleteWhere { OrderItemsTable.orderId inList orderIds }
                        StockTransactionsTable.deleteWhere { StockTransactionsTable.orderId inList orderIds }
                    }

                    // 2. Delete stock transactions by stock items
                    val stockIds = StockTable.selectAll().where { StockTable.vendorId eq vendorUuid }.map { it[StockTable.id].value }
                    if (stockIds.isNotEmpty()) {
                        StockTransactionsTable.deleteWhere { StockTransactionsTable.stockId inList stockIds }
                    }

                    // 3. Delete item variant options/groups (before items)
                    val itemIds = ItemsTable.selectAll().where { ItemsTable.vendorId eq vendorUuid }.map { it[ItemsTable.id].value }
                    if (itemIds.isNotEmpty()) {
                        val groupIds = ItemVariantGroupsTable.selectAll()
                            .where { ItemVariantGroupsTable.itemId inList itemIds }
                            .map { it[ItemVariantGroupsTable.id].value }
                        if (groupIds.isNotEmpty()) {
                            ItemVariantOptionsTable.deleteWhere { ItemVariantOptionsTable.groupId inList groupIds }
                        }
                        ItemVariantGroupsTable.deleteWhere { ItemVariantGroupsTable.itemId inList itemIds }
                    }

                    // 4. Delete recipe ingredients, then recipes
                    val recipeIds = RecipesTable.selectAll().where { RecipesTable.vendorId eq vendorUuid }.map { it[RecipesTable.id].value }
                    if (recipeIds.isNotEmpty()) {
                        RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId inList recipeIds }
                    }
                    RecipesTable.deleteWhere { RecipesTable.vendorId eq vendorUuid }

                    // 5. Delete worker-dependent records
                    val workerIds = WorkersTable.selectAll().where { WorkersTable.vendorId eq vendorUuid }.map { it[WorkersTable.id].value }
                    if (workerIds.isNotEmpty()) {
                        AttendanceAuthLogsTable.deleteWhere { AttendanceAuthLogsTable.workerId inList workerIds }
                        AttendanceTable.deleteWhere { AttendanceTable.workerId inList workerIds }
                        SalaryPaymentsTable.deleteWhere { SalaryPaymentsTable.workerId inList workerIds }
                    }

                    // 6. Delete overtime entries
                    OvertimeTable.deleteWhere { OvertimeTable.vendorId eq vendorUuid }

                    // 7. Delete announcement reads, then announcements
                    val announcementIds = AnnouncementsTable.selectAll().where { AnnouncementsTable.vendorId eq vendorUuid }.map { it[AnnouncementsTable.id].value }
                    if (announcementIds.isNotEmpty()) {
                        AnnouncementReadsTable.deleteWhere { AnnouncementReadsTable.announcementId inList announcementIds }
                    }
                    AnnouncementsTable.deleteWhere { AnnouncementsTable.vendorId eq vendorUuid }

                    // 8. Delete customer addresses, then customers
                    val customerIds = CustomersTable.selectAll().where { CustomersTable.vendorId eq vendorUuid }.map { it[CustomersTable.id].value }
                    if (customerIds.isNotEmpty()) {
                        CustomerAddressesTable.deleteWhere { CustomerAddressesTable.customerId inList customerIds }
                    }
                    CustomersTable.deleteWhere { CustomersTable.vendorId eq vendorUuid }

                    // 9. Delete reservations
                    ReservationsTable.deleteWhere { ReservationsTable.vendorId eq vendorUuid }

                    // 10. Delete vendor-level tables
                    StockTable.deleteWhere { StockTable.vendorId eq vendorUuid }
                    OrdersTable.deleteWhere { OrdersTable.vendorId eq vendorUuid }
                    ItemsTable.deleteWhere { ItemsTable.vendorId eq vendorUuid }
                    WorkerRolesTable.deleteWhere { WorkerRolesTable.vendorId eq vendorUuid }
                    WorkersTable.deleteWhere { WorkersTable.vendorId eq vendorUuid }
                    TaxPlacesTable.deleteWhere { TaxPlacesTable.vendorId eq vendorUuid }
                    TablesTable.deleteWhere { TablesTable.vendorId eq vendorUuid }
                    CategoriesTable.deleteWhere { CategoriesTable.vendorId eq vendorUuid }

                    // 11. Delete users and their refresh tokens
                    val userIds = UsersTable.selectAll().where { UsersTable.vendorId eq vendorUuid }.map { it[UsersTable.id].value }
                    if (userIds.isNotEmpty()) {
                        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId inList userIds }
                    }
                    UsersTable.deleteWhere { UsersTable.vendorId eq vendorUuid }

                    // 12. Delete vendor subscription
                    VendorSubscriptionsTable.deleteWhere { VendorSubscriptionsTable.vendorId eq vendorUuid }

                    // 13. Delete request/audit logs
                    RequestLogsTable.deleteWhere { RequestLogsTable.vendorId eq vendorUuid.toString() }

                    // 14. Finally delete the vendor
                    VendorsTable.deleteWhere { VendorsTable.id eq vendorUuid }
                    true
                }

                // Clean up uploaded files for this vendor
                if (deleted) {
                    try {
                        val uploadsDir = java.io.File("uploads")
                        if (uploadsDir.exists()) {
                            uploadsDir.listFiles()?.filter { it.isDirectory && it.name.endsWith("-$vendorId") }?.forEach { dir ->
                                dir.deleteRecursively()
                            }
                        }
                    } catch (_: Exception) {
                        // Don't fail the response if file cleanup fails
                    }
                }

                if (deleted) {
                    trace.step("Vendor and associated data deleted via CMS", mapOf("vendorId" to vendorId))
                    val json = buildJsonObject { put("success", true); put("message", "Vendor and all associated data deleted") }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Vendor not found for CMS deletion", mapOf("vendorId" to vendorId))
                    call.respondText("""{"error":"Vendor not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("CMS delete vendor completed")
            }

            // ─── Vendors: Change Plan ─────────────────────────────
            put("/vendors/{id}/plan") {
                val trace = call.routeTrace()
                trace.step("CMS change vendor plan started")
                val vendorId = call.parameters["id"]
                    ?: return@put call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@put call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))
                val request = call.receive<AdminChangePlanRequest>()
                trace.step("Received plan change request", mapOf("plan" to request.plan, "notes" to (request.notes ?: "null")))
                trace.step("Assigning plan to vendor")
                planService.assignPlanToVendor(vendorUuid, request.plan, request.notes)
                trace.step("Plan assigned", mapOf("vendorId" to vendorId, "newPlan" to request.plan))
                val json = buildJsonObject { put("success", true); put("message", "Plan updated to ${request.plan}") }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("CMS change vendor plan completed")
            }

            // ─── Vendors: Usage ───────────────────────────────────
            get("/vendors/{id}/usage") {
                val trace = call.routeTrace()
                trace.step("CMS get vendor usage started")
                val vendorId = call.parameters["id"]
                    ?: return@get call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@get call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))
                trace.step("Fetching plan limits and usage")
                val limits = planService.getVendorPlanLimits(vendorUuid)
                val usage = planService.getVendorUsage(vendorUuid)
                trace.step("Usage fetched", mapOf("planName" to limits.planName))
                val json = buildJsonObject {
                    putJsonObject("plan") {
                        put("name", limits.planName)
                        put("display_name", limits.planDisplayName)
                        put("max_managers", limits.effectiveMaxManagers())
                        put("max_cashiers", limits.effectiveMaxCashiers())
                        put("max_delivery", limits.effectiveMaxDelivery())
                        put("max_orders_per_month", limits.effectiveMaxOrders())
                        put("max_menu_items", limits.effectiveMaxItems())
                    }
                    putJsonObject("usage") {
                        put("managers", usage["managers"] as Int)
                        put("cashiers", usage["cashiers"] as Int)
                        put("delivery", usage["delivery"] as Int)
                        put("monthly_orders", usage["monthlyOrders"] as Int)
                        put("menu_items", usage["menuItems"] as Int)
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("CMS get vendor usage completed")
            }

            // ─── Vendors: Detail (users, orders, revenue, tax, plan usage) ───
            get("/vendors/{id}/detail") {
                val trace = call.routeTrace()
                trace.step("CMS get vendor detail started")
                val vendorId = call.parameters["id"]
                    ?: return@get call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@get call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

                trace.step("Querying vendor detail from database")
                val result = transaction {
                    // 1. Vendor row
                    val vendorRow = VendorsTable.selectAll()
                        .where { VendorsTable.id eq vendorUuid }
                        .firstOrNull() ?: return@transaction null

                    // 2. Subscription + plan info
                    val sub = VendorSubscriptionsTable
                        .innerJoin(SubscriptionPlansTable)
                        .selectAll()
                        .where { VendorSubscriptionsTable.vendorId eq vendorUuid }
                        .firstOrNull()

                    // 3. All users for this vendor
                    val users = UsersTable.selectAll()
                        .where { UsersTable.vendorId eq vendorUuid }
                        .map { row ->
                            buildJsonObject {
                                put("id", row[UsersTable.id].value.toString())
                                put("name", row[UsersTable.name])
                                put("phone", row[UsersTable.phone])
                                put("email", row[UsersTable.email])
                                put("role", row[UsersTable.role])
                                put("active", row[UsersTable.active])
                                put("created_at", row[UsersTable.createdAt].toEpochMilliseconds())
                            }
                        }

                    // 4. Order stats
                    val now = Clock.System.now()
                    val todayStart = kotlinx.datetime.Instant.fromEpochMilliseconds(
                        (now.toEpochMilliseconds() / 86400000L) * 86400000L
                    )
                    val jNow = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds())
                        .atZone(java.time.ZoneOffset.UTC)
                    val monthStart = kotlinx.datetime.Instant.fromEpochMilliseconds(
                        java.time.ZonedDateTime.of(jNow.year, jNow.monthValue, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC)
                            .toInstant().toEpochMilli()
                    )

                    val vendorOrders = OrdersTable.selectAll()
                        .where { OrdersTable.vendorId eq vendorUuid }

                    val totalOrders = vendorOrders.count()
                    val todayOrders = OrdersTable.selectAll()
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.createdAt greaterEq todayStart) }
                        .count()
                    val monthOrders = OrdersTable.selectAll()
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.createdAt greaterEq monthStart) }
                        .count()

                    // By channel
                    val byChannel = OrdersTable.select(OrdersTable.channel, OrdersTable.id.count())
                        .where { OrdersTable.vendorId eq vendorUuid }
                        .groupBy(OrdersTable.channel)
                        .associate { it[OrdersTable.channel] to it[OrdersTable.id.count()] }

                    // By status
                    val byStatus = OrdersTable.select(OrdersTable.status, OrdersTable.id.count())
                        .where { OrdersTable.vendorId eq vendorUuid }
                        .groupBy(OrdersTable.status)
                        .associate { it[OrdersTable.status] to it[OrdersTable.id.count()] }

                    // 5. Revenue stats (exclude CANCELED and REFUNDED)
                    val excludedStatuses = listOf("CANCELED", "REFUNDED")

                    val totalRevenue = OrdersTable.select(OrdersTable.total.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) }
                        .firstOrNull()?.get(OrdersTable.total.sum())?.toDouble() ?: 0.0

                    val todayRevenue = OrdersTable.select(OrdersTable.total.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) and (OrdersTable.createdAt greaterEq todayStart) }
                        .firstOrNull()?.get(OrdersTable.total.sum())?.toDouble() ?: 0.0

                    val monthRevenue = OrdersTable.select(OrdersTable.total.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) and (OrdersTable.createdAt greaterEq monthStart) }
                        .firstOrNull()?.get(OrdersTable.total.sum())?.toDouble() ?: 0.0

                    // Revenue by payment method
                    val byPaymentMethod = OrdersTable
                        .select(OrdersTable.paymentMethod, OrdersTable.id.count(), OrdersTable.total.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) }
                        .groupBy(OrdersTable.paymentMethod)
                        .associate { row ->
                            row[OrdersTable.paymentMethod] to buildJsonObject {
                                put("count", row[OrdersTable.id.count()])
                                put("amount", row[OrdersTable.total.sum()]?.toDouble() ?: 0.0)
                            }
                        }

                    // Revenue by channel
                    val revenueByChannel = OrdersTable
                        .select(OrdersTable.channel, OrdersTable.total.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) }
                        .groupBy(OrdersTable.channel)
                        .associate { it[OrdersTable.channel] to (it[OrdersTable.total.sum()]?.toDouble() ?: 0.0) }

                    // 6. Tax collected
                    val totalTax = OrdersTable.select(OrdersTable.tax.sum())
                        .where { (OrdersTable.vendorId eq vendorUuid) and (OrdersTable.status notInList excludedStatuses) }
                        .firstOrNull()?.get(OrdersTable.tax.sum())?.toDouble() ?: 0.0

                    // 7. Plan usage
                    val limits = planService.getVendorPlanLimits(vendorUuid)
                    val usage = planService.getVendorUsage(vendorUuid)

                    // Build JSON response
                    buildJsonObject {
                        putJsonObject("vendor") {
                            put("id", vendorRow[VendorsTable.id].value.toString())
                            put("name", vendorRow[VendorsTable.name])
                            put("address", vendorRow[VendorsTable.address])
                            put("contact_phone", vendorRow[VendorsTable.contactPhone])
                            put("wallet_phone", vendorRow[VendorsTable.walletPhone])
                            put("business_type", vendorRow[VendorsTable.businessType])
                            put("store_type", vendorRow[VendorsTable.storeType])
                            put("is_suspended", vendorRow[VendorsTable.isSuspended])
                            put("suspension_reason", vendorRow[VendorsTable.suspensionReason])
                            put("logo_url", vendorRow[VendorsTable.logoUrl])
                            put("digital_menu_url", vendorRow[VendorsTable.digitalMenuUrl])
                            put("enable_tables", vendorRow[VendorsTable.enableTables])
                            put("enable_dine_in", vendorRow[VendorsTable.enableDineIn])
                            put("enable_delivery", vendorRow[VendorsTable.enableDelivery])
                            put("enable_takeaway", vendorRow[VendorsTable.enableTakeaway])
                            put("enable_in_store", vendorRow[VendorsTable.enableInStore])
                            put("enable_pickup_later", vendorRow[VendorsTable.enablePickupLater])
                            put("tax_enabled", vendorRow[VendorsTable.taxEnabled])
                            put("default_tax_percent", vendorRow[VendorsTable.defaultTaxPercent].toDouble())
                            put("stock_mode", vendorRow[VendorsTable.stockMode])
                            put("default_delivery_fee", vendorRow[VendorsTable.defaultDeliveryFee].toDouble())
                            put("offline_mode_enabled", vendorRow[VendorsTable.offlineModeEnabled])
                            put("biometric_required", vendorRow[VendorsTable.biometricRequired])
                            // Loyalty settings
                            put("loyalty_enabled", vendorRow[VendorsTable.loyaltyEnabled])
                            put("points_earn_rate", vendorRow[VendorsTable.pointsEarnRate].toDouble())
                            put("points_redeem_rate", vendorRow[VendorsTable.pointsRedeemRate].toDouble())
                            put("min_points_redeem", vendorRow[VendorsTable.minPointsRedeem])
                            // Discount settings
                            put("max_manual_discount_percent", vendorRow[VendorsTable.maxManualDiscountPercent].toDouble())
                            put("manual_discount_requires_pin", vendorRow[VendorsTable.manualDiscountRequiresPin])
                            put("plan_name", sub?.get(SubscriptionPlansTable.name))
                            put("plan_display_name", sub?.get(SubscriptionPlansTable.displayName))
                            put("subscription_status", sub?.get(VendorSubscriptionsTable.status))
                            sub?.get(VendorSubscriptionsTable.startedAt)?.let {
                                put("subscription_started_at", it.toEpochMilliseconds())
                            }
                            sub?.get(VendorSubscriptionsTable.expiresAt)?.let {
                                put("subscription_expires_at", it.toEpochMilliseconds())
                            }
                            put("created_at", vendorRow[VendorsTable.createdAt].toEpochMilliseconds())
                            put("updated_at", vendorRow[VendorsTable.updatedAt].toEpochMilliseconds())
                        }
                        put("users", buildJsonArray { users.forEach { add(it) } })
                        putJsonObject("stats") {
                            putJsonObject("orders") {
                                put("total", totalOrders)
                                put("today", todayOrders)
                                put("this_month", monthOrders)
                                put("by_channel", buildJsonObject { byChannel.forEach { (k, v) -> put(k, v) } })
                                put("by_status", buildJsonObject { byStatus.forEach { (k, v) -> put(k, v) } })
                            }
                            putJsonObject("revenue") {
                                put("total", totalRevenue)
                                put("today", todayRevenue)
                                put("this_month", monthRevenue)
                                put("by_payment_method", buildJsonObject { byPaymentMethod.forEach { (k, v) -> put(k, v) } })
                                put("by_channel", buildJsonObject { revenueByChannel.forEach { (k, v) -> put(k, v) } })
                            }
                            putJsonObject("tax") {
                                put("total_collected", totalTax)
                            }
                        }
                        putJsonObject("plan_usage") {
                            putJsonObject("plan") {
                                put("name", limits.planName)
                                put("display_name", limits.planDisplayName)
                                put("max_managers", limits.effectiveMaxManagers())
                                put("max_cashiers", limits.effectiveMaxCashiers())
                                put("max_delivery", limits.effectiveMaxDelivery())
                                put("max_orders_per_month", limits.effectiveMaxOrders())
                                put("max_menu_items", limits.effectiveMaxItems())
                            }
                            putJsonObject("usage") {
                                put("managers", usage["managers"] as Int)
                                put("cashiers", usage["cashiers"] as Int)
                                put("delivery", usage["delivery"] as Int)
                                put("monthly_orders", usage["monthlyOrders"] as Int)
                                put("menu_items", usage["menuItems"] as Int)
                            }
                        }
                    }
                }

                if (result != null) {
                    trace.step("Vendor detail fetched", mapOf("vendorId" to vendorId))
                    call.respondText(result.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Vendor not found", mapOf("vendorId" to vendorId))
                    call.respondText("""{"error":"Vendor not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("CMS get vendor detail completed")
            }

            // ─── Logs: Paginated list ────────────────────────────
            get("/logs") {
                val trace = call.routeTrace()
                trace.step("Query request logs started")
                val params = RequestLogService.LogQueryParams(
                    vendorId = call.parameters["vendorId"],
                    userId = call.parameters["userId"],
                    method = call.parameters["method"],
                    pathSearch = call.parameters["path"],
                    statusGroup = call.parameters["statusGroup"],
                    resource = call.parameters["resource"],
                    action = call.parameters["action"],
                    startDate = call.parameters["startDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    endDate = call.parameters["endDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    page = call.parameters["page"]?.toIntOrNull() ?: 1,
                    pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50
                )
                trace.step("Querying logs", mapOf(
                    "page" to params.page.toString(),
                    "pageSize" to params.pageSize.toString(),
                    "vendorId" to (params.vendorId ?: "null"),
                    "method" to (params.method ?: "null")
                ))
                val result = requestLogService.queryLogs(params)
                trace.step("Logs fetched", mapOf("total" to result.total.toString(), "totalPages" to result.totalPages.toString()))
                val json = buildJsonObject {
                    put("logs", buildJsonArray { result.logs.forEach { add(it) } })
                    put("total", result.total)
                    put("page", result.page)
                    put("pageSize", result.pageSize)
                    put("totalPages", result.totalPages)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Query request logs completed")
            }

            // ─── Logs: Stats ────────────────────────────────────────
            get("/logs/stats") {
                val trace = call.routeTrace()
                trace.step("Get log stats started")
                val vendorId = call.parameters["vendorId"]
                val startDate = call.parameters["startDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                val endDate = call.parameters["endDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                trace.step("Fetching log stats", mapOf("vendorId" to (vendorId ?: "null")))
                val stats = requestLogService.getStats(vendorId, startDate, endDate)
                trace.step("Log stats fetched", mapOf(
                    "totalRequests" to stats.totalRequests.toString(),
                    "errorCount" to stats.errorCount.toString(),
                    "errorRate" to stats.errorRate.toString()
                ))
                val json = buildJsonObject {
                    put("totalRequests", stats.totalRequests)
                    put("errorCount", stats.errorCount)
                    put("errorRate", stats.errorRate)
                    put("avgDurationMs", stats.avgDurationMs)
                    put("statusBreakdown", buildJsonObject {
                        stats.statusBreakdown.forEach { (k, v) -> put(k, v) }
                    })
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get log stats completed")
            }

            // ─── Logs: Top endpoints ────────────────────────────────
            get("/logs/top-endpoints") {
                val trace = call.routeTrace()
                trace.step("Get top endpoints started")
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val vendorId = call.parameters["vendorId"]
                trace.step("Fetching top endpoints", mapOf("limit" to limit.toString(), "vendorId" to (vendorId ?: "null")))
                val endpoints = requestLogService.getTopEndpoints(limit, vendorId)
                trace.step("Top endpoints fetched", mapOf("count" to endpoints.size.toString()))
                val json = buildJsonArray {
                    endpoints.forEach { ep ->
                        addJsonObject {
                            put("method", ep.method)
                            put("path", ep.path)
                            put("count", ep.count)
                            put("avgDurationMs", ep.avgDurationMs)
                            put("errorCount", ep.errorCount)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get top endpoints completed")
            }

            // ─── Logs: Slowest endpoints ────────────────────────────
            get("/logs/slowest-endpoints") {
                val trace = call.routeTrace()
                trace.step("Get slowest endpoints started")
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val vendorId = call.parameters["vendorId"]
                trace.step("Fetching slowest endpoints", mapOf("limit" to limit.toString(), "vendorId" to (vendorId ?: "null")))
                val endpoints = requestLogService.getSlowestEndpoints(limit, vendorId)
                trace.step("Slowest endpoints fetched", mapOf("count" to endpoints.size.toString()))
                val json = buildJsonArray {
                    endpoints.forEach { ep ->
                        addJsonObject {
                            put("method", ep.method)
                            put("path", ep.path)
                            put("count", ep.count)
                            put("avgDurationMs", ep.avgDurationMs)
                            put("errorCount", ep.errorCount)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get slowest endpoints completed")
            }

            // ─── Logs: Error endpoints ──────────────────────────────
            get("/logs/error-endpoints") {
                val trace = call.routeTrace()
                trace.step("Get error endpoints started")
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val vendorId = call.parameters["vendorId"]
                trace.step("Fetching error endpoints", mapOf("limit" to limit.toString(), "vendorId" to (vendorId ?: "null")))
                val endpoints = requestLogService.getErrorEndpoints(limit, vendorId)
                trace.step("Error endpoints fetched", mapOf("count" to endpoints.size.toString()))
                val json = buildJsonArray {
                    endpoints.forEach { ep ->
                        addJsonObject {
                            put("method", ep.method)
                            put("path", ep.path)
                            put("count", ep.count)
                            put("avgDurationMs", ep.avgDurationMs)
                            put("errorCount", ep.errorCount)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get error endpoints completed")
            }

            // ─── Logs: Timeline ─────────────────────────────────────
            get("/logs/timeline") {
                val trace = call.routeTrace()
                trace.step("Get request timeline started")
                val hours = call.parameters["hours"]?.toIntOrNull() ?: 24
                val vendorId = call.parameters["vendorId"]
                trace.step("Fetching timeline", mapOf("hours" to hours.toString(), "vendorId" to (vendorId ?: "null")))
                val timeline = requestLogService.getRequestTimeline(hours, vendorId)
                trace.step("Timeline fetched", mapOf("dataPoints" to timeline.size.toString()))
                val json = buildJsonArray {
                    timeline.forEach { point ->
                        addJsonObject {
                            put("hour", point.hour)
                            put("count", point.count)
                            put("errorCount", point.errorCount)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get request timeline completed")
            }

            // ─── Logs: Vendors that appear in logs ──────────────────
            get("/logs/vendors") {
                val trace = call.routeTrace()
                trace.step("Get logged vendors started")
                val vendors = requestLogService.getLoggedVendors()
                trace.step("Logged vendors fetched", mapOf("count" to vendors.size.toString()))
                val json = buildJsonArray {
                    vendors.forEach { v ->
                        addJsonObject {
                            put("id", v.id)
                            put("name", v.name)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get logged vendors completed")
            }

            // ─── Logs: Users for a vendor ───────────────────────────
            get("/logs/vendors/{vendorId}/users") {
                val trace = call.routeTrace()
                trace.step("Get vendor users from logs started")
                val vendorId = call.parameters["vendorId"]
                    ?: return@get call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                trace.step("Fetching users for vendor", mapOf("vendorId" to vendorId))
                val users = requestLogService.getVendorUsers(vendorId)
                trace.step("Vendor users fetched", mapOf("vendorId" to vendorId, "count" to users.size.toString()))
                val json = buildJsonArray {
                    users.forEach { u ->
                        addJsonObject {
                            put("id", u.id)
                            put("name", u.name)
                            put("role", u.role)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get vendor users from logs completed")
            }

            // ─── Logs: Resource breakdown ─────────────────────────────
            get("/logs/resources") {
                val trace = call.routeTrace()
                trace.step("Get resource breakdown started")
                val vendorId = call.parameters["vendorId"]
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
                trace.step("Fetching resource breakdown", mapOf("vendorId" to (vendorId ?: "null"), "limit" to limit.toString()))
                val resources = requestLogService.getResourceBreakdown(vendorId = vendorId, limit = limit)
                val json = buildJsonArray {
                    resources.forEach { r ->
                        addJsonObject {
                            put("resource", r.resource)
                            put("count", r.count)
                            put("avgDurationMs", r.avgDurationMs)
                            put("errorCount", r.errorCount)
                        }
                    }
                }
                trace.step("Resource breakdown fetched", mapOf("count" to resources.size.toString()))
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get resource breakdown completed")
            }

            // ─── Logs: Action breakdown ──────────────────────────────
            get("/logs/actions") {
                val trace = call.routeTrace()
                trace.step("Get action breakdown started")
                val vendorId = call.parameters["vendorId"]
                val resource = call.parameters["resource"]
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
                trace.step("Fetching action breakdown", mapOf(
                    "vendorId" to (vendorId ?: "null"),
                    "resource" to (resource ?: "null"),
                    "limit" to limit.toString()
                ))
                val actions = requestLogService.getActionBreakdown(resource = resource, vendorId = vendorId, limit = limit)
                trace.step("Action breakdown fetched", mapOf("count" to actions.size.toString()))
                val json = buildJsonArray {
                    actions.forEach { a ->
                        addJsonObject {
                            put("action", a.action)
                            put("count", a.count)
                            put("avgDurationMs", a.avgDurationMs)
                            put("errorCount", a.errorCount)
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get action breakdown completed")
            }

            // ─── Logs: Live monitoring ───────────────────────────────
            get("/logs/monitoring") {
                val trace = call.routeTrace()
                trace.step("Get live monitoring started")
                val vendorId = call.parameters["vendorId"]
                trace.step("Fetching live monitoring data", mapOf("vendorId" to (vendorId ?: "null")))
                val data = requestLogService.getLiveMonitoring(vendorId)
                trace.step("Live monitoring data fetched", mapOf(
                    "requestsPerMinute" to data.requestsPerMinute.toString(),
                    "p95DurationMs" to data.p95DurationMs.toString()
                ))
                val json = buildJsonObject {
                    put("requestsPerMinute", data.requestsPerMinute)
                    put("p95DurationMs", data.p95DurationMs)
                    put("activeResources", buildJsonArray { data.activeResources.forEach { add(it) } })
                    put("recentErrors", buildJsonArray { data.recentErrors.forEach { add(it) } })
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Get live monitoring completed")
            }

            // ─── Logs: Cleanup ──────────────────────────────────────
            delete("/logs/cleanup") {
                val trace = call.routeTrace()
                trace.step("Cleanup old logs started")
                val days = call.parameters["days"]?.toIntOrNull() ?: 30
                trace.step("Cleaning up logs", mapOf("olderThanDays" to days.toString()))
                requestLogService.cleanupOldLogs(days)
                trace.step("Logs cleaned up", mapOf("days" to days.toString()))
                val json = buildJsonObject { put("success", true); put("message", "Logs older than $days days cleaned up") }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Cleanup old logs completed")
            }

            // ─── Logs: Clear All ────────────────────────────────────
            delete("/logs/clear-all") {
                val trace = call.routeTrace()
                trace.step("Clear all request logs started")
                val deleted = requestLogService.clearAllLogs()
                trace.step("All request logs cleared", mapOf("deletedCount" to deleted.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deleted)
                    put("message", "All $deleted request logs cleared")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Clear all request logs completed")
            }

            // ─── Logs: Clear by Vendor ──────────────────────────────
            delete("/logs/clear-vendor/{vendorId}") {
                val trace = call.routeTrace()
                trace.step("Clear vendor logs started")
                val vendorId = call.parameters["vendorId"]
                    ?: return@delete call.respondText(
                        """{"error":"Missing vendorId"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Clearing logs for vendor", mapOf("vendorId" to vendorId))
                val deleted = requestLogService.clearVendorLogs(vendorId)
                trace.step("Vendor logs cleared", mapOf("vendorId" to vendorId, "deletedCount" to deleted.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deleted)
                    put("vendorId", vendorId)
                    put("message", "Cleared $deleted logs for vendor")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Clear vendor logs completed")
            }

            // ─── Logs: Clear Admin/System Logs ──────────────────────
            delete("/logs/clear-admin") {
                val trace = call.routeTrace()
                trace.step("Clear admin logs started")
                val deleted = requestLogService.clearAdminLogs()
                trace.step("Admin logs cleared", mapOf("deletedCount" to deleted.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deleted)
                    put("message", "Cleared $deleted admin/system logs")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Clear admin logs completed")
            }

            // ─── Uploaded Log Files: List ────────────────────────────
            get("/logs/files") {
                val trace = call.routeTrace()
                trace.step("List uploaded log files started")
                val logsDir = java.io.File("logs/vendors")
                val files = mutableListOf<kotlinx.serialization.json.JsonObject>()

                if (logsDir.exists() && logsDir.isDirectory) {
                    logsDir.listFiles()?.filter { it.isDirectory }?.forEach { vendorDir ->
                        val vendorFolder = vendorDir.name
                        vendorDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                            files.add(buildJsonObject {
                                put("vendorFolder", vendorFolder)
                                put("fileName", file.name)
                                put("size", file.length())
                                put("sizeFormatted", formatFileSize(file.length()))
                                put("lastModified", file.lastModified())
                                put("path", file.path)
                            })
                        }
                    }
                }

                // Also check old-style vendor log dirs at top level
                val topLogsDir = java.io.File("logs")
                if (topLogsDir.exists()) {
                    topLogsDir.listFiles()?.filter { it.isDirectory && it.name != "vendors" && it.name != "backend" }?.forEach { vendorDir ->
                        vendorDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                            files.add(buildJsonObject {
                                put("vendorFolder", vendorDir.name)
                                put("fileName", file.name)
                                put("size", file.length())
                                put("sizeFormatted", formatFileSize(file.length()))
                                put("lastModified", file.lastModified())
                                put("path", file.path)
                            })
                        }
                    }
                }

                trace.step("Uploaded log files listed", mapOf("fileCount" to files.size.toString()))
                val json = buildJsonArray { files.sortedByDescending { it["lastModified"]?.jsonPrimitive?.longOrNull ?: 0L }.forEach { add(it) } }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("List uploaded log files completed")
            }

            // ─── Uploaded Log Files: Delete ─────────────────────────
            delete("/logs/files/delete") {
                val trace = call.routeTrace()
                trace.step("Delete uploaded log file started")
                val body = call.receiveText()
                val jsonBody = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
                val filePath = jsonBody["path"]?.jsonPrimitive?.content
                    ?: return@delete call.respondText(
                        """{"error":"path field required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Deleting file", mapOf("path" to filePath))

                // Security check: only allow deleting from logs/ directory
                val file = java.io.File(filePath)
                val canonicalPath = file.canonicalPath
                val logsBasePath = java.io.File("logs").canonicalPath
                if (!canonicalPath.startsWith(logsBasePath) || !file.name.endsWith(".log")) {
                    trace.step("Delete rejected - invalid path", mapOf("path" to filePath))
                    return@delete call.respondText(
                        """{"error":"Invalid file path - only log files in logs/ directory can be deleted"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                }

                if (file.exists()) {
                    val deleted = file.delete()
                    trace.step("File delete result", mapOf("path" to filePath, "deleted" to deleted.toString()))

                    // Clean up empty parent directory
                    val parent = file.parentFile
                    if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                        parent.delete()
                        trace.step("Cleaned up empty directory", mapOf("dir" to parent.path))
                    }

                    val json = buildJsonObject {
                        put("success", deleted)
                        put("message", if (deleted) "File deleted" else "Failed to delete file")
                    }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("File not found", mapOf("path" to filePath))
                    call.respondText(
                        """{"error":"File not found"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound
                    )
                }
                trace.step("Delete uploaded log file completed")
            }

            // ─── Uploaded Log Files: Clear All for Vendor ───────────
            delete("/logs/files/clear-vendor/{vendorFolder}") {
                val trace = call.routeTrace()
                trace.step("Clear vendor log files started")
                val vendorFolder = call.parameters["vendorFolder"]
                    ?: return@delete call.respondText(
                        """{"error":"Missing vendorFolder"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Clearing log files for vendor folder", mapOf("vendorFolder" to vendorFolder))

                var deletedCount = 0

                // Check logs/vendors/<folder>
                val vendorDir1 = java.io.File("logs/vendors/$vendorFolder")
                if (vendorDir1.exists() && vendorDir1.isDirectory) {
                    vendorDir1.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                        if (file.delete()) deletedCount++
                    }
                    if (vendorDir1.listFiles()?.isEmpty() == true) vendorDir1.delete()
                }

                // Check logs/<folder> (old style)
                val vendorDir2 = java.io.File("logs/$vendorFolder")
                if (vendorDir2.exists() && vendorDir2.isDirectory) {
                    vendorDir2.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                        if (file.delete()) deletedCount++
                    }
                    if (vendorDir2.listFiles()?.isEmpty() == true) vendorDir2.delete()
                }

                trace.step("Vendor log files cleared", mapOf("vendorFolder" to vendorFolder, "deletedCount" to deletedCount.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deletedCount)
                    put("message", "Cleared $deletedCount log files for $vendorFolder")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Clear vendor log files completed")
            }

            // ─── Analytics: Overview ──────────────────────────────
            get("/analytics/overview") {
                val trace = call.routeTrace()
                trace.step("Get analytics overview started")
                val now = Clock.System.now()
                val todayStart = Instant.parse(now.toString().substring(0, 10) + "T00:00:00Z")
                val monthStart = Instant.parse(now.toString().substring(0, 7) + "-01T00:00:00Z")

                trace.step("Querying analytics data from database")
                val analytics = transaction {
                    val totalVendors = VendorsTable.selectAll().count().toInt()
                    val activeVendors = VendorsTable.selectAll().where { VendorsTable.isSuspended eq false }.count().toInt()
                    val suspendedVendors = totalVendors - activeVendors

                    // Plan distribution
                    val starterCount = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll().where { SubscriptionPlansTable.name eq "STARTER" }.count().toInt()
                    val businessCount = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll().where { SubscriptionPlansTable.name eq "BUSINESS" }.count().toInt()
                    val enterpriseCount = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll().where { SubscriptionPlansTable.name eq "ENTERPRISE" }.count().toInt()

                    // Orders today
                    val ordersToday = OrdersTable.selectAll()
                        .where { OrdersTable.createdAt greaterEq todayStart }
                        .count().toInt()

                    // Orders this month
                    val ordersThisMonth = OrdersTable.selectAll()
                        .where { OrdersTable.createdAt greaterEq monthStart }
                        .count().toInt()

                    // Revenue today
                    val revenueToday = OrdersTable.selectAll()
                        .where { (OrdersTable.createdAt greaterEq todayStart) and (OrdersTable.status eq "COMPLETED") }
                        .sumOf { it[OrdersTable.total].toDouble() }

                    // Revenue this month
                    val revenueThisMonth = OrdersTable.selectAll()
                        .where { (OrdersTable.createdAt greaterEq monthStart) and (OrdersTable.status eq "COMPLETED") }
                        .sumOf { it[OrdersTable.total].toDouble() }

                    // Total users
                    val totalUsers = UsersTable.selectAll().count().toInt()
                    val activeUsers = UsersTable.selectAll().where { UsersTable.active eq true }.count().toInt()

                    // Workers
                    val totalWorkers = WorkersTable.selectAll().count().toInt()
                    val activeWorkers = WorkersTable.selectAll().where { WorkersTable.active eq true }.count().toInt()

                    // Per-vendor stats
                    val vendorStats = VendorsTable.selectAll().map { row ->
                        val vid = row[VendorsTable.id].value
                        val vOrdersToday = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vid) and (OrdersTable.createdAt greaterEq todayStart) }
                            .count().toInt()
                        val vRevenueMonth = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vid) and (OrdersTable.createdAt greaterEq monthStart) and (OrdersTable.status eq "COMPLETED") }
                            .sumOf { it[OrdersTable.total].toDouble() }
                        val vWorkers = WorkersTable.selectAll()
                            .where { WorkersTable.vendorId eq vid }
                            .count().toInt()
                        val vActiveWorkers = WorkersTable.selectAll()
                            .where { (WorkersTable.vendorId eq vid) and (WorkersTable.active eq true) }
                            .count().toInt()

                        buildJsonObject {
                            put("vendor_id", vid.toString())
                            put("vendor_name", row[VendorsTable.name])
                            put("is_suspended", row[VendorsTable.isSuspended])
                            put("orders_today", vOrdersToday)
                            put("revenue_this_month", vRevenueMonth)
                            put("total_workers", vWorkers)
                            put("active_workers", vActiveWorkers)
                        }
                    }

                    buildJsonObject {
                        putJsonObject("summary") {
                            put("total_vendors", totalVendors)
                            put("active_vendors", activeVendors)
                            put("suspended_vendors", suspendedVendors)
                            put("orders_today", ordersToday)
                            put("orders_this_month", ordersThisMonth)
                            put("revenue_today", revenueToday)
                            put("revenue_this_month", revenueThisMonth)
                            put("total_users", totalUsers)
                            put("active_users", activeUsers)
                            put("total_workers", totalWorkers)
                            put("active_workers", activeWorkers)
                        }
                        putJsonObject("plan_distribution") {
                            put("starter", starterCount)
                            put("business", businessCount)
                            put("enterprise", enterpriseCount)
                        }
                        put("vendors", buildJsonArray { vendorStats.forEach { add(it) } })
                    }
                }
                trace.step("Analytics overview fetched")
                call.respondText(analytics.toString(), ContentType.Application.Json)
                trace.step("Get analytics overview completed")
            }

            get("/analytics/platform") {
                val trace = call.routeTrace()
                trace.step("Get platform analytics started")
                val now = Clock.System.now()
                val monthStart = Instant.parse(now.toString().substring(0, 7) + "-01T00:00:00Z")

                val platform = transaction {
                    // Total vendors
                    val totalVendors = VendorsTable.selectAll().count().toInt()
                    val activeVendors = VendorsTable.selectAll()
                        .where { VendorsTable.isSuspended eq false }.count().toInt()

                    // New vendors this month
                    val newThisMonth = VendorsTable.selectAll()
                        .where { VendorsTable.createdAt greaterEq monthStart }.count().toInt()

                    // MRR = sum of plan prices for all active subscriptions
                    val mrr = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll()
                        .where { VendorSubscriptionsTable.status eq "ACTIVE" }
                        .sumOf { it[SubscriptionPlansTable.priceEgp].toLong() }

                    // Active vs expired subscriptions
                    val activeSubs = VendorSubscriptionsTable.selectAll()
                        .where { VendorSubscriptionsTable.status eq "ACTIVE" }.count().toInt()
                    val expiredSubs = VendorSubscriptionsTable.selectAll()
                        .where { VendorSubscriptionsTable.status eq "EXPIRED" }.count().toInt()

                    // Platform-wide orders & revenue this month
                    val ordersThisMonth = OrdersTable.selectAll()
                        .where { OrdersTable.createdAt greaterEq monthStart }.count().toInt()
                    val revenueThisMonth = OrdersTable.selectAll()
                        .where { (OrdersTable.createdAt greaterEq monthStart) and (OrdersTable.status eq "COMPLETED") }
                        .sumOf { it[OrdersTable.total].toDouble() }

                    val avgRevenuePerVendor = if (activeVendors > 0) revenueThisMonth / activeVendors else 0.0

                    // Plan distribution with revenue
                    val planRevenue = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll()
                        .where { VendorSubscriptionsTable.status eq "ACTIVE" }
                        .groupBy { it[SubscriptionPlansTable.name] }
                        .map { (planName, rows) ->
                            buildJsonObject {
                                put("plan", planName)
                                put("count", rows.size)
                                put("revenue", rows.sumOf { it[SubscriptionPlansTable.priceEgp].toLong() })
                            }
                        }

                    // Top 10 vendors by revenue this month
                    val topVendors = VendorsTable.selectAll().map { row ->
                        val vid = row[VendorsTable.id].value
                        val rev = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vid) and (OrdersTable.createdAt greaterEq monthStart) and (OrdersTable.status eq "COMPLETED") }
                            .sumOf { it[OrdersTable.total].toDouble() }
                        val orders = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vid) and (OrdersTable.createdAt greaterEq monthStart) }
                            .count().toInt()
                        Triple(row, rev, orders)
                    }.sortedByDescending { it.second }.take(10).map { (row, rev, orders) ->
                        buildJsonObject {
                            put("vendor_id", row[VendorsTable.id].value.toString())
                            put("vendor_name", row[VendorsTable.name])
                            put("revenue", rev)
                            put("orders", orders)
                            put("is_suspended", row[VendorsTable.isSuspended])
                        }
                    }

                    buildJsonObject {
                        put("total_vendors", totalVendors)
                        put("active_vendors", activeVendors)
                        put("new_this_month", newThisMonth)
                        put("mrr", mrr)
                        put("active_subscriptions", activeSubs)
                        put("expired_subscriptions", expiredSubs)
                        put("orders_this_month", ordersThisMonth)
                        put("revenue_this_month", revenueThisMonth)
                        put("avg_revenue_per_vendor", avgRevenuePerVendor)
                        put("plan_revenue", buildJsonArray { planRevenue.forEach { add(it) } })
                        put("top_vendors", buildJsonArray { topVendors.forEach { add(it) } })
                    }
                }

                trace.step("Platform analytics fetched")
                call.respondText(platform.toString(), ContentType.Application.Json)
                trace.step("Get platform analytics completed")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
        bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
        else -> "$bytes B"
    }
}
