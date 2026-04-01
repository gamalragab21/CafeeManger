package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.model.DomainDefaults
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
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
    val enable_kds: Boolean = true,
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
    val enable_digital_menu: Boolean = true,
    val enable_recipe: Boolean = true,
    val enable_split_payment: Boolean = true,
    val enable_cash_drawer: Boolean = true,
    val enable_returns: Boolean = true,
    val enable_customer_credit: Boolean = false,
    val enable_installments: Boolean = false,
    val enable_pre_orders: Boolean = false,
    val enable_scheduled_orders: Boolean = false,
    val enable_suppliers: Boolean = true,
    val enable_drug_interactions: Boolean = false,
    val enable_prescriptions: Boolean = false,
    val enable_analytics: Boolean = true,
    val enable_announcements: Boolean = true,
    val loyalty_enabled: Boolean = false,
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
    val users_count: Int = 0,
    val plan_name: String? = null,
    val plan_display_name: String? = null,
    val subscription_status: String? = null,
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
    val enable_kds: Boolean? = null,
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
    val enable_digital_menu: Boolean? = null,
    val enable_recipe: Boolean? = null,
    val enable_split_payment: Boolean? = null,
    val enable_cash_drawer: Boolean? = null,
    val enable_returns: Boolean? = null,
    val enable_customer_credit: Boolean? = null,
    val enable_installments: Boolean? = null,
    val enable_pre_orders: Boolean? = null,
    val enable_scheduled_orders: Boolean? = null,
    val enable_suppliers: Boolean? = null,
    val enable_drug_interactions: Boolean? = null,
    val enable_prescriptions: Boolean? = null,
    val enable_analytics: Boolean? = null,
    val enable_announcements: Boolean? = null,
    val loyalty_enabled: Boolean? = null,
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
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
    val enable_kds: Boolean? = null,
    val enable_dine_in: Boolean? = null,
    val enable_delivery: Boolean? = null,
    val enable_takeaway: Boolean? = null,
    val enable_in_store: Boolean? = null,
    val enable_pickup_later: Boolean? = null,
    // Tax & stock
    val tax_enabled: Boolean? = null,
    val default_tax_percent: Double? = null,
    val stock_mode: String? = null,
    // Subscription plan
    val plan: String = "STARTER",  // STARTER, BUSINESS, ENTERPRISE
    // Manager user
    val manager_name: String,
    val manager_phone: String,
    val manager_email: String? = null,
    val manager_password: String,
)

@Serializable
data class AdminChangePlanRequest(
    val plan: String,       // STARTER, BUSINESS, ENTERPRISE
    val notes: String? = null,
)

