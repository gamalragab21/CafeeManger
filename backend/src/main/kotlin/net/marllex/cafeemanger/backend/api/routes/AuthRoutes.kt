package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.domain.service.AuthService
import org.koin.java.KoinJavaComponent.inject

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class RegisterRequest(
    val vendor_name: String,
    val vendor_address: String,
    val vendor_phone: String,
    val manager_name: String,
    val manager_phone: String,
    val manager_email: String? = null,
    val password: String,
    val store_type: String? = null,
    val enable_tables: Boolean = true,
    val enable_dine_in: Boolean = true,
    val enable_delivery: Boolean = true,
    val digital_menu_url: String? = null,
)

@Serializable
data class RefreshTokenRequest(val refresh_token: String)

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
    val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    val id: String,
    val vendor_id: String,
    val role: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val active: Boolean = true
)

fun Route.authRoutes() {
    val authService by inject<AuthService>(
        clazz = AuthService::class.java
    )

    route("/api/v1/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            require(request.phone.isNotBlank()) { "Phone is required" }
            require(request.password.isNotBlank()) { "Password is required" }

            val result = authService.login(request.phone, request.password)

            call.respond(
                HttpStatusCode.OK, AuthResponse(
                    access_token = result.accessToken,
                    refresh_token = result.refreshToken,
                    user = AuthUserDto(
                        id = result.userId,
                        vendor_id = result.vendorId,
                        role = result.role,
                        name = result.name,
                        phone = result.phone,
                        email = result.email
                    )
                )
            )
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            require(request.vendor_name.isNotBlank()) { "Vendor name is required" }
            require(request.vendor_address.isNotBlank()) { "Vendor address is required" }
            require(request.vendor_phone.isNotBlank()) { "Vendor phone is required" }
            require(request.manager_name.isNotBlank()) { "Manager name is required" }
            require(request.manager_phone.isNotBlank()) { "Manager phone is required" }
            require(request.password.length >= 6) { "Password must be at least 6 characters" }

            val result = authService.register(
                vendorName = request.vendor_name,
                vendorAddress = request.vendor_address,
                vendorPhone = request.vendor_phone,
                managerName = request.manager_name,
                managerPhone = request.manager_phone,
                managerEmail = request.manager_email,
                password = request.password,
                storeType = request.store_type,
                enableTables = request.enable_tables,
                enableDineIn = request.enable_dine_in,
                enableDelivery = request.enable_delivery,
                digitalMenuUrl = request.digital_menu_url,
            )

            call.respond(
                HttpStatusCode.Created, AuthResponse(
                    access_token = result.accessToken,
                    refresh_token = result.refreshToken,
                    user = AuthUserDto(
                        id = result.userId,
                        vendor_id = result.vendorId,
                        role = result.role,
                        name = result.name,
                        phone = result.phone,
                        email = result.email
                    )
                )
            )
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            require(request.refresh_token.isNotBlank()) { "Refresh token is required" }

            val result = authService.refreshToken(request.refresh_token)

            call.respond(
                HttpStatusCode.OK, AuthResponse(
                    access_token = result.accessToken,
                    refresh_token = result.refreshToken,
                    user = AuthUserDto(
                        id = result.userId,
                        vendor_id = result.vendorId,
                        role = result.role,
                        name = result.name,
                        phone = result.phone,
                        email = result.email
                    )
                )
            )
        }

        post("/logout") {
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}
