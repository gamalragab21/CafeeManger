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
import io.ktor.server.request.header
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.SubscriptionPlansTable
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.java.KoinJavaComponent
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
    val offline_mode_enabled: Boolean = false,
    val biometric_required: Boolean = false,
    val enable_offline_mode: Boolean = false,
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
    val enable_stock: Boolean = true,
    val enable_attendance: Boolean = true,
    val enable_overtime: Boolean = false,
    val enable_salary: Boolean = false,
    val enable_customers: Boolean = true,
    val enable_export: Boolean = true,
    val enable_digital_receipt: Boolean = false,
    val enable_whatsapp_receipt: Boolean = false,
    val enable_worker_qrcode: Boolean = false,
    val enable_loyalty: Boolean = false,
    val enable_manual_discount: Boolean = true,
    val enable_offers: Boolean = true,
    // Loyalty & discount settings
    val loyalty_enabled: Boolean = false,
    val points_earn_rate: Double = 1.0,
    val points_redeem_rate: Double = 0.1,
    val min_points_redeem: Int = 100,
    val max_manual_discount_percent: Double = 100.0,
    val manual_discount_requires_pin: Boolean = false,
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
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
    val biometric_required: Boolean? = null,
    val enable_offline_mode: Boolean? = null,
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
    val enable_stock: Boolean? = null,
    val enable_attendance: Boolean? = null,
    val enable_overtime: Boolean? = null,
    val enable_salary: Boolean? = null,
    val enable_customers: Boolean? = null,
    val enable_export: Boolean? = null,
    val enable_digital_receipt: Boolean? = null,
    val enable_whatsapp_receipt: Boolean? = null,
    val enable_worker_qrcode: Boolean? = null,
    val enable_loyalty: Boolean? = null,
    val enable_manual_discount: Boolean? = null,
    val enable_offers: Boolean? = null,
    // Loyalty & discount settings
    val loyalty_enabled: Boolean? = null,
    val points_earn_rate: Double? = null,
    val points_redeem_rate: Double? = null,
    val min_points_redeem: Int? = null,
    val max_manual_discount_percent: Double? = null,
    val manual_discount_requires_pin: Boolean? = null,
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

@Serializable
data class PlanFeaturesResponse(
    val plan_name: String,
    val plan_display_name: String,
    val price_egp: Int,
    val features: PlanFeaturesDto,
    val limits: PlanLimitsDto,
    val usage: PlanUsageDto,
)

@Serializable
data class PlanFeaturesDto(
    val stock_management: Boolean,
    val worker_attendance: Boolean,
    val overtime: Boolean,
    val salaries: Boolean,
    val delivery_module: Boolean,
    val customer_management: Boolean,
    val table_management: Boolean,
    val digital_receipt: Boolean,
    val worker_qrcode: Boolean,
    val loyalty_points: Boolean = false,
    val manual_discount: Boolean = false,
    val offers_management: Boolean = false,
    val analytics: String,
    val digital_menu: String,
)

@Serializable
data class PlanLimitsDto(
    val max_managers: Int,
    val max_cashiers: Int,
    val max_delivery: Int,
    val max_orders_per_month: Int,
    val max_menu_items: Int,
    val max_branches: Int,
)

@Serializable
data class PlanUsageDto(
    val managers: Int,
    val cashiers: Int,
    val delivery: Int,
    val monthly_orders: Int,
    val menu_items: Int,
)

@Serializable
data class PlanSummaryResponse(
    val name: String,
    val display_name: String,
    val price_egp: Int,
    val features: PlanFeaturesDto,
    val limits: PlanLimitsDto,
)

