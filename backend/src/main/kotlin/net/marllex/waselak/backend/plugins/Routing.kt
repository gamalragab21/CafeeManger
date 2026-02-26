package net.marllex.waselak.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.marllex.waselak.backend.api.routes.*

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

        // Protected routes
        authenticate("auth-jwt") {
            vendorRoutes()
            categoryRoutes()
            orderRoutes()
            itemRoutes()
            tableRoutes()
            userManagementRoutes()
            analyticsRoutes()
            analyticsDashboardRoutes()
            taxPlacesRoutes()
            stockRoutes()
            customerRoutes()
            workerRoutes()
            attendanceRoutes()
            announcementRoutes()
            overtimeRoutes()
            chatbotRoutes()
            exportRoutes() // Export data as PDF/Excel (MANAGER only)
        }
    }
}
