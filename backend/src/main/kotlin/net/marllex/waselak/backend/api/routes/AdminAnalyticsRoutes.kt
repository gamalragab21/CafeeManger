package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AnalyticsQueryService
import net.marllex.waselak.backend.plugins.AdminPrincipal
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

/**
 * CMS analytics wrapper routes for the admin app.
 *
 * These endpoints expose the same analytics data as the vendor-facing
 * `/api/v1/analytics/dashboard/` routes, but authenticated with admin JWT
 * and taking vendorId from the URL path instead of the JWT principal.
 *
 * Route pattern: GET /api/v1/cms/vendors/{id}/analytics/{type}
 */
fun Route.adminAnalyticsRoutes() {
    val analyticsService by KoinJavaComponent.inject<AnalyticsQueryService>(clazz = AnalyticsQueryService::class.java)

    route("/api/v1/cms") {
        authenticate("admin-jwt-bearer") {
            route("/vendors/{id}/analytics") {

                // Helper: parse vendorId from path
                fun RoutingCall.vendorUUID(): UUID =
                    UUID.fromString(parameters["id"] ?: throw IllegalArgumentException("Missing vendor id"))

                // Helper: parse date range from query params (same logic as vendor routes)
                fun RoutingCall.dateRange(): Pair<Instant, Instant> {
                    val now = Clock.System.now()
                    val from = parameters["from"]?.toLongOrNull()
                        ?.let { Instant.fromEpochMilliseconds(it) }
                        ?: (now - 30.days())
                    val to = parameters["to"]?.toLongOrNull()
                        ?.let { Instant.fromEpochMilliseconds(it) }
                        ?: now
                    return from to to
                }

                // ── 1. Executive Summary ─────────────────────────────────
                get("/executive-summary") {
                    val trace = call.routeTrace()
                    trace.step("CMS executive summary started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()
                    trace.step("Params", mapOf("vendorId" to vendorId.toString()))

                    val result = analyticsService.getExecutiveSummary(vendorId, from, to)
                    trace.step("CMS executive summary completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 2. Revenue & Profit ──────────────────────────────────
                get("/revenue-profit") {
                    val trace = call.routeTrace()
                    trace.step("CMS revenue-profit started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getRevenueProfit(vendorId, from, to)
                    trace.step("CMS revenue-profit completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 3. Orders Intelligence ───────────────────────────────
                get("/orders-intelligence") {
                    val trace = call.routeTrace()
                    trace.step("CMS orders-intelligence started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getOrdersIntelligence(vendorId, from, to)
                    trace.step("CMS orders-intelligence completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 4. Peak Times ────────────────────────────────────────
                get("/peak-times") {
                    val trace = call.routeTrace()
                    trace.step("CMS peak-times started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getPeakTimes(vendorId, from, to)
                    trace.step("CMS peak-times completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 5. Cashier Performance ───────────────────────────────
                get("/cashier-performance") {
                    val trace = call.routeTrace()
                    trace.step("CMS cashier-performance started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getCashierPerformance(vendorId, from, to)
                    trace.step("CMS cashier-performance completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 6. Delivery Performance ──────────────────────────────
                get("/delivery-performance") {
                    val trace = call.routeTrace()
                    trace.step("CMS delivery-performance started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getDeliveryPerformance(vendorId, from, to)
                    trace.step("CMS delivery-performance completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 7. Product Intelligence ──────────────────────────────
                get("/product-intelligence") {
                    val trace = call.routeTrace()
                    trace.step("CMS product-intelligence started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()
                    val limit = call.parameters["limit"]?.toIntOrNull() ?: 20

                    val result = analyticsService.getProductIntelligence(vendorId, from, to, limit)
                    trace.step("CMS product-intelligence completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 8. Customer Intelligence ─────────────────────────────
                get("/customer-intelligence") {
                    val trace = call.routeTrace()
                    trace.step("CMS customer-intelligence started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getCustomerIntelligence(vendorId, from, to)
                    trace.step("CMS customer-intelligence completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 9. Alerts ────────────────────────────────────────────
                get("/alerts") {
                    val trace = call.routeTrace()
                    trace.step("CMS alerts started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getAlerts(vendorId, from, to)
                    trace.step("CMS alerts completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 10. Stock Overview ───────────────────────────────────
                get("/stock-overview") {
                    val trace = call.routeTrace()
                    trace.step("CMS stock-overview started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()

                    val result = analyticsService.getStockOverview(vendorId)
                    trace.step("CMS stock-overview completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 11. Offers Analytics ─────────────────────────────────
                get("/offers-analytics") {
                    val trace = call.routeTrace()
                    trace.step("CMS offers-analytics started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getOffersAnalytics(vendorId, from, to)
                    trace.step("CMS offers-analytics completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 12. Discount Analytics ───────────────────────────────
                get("/discount-analytics") {
                    val trace = call.routeTrace()
                    trace.step("CMS discount-analytics started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getDiscountAnalytics(vendorId, from, to)
                    trace.step("CMS discount-analytics completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 13. Loyalty Analytics ────────────────────────────────
                get("/loyalty-analytics") {
                    val trace = call.routeTrace()
                    trace.step("CMS loyalty-analytics started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getLoyaltyAnalytics(vendorId, from, to)
                    trace.step("CMS loyalty-analytics completed")
                    call.respond(HttpStatusCode.OK, result)
                }

                // ── 14. Staff Costs Analytics ───────────────────────────
                get("/staff-costs") {
                    val trace = call.routeTrace()
                    trace.step("CMS staff-costs started")
                    call.principal<AdminPrincipal>()!!
                    val vendorId = call.vendorUUID()
                    val (from, to) = call.dateRange()

                    val result = analyticsService.getStaffCostsAnalytics(vendorId, from, to)
                    trace.step("CMS staff-costs completed")
                    call.respond(HttpStatusCode.OK, result)
                }
            }

            // ─── Vendor Orders (paginated) ───────────────────────────────
            get("/vendors/{id}/orders") {
                val trace = call.routeTrace()
                trace.step("CMS vendor orders started")
                call.principal<AdminPrincipal>()!!
                val vendorId = UUID.fromString(call.parameters["id"]!!)
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 20
                val status = call.parameters["status"]
                val channel = call.parameters["channel"]
                val search = call.parameters["search"]
                val dateFrom = call.parameters["from"]?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
                val dateTo = call.parameters["to"]?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }

                val result = transaction {
                    var query = OrdersTable.selectAll().where {
                        OrdersTable.vendorId eq vendorId
                    }
                    if (status != null) {
                        query = query.andWhere { OrdersTable.status eq status }
                    }
                    if (channel != null) {
                        query = query.andWhere { OrdersTable.channel eq channel }
                    }
                    if (dateFrom != null) {
                        query = query.andWhere { OrdersTable.createdAt greaterEq dateFrom }
                    }
                    if (dateTo != null) {
                        query = query.andWhere { OrdersTable.createdAt lessEq dateTo }
                    }
                    if (!search.isNullOrBlank()) {
                        query = query.andWhere {
                            (OrdersTable.clientName like "%$search%") or
                                    (OrdersTable.clientPhone like "%$search%")
                        }
                    }

                    val totalCount = query.count().toInt()
                    val orders = query
                        .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                        .limit(pageSize).offset(((page - 1) * pageSize).toLong())
                        .map { row ->
                            buildJsonObject {
                                put("id", row[OrdersTable.id].value.toString())
                                put("channel", row[OrdersTable.channel])
                                put("status", row[OrdersTable.status])
                                put("payment_method", row[OrdersTable.paymentMethod])
                                put("payment_status", row[OrdersTable.paymentStatus])
                                put("subtotal", row[OrdersTable.subtotal].toDouble())
                                put("delivery_fee", row[OrdersTable.deliveryFee].toDouble())
                                put("discount", row[OrdersTable.discount].toDouble())
                                put("tax", row[OrdersTable.tax].toDouble())
                                put("total", row[OrdersTable.total].toDouble())
                                put("client_name", row[OrdersTable.clientName] ?: "")
                                put("client_phone", row[OrdersTable.clientPhone] ?: "")
                                put("notes", row[OrdersTable.notes] ?: "")
                                put("created_at", row[OrdersTable.createdAt].toEpochMilliseconds())
                                put("updated_at", row[OrdersTable.updatedAt].toEpochMilliseconds())
                                row[OrdersTable.refundedAt]?.let { put("refunded_at", it.toEpochMilliseconds()) }
                                row[OrdersTable.refundReason]?.let { put("refund_reason", it) }
                                put("points_earned", row[OrdersTable.pointsEarned])
                                put("points_redeemed", row[OrdersTable.pointsRedeemed])
                            }
                        }

                    buildJsonObject {
                        put("total", totalCount)
                        put("page", page)
                        put("page_size", pageSize)
                        put("total_pages", if (totalCount > 0) (totalCount + pageSize - 1) / pageSize else 0)
                        put("orders", JsonArray(orders))
                    }
                }
                trace.step("CMS vendor orders completed", mapOf("page" to page.toString()))
                call.respondText(result.toString(), ContentType.Application.Json)
            }

            // ─── Vendor Order Detail ─────────────────────────────────────
            get("/vendors/{id}/orders/{orderId}") {
                val trace = call.routeTrace()
                trace.step("CMS order detail started")
                call.principal<AdminPrincipal>()!!
                val vendorId = UUID.fromString(call.parameters["id"]!!)
                val orderId = UUID.fromString(call.parameters["orderId"]!!)

                val result = transaction {
                    val order = OrdersTable.selectAll().where {
                        (OrdersTable.id eq orderId) and (OrdersTable.vendorId eq vendorId)
                    }.firstOrNull() ?: return@transaction null

                    val items = OrderItemsTable.selectAll().where {
                        OrderItemsTable.orderId eq orderId
                    }.map { item ->
                        buildJsonObject {
                            put("id", item[OrderItemsTable.id].value.toString())
                            put("item_id", item[OrderItemsTable.itemId].value.toString())
                            put("item_name", item[OrderItemsTable.itemNameSnapshot])
                            put("item_price", item[OrderItemsTable.itemPriceSnapshot].toDouble())
                            put("quantity", item[OrderItemsTable.quantity])
                            item[OrderItemsTable.note]?.let { put("note", it) }
                            item[OrderItemsTable.variantOptionsSnapshot]?.let { put("variant_options", it) }
                        }
                    }

                    buildJsonObject {
                        put("id", order[OrdersTable.id].value.toString())
                        put("channel", order[OrdersTable.channel])
                        put("status", order[OrdersTable.status])
                        put("payment_method", order[OrdersTable.paymentMethod])
                        put("payment_status", order[OrdersTable.paymentStatus])
                        put("subtotal", order[OrdersTable.subtotal].toDouble())
                        put("delivery_fee", order[OrdersTable.deliveryFee].toDouble())
                        put("discount", order[OrdersTable.discount].toDouble())
                        put("tax", order[OrdersTable.tax].toDouble())
                        put("total", order[OrdersTable.total].toDouble())
                        put("client_name", order[OrdersTable.clientName] ?: "")
                        put("client_phone", order[OrdersTable.clientPhone] ?: "")
                        order[OrdersTable.clientAddress]?.let { put("client_address", it) }
                        order[OrdersTable.geoLat]?.let { put("geo_lat", it) }
                        order[OrdersTable.geoLng]?.let { put("geo_lng", it) }
                        put("notes", order[OrdersTable.notes] ?: "")
                        put("created_at", order[OrdersTable.createdAt].toEpochMilliseconds())
                        put("updated_at", order[OrdersTable.updatedAt].toEpochMilliseconds())
                        order[OrdersTable.refundedAt]?.let { put("refunded_at", it.toEpochMilliseconds()) }
                        order[OrdersTable.refundReason]?.let { put("refund_reason", it) }
                        put("points_earned", order[OrdersTable.pointsEarned])
                        put("points_redeemed", order[OrdersTable.pointsRedeemed])
                        put("items", JsonArray(items))
                    }
                }

                if (result != null) {
                    trace.step("CMS order detail completed")
                    call.respondText(result.toString(), ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"Order not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // ─── Vendor Customers (paginated) ────────────────────────────
            get("/vendors/{id}/customers") {
                val trace = call.routeTrace()
                trace.step("CMS vendor customers started")
                call.principal<AdminPrincipal>()!!
                val vendorId = UUID.fromString(call.parameters["id"]!!)
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 20
                val search = call.parameters["search"]
                val sortBy = call.parameters["sortBy"] ?: "total_spent" // total_spent, order_count, name, last_order
                val sortDir = if (call.parameters["sortDir"] == "asc") SortOrder.ASC else SortOrder.DESC

                val result = transaction {
                    var query = CustomersTable.selectAll().where {
                        CustomersTable.vendorId eq vendorId
                    }
                    if (!search.isNullOrBlank()) {
                        query = query.andWhere {
                            (CustomersTable.name like "%$search%") or
                                    (CustomersTable.phone like "%$search%")
                        }
                    }

                    val sortColumn = when (sortBy) {
                        "order_count" -> CustomersTable.orderCount
                        "name" -> CustomersTable.name
                        "last_order" -> CustomersTable.lastOrderAt
                        else -> CustomersTable.totalSpent
                    }
                    query = query.orderBy(sortColumn to sortDir)

                    val totalCount = query.count().toInt()
                    val customers = query
                        .limit(pageSize).offset(((page - 1) * pageSize).toLong())
                        .map { row ->
                            buildJsonObject {
                                put("id", row[CustomersTable.id].value.toString())
                                put("name", row[CustomersTable.name] ?: "")
                                put("phone", row[CustomersTable.phone])
                                put("notes", row[CustomersTable.notes] ?: "")
                                put("order_count", row[CustomersTable.orderCount])
                                put("total_spent", row[CustomersTable.totalSpent].toDouble())
                                put("points_balance", row[CustomersTable.pointsBalance])
                                row[CustomersTable.lastOrderAt]?.let { put("last_order_at", it.toEpochMilliseconds()) }
                                put("created_at", row[CustomersTable.createdAt].toEpochMilliseconds())
                            }
                        }

                    buildJsonObject {
                        put("total", totalCount)
                        put("page", page)
                        put("page_size", pageSize)
                        put("total_pages", if (totalCount > 0) (totalCount + pageSize - 1) / pageSize else 0)
                        put("customers", JsonArray(customers))
                    }
                }
                trace.step("CMS vendor customers completed")
                call.respondText(result.toString(), ContentType.Application.Json)
            }

            // ─── Vendor Workers (with attendance summary) ────────────────
            get("/vendors/{id}/workers") {
                val trace = call.routeTrace()
                trace.step("CMS vendor workers started")
                call.principal<AdminPrincipal>()!!
                val vendorId = UUID.fromString(call.parameters["id"]!!)

                val result = transaction {
                    val workers = WorkersTable.selectAll().where {
                        WorkersTable.vendorId eq vendorId
                    }.toList()

                    // Get attendance summary for the last 30 days
                    val thirtyDaysAgo = Clock.System.now() - kotlin.time.Duration.parse("30d")
                    val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

                    val workerIds = workers.map { it[WorkersTable.id] }
                    val attendanceData = if (workerIds.isNotEmpty()) {
                        AttendanceTable.selectAll().where {
                            (AttendanceTable.vendorId eq vendorId) and
                                    (AttendanceTable.checkIn greaterEq thirtyDaysAgo)
                        }.toList().groupBy { it[AttendanceTable.workerId] }
                    } else emptyMap()

                    val workerList = workers.map { worker ->
                        val wId = worker[WorkersTable.id]
                        val attendance = attendanceData[wId].orEmpty()
                        val totalDays = attendance.size
                        val totalMinutes = attendance.sumOf { it[AttendanceTable.workedMinutes] ?: 0 }
                        val checkedInToday = attendance.any { it[AttendanceTable.date] == todayStr }

                        buildJsonObject {
                            put("id", wId.value.toString())
                            put("worker_id", worker[WorkersTable.workerId])
                            put("full_name", worker[WorkersTable.fullName])
                            put("phone", worker[WorkersTable.phone] ?: "")
                            put("role", worker[WorkersTable.role])
                            put("salary_type", worker[WorkersTable.salaryType])
                            put("salary_amount", worker[WorkersTable.salaryAmount].toDouble())
                            put("active", worker[WorkersTable.active])
                            put("created_at", worker[WorkersTable.createdAt].toEpochMilliseconds())
                            put("attendance_days_30d", totalDays)
                            put("worked_minutes_30d", totalMinutes)
                            put("checked_in_today", checkedInToday)
                        }
                    }

                    buildJsonObject {
                        put("total", workerList.size)
                        put("workers", JsonArray(workerList))
                    }
                }
                trace.step("CMS vendor workers completed")
                call.respondText(result.toString(), ContentType.Application.Json)
            }

        // ═══════════════════════════════════════════════════════════════════
        // CMS User Management for vendors
        // ═══════════════════════════════════════════════════════════════════

            route("/vendors/{id}/users") {

            // POST — Create a new user for a vendor
            post {
                val trace = call.routeTrace()
                trace.step("CMS create user started")
                call.principal<AdminPrincipal>()!!
                val vendorId = try {
                    UUID.fromString(call.parameters["id"] ?: "")
                } catch (_: Exception) {
                    return@post call.respondText(
                        """{"error":"Invalid vendor ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val body = call.receive<JsonObject>()
                val name = body["name"]?.jsonPrimitive?.contentOrNull
                val phone = body["phone"]?.jsonPrimitive?.contentOrNull
                val email = body["email"]?.jsonPrimitive?.contentOrNull
                val password = body["password"]?.jsonPrimitive?.contentOrNull
                val role = body["role"]?.jsonPrimitive?.contentOrNull ?: "CASHIER"

                if (name.isNullOrBlank() || phone.isNullOrBlank() || password.isNullOrBlank()) {
                    return@post call.respondText(
                        """{"error":"name, phone, and password are required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())

                val userId = transaction {
                    // Check for duplicate phone
                    val existing = UsersTable.selectAll()
                        .where { (UsersTable.vendorId eq vendorId) and (UsersTable.phone eq phone) }
                        .firstOrNull()
                    if (existing != null) return@transaction null

                    UsersTable.insertAndGetId {
                        it[UsersTable.vendorId] = vendorId
                        it[UsersTable.name] = name
                        it[UsersTable.phone] = phone
                        it[UsersTable.email] = email
                        it[UsersTable.passwordHash] = hashedPassword
                        it[UsersTable.role] = role.uppercase()
                        it[UsersTable.active] = true
                    }.value.toString()
                }

                if (userId != null) {
                    trace.step("User created", mapOf("userId" to userId))
                    call.respondText(
                        buildJsonObject { put("id", userId); put("success", true) }.toString(),
                        ContentType.Application.Json,
                        HttpStatusCode.Created
                    )
                } else {
                    call.respondText(
                        """{"error":"Phone number already exists for this vendor"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Conflict
                    )
                }
            }

            // PUT /{userId} — Update user details
            put("/{userId}") {
                val trace = call.routeTrace()
                trace.step("CMS update user started")
                call.principal<AdminPrincipal>()!!

                val userId = try {
                    UUID.fromString(call.parameters["userId"] ?: "")
                } catch (_: Exception) {
                    return@put call.respondText(
                        """{"error":"Invalid user ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val body = call.receive<JsonObject>()

                val updated = transaction {
                    UsersTable.update({ UsersTable.id eq userId }) { stmt ->
                        body["name"]?.jsonPrimitive?.contentOrNull?.let { stmt[name] = it }
                        body["email"]?.jsonPrimitive?.contentOrNull?.let { stmt[email] = it }
                        body["active"]?.jsonPrimitive?.booleanOrNull?.let { stmt[active] = it }
                    }
                }

                trace.step("User updated", mapOf("userId" to userId.toString(), "rows" to updated.toString()))
                call.respondText(
                    buildJsonObject { put("success", updated > 0) }.toString(),
                    ContentType.Application.Json
                )
            }

            // PUT /{userId}/reset-password — Reset user password
            put("/{userId}/reset-password") {
                val trace = call.routeTrace()
                trace.step("CMS reset password started")
                call.principal<AdminPrincipal>()!!

                val userId = try {
                    UUID.fromString(call.parameters["userId"] ?: "")
                } catch (_: Exception) {
                    return@put call.respondText(
                        """{"error":"Invalid user ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val body = call.receive<JsonObject>()
                val newPassword = body["new_password"]?.jsonPrimitive?.contentOrNull

                if (newPassword.isNullOrBlank() || newPassword.length < 6) {
                    return@put call.respondText(
                        """{"error":"Password must be at least 6 characters"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val hashed = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt())

                val updated = transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[passwordHash] = hashed
                    }
                }

                trace.step("Password reset", mapOf("userId" to userId.toString(), "rows" to updated.toString()))
                call.respondText(
                    buildJsonObject { put("success", updated > 0) }.toString(),
                    ContentType.Application.Json
                )
            }

            // DELETE /{userId} — Deactivate user (soft delete)
            delete("/{userId}") {
                val trace = call.routeTrace()
                trace.step("CMS deactivate user started")
                call.principal<AdminPrincipal>()!!

                val userId = try {
                    UUID.fromString(call.parameters["userId"] ?: "")
                } catch (_: Exception) {
                    return@delete call.respondText(
                        """{"error":"Invalid user ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }

                val updated = transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[active] = false
                    }
                }

                trace.step("User deactivated", mapOf("userId" to userId.toString(), "rows" to updated.toString()))
                call.respondText(
                    buildJsonObject { put("success", updated > 0) }.toString(),
                    ContentType.Application.Json
                )
            }
        }

            // ── Platform-wide Alerts ─────────────────────────────────
            get("/alerts") {
                val trace = call.routeTrace()
                trace.step("CMS platform alerts started")
                call.principal<AdminPrincipal>()!!

                val now = Clock.System.now()
                val sevenDaysAgo = now - 7.days()

                val alerts = transaction {
                    val alertsList = mutableListOf<JsonObject>()

                    // 1. Vendors approaching plan limits (>80% of max orders)
                    val monthStart = Instant.parse(now.toString().substring(0, 7) + "-01T00:00:00Z")
                    val subscriptions = (VendorSubscriptionsTable innerJoin SubscriptionPlansTable)
                        .selectAll()
                        .where { VendorSubscriptionsTable.status eq "ACTIVE" }

                    for (sub in subscriptions) {
                        val vendorId = sub[VendorSubscriptionsTable.vendorId]
                        val maxOrders = sub[VendorSubscriptionsTable.overrideMaxOrders]
                            ?: sub[SubscriptionPlansTable.maxOrdersPerMonth]
                        val ordersThisMonth = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vendorId) and (OrdersTable.createdAt greaterEq monthStart) }
                            .count().toInt()

                        if (maxOrders > 0 && ordersThisMonth >= (maxOrders * 0.8).toInt()) {
                            val vendorName = VendorsTable.selectAll()
                                .where { VendorsTable.id eq vendorId }
                                .firstOrNull()?.get(VendorsTable.name) ?: "Unknown"

                            alertsList.add(buildJsonObject {
                                put("type", "PLAN_LIMIT")
                                put("severity", if (ordersThisMonth >= maxOrders) "CRITICAL" else "WARNING")
                                put("vendor_id", vendorId.toString())
                                put("vendor_name", vendorName)
                                put("message", "$ordersThisMonth / $maxOrders orders used this month")
                            })
                        }
                    }

                    // 2. Vendors with zero orders in 7 days (active only)
                    val activeVendors = VendorsTable.selectAll()
                        .where { VendorsTable.isSuspended eq false }
                    for (vendor in activeVendors) {
                        val vid = vendor[VendorsTable.id].value
                        val recentOrders = OrdersTable.selectAll()
                            .where { (OrdersTable.vendorId eq vid) and (OrdersTable.createdAt greaterEq sevenDaysAgo) }
                            .count().toInt()
                        if (recentOrders == 0) {
                            alertsList.add(buildJsonObject {
                                put("type", "NO_ORDERS")
                                put("severity", "WARNING")
                                put("vendor_id", vid.toString())
                                put("vendor_name", vendor[VendorsTable.name])
                                put("message", "No orders in the last 7 days")
                            })
                        }
                    }

                    // 3. Expiring subscriptions (within next 7 days)
                    val sevenDaysFromNow = now + 7.days()
                    val expiringSubs = VendorSubscriptionsTable.selectAll()
                        .where {
                            (VendorSubscriptionsTable.status eq "ACTIVE") and
                                    (VendorSubscriptionsTable.expiresAt.isNotNull()) and
                                    (VendorSubscriptionsTable.expiresAt lessEq sevenDaysFromNow)
                        }
                    for (sub in expiringSubs) {
                        val vendorId = sub[VendorSubscriptionsTable.vendorId]
                        val vendorName = VendorsTable.selectAll()
                            .where { VendorsTable.id eq vendorId }
                            .firstOrNull()?.get(VendorsTable.name) ?: "Unknown"
                        val expiresAt = sub[VendorSubscriptionsTable.expiresAt]
                        val isExpired = expiresAt != null && expiresAt < now

                        alertsList.add(buildJsonObject {
                            put("type", if (isExpired) "SUBSCRIPTION_EXPIRED" else "SUBSCRIPTION_EXPIRING")
                            put("severity", if (isExpired) "CRITICAL" else "WARNING")
                            put("vendor_id", vendorId.toString())
                            put("vendor_name", vendorName)
                            put("message", if (isExpired) "Subscription has expired" else "Subscription expiring soon")
                        })
                    }

                    buildJsonArray { alertsList.forEach { add(it) } }
                }

                trace.step("Platform alerts fetched", "count", alerts.size.toString())
                call.respondText(alerts.toString(), ContentType.Application.Json)
                trace.step("CMS platform alerts completed")
            }
        }
    }
}

private fun Int.days(): kotlin.time.Duration = kotlin.time.Duration.parse("${this}d")
