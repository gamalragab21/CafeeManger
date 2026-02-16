package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.data.database.AttendanceTable
import net.marllex.waselak.backend.data.database.SalaryPaymentsTable
import net.marllex.waselak.backend.data.database.WorkersTable
import net.marllex.waselak.backend.config.JwtConfig
import net.marllex.waselak.backend.domain.service.AuthService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.application
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
    @kotlinx.serialization.SerialName("app_type") val appType: String? = null,
)

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

            // Enforce role-to-app access: users can only login to their matching app
            val appType = request.appType
            if (appType != null) {
                val userRole = result.role.uppercase()
                val allowed = when (appType.uppercase()) {
                    "MANAGER" -> userRole == "MANAGER"
                    "CASHIER" -> userRole == "CASHIER" || userRole == "MANAGER"
                    "DELIVERY" -> userRole == "DELIVERY" || userRole == "MANAGER"
                    else -> true
                }
                if (!allowed) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your account role ($userRole) does not have permission to access the $appType app")
                    )
                    return@post
                }
            }

            // Auto check-in: if user has linked worker, auto record attendance
            try {
                transaction {
                    val userUUID = UUID.fromString(result.userId)
                    val worker = WorkersTable.selectAll()
                        .where { WorkersTable.userId eq userUUID }
                        .firstOrNull()

                    if (worker != null) {
                        val now = Clock.System.now()
                        val todayStr = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                        val workerId = worker[WorkersTable.id].value
                        val vendorUUID = UUID.fromString(result.vendorId)

                        // Check if already checked in today
                        val existingAttendance = AttendanceTable.selectAll().where {
                            (AttendanceTable.workerId eq workerId) and
                            (AttendanceTable.date eq todayStr)
                        }.firstOrNull()

                        if (existingAttendance == null) {
                            // Auto check-in
                            val attendanceId = AttendanceTable.insertAndGetId {
                                it[AttendanceTable.vendorId] = vendorUUID
                                it[AttendanceTable.workerId] = workerId
                                it[date] = todayStr
                                it[checkIn] = now
                                it[recordedBy] = userUUID
                                it[note] = "Auto check-in on login"
                                it[createdAt] = now
                                it[updatedAt] = now
                            }

                            // Auto-generate salary record
                            val salaryType = worker[WorkersTable.salaryType]
                            val salaryAmount = worker[WorkersTable.salaryAmount]

                            if (salaryType == "DAILY") {
                                val existing = SalaryPaymentsTable.selectAll().where {
                                    (SalaryPaymentsTable.workerId eq workerId) and
                                    (SalaryPaymentsTable.periodStart eq todayStr) and
                                    (SalaryPaymentsTable.periodType eq "DAY")
                                }.firstOrNull()

                                if (existing == null) {
                                    SalaryPaymentsTable.insertAndGetId {
                                        it[SalaryPaymentsTable.vendorId] = vendorUUID
                                        it[SalaryPaymentsTable.workerId] = workerId
                                        it[periodType] = "DAY"
                                        it[periodStart] = todayStr
                                        it[periodEnd] = todayStr
                                        it[workedDays] = 1
                                        it[amount] = salaryAmount
                                        it[paid] = false
                                        it[createdAt] = now
                                        it[updatedAt] = now
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Don't fail login if attendance auto-record fails
            }

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
            // Auto check-out: try to parse JWT from header
            try {
                val authHeader = call.request.headers["Authorization"]
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    val token = authHeader.removePrefix("Bearer ")
                    val jwtConfig = JwtConfig(this@route.application.environment.config)
                    val verifier = JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                        .withIssuer(jwtConfig.issuer)
                        .withAudience(jwtConfig.audience)
                        .build()
                    val decoded = verifier.verify(token)
                    val userId = decoded.subject

                    transaction {
                        val userUUID = UUID.fromString(userId)
                        val worker = WorkersTable.selectAll()
                            .where { WorkersTable.userId eq userUUID }
                            .firstOrNull()

                        if (worker != null) {
                            val now = Clock.System.now()
                            val todayStr = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            val workerId = worker[WorkersTable.id].value

                            // Find today's open attendance
                            val openAttendance = AttendanceTable.selectAll().where {
                                (AttendanceTable.workerId eq workerId) and
                                (AttendanceTable.date eq todayStr) and
                                (AttendanceTable.checkOut.isNull())
                            }.firstOrNull()

                            if (openAttendance != null) {
                                val checkInTime = openAttendance[AttendanceTable.checkIn]
                                val workedMinutes = ((now.toEpochMilliseconds() - checkInTime.toEpochMilliseconds()) / 60000).toInt()

                                AttendanceTable.update({
                                    AttendanceTable.id eq openAttendance[AttendanceTable.id]
                                }) {
                                    it[checkOut] = now
                                    it[AttendanceTable.workedMinutes] = workedMinutes
                                    it[AttendanceTable.note] = "Auto check-out on logout"
                                    it[updatedAt] = now
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Don't fail logout if attendance auto-record fails
            }

            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}
