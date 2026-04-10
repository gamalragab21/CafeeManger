package net.marllex.waselak.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.api.routes.*
import net.marllex.waselak.backend.data.database.VendorsTable
import net.marllex.waselak.backend.domain.service.AccountSuspendedException
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.configureRouting() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    routing {
        // Health check
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP", "service" to "Waselak API"))
        }

        // Public routes (no auth required)
        authRoutes()
        // Public receipt view
        orderSharePublicRoutes()
        // Public digital menu page
        digitalMenuRoutes()
        // Admin routes (password-protected, no JWT)
        adminRoutes()
        // Admin API routes (JWT Bearer auth for CMP admin app)
        adminApiRoutes()
        // Admin CMS analytics routes (vendor analytics via admin JWT)
        adminAnalyticsRoutes()
        // CMS App Settings (social links etc.) — inside admin JWT auth
        cmsAppSettingsRoutes()
        // Admin dashboard (cookie-based JWT)
        adminDashboardRoutes()

        // CRM Sales Dashboard
        crmRoutes()

        // Protected routes
        authenticate("auth-jwt") {
            // Global interceptor: block ALL API calls if vendor is suspended
            intercept(ApplicationCallPipeline.Call) {
                val principal = call.principal<UserPrincipal>()
                if (principal != null) {
                    val vendorUuid = try { UUID.fromString(principal.vendorId) } catch (_: Exception) { null }
                    if (vendorUuid != null) {
                        val suspensionInfo = transaction {
                            VendorsTable.selectAll()
                                .where { VendorsTable.id eq vendorUuid }
                                .firstOrNull()
                                ?.let {
                                    it[VendorsTable.isSuspended] to it[VendorsTable.suspensionReason]
                                }
                        }
                        if (suspensionInfo != null && suspensionInfo.first) {
                            val reason = suspensionInfo.second ?: "No reason provided"
                            throw AccountSuspendedException("Your vendor account is suspended: $reason")
                        }
                    }
                }
            }

            vendorRoutes()
            categoryRoutes()
            orderRoutes()
            itemRoutes()
            tableRoutes()
            reservationRoutes()
            userManagementRoutes()
            analyticsRoutes()
            analyticsDashboardRoutes()
            taxPlacesRoutes()
            stockRoutes()
            recipeRoutes()
            customerRoutes()
            offerRoutes()
            workerRoutes()
            attendanceRoutes()
            overtimeRoutes()
            announcementRoutes()
            overtimeRoutes()
            chatbotRoutes()
            returnRoutes() // Product returns & exchanges
            kdsRoutes() // Kitchen Display System
            cashDrawerRoutes() // Cash drawer management
            splitPaymentRoutes() // Split payments per order
            prescriptionRoutes() // Pharmacy prescriptions
            drugInteractionRoutes() // Drug interaction warnings
            customerCreditRoutes() // Customer credit accounts
            installmentRoutes() // Installment payment plans
            scheduledOrderRoutes() // Pre-orders / scheduled orders
            supplierRoutes() // Suppliers & purchase orders
            notificationRoutes() // Unified notifications & device tokens
            exportRoutes() // Export data as PDF/Excel (MANAGER only)
            uploadRoutes() // File upload (multipart)
            logRoutes() // App log upload
            appUpdateRoutes() // Public: check-update endpoint only
        }
    }
}