fun Route.vendorRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/vendors") {
        // ─── List all available plans ───────────────────────────
        get("/plans") {
            val trace = call.routeTrace()
            trace.step("List plans started")
            currentUser() // authenticate
            val plans = planService.listActivePlans()
            trace.step("Fetched active plans", mapOf("count" to plans.size.toString()))
            val response = plans.map { plan ->
                PlanSummaryResponse(
                    name = plan[SubscriptionPlansTable.name],
                    display_name = plan[SubscriptionPlansTable.displayName],
                    price_egp = plan[SubscriptionPlansTable.priceEgp],
                    features = PlanFeaturesDto(
                        stock_management = plan[SubscriptionPlansTable.stockManagement],
                        worker_attendance = plan[SubscriptionPlansTable.workerAttendance],
                        overtime = plan[SubscriptionPlansTable.overtime],
                        salaries = plan[SubscriptionPlansTable.salaries],
                        delivery_module = plan[SubscriptionPlansTable.deliveryModule],
                        customer_management = plan[SubscriptionPlansTable.customerManagement],
                        table_management = plan[SubscriptionPlansTable.tableManagement],
                        digital_receipt = plan[SubscriptionPlansTable.digitalReceipt],
                        worker_qrcode = plan[SubscriptionPlansTable.workerQrcode],
                        loyalty_points = plan[SubscriptionPlansTable.loyaltyPoints],
                        manual_discount = plan[SubscriptionPlansTable.manualDiscount],
                        offers_management = plan[SubscriptionPlansTable.offersManagement],
                        analytics = plan[SubscriptionPlansTable.analytics],
                        digital_menu = plan[SubscriptionPlansTable.digitalMenu],
                    ),
                    limits = PlanLimitsDto(
                        max_managers = plan[SubscriptionPlansTable.maxManagers],
                        max_cashiers = plan[SubscriptionPlansTable.maxCashiers],
                        max_delivery = plan[SubscriptionPlansTable.maxDelivery],
                        max_orders_per_month = plan[SubscriptionPlansTable.maxOrdersPerMonth],
                        max_menu_items = plan[SubscriptionPlansTable.maxMenuItems],
                        max_branches = plan[SubscriptionPlansTable.maxBranches],
                    ),
                )
            }
            trace.step("List plans completed", mapOf("count" to response.size.toString()))
            call.respond(HttpStatusCode.OK, response)
        }

        // ─── Plan info for current vendor ───────────────────────
        get("/me/plan") {
            val trace = call.routeTrace()
            trace.step("Get vendor plan started")
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Fetching vendor plan limits", mapOf("vendorId" to principal.vendorId))
            val limits = planService.getVendorPlanLimits(vendorUUID)
            trace.step("Plan limits fetched", mapOf("planName" to limits.planName))
            val usage = planService.getVendorUsage(vendorUUID)
            trace.step("Plan usage fetched", mapOf(
                "managers" to (usage["managers"] as Int).toString(),
                "cashiers" to (usage["cashiers"] as Int).toString(),
                "delivery" to (usage["delivery"] as Int).toString(),
                "monthlyOrders" to (usage["monthlyOrders"] as Int).toString(),
                "menuItems" to (usage["menuItems"] as Int).toString()
            ))

            trace.step("Get vendor plan completed")
            call.respond(HttpStatusCode.OK, PlanFeaturesResponse(
                plan_name = limits.planName,
                plan_display_name = limits.planDisplayName,
                price_egp = limits.priceEgp,
                features = PlanFeaturesDto(
                    stock_management = limits.stockManagement,
                    worker_attendance = limits.workerAttendance,
                    overtime = limits.overtime,
                    salaries = limits.salaries,
                    delivery_module = limits.deliveryModule,
                    customer_management = limits.customerManagement,
                    table_management = limits.tableManagement,
                    digital_receipt = limits.digitalReceipt,
                    worker_qrcode = limits.workerQrcode,
                    loyalty_points = limits.loyaltyPoints,
                    manual_discount = limits.manualDiscount,
                    offers_management = limits.offersManagement,
                    analytics = limits.analytics,
                    digital_menu = limits.digitalMenu,
                ),
                limits = PlanLimitsDto(
                    max_managers = limits.effectiveMaxManagers(),
                    max_cashiers = limits.effectiveMaxCashiers(),
                    max_delivery = limits.effectiveMaxDelivery(),
                    max_orders_per_month = limits.effectiveMaxOrders(),
                    max_menu_items = limits.effectiveMaxItems(),
                    max_branches = limits.maxBranches,
                ),
                usage = PlanUsageDto(
                    managers = usage["managers"] as Int,
                    cashiers = usage["cashiers"] as Int,
                    delivery = usage["delivery"] as Int,
                    monthly_orders = usage["monthlyOrders"] as Int,
                    menu_items = usage["menuItems"] as Int,
                ),
            ))
        }

        get("/me") {
            val trace = call.routeTrace()
            trace.step("Get vendor profile started")
            val principal = currentUser()
            trace.step("Fetching vendor profile", mapOf("vendorId" to principal.vendorId))
            val vendor = transaction {
                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .firstOrNull() ?: throw NoSuchElementException("Vendor not found")
            }
            trace.step("Vendor profile fetched", mapOf(
                "vendorId" to vendor[VendorsTable.id].toString(),
                "name" to vendor[VendorsTable.name]
            ))

            // Get plan limits to combine with vendor toggles
            val planLimits = try {
                planService.getVendorPlanLimits(UUID.fromString(principal.vendorId))
            } catch (_: Exception) {
                null
            }

            val host = call.request.header("Host") ?: "localhost:8080"
            val scheme = call.request.header("X-Forwarded-Proto") ?: "http"

            trace.step("Get vendor profile completed")
            call.respond(HttpStatusCode.OK, VendorResponse(
                id = vendor[VendorsTable.id].toString(),
                name = vendor[VendorsTable.name],
                logo_url = rewriteUploadUrl(vendor[VendorsTable.logoUrl], host, scheme),
                address = vendor[VendorsTable.address],
                contact_phone = vendor[VendorsTable.contactPhone],
                wallet_phone = vendor[VendorsTable.walletPhone],
                default_delivery_fee = vendor[VendorsTable.defaultDeliveryFee].toDouble(),
                store_type = vendor[VendorsTable.storeType],
                enable_tables = vendor[VendorsTable.enableTables],
                enable_kds = vendor[VendorsTable.enableKds],
                enable_dine_in = vendor[VendorsTable.enableDineIn],
                enable_delivery = vendor[VendorsTable.enableDelivery],
                enable_takeaway = vendor[VendorsTable.enableTakeaway],
                enable_in_store = vendor[VendorsTable.enableInStore],
                enable_pickup_later = vendor[VendorsTable.enablePickupLater],
                business_type = vendor[VendorsTable.businessType],
                tax_enabled = vendor[VendorsTable.taxEnabled],
                default_tax_percent = vendor[VendorsTable.defaultTaxPercent].toDouble(),
                stock_mode = vendor[VendorsTable.stockMode],
                offline_mode_enabled = vendor[VendorsTable.offlineModeEnabled],
                biometric_required = vendor[VendorsTable.biometricRequired],
                enable_offline_mode = vendor[VendorsTable.enableOfflineMode],
                digital_menu_url = vendor[VendorsTable.digitalMenuUrl],
                enable_digital_menu = vendor[VendorsTable.enableDigitalMenu],
                enable_recipe = vendor[VendorsTable.enableRecipe],
                // Feature flags: Admin toggle (vendor config) is the SOURCE OF TRUTH
                // If admin enables a feature → it works (even if plan doesn't include it)
                // If admin disables a feature → it's hidden (even if plan includes it)
                // Plan is only the DEFAULT — admin can override for special clients
                enable_split_payment = vendor[VendorsTable.enableSplitPayment],
                enable_cash_drawer = vendor[VendorsTable.enableCashDrawer],
                enable_returns = vendor[VendorsTable.enableReturns],
                enable_customer_credit = vendor[VendorsTable.enableCustomerCredit],
                enable_installments = vendor[VendorsTable.enableInstallments],
                enable_pre_orders = vendor[VendorsTable.enablePreOrders],
                enable_scheduled_orders = vendor[VendorsTable.enableScheduledOrders],
                enable_suppliers = vendor[VendorsTable.enableSuppliers],
                enable_drug_interactions = vendor[VendorsTable.enableDrugInteractions],
                enable_prescriptions = vendor[VendorsTable.enablePrescriptions],
                enable_analytics = vendor[VendorsTable.enableAnalytics],
                enable_announcements = vendor[VendorsTable.enableAnnouncements],
                enable_stock = vendor[VendorsTable.enableStock],
                enable_attendance = vendor[VendorsTable.enableAttendance],
                enable_overtime = vendor[VendorsTable.enableOvertime],
                enable_salary = vendor[VendorsTable.enableSalary],
                enable_customers = vendor[VendorsTable.enableCustomers],
                enable_export = vendor[VendorsTable.enableExport],
                enable_digital_receipt = vendor[VendorsTable.enableDigitalReceipt],
                enable_whatsapp_receipt = vendor[VendorsTable.enableWhatsappReceipt],
                enable_worker_qrcode = vendor[VendorsTable.enableWorkerQrcode],
                enable_loyalty = vendor[VendorsTable.enableLoyalty],
                enable_manual_discount = vendor[VendorsTable.enableManualDiscount],
                enable_offers = vendor[VendorsTable.enableOffers],
                loyalty_enabled = vendor[VendorsTable.loyaltyEnabled],
                points_earn_rate = vendor[VendorsTable.pointsEarnRate].toDouble(),
                points_redeem_rate = vendor[VendorsTable.pointsRedeemRate].toDouble(),
                min_points_redeem = vendor[VendorsTable.minPointsRedeem],
                max_manual_discount_percent = vendor[VendorsTable.maxManualDiscountPercent].toDouble(),
                manual_discount_requires_pin = vendor[VendorsTable.manualDiscountRequiresPin],
                facebook_url = vendor[VendorsTable.facebookUrl],
                landing_page_url = vendor[VendorsTable.landingPageUrl],
                instagram_url = vendor[VendorsTable.instagramUrl],
                whatsapp_number = vendor[VendorsTable.whatsappNumber],
                created_at = vendor[VendorsTable.createdAt].toEpochMilliseconds(),
                updated_at = vendor[VendorsTable.updatedAt].toEpochMilliseconds()
            ))
        }

        put("/me") {
            val trace = call.routeTrace()
            trace.step("Update vendor started")
            val principal = requireRole("MANAGER")

            // Check if vendor is suspended
            val currentVendor = transaction {
                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .firstOrNull() ?: throw NoSuchElementException("Vendor not found")
            }
            if (currentVendor[VendorsTable.isSuspended]) {
                trace.step("Update vendor rejected - vendor suspended", mapOf("vendorId" to principal.vendorId))
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Vendor account is suspended. Contact admin."))
                return@put
            }

            val request = call.receive<UpdateVendorRequest>()

            // Build a map of which settings are being changed
            val settingsChanged = mutableListOf<String>()
            request.name?.let { settingsChanged.add("name") }
            request.logo_url?.let { settingsChanged.add("logo_url") }
            request.address?.let { settingsChanged.add("address") }
            request.contact_phone?.let { settingsChanged.add("contact_phone") }
            request.wallet_phone?.let { settingsChanged.add("wallet_phone") }
            request.default_delivery_fee?.let { settingsChanged.add("default_delivery_fee") }
            request.store_type?.let { settingsChanged.add("store_type") }
            request.enable_tables?.let { settingsChanged.add("enable_tables") }
            request.enable_kds?.let { settingsChanged.add("enable_kds") }
            request.enable_dine_in?.let { settingsChanged.add("enable_dine_in") }
            request.enable_delivery?.let { settingsChanged.add("enable_delivery") }
            request.enable_takeaway?.let { settingsChanged.add("enable_takeaway") }
            request.enable_in_store?.let { settingsChanged.add("enable_in_store") }
            request.enable_pickup_later?.let { settingsChanged.add("enable_pickup_later") }
            request.business_type?.let { settingsChanged.add("business_type") }
            request.tax_enabled?.let { settingsChanged.add("tax_enabled") }
            request.default_tax_percent?.let { settingsChanged.add("default_tax_percent") }
            request.stock_mode?.let { settingsChanged.add("stock_mode") }
            request.biometric_required?.let { settingsChanged.add("biometric_required") }
            request.enable_offline_mode?.let { settingsChanged.add("enable_offline_mode") }
            request.digital_menu_url?.let { settingsChanged.add("digital_menu_url") }
            request.enable_digital_menu?.let { settingsChanged.add("enable_digital_menu") }
            request.enable_recipe?.let { settingsChanged.add("enable_recipe") }

            trace.step("Updating vendor", mapOf(
                "vendorId" to principal.vendorId,
                "name" to (request.name ?: "null"),
                "settingsChanged" to settingsChanged.joinToString(",")
            ))

            val updated = transaction {
                // Delete old logo file if being replaced
                if (request.logo_url != null) {
                    val oldLogoUrl = VendorsTable.selectAll()
                        .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                        .firstOrNull()?.get(VendorsTable.logoUrl)
                    if (oldLogoUrl != null && oldLogoUrl != request.logo_url) {
                        deleteUploadedFile(oldLogoUrl)
                    }
                }
                trace.step("Executing vendor update in database")
                VendorsTable.update({ VendorsTable.id eq UUID.fromString(principal.vendorId) }) { stmt ->
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
                    request.biometric_required?.let { stmt[biometricRequired] = it }
                    request.enable_offline_mode?.let { stmt[enableOfflineMode] = it }
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
                    request.enable_stock?.let { stmt[enableStock] = it }
                    request.enable_attendance?.let { stmt[enableAttendance] = it }
                    request.enable_overtime?.let { stmt[enableOvertime] = it }
                    request.enable_salary?.let { stmt[enableSalary] = it }
                    request.enable_customers?.let { stmt[enableCustomers] = it }
                    request.enable_export?.let { stmt[enableExport] = it }
                    request.enable_digital_receipt?.let { stmt[enableDigitalReceipt] = it }
                    request.enable_whatsapp_receipt?.let { stmt[enableWhatsappReceipt] = it }
                    request.enable_worker_qrcode?.let { stmt[enableWorkerQrcode] = it }
                    request.enable_loyalty?.let { stmt[enableLoyalty] = it }
                    request.enable_manual_discount?.let { stmt[enableManualDiscount] = it }
                    request.enable_offers?.let { stmt[enableOffers] = it }
                    request.loyalty_enabled?.let { stmt[loyaltyEnabled] = it }
                    request.points_earn_rate?.let { stmt[pointsEarnRate] = it.toBigDecimal() }
                    request.points_redeem_rate?.let { stmt[pointsRedeemRate] = it.toBigDecimal() }
                    request.min_points_redeem?.let { stmt[minPointsRedeem] = it }
                    request.max_manual_discount_percent?.let { stmt[maxManualDiscountPercent] = it.toBigDecimal() }
                    request.manual_discount_requires_pin?.let { stmt[manualDiscountRequiresPin] = it }
                    request.facebook_url?.let { stmt[facebookUrl] = it }
                    request.landing_page_url?.let { stmt[landingPageUrl] = it }
                    request.instagram_url?.let { stmt[instagramUrl] = it }
                    request.whatsapp_number?.let { stmt[whatsappNumber] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                VendorsTable.selectAll()
                    .where { VendorsTable.id eq UUID.fromString(principal.vendorId) }
                    .first()
            }
            trace.step("Update vendor result", mapOf(
                "vendorId" to updated[VendorsTable.id].toString(),
                "name" to updated[VendorsTable.name]
            ))

            // Get plan limits for combined flag response
            val planLimits = try {
                planService.getVendorPlanLimits(UUID.fromString(principal.vendorId))
            } catch (_: Exception) { null }

            val putHost = call.request.header("Host") ?: "localhost:8080"
            val putScheme = call.request.header("X-Forwarded-Proto") ?: "http"

            trace.step("Update vendor completed")
            call.respond(HttpStatusCode.OK, VendorResponse(
                id = updated[VendorsTable.id].toString(),
                name = updated[VendorsTable.name],
                logo_url = rewriteUploadUrl(updated[VendorsTable.logoUrl], putHost, putScheme),
                address = updated[VendorsTable.address],
                contact_phone = updated[VendorsTable.contactPhone],
                wallet_phone = updated[VendorsTable.walletPhone],
                default_delivery_fee = updated[VendorsTable.defaultDeliveryFee].toDouble(),
                store_type = updated[VendorsTable.storeType],
                enable_tables = updated[VendorsTable.enableTables],
                enable_kds = updated[VendorsTable.enableKds],
                enable_dine_in = updated[VendorsTable.enableDineIn],
                enable_delivery = updated[VendorsTable.enableDelivery],
                enable_takeaway = updated[VendorsTable.enableTakeaway],
                enable_in_store = updated[VendorsTable.enableInStore],
                enable_pickup_later = updated[VendorsTable.enablePickupLater],
                business_type = updated[VendorsTable.businessType],
                tax_enabled = updated[VendorsTable.taxEnabled],
                default_tax_percent = updated[VendorsTable.defaultTaxPercent].toDouble(),
                stock_mode = updated[VendorsTable.stockMode],
                offline_mode_enabled = updated[VendorsTable.offlineModeEnabled],
                biometric_required = updated[VendorsTable.biometricRequired],
                enable_offline_mode = updated[VendorsTable.enableOfflineMode],
                digital_menu_url = updated[VendorsTable.digitalMenuUrl],
                enable_digital_menu = updated[VendorsTable.enableDigitalMenu],
                enable_recipe = updated[VendorsTable.enableRecipe],
                // Feature flags: Admin toggle is source of truth (same as GET /me)
                enable_split_payment = updated[VendorsTable.enableSplitPayment],
                enable_cash_drawer = updated[VendorsTable.enableCashDrawer],
                enable_returns = updated[VendorsTable.enableReturns],
                enable_customer_credit = updated[VendorsTable.enableCustomerCredit],
                enable_installments = updated[VendorsTable.enableInstallments],
                enable_pre_orders = updated[VendorsTable.enablePreOrders],
                enable_scheduled_orders = updated[VendorsTable.enableScheduledOrders],
                enable_suppliers = updated[VendorsTable.enableSuppliers],
                enable_drug_interactions = updated[VendorsTable.enableDrugInteractions],
                enable_prescriptions = updated[VendorsTable.enablePrescriptions],
                enable_analytics = updated[VendorsTable.enableAnalytics],
                enable_announcements = updated[VendorsTable.enableAnnouncements],
                enable_stock = updated[VendorsTable.enableStock],
                enable_attendance = updated[VendorsTable.enableAttendance],
                enable_overtime = updated[VendorsTable.enableOvertime],
                enable_salary = updated[VendorsTable.enableSalary],
                enable_customers = updated[VendorsTable.enableCustomers],
                enable_export = updated[VendorsTable.enableExport],
                enable_digital_receipt = updated[VendorsTable.enableDigitalReceipt],
                enable_whatsapp_receipt = updated[VendorsTable.enableWhatsappReceipt],
                enable_worker_qrcode = updated[VendorsTable.enableWorkerQrcode],
                enable_loyalty = updated[VendorsTable.enableLoyalty],
                enable_manual_discount = updated[VendorsTable.enableManualDiscount],
                enable_offers = updated[VendorsTable.enableOffers],
                loyalty_enabled = updated[VendorsTable.loyaltyEnabled],
                points_earn_rate = updated[VendorsTable.pointsEarnRate].toDouble(),
                points_redeem_rate = updated[VendorsTable.pointsRedeemRate].toDouble(),
                min_points_redeem = updated[VendorsTable.minPointsRedeem],
                max_manual_discount_percent = updated[VendorsTable.maxManualDiscountPercent].toDouble(),
                manual_discount_requires_pin = updated[VendorsTable.manualDiscountRequiresPin],
                facebook_url = updated[VendorsTable.facebookUrl],
                landing_page_url = updated[VendorsTable.landingPageUrl],
                instagram_url = updated[VendorsTable.instagramUrl],
                whatsapp_number = updated[VendorsTable.whatsappNumber],
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