@Serializable
data class AdminUpdateOverridesRequest(
    val override_max_managers: Int? = null,
    val override_max_cashiers: Int? = null,
    val override_max_delivery: Int? = null,
    val override_max_orders: Int? = null,
    val override_max_items: Int? = null,
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
private fun mapVendorRow(row: ResultRow, usersCount: Int = 0): AdminVendorResponse {
    val vendorId = row[VendorsTable.id].value
    // Fetch subscription + plan info
    val subscription = VendorSubscriptionsTable.selectAll()
        .where { VendorSubscriptionsTable.vendorId eq vendorId }
        .firstOrNull()
    val planRow = subscription?.let { sub ->
        SubscriptionPlansTable.selectAll()
            .where { SubscriptionPlansTable.id eq sub[VendorSubscriptionsTable.planId] }
            .firstOrNull()
    }

    return AdminVendorResponse(
        id = vendorId.toString(),
        name = row[VendorsTable.name],
        logo_url = row[VendorsTable.logoUrl],
        address = row[VendorsTable.address],
        contact_phone = row[VendorsTable.contactPhone],
        wallet_phone = row[VendorsTable.walletPhone],
        default_delivery_fee = row[VendorsTable.defaultDeliveryFee].toDouble(),
        store_type = row[VendorsTable.storeType],
        enable_tables = row[VendorsTable.enableTables],
        enable_kds = row[VendorsTable.enableKds],
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
        enable_digital_menu = row[VendorsTable.enableDigitalMenu],
        enable_recipe = row[VendorsTable.enableRecipe],
        enable_split_payment = row[VendorsTable.enableSplitPayment],
        enable_cash_drawer = row[VendorsTable.enableCashDrawer],
        enable_returns = row[VendorsTable.enableReturns],
        enable_customer_credit = row[VendorsTable.enableCustomerCredit],
        enable_installments = row[VendorsTable.enableInstallments],
        enable_pre_orders = row[VendorsTable.enablePreOrders],
        enable_scheduled_orders = row[VendorsTable.enableScheduledOrders],
        enable_suppliers = row[VendorsTable.enableSuppliers],
        enable_drug_interactions = row[VendorsTable.enableDrugInteractions],
        enable_prescriptions = row[VendorsTable.enablePrescriptions],
        enable_analytics = row[VendorsTable.enableAnalytics],
        enable_announcements = row[VendorsTable.enableAnnouncements],
        loyalty_enabled = row[VendorsTable.loyaltyEnabled],
        facebook_url = row[VendorsTable.facebookUrl],
        landing_page_url = row[VendorsTable.landingPageUrl],
        instagram_url = row[VendorsTable.instagramUrl],
        whatsapp_number = row[VendorsTable.whatsappNumber],
        users_count = usersCount,
        plan_name = planRow?.get(SubscriptionPlansTable.name),
        plan_display_name = planRow?.get(SubscriptionPlansTable.displayName),
        subscription_status = subscription?.get(VendorSubscriptionsTable.status),
        created_at = row[VendorsTable.createdAt].toEpochMilliseconds(),
        updated_at = row[VendorsTable.updatedAt].toEpochMilliseconds(),
    )
}

// ─── Routes ──────────────────────────────────────────────────────
fun Route.adminRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/admin") {

        // ─── Plan Management ──────────────────────────────────
        // GET /api/v1/admin/plans — list all available plans
        get("/plans") {
            val trace = call.routeTrace()
            trace.step("List plans started")
            if (!requireAdminPassword()) return@get

            trace.step("Fetching active plans")
            val plans = planService.listActivePlans()
            trace.step("Plans fetched", mapOf("count" to plans.size.toString()))
            val json = buildJsonArray {
                plans.forEach { plan ->
                    addJsonObject {
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
                        put("analytics", plan[SubscriptionPlansTable.analytics])
                        put("digital_menu", plan[SubscriptionPlansTable.digitalMenu])
                        put("loyalty_points", plan[SubscriptionPlansTable.loyaltyPoints])
                        put("manual_discount", plan[SubscriptionPlansTable.manualDiscount])
                        put("offers_management", plan[SubscriptionPlansTable.offersManagement])
                    }
                }
            }
            call.respondText(json.toString(), ContentType.Application.Json)
            trace.step("List plans completed")
        }

        // PUT /api/v1/admin/vendors/{id}/plan — change vendor's plan
        put("/vendors/{id}/plan") {
            val trace = call.routeTrace()
            trace.step("Change vendor plan started")
            if (!requireAdminPassword()) return@put

            val vendorId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))
            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            val request = call.receive<AdminChangePlanRequest>()
            trace.step("Received plan change request", mapOf("plan" to request.plan, "notes" to (request.notes ?: "null")))
            val validPlans = listOf("STARTER", "BUSINESS", "ENTERPRISE")
            require(request.plan.uppercase() in validPlans) {
                "Invalid plan. Must be one of: ${validPlans.joinToString()}"
            }

            trace.step("Assigning plan to vendor")
            planService.assignPlanToVendor(vendorUuid, request.plan, request.notes)
            val json = buildJsonObject { put("success", true); put("message", "Plan updated to ${request.plan}") }
            call.respondText(json.toString(), ContentType.Application.Json)
            trace.step("Change vendor plan completed", mapOf("vendorId" to vendorId, "newPlan" to request.plan))
        }

        // PUT /api/v1/admin/vendors/{id}/overrides — update vendor limit overrides
        put("/vendors/{id}/overrides") {
            val trace = call.routeTrace()
            trace.step("Update vendor overrides started")
            if (!requireAdminPassword()) return@put

            val vendorId = call.parameters["id"]
                ?: return@put call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@put call.respondText("""{"error":"Invalid vendor ID format"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            val request = call.receive<AdminUpdateOverridesRequest>()
            trace.step("Received overrides request", mapOf(
                "maxManagers" to (request.override_max_managers?.toString() ?: "null"),
                "maxCashiers" to (request.override_max_cashiers?.toString() ?: "null"),
                "maxDelivery" to (request.override_max_delivery?.toString() ?: "null"),
                "maxOrders" to (request.override_max_orders?.toString() ?: "null"),
                "maxItems" to (request.override_max_items?.toString() ?: "null")
            ))
            trace.step("Applying vendor overrides")
            planService.updateVendorOverrides(
                vendorUuid,
                overrideMaxManagers = request.override_max_managers,
                overrideMaxCashiers = request.override_max_cashiers,
                overrideMaxDelivery = request.override_max_delivery,
                overrideMaxOrders = request.override_max_orders,
                overrideMaxItems = request.override_max_items,
            )
            val json = buildJsonObject { put("success", true); put("message", "Vendor overrides updated") }
            call.respondText(json.toString(), ContentType.Application.Json)
            trace.step("Update vendor overrides completed", mapOf("vendorId" to vendorId))
        }

        // GET /api/v1/admin/vendors/{id}/usage — get vendor usage vs plan limits
        get("/vendors/{id}/usage") {
            val trace = call.routeTrace()
            trace.step("Get vendor usage started")
            if (!requireAdminPassword()) return@get

            val vendorId = call.parameters["id"]
                ?: return@get call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@get call.respondText("""{"error":"Invalid vendor ID format"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            trace.step("Fetching vendor plan limits")
            val limits = planService.getVendorPlanLimits(vendorUuid)
            trace.step("Plan limits fetched", mapOf("planName" to limits.planName))

            trace.step("Fetching vendor usage")
            val usage = planService.getVendorUsage(vendorUuid)
            trace.step("Usage fetched", mapOf(
                "managers" to (usage["managers"] as Int).toString(),
                "cashiers" to (usage["cashiers"] as Int).toString(),
                "menuItems" to (usage["menuItems"] as Int).toString()
            ))

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
                putJsonObject("features") {
                    put("stock_management", limits.stockManagement)
                    put("worker_attendance", limits.workerAttendance)
                    put("delivery_module", limits.deliveryModule)
                    put("analytics", limits.analytics)
                    put("digital_menu", limits.digitalMenu)
                    put("loyalty_points", limits.loyaltyPoints)
                    put("manual_discount", limits.manualDiscount)
                    put("offers_management", limits.offersManagement)
                }
            }
            call.respondText(json.toString(), ContentType.Application.Json)
            trace.step("Get vendor usage completed", mapOf("vendorId" to vendorId))
        }

        // POST /api/v1/admin/vendors — create new vendor with manager
        post("/vendors") {
            val trace = call.routeTrace()
            trace.step("Create vendor started")
            if (!requireAdminPassword()) return@post

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
            require(request.plan.uppercase() in validPlans) {
                "Invalid plan. Must be one of: ${validPlans.joinToString()}"
            }

            // Auto-configure channel flags based on business_type
            val bt = request.business_type.uppercase()
            val defaults = DomainDefaults.forType(bt)
            val enableTables = request.enable_tables ?: defaults.enableTables
            val enableKds = request.enable_kds ?: defaults.enableKds
            val enableDineIn = request.enable_dine_in ?: defaults.enableDineIn
            val enableDelivery = request.enable_delivery ?: defaults.enableDelivery
            val enableTakeaway = request.enable_takeaway ?: defaults.enableTakeaway
            val enableInStore = request.enable_in_store ?: defaults.enableInStore
            val enablePickupLater = request.enable_pickup_later ?: defaults.enablePickupLater
            val taxEnabled = request.tax_enabled ?: defaults.taxEnabled
            val defaultTaxPercent = request.default_tax_percent ?: defaults.defaultTaxPercent
            val stockMode = request.stock_mode ?: defaults.stockMode
            val enableDigitalMenu = defaults.enableDigitalMenu
            val enableRecipe = defaults.enableRecipe

            trace.step("Creating vendor and manager in transaction")
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
                    it[VendorsTable.enableKds] = enableKds
                    it[VendorsTable.enableDineIn] = enableDineIn
                    it[VendorsTable.enableDelivery] = enableDelivery
                    it[VendorsTable.enableTakeaway] = enableTakeaway
                    it[VendorsTable.enableInStore] = enableInStore
                    it[VendorsTable.enablePickupLater] = enablePickupLater
                    it[VendorsTable.taxEnabled] = taxEnabled
                    it[VendorsTable.defaultTaxPercent] = java.math.BigDecimal.valueOf(defaultTaxPercent)
                    it[VendorsTable.stockMode] = stockMode
                    it[VendorsTable.enableDigitalMenu] = enableDigitalMenu
                    it[VendorsTable.enableRecipe] = enableRecipe
                    it[isSuspended] = false
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Assign subscription plan
                planService.assignPlanToVendor(vendorId.value, request.plan)

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

            trace.step("Vendor created", mapOf(
                "vendorId" to result.vendor.id,
                "managerId" to result.manager_id,
                "vendorName" to result.vendor.name
            ))
            call.respond(HttpStatusCode.Created, result)
            trace.step("Create vendor completed")
        }

        // GET /api/v1/admin/vendors — list all stores
        get("/vendors") {
            val trace = call.routeTrace()
            trace.step("List vendors started")
            if (!requireAdminPassword()) return@get

            trace.step("Fetching all vendors")
            val vendors = transaction {
                VendorsTable.selectAll().map { row ->
                    val vendorId = row[VendorsTable.id]
                    val usersCount = UsersTable.selectAll()
                        .where { UsersTable.vendorId eq vendorId }
                        .count().toInt()
                    mapVendorRow(row, usersCount)
                }
            }
            trace.step("Vendors fetched", mapOf("count" to vendors.size.toString()))

            call.respond(HttpStatusCode.OK, vendors)
            trace.step("List vendors completed")
        }

        // GET /api/v1/admin/vendors/{id} — get single store by ID
        get("/vendors/{id}") {
            val trace = call.routeTrace()
            trace.step("Get vendor by ID started")
            if (!requireAdminPassword()) return@get

            val vendorId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            trace.step("Querying vendor from database")
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
                trace.step("Vendor not found", mapOf("vendorId" to vendorId))
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                trace.step("Vendor found", mapOf("vendorId" to vendorId, "vendorName" to vendor.name))
                call.respond(HttpStatusCode.OK, vendor)
            }
            trace.step("Get vendor by ID completed")
        }

        // PUT /api/v1/admin/vendors/{id} — update any store by ID
        put("/vendors/{id}") {
            val trace = call.routeTrace()
            trace.step("Update vendor started")
            if (!requireAdminPassword()) return@put

            val vendorId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            val request = call.receive<AdminUpdateVendorRequest>()
            trace.step("Received update request", mapOf(
                "name" to (request.name ?: "null"),
                "businessType" to (request.business_type ?: "null"),
                "isSuspended" to (request.is_suspended?.toString() ?: "null")
            ))

            trace.step("Updating vendor in database")
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
                    request.enable_kds?.let { stmt[enableKds] = it }
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
                    request.enable_digital_menu?.let { stmt[enableDigitalMenu] = it }
                    request.enable_recipe?.let { stmt[enableRecipe] = it }
                    request.enable_split_payment?.let { stmt[enableSplitPayment] = it }
                    request.enable_cash_drawer?.let { stmt[enableCashDrawer] = it }
                    request.enable_returns?.let { stmt[enableReturns] = it }
                    request.enable_customer_credit?.let { stmt[enableCustomerCredit] = it }
                    request.enable_installments?.let { stmt[enableInstallments] = it }
                    request.enable_pre_orders?.let { stmt[enablePreOrders] = it }
                    request.enable_scheduled_orders?.let { stmt[enableScheduledOrders] = it }
                    request.enable_suppliers?.let { stmt[enableSuppliers] = it }
                    request.enable_drug_interactions?.let { stmt[enableDrugInteractions] = it }
                    request.enable_prescriptions?.let { stmt[enablePrescriptions] = it }
                    request.enable_analytics?.let { stmt[enableAnalytics] = it }
                    request.enable_announcements?.let { stmt[enableAnnouncements] = it }
                    request.loyalty_enabled?.let { stmt[loyaltyEnabled] = it }
                    request.facebook_url?.let { stmt[facebookUrl] = it }
                    request.landing_page_url?.let { stmt[landingPageUrl] = it }
                    request.instagram_url?.let { stmt[instagramUrl] = it }
                    request.whatsapp_number?.let { stmt[whatsappNumber] = it }
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
                trace.step("Vendor not found for update", mapOf("vendorId" to vendorId))
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                trace.step("Vendor updated", mapOf("vendorId" to vendorId, "vendorName" to updated.name))
                call.respond(HttpStatusCode.OK, updated)
            }
            trace.step("Update vendor completed")
        }

        // DELETE /api/v1/admin/vendors/{id} — delete vendor and ALL associated data
        delete("/vendors/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete vendor started")
            if (!requireAdminPassword()) return@delete

            val vendorId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing vendor ID"))

            val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid vendor ID format"))
            }
            trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

            trace.step("Deleting vendor and all associated data")
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

                // 4. Delete item variant options/groups (before items)
                val itemIds = ItemsTable.selectAll()
                    .where { ItemsTable.vendorId eq vendorUuid }
                    .map { it[ItemsTable.id].value }
                if (itemIds.isNotEmpty()) {
                    val groupIds = ItemVariantGroupsTable.selectAll()
                        .where { ItemVariantGroupsTable.itemId inList itemIds }
                        .map { it[ItemVariantGroupsTable.id].value }
                    if (groupIds.isNotEmpty()) {
                        ItemVariantOptionsTable.deleteWhere { ItemVariantOptionsTable.groupId inList groupIds }
                    }
                    ItemVariantGroupsTable.deleteWhere { ItemVariantGroupsTable.itemId inList itemIds }
                }

                // 5. Delete recipe ingredients, then recipes
                val recipeIds = RecipesTable.selectAll()
                    .where { RecipesTable.vendorId eq vendorUuid }
                    .map { it[RecipesTable.id].value }
                if (recipeIds.isNotEmpty()) {
                    RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId inList recipeIds }
                }
                RecipesTable.deleteWhere { RecipesTable.vendorId eq vendorUuid }

                // 6. Delete worker-dependent records
                val workerIds = WorkersTable.selectAll()
                    .where { WorkersTable.vendorId eq vendorUuid }
                    .map { it[WorkersTable.id].value }
                if (workerIds.isNotEmpty()) {
                    AttendanceAuthLogsTable.deleteWhere { AttendanceAuthLogsTable.workerId inList workerIds }
                    AttendanceTable.deleteWhere { AttendanceTable.workerId inList workerIds }
                    SalaryPaymentsTable.deleteWhere { SalaryPaymentsTable.workerId inList workerIds }
                }

                // 7. Delete overtime entries
                OvertimeTable.deleteWhere { OvertimeTable.vendorId eq vendorUuid }

                // 8. Delete announcement reads, then announcements
                val announcementIds = AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.vendorId eq vendorUuid }
                    .map { it[AnnouncementsTable.id].value }
                if (announcementIds.isNotEmpty()) {
                    AnnouncementReadsTable.deleteWhere { AnnouncementReadsTable.announcementId inList announcementIds }
                }
                AnnouncementsTable.deleteWhere { AnnouncementsTable.vendorId eq vendorUuid }

                // 9. Delete customer addresses, then customers
                val customerIds = CustomersTable.selectAll()
                    .where { CustomersTable.vendorId eq vendorUuid }
                    .map { it[CustomersTable.id].value }
                if (customerIds.isNotEmpty()) {
                    CustomerAddressesTable.deleteWhere { CustomerAddressesTable.customerId inList customerIds }
                }
                CustomersTable.deleteWhere { CustomersTable.vendorId eq vendorUuid }

                // 10. Delete reservations
                ReservationsTable.deleteWhere { ReservationsTable.vendorId eq vendorUuid }

                // 11. Delete vendor-level tables
                StockTable.deleteWhere { StockTable.vendorId eq vendorUuid }
                OrdersTable.deleteWhere { OrdersTable.vendorId eq vendorUuid }
                ItemsTable.deleteWhere { ItemsTable.vendorId eq vendorUuid }
                WorkerRolesTable.deleteWhere { WorkerRolesTable.vendorId eq vendorUuid }
                WorkersTable.deleteWhere { WorkersTable.vendorId eq vendorUuid }
                TaxPlacesTable.deleteWhere { TaxPlacesTable.vendorId eq vendorUuid }
                TablesTable.deleteWhere { TablesTable.vendorId eq vendorUuid }
                CategoriesTable.deleteWhere { CategoriesTable.vendorId eq vendorUuid }

                // 12. Delete users and their refresh tokens
                val userIds = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUuid }
                    .map { it[UsersTable.id].value }
                if (userIds.isNotEmpty()) {
                    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId inList userIds }
                }
                UsersTable.deleteWhere { UsersTable.vendorId eq vendorUuid }

                // 13. Delete vendor subscription
                VendorSubscriptionsTable.deleteWhere { VendorSubscriptionsTable.vendorId eq vendorUuid }

                // 14. Delete request/audit logs
                RequestLogsTable.deleteWhere { RequestLogsTable.vendorId eq vendorUuid.toString() }

                // 15. Finally delete the vendor
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

            if (!deleted) {
                trace.step("Vendor not found for deletion", mapOf("vendorId" to vendorId))
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vendor not found"))
            } else {
                trace.step("Vendor and associated data deleted", mapOf("vendorId" to vendorId))
                val successJson = buildJsonObject { put("success", true); put("message", "Vendor and all associated data deleted") }
                call.respondText(successJson.toString(), ContentType.Application.Json)
            }
            trace.step("Delete vendor completed")
        }
    }
}