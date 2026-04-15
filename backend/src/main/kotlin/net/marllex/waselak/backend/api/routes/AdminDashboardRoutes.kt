package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AdminAuthService
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.domain.service.RequestLogService
import net.marllex.waselak.backend.plugins.AdminPrincipal
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

fun Route.adminDashboardRoutes() {
    val adminAuthService by KoinJavaComponent.inject<AdminAuthService>(clazz = AdminAuthService::class.java)
    val requestLogService by KoinJavaComponent.inject<RequestLogService>(clazz = RequestLogService::class.java)
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    // ─── Login Page ──────────────────────────────────────────────
    get("/admin/login") {
        val trace = call.routeTrace()
        trace.step("Serve admin login page started")
        call.respondText(loginPageHtml(), ContentType.Text.Html)
        trace.step("Serve admin login page completed")
    }

    post("/admin/login") {
        val trace = call.routeTrace()
        trace.step("Admin dashboard login started")
        val params = call.receiveParameters()
        val email = params["email"] ?: ""
        trace.step("Received login form submission", mapOf("email" to email))

        val result = adminAuthService.login(email, password = params["password"] ?: "")
        if (result != null) {
            trace.step("Dashboard login successful", mapOf("adminId" to result.id, "name" to result.name))
            call.response.cookies.append(
                Cookie(
                    name = "admin_token",
                    value = result.token,
                    path = "/admin",
                    httpOnly = true,
                    maxAge = 86400
                )
            )
            call.respondRedirect("/admin/dashboard")
        } else {
            trace.step("Dashboard login failed", mapOf("email" to email))
            call.respondText(
                loginPageHtml(error = "Invalid email or password"),
                ContentType.Text.Html
            )
        }
        trace.step("Admin dashboard login completed")
    }

    post("/admin/logout") {
        val trace = call.routeTrace()
        trace.step("Admin dashboard logout started")
        call.response.cookies.append(
            Cookie(
                name = "admin_token",
                value = "",
                path = "/admin",
                httpOnly = true,
                maxAge = 0
            )
        )
        call.respondRedirect("/admin/login")
        trace.step("Admin dashboard logout completed")
    }

    // ─── Protected Admin Routes ──────────────────────────────────
    authenticate("admin-jwt") {

        // ─── Dashboard Overview ─────────────────────────────────
        get("/admin/dashboard") {
            val trace = call.routeTrace()
            trace.step("Serve admin dashboard started")
            val admin = call.principal<AdminPrincipal>()!!
            trace.step("Admin authenticated", mapOf("adminEmail" to admin.email))
            call.respondText(
                adminLayout("لوحة التحكم", admin, "dashboard", dashboardContent()),
                ContentType.Text.Html
            )
            trace.step("Serve admin dashboard completed")
        }

        // ─── Vendors Page ───────────────────────────────────────
        get("/admin/vendors") {
            val trace = call.routeTrace()
            trace.step("Serve admin vendors page started")
            val admin = call.principal<AdminPrincipal>()!!
            call.respondText(
                adminLayout("العملاء (Vendors)", admin, "vendors", vendorsContent(admin)),
                ContentType.Text.Html
            )
            trace.step("Serve admin vendors page completed")
        }

        // ─── Vendor Detail Page ─────────────────────────────────
        get("/admin/vendors/{id}") {
            val trace = call.routeTrace()
            trace.step("Serve admin vendor detail started")
            val admin = call.principal<AdminPrincipal>()!!
            val vendorId = call.parameters["id"] ?: return@get call.respondRedirect("/admin/vendors")
            call.respondText(
                adminLayout("تفاصيل العميل", admin, "vendors", vendorDetailContent(vendorId, admin)),
                ContentType.Text.Html
            )
            trace.step("Serve admin vendor detail completed")
        }

        // ─── Vendor Edit Page ────────────────────────────────────
        get("/admin/vendors/{id}/edit") {
            val trace = call.routeTrace()
            trace.step("Serve admin vendor edit started")
            val admin = call.principal<AdminPrincipal>()!!
            val vendorId = call.parameters["id"] ?: return@get call.respondRedirect("/admin/vendors")
            call.respondText(
                adminLayout("تعديل بيانات العميل", admin, "vendors", vendorEditContent(vendorId)),
                ContentType.Text.Html
            )
            trace.step("Serve admin vendor edit completed")
        }

        // ─── Team Management Page ───────────────────────────────
        get("/admin/team") {
            val trace = call.routeTrace()
            trace.step("Serve admin team page started")
            val admin = call.principal<AdminPrincipal>()!!
            if (!admin.isSuperAdmin) {
                return@get call.respondRedirect("/admin/dashboard")
            }
            call.respondText(
                adminLayout("فريق الإدارة", admin, "team", teamContent(admin)),
                ContentType.Text.Html
            )
            trace.step("Serve admin team page completed")
        }

        // ─── Plans Page ─────────────────────────────────────────
        get("/admin/plans") {
            val trace = call.routeTrace()
            trace.step("Serve admin plans page started")
            val admin = call.principal<AdminPrincipal>()!!
            call.respondText(
                adminLayout("الباقات", admin, "plans", plansContent()),
                ContentType.Text.Html
            )
            trace.step("Serve admin plans page completed")
        }

        // ─── Logs Page ──────────────────────────────────────────
        get("/admin/logs") {
            val trace = call.routeTrace()
            trace.step("Serve admin logs page started")
            val admin = call.principal<AdminPrincipal>()!!
            call.respondText(
                adminLayout("سجل الطلبات", admin, "logs", logsContent()),
                ContentType.Text.Html
            )
            trace.step("Serve admin logs page completed")
        }

        // ═══════════════════════════════════════════════════════
        // JSON API Endpoints (for dashboard data)
        // ═══════════════════════════════════════════════════════
        route("/admin/api") {
            get("/vendors") {
                val trace = call.routeTrace()
                trace.step("Dashboard get vendors started")
                val vendors = requestLogService.getLoggedVendors()
                trace.step("Dashboard vendors fetched", mapOf("count" to vendors.size.toString()))
                val json = buildJsonArray {
                    vendors.forEach { v ->
                        addJsonObject { put("id", v.id); put("name", v.name) }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard get vendors completed")
            }

            get("/users") {
                val trace = call.routeTrace()
                trace.step("Dashboard get users started")
                val vendorId = call.request.queryParameters["vendorId"]
                    ?: return@get call.respondText(
                        """{"error":"vendorId required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Fetching users for vendor", mapOf("vendorId" to vendorId))
                val users = requestLogService.getVendorUsers(vendorId)
                trace.step("Dashboard users fetched", mapOf("vendorId" to vendorId, "count" to users.size.toString()))
                val json = buildJsonArray {
                    users.forEach { u ->
                        addJsonObject { put("id", u.id); put("name", u.name); put("role", u.role) }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard get users completed")
            }

            get("/vendors/full") {
                val trace = call.routeTrace()
                trace.step("Dashboard get full vendors started")
                val vendorsData = transaction {
                    VendorsTable.selectAll().map { row ->
                        val vendorId = row[VendorsTable.id].value

                        val usersCount = UsersTable.selectAll()
                            .where { UsersTable.vendorId eq vendorId }
                            .count().toInt()

                        val subscription = VendorSubscriptionsTable.selectAll()
                            .where { VendorSubscriptionsTable.vendorId eq vendorId }
                            .firstOrNull()

                        val planRow = subscription?.let { sub ->
                            SubscriptionPlansTable.selectAll()
                                .where { SubscriptionPlansTable.id eq sub[VendorSubscriptionsTable.planId] }
                                .firstOrNull()
                        }

                        val usage = planService.getVendorUsage(vendorId)

                        buildJsonObject {
                            put("id", vendorId.toString())
                            put("name", row[VendorsTable.name])
                            put("address", row[VendorsTable.address])
                            put("phone", row[VendorsTable.contactPhone])
                            put("businessType", row[VendorsTable.businessType])
                            put("isSuspended", row[VendorsTable.isSuspended])
                            put("usersCount", usersCount)
                            put("createdAt", row[VendorsTable.createdAt].toEpochMilliseconds())
                            put("planName", planRow?.get(SubscriptionPlansTable.name) ?: "NONE")
                            put("planDisplayName", planRow?.get(SubscriptionPlansTable.displayName) ?: "No Plan")
                            put("subscriptionStatus", subscription?.get(VendorSubscriptionsTable.status) ?: "NONE")
                            put("managers", usage["managers"].toString().toInt())
                            put("cashiers", usage["cashiers"].toString().toInt())
                            put("delivery", usage["delivery"].toString().toInt())
                            put("menuItems", usage["menuItems"].toString().toInt())
                            put("monthlyOrders", usage["monthlyOrders"].toString().toInt())
                            if (planRow != null) {
                                put("maxManagers", planRow[SubscriptionPlansTable.maxManagers])
                                put("maxCashiers", planRow[SubscriptionPlansTable.maxCashiers])
                                put("maxDelivery", planRow[SubscriptionPlansTable.maxDelivery])
                                put("maxMenuItems", planRow[SubscriptionPlansTable.maxMenuItems])
                                put("maxOrdersPerMonth", planRow[SubscriptionPlansTable.maxOrdersPerMonth])
                            }
                        }
                    }
                }
                trace.step("Full vendors data fetched", mapOf("count" to vendorsData.size.toString()))
                val json = buildJsonArray { vendorsData.forEach { add(it) } }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard get full vendors completed")
            }

            get("/plans") {
                val trace = call.routeTrace()
                trace.step("Dashboard list plans started")
                val plans = planService.listActivePlans()
                trace.step("Dashboard plans fetched", mapOf("count" to plans.size.toString()))
                val json = buildJsonArray {
                    plans.forEach { plan ->
                        addJsonObject {
                            put("name", plan[SubscriptionPlansTable.name])
                            put("displayName", plan[SubscriptionPlansTable.displayName])
                            put("priceEgp", plan[SubscriptionPlansTable.priceEgp])
                            put("maxManagers", plan[SubscriptionPlansTable.maxManagers])
                            put("maxCashiers", plan[SubscriptionPlansTable.maxCashiers])
                            put("maxDelivery", plan[SubscriptionPlansTable.maxDelivery])
                            put("maxOrdersPerMonth", plan[SubscriptionPlansTable.maxOrdersPerMonth])
                            put("maxMenuItems", plan[SubscriptionPlansTable.maxMenuItems])
                            put("maxBranches", plan[SubscriptionPlansTable.maxBranches])
                            put("stockManagement", plan[SubscriptionPlansTable.stockManagement])
                            put("workerAttendance", plan[SubscriptionPlansTable.workerAttendance])
                            put("deliveryModule", plan[SubscriptionPlansTable.deliveryModule])
                            put("analytics", plan[SubscriptionPlansTable.analytics])
                            put("digitalMenu", plan[SubscriptionPlansTable.digitalMenu])
                        }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard list plans completed")
            }

            get("/logs") {
                val trace = call.routeTrace()
                trace.step("Dashboard query logs started")
                val params = call.request.queryParameters
                val queryParams = RequestLogService.LogQueryParams(
                    vendorId = params["vendorId"]?.takeIf { it.isNotBlank() },
                    userId = params["userId"]?.takeIf { it.isNotBlank() },
                    method = params["method"]?.takeIf { it.isNotBlank() },
                    pathSearch = params["path"]?.takeIf { it.isNotBlank() },
                    statusGroup = params["status"]?.takeIf { it.isNotBlank() },
                    startDate = params["startDate"]?.takeIf { it.isNotBlank() }?.let {
                        runCatching { Instant.parse(it) }.getOrNull()
                    },
                    endDate = params["endDate"]?.takeIf { it.isNotBlank() }?.let {
                        runCatching { Instant.parse(it) }.getOrNull()
                    },
                    page = params["page"]?.toIntOrNull() ?: 1,
                    pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(10, 100) ?: 50
                )

                trace.step("Querying dashboard logs", mapOf(
                    "page" to queryParams.page.toString(),
                    "pageSize" to queryParams.pageSize.toString(),
                    "vendorId" to (queryParams.vendorId ?: "null")
                ))
                val result = requestLogService.queryLogs(queryParams)
                trace.step("Dashboard logs fetched", mapOf("total" to result.total.toString(), "totalPages" to result.totalPages.toString()))
                val json = buildJsonObject {
                    putJsonArray("logs") {
                        result.logs.forEach { log -> add(log) }
                    }
                    put("total", result.total)
                    put("page", result.page)
                    put("pageSize", result.pageSize)
                    put("totalPages", result.totalPages)
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard query logs completed")
            }

            get("/logs/stats") {
                val trace = call.routeTrace()
                trace.step("Dashboard log stats started")
                val params = call.request.queryParameters
                val vendorId = params["vendorId"]?.takeIf { it.isNotBlank() }
                val startDate = params["startDate"]?.takeIf { it.isNotBlank() }?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                val endDate = params["endDate"]?.takeIf { it.isNotBlank() }?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                trace.step("Fetching dashboard log stats", mapOf("vendorId" to (vendorId ?: "null")))

                val stats = requestLogService.getStats(vendorId, startDate, endDate)
                trace.step("Dashboard log stats fetched", mapOf(
                    "totalRequests" to stats.totalRequests.toString(),
                    "errorCount" to stats.errorCount.toString(),
                    "errorRate" to stats.errorRate.toString()
                ))
                val json = buildJsonObject {
                    put("totalRequests", stats.totalRequests)
                    put("errorCount", stats.errorCount)
                    put("errorRate", stats.errorRate)
                    put("avgDurationMs", stats.avgDurationMs)
                    putJsonObject("statusBreakdown") {
                        stats.statusBreakdown.forEach { (k, v) -> put(k, v) }
                    }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard log stats completed")
            }

            put("/vendors/{id}/plan") {
                val trace = call.routeTrace()
                trace.step("Dashboard change vendor plan started")
                val vendorId = call.parameters["id"]
                    ?: return@put call.respondText(
                        """{"error":"Missing vendor ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                val vendorUuid = try { UUID.fromString(vendorId) } catch (_: Exception) {
                    return@put call.respondText(
                        """{"error":"Invalid vendor ID"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to vendorId))

                val body = call.receiveText()
                val jsonBody = Json.parseToJsonElement(body).jsonObject
                val planName = jsonBody["plan"]?.jsonPrimitive?.content
                    ?: return@put call.respondText(
                        """{"error":"plan field required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                val notes = jsonBody["notes"]?.jsonPrimitive?.contentOrNull
                trace.step("Received plan change", mapOf("planName" to planName, "notes" to (notes ?: "null")))

                trace.step("Assigning plan to vendor")
                planService.assignPlanToVendor(vendorUuid, planName, notes)
                trace.step("Plan assigned", mapOf("vendorId" to vendorId, "newPlan" to planName))
                val json = buildJsonObject {
                    put("success", true)
                    put("message", "Plan updated to $planName")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard change vendor plan completed")
            }

            // ─── Log Management: Clear All Request Logs ─────────────────
            delete("/logs/clear-all") {
                val trace = call.routeTrace()
                trace.step("Dashboard clear all logs started")
                val deleted = requestLogService.clearAllLogs()
                trace.step("All request logs cleared", mapOf("deletedCount" to deleted.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deleted)
                    put("message", "All $deleted request logs cleared")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard clear all logs completed")
            }

            delete("/logs/clear-vendor/{vendorId}") {
                val trace = call.routeTrace()
                trace.step("Dashboard clear vendor logs started")
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
                trace.step("Dashboard clear vendor logs completed")
            }

            delete("/logs/clear-admin") {
                val trace = call.routeTrace()
                trace.step("Dashboard clear admin logs started")
                val deleted = requestLogService.clearAdminLogs()
                trace.step("Admin logs cleared", mapOf("deletedCount" to deleted.toString()))
                val json = buildJsonObject {
                    put("success", true)
                    put("deleted", deleted)
                    put("message", "Cleared $deleted admin/system logs")
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard clear admin logs completed")
            }

            get("/logs/files") {
                val trace = call.routeTrace()
                trace.step("Dashboard list log files started")
                val logsDir = java.io.File("logs/vendors")
                val files = mutableListOf<JsonObject>()

                if (logsDir.exists() && logsDir.isDirectory) {
                    logsDir.listFiles()?.filter { it.isDirectory }?.forEach { vendorDir ->
                        val vendorFolder = vendorDir.name
                        vendorDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                            files.add(buildJsonObject {
                                put("vendorFolder", vendorFolder)
                                put("fileName", file.name)
                                put("size", file.length())
                                put("sizeFormatted", dashFormatFileSize(file.length()))
                                put("lastModified", file.lastModified())
                                put("path", file.path)
                            })
                        }
                    }
                }

                val topLogsDir = java.io.File("logs")
                if (topLogsDir.exists()) {
                    topLogsDir.listFiles()?.filter { it.isDirectory && it.name != "vendors" && it.name != "backend" }?.forEach { vendorDir ->
                        vendorDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                            files.add(buildJsonObject {
                                put("vendorFolder", vendorDir.name)
                                put("fileName", file.name)
                                put("size", file.length())
                                put("sizeFormatted", dashFormatFileSize(file.length()))
                                put("lastModified", file.lastModified())
                                put("path", file.path)
                            })
                        }
                    }
                }

                trace.step("Log files listed", mapOf("fileCount" to files.size.toString()))
                val json = buildJsonArray {
                    files.sortedByDescending { it["lastModified"]?.jsonPrimitive?.longOrNull ?: 0L }.forEach { add(it) }
                }
                call.respondText(json.toString(), ContentType.Application.Json)
                trace.step("Dashboard list log files completed")
            }

            delete("/logs/files/delete") {
                val trace = call.routeTrace()
                trace.step("Dashboard delete log file started")
                val body = call.receiveText()
                val jsonBody = Json.parseToJsonElement(body).jsonObject
                val filePath = jsonBody["path"]?.jsonPrimitive?.content
                    ?: return@delete call.respondText(
                        """{"error":"path field required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Deleting file", mapOf("path" to filePath))

                val file = java.io.File(filePath)
                val canonicalPath = file.canonicalPath
                val logsBasePath = java.io.File("logs").canonicalPath
                if (!canonicalPath.startsWith(logsBasePath) || !file.name.endsWith(".log")) {
                    trace.step("Delete rejected - invalid path", mapOf("path" to filePath))
                    return@delete call.respondText(
                        """{"error":"Invalid file path"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                }

                if (file.exists()) {
                    val deleted = file.delete()
                    val parent = file.parentFile
                    if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                        parent.delete()
                    }
                    trace.step("File deleted", mapOf("path" to filePath, "deleted" to deleted.toString()))
                    val json = buildJsonObject {
                        put("success", deleted)
                        put("message", if (deleted) "File deleted" else "Failed to delete file")
                    }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"File not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("Dashboard delete log file completed")
            }

            delete("/logs/files/clear-vendor/{vendorFolder}") {
                val trace = call.routeTrace()
                trace.step("Dashboard clear vendor log files started")
                val vendorFolder = call.parameters["vendorFolder"]
                    ?: return@delete call.respondText(
                        """{"error":"Missing vendorFolder"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                trace.step("Clearing log files for vendor folder", mapOf("vendorFolder" to vendorFolder))

                var deletedCount = 0

                val vendorDir1 = java.io.File("logs/vendors/$vendorFolder")
                if (vendorDir1.exists() && vendorDir1.isDirectory) {
                    vendorDir1.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.forEach { file ->
                        if (file.delete()) deletedCount++
                    }
                    if (vendorDir1.listFiles()?.isEmpty() == true) vendorDir1.delete()
                }

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
                trace.step("Dashboard clear vendor log files completed")
            }

            get("/logs/files/view") {
                val trace = call.routeTrace()
                trace.step("Dashboard view log file started")
                val filePath = call.parameters["path"]
                    ?: return@get call.respondText(
                        """{"error":"path parameter required"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )

                val file = java.io.File(filePath)
                val canonicalPath = file.canonicalPath
                val logsBasePath = java.io.File("logs").canonicalPath
                if (!canonicalPath.startsWith(logsBasePath) || !file.name.endsWith(".log")) {
                    return@get call.respondText(
                        """{"error":"Invalid file path"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                }

                if (file.exists()) {
                    val download = call.parameters["download"] == "true"
                    if (download) {
                        call.response.header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                    }
                    call.respondText(file.readText(), ContentType.Text.Plain)
                } else {
                    call.respondText("File not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
                trace.step("Dashboard view log file completed")
            }

            // ─── Vendor CRUD (cookie auth) ─────────────────────────
            post("/vendors") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val vendorName = obj["vendor_name"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "vendor_name required"))
                val managerName = obj["manager_name"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "manager_name required"))
                val managerPhone = obj["manager_phone"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "manager_phone required"))
                val managerPassword = obj["manager_password"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "manager_password required"))
                val businessType = obj["business_type"]?.jsonPrimitive?.content ?: "RESTAURANT"
                val plan = obj["plan"]?.jsonPrimitive?.content ?: "STARTER"

                val result = transaction {
                    val vendorId = VendorsTable.insertAndGetId {
                        it[name] = vendorName
                        it[address] = obj["vendor_address"]?.jsonPrimitive?.content ?: ""
                        it[contactPhone] = obj["vendor_phone"]?.jsonPrimitive?.content ?: ""
                        it[VendorsTable.businessType] = businessType
                    }
                    val passwordHash = BCrypt.hashpw(managerPassword, BCrypt.gensalt())
                    UsersTable.insertAndGetId {
                        it[UsersTable.vendorId] = vendorId.value
                        it[role] = "MANAGER"
                        it[UsersTable.name] = managerName
                        it[phone] = managerPhone
                        it[UsersTable.passwordHash] = passwordHash
                    }
                    // Create subscription
                    val planRow = SubscriptionPlansTable.selectAll().where { SubscriptionPlansTable.name eq plan }.firstOrNull()
                    if (planRow != null) {
                        VendorSubscriptionsTable.insert {
                            it[VendorSubscriptionsTable.vendorId] = vendorId.value
                            it[planId] = planRow[SubscriptionPlansTable.id]
                            it[status] = "ACTIVE"
                        }
                    }
                    vendorId.value.toString()
                }
                call.respond(HttpStatusCode.Created, mapOf("status" to "created", "vendor_id" to result))
            }

            post("/vendors/{id}/suspend") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                transaction {
                    val vendor = VendorsTable.selectAll().where { VendorsTable.id eq UUID.fromString(id) }.firstOrNull()
                    val currentSuspended = vendor?.get(VendorsTable.isSuspended) ?: false
                    VendorsTable.update({ VendorsTable.id eq UUID.fromString(id) }) {
                        it[isSuspended] = !currentSuspended
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "toggled"))
            }

            // ─── Vendor Update (cookie auth) ──────────────────────
            put("/vendors/{id}") {
                val trace = call.routeTrace()
                trace.step("Dashboard update vendor started")
                val id = call.parameters["id"]
                    ?: return@put call.respondText("""{"error":"Missing vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                val vendorUuid = try { UUID.fromString(id) } catch (_: Exception) {
                    return@put call.respondText("""{"error":"Invalid vendor ID"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                trace.step("Parsed vendor ID", mapOf("vendorId" to id))

                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                trace.step("Received update request body")

                val updated = transaction {
                    val exists = VendorsTable.selectAll()
                        .where { VendorsTable.id eq vendorUuid }
                        .firstOrNull() != null
                    if (!exists) return@transaction false

                    VendorsTable.update({ VendorsTable.id eq vendorUuid }) { stmt ->
                        obj["name"]?.jsonPrimitive?.contentOrNull?.let { stmt[name] = it }
                        obj["address"]?.jsonPrimitive?.contentOrNull?.let { stmt[address] = it }
                        obj["contact_phone"]?.jsonPrimitive?.contentOrNull?.let { stmt[contactPhone] = it }
                        obj["wallet_phone"]?.jsonPrimitive?.contentOrNull?.let { stmt[walletPhone] = it }
                        obj["logo_url"]?.jsonPrimitive?.contentOrNull?.let { stmt[logoUrl] = it }
                        obj["store_type"]?.jsonPrimitive?.contentOrNull?.let { stmt[storeType] = it }
                        obj["business_type"]?.jsonPrimitive?.contentOrNull?.let { stmt[businessType] = it }
                        obj["default_delivery_fee"]?.jsonPrimitive?.doubleOrNull?.let { stmt[defaultDeliveryFee] = java.math.BigDecimal.valueOf(it) }
                        obj["tax_enabled"]?.jsonPrimitive?.booleanOrNull?.let { stmt[taxEnabled] = it }
                        obj["default_tax_percent"]?.jsonPrimitive?.doubleOrNull?.let { stmt[defaultTaxPercent] = java.math.BigDecimal.valueOf(it) }
                        obj["stock_mode"]?.jsonPrimitive?.contentOrNull?.let { stmt[stockMode] = it }
                        obj["digital_menu_url"]?.jsonPrimitive?.contentOrNull?.let { stmt[digitalMenuUrl] = it }
                        obj["enable_digital_menu"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableDigitalMenu] = it }
                        obj["enable_tables"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableTables] = it }
                        obj["enable_kds"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableKds] = it }
                        obj["enable_dine_in"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableDineIn] = it }
                        obj["enable_delivery"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableDelivery] = it }
                        obj["enable_takeaway"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableTakeaway] = it }
                        obj["enable_in_store"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableInStore] = it }
                        obj["enable_pickup_later"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enablePickupLater] = it }
                        obj["enable_recipe"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableRecipe] = it }
                        obj["enable_split_payment"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableSplitPayment] = it }
                        obj["enable_cash_drawer"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableCashDrawer] = it }
                        obj["enable_returns"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableReturns] = it }
                        obj["enable_customer_credit"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableCustomerCredit] = it }
                        obj["enable_installments"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableInstallments] = it }
                        obj["enable_pre_orders"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enablePreOrders] = it }
                        obj["enable_scheduled_orders"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableScheduledOrders] = it }
                        obj["enable_suppliers"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableSuppliers] = it }
                        obj["enable_drug_interactions"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableDrugInteractions] = it }
                        obj["enable_prescriptions"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enablePrescriptions] = it }
                        obj["enable_analytics"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableAnalytics] = it }
                        obj["enable_announcements"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableAnnouncements] = it }
                        obj["enable_stock"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableStock] = it }
                        obj["enable_attendance"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableAttendance] = it }
                        obj["enable_overtime"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableOvertime] = it }
                        obj["enable_salary"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableSalary] = it }
                        obj["enable_customers"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableCustomers] = it }
                        obj["enable_export"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableExport] = it }
                        obj["enable_digital_receipt"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableDigitalReceipt] = it }
                        obj["enable_whatsapp_receipt"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableWhatsappReceipt] = it }
                        obj["enable_worker_qrcode"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableWorkerQrcode] = it }
                        obj["enable_loyalty"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableLoyalty] = it }
                        obj["enable_manual_discount"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableManualDiscount] = it }
                        obj["enable_offers"]?.jsonPrimitive?.booleanOrNull?.let { stmt[enableOffers] = it }
                        obj["loyalty_enabled"]?.jsonPrimitive?.booleanOrNull?.let { stmt[loyaltyEnabled] = it }
                        obj["points_earn_rate"]?.jsonPrimitive?.doubleOrNull?.let { stmt[pointsEarnRate] = java.math.BigDecimal.valueOf(it) }
                        obj["points_redeem_rate"]?.jsonPrimitive?.doubleOrNull?.let { stmt[pointsRedeemRate] = java.math.BigDecimal.valueOf(it) }
                        obj["min_points_redeem"]?.jsonPrimitive?.intOrNull?.let { stmt[minPointsRedeem] = it }
                        obj["max_manual_discount_percent"]?.jsonPrimitive?.doubleOrNull?.let { stmt[maxManualDiscountPercent] = java.math.BigDecimal.valueOf(it) }
                        obj["manual_discount_requires_pin"]?.jsonPrimitive?.booleanOrNull?.let { stmt[manualDiscountRequiresPin] = it }
                        stmt[updatedAt] = kotlinx.datetime.Clock.System.now()
                    }
                    true
                }
                if (updated) {
                    trace.step("Vendor updated via dashboard", mapOf("vendorId" to id))
                    val json = buildJsonObject { put("success", true); put("message", "Vendor updated") }
                    call.respondText(json.toString(), ContentType.Application.Json)
                } else {
                    trace.step("Vendor not found for dashboard update", mapOf("vendorId" to id))
                    call.respondText("""{"error":"Vendor not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
                trace.step("Dashboard update vendor completed")
            }

            delete("/vendors/{id}") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                val vendorUUID = UUID.fromString(id)
                transaction {
                    // Cascade delete — delete order items first via order IDs
                    val orderIds = OrdersTable.selectAll().where { OrdersTable.vendorId eq vendorUUID }.map { it[OrdersTable.id].value }
                    orderIds.forEach { oid -> OrderItemsTable.deleteWhere { OrderItemsTable.orderId eq oid } }
                    OrdersTable.deleteWhere { OrdersTable.vendorId eq vendorUUID }
                    UsersTable.deleteWhere { UsersTable.vendorId eq vendorUUID }
                    WorkersTable.deleteWhere { WorkersTable.vendorId eq vendorUUID }
                    CategoriesTable.deleteWhere { CategoriesTable.vendorId eq vendorUUID }
                    ItemsTable.deleteWhere { ItemsTable.vendorId eq vendorUUID }
                    VendorSubscriptionsTable.deleteWhere { VendorSubscriptionsTable.vendorId eq vendorUUID }
                    VendorsTable.deleteWhere { VendorsTable.id eq vendorUUID }
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
            }

            // ─── Vendor User CRUD (cookie auth) ────────────────────
            get("/vendors/{id}/users") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                val users = transaction {
                    UsersTable.selectAll().where { UsersTable.vendorId eq UUID.fromString(id) }
                        .map { buildJsonObject {
                            put("id", it[UsersTable.id].value.toString())
                            put("name", it[UsersTable.name])
                            put("phone", it[UsersTable.phone])
                            put("email", it[UsersTable.email] ?: "")
                            put("role", it[UsersTable.role])
                            put("active", it[UsersTable.active])
                        } }
                }
                call.respondText(buildJsonArray { users.forEach { add(it) } }.toString(), ContentType.Application.Json)
            }

            post("/vendors/{id}/users") {
                val vendorId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val userName = obj["name"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name required"))
                val userPhone = obj["phone"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phone required"))
                val userPassword = obj["password"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "password required"))
                val userRole = obj["role"]?.jsonPrimitive?.content ?: "CASHIER"
                val vendorUUID = UUID.fromString(vendorId)
                // Check phone uniqueness
                val existing = transaction { UsersTable.selectAll().where { (UsersTable.vendorId eq vendorUUID) and (UsersTable.phone eq userPhone) }.firstOrNull() }
                if (existing != null) return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Phone already exists"))
                val passwordHash = BCrypt.hashpw(userPassword, BCrypt.gensalt())
                val now = kotlinx.datetime.Clock.System.now()
                transaction {
                    val userId = UsersTable.insertAndGetId {
                        it[UsersTable.vendorId] = vendorUUID
                        it[role] = userRole; it[name] = userName; it[phone] = userPhone
                        it[UsersTable.passwordHash] = passwordHash; it[active] = true
                        it[createdAt] = now; it[updatedAt] = now
                    }
                    val workerCount = WorkersTable.selectAll().where { WorkersTable.vendorId eq vendorUUID }.count()
                    WorkersTable.insert {
                        it[WorkersTable.vendorId] = vendorUUID; it[WorkersTable.userId] = userId
                        it[workerId] = "WRK-${(workerCount + 1).toString().padStart(3, '0')}"
                        it[fullName] = userName; it[WorkersTable.phone] = userPhone
                        it[WorkersTable.role] = userRole; it[salaryType] = "MONTHLY"
                        it[WorkersTable.active] = true; it[WorkersTable.createdAt] = now; it[WorkersTable.updatedAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("status" to "created"))
            }

            delete("/vendors/{vendorId}/users/{userId}") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val userId = call.parameters["userId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "userId required"))
                transaction {
                    UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                        it[active] = false; it[updatedAt] = kotlinx.datetime.Clock.System.now()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "deactivated"))
            }

            // ─── Team CRUD (cookie auth) ───────────────────────────
            get("/team") {
                val team = transaction {
                    AdminUsersTable.selectAll().orderBy(AdminUsersTable.createdAt, SortOrder.ASC).map {
                        buildJsonObject {
                            put("id", it[AdminUsersTable.id].value.toString())
                            put("name", it[AdminUsersTable.name])
                            put("email", it[AdminUsersTable.email])
                            put("role", it[AdminUsersTable.role])
                            put("active", it[AdminUsersTable.active])
                            put("last_login_at", it[AdminUsersTable.lastLoginAt]?.toEpochMilliseconds())
                            put("created_at", it[AdminUsersTable.createdAt].toEpochMilliseconds())
                        }
                    }
                }
                call.respondText(buildJsonArray { team.forEach { add(it) } }.toString(), ContentType.Application.Json)
            }

            post("/team") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                val tName = obj["name"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name required"))
                val tEmail = obj["email"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "email required"))
                val tPassword = obj["password"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "password required"))
                val tRole = obj["role"]?.jsonPrimitive?.content ?: "support"
                // Check email uniqueness
                val existing = transaction { AdminUsersTable.selectAll().where { AdminUsersTable.email eq tEmail }.firstOrNull() }
                if (existing != null) return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already exists"))
                val passwordHash = BCrypt.hashpw(tPassword, BCrypt.gensalt())
                transaction {
                    AdminUsersTable.insert {
                        it[name] = tName; it[email] = tEmail
                        it[AdminUsersTable.passwordHash] = passwordHash
                        it[role] = if (tRole in listOf("super_admin", "support")) tRole else "support"
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("status" to "created"))
            }

            put("/team/{id}") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                val body = call.receiveText()
                val obj = Json.parseToJsonElement(body).jsonObject
                transaction {
                    AdminUsersTable.update({ AdminUsersTable.id eq UUID.fromString(id) }) {
                        obj["name"]?.jsonPrimitive?.contentOrNull?.let { n -> it[name] = n }
                        obj["role"]?.jsonPrimitive?.contentOrNull?.let { r -> if (r in listOf("super_admin", "support")) it[role] = r }
                        obj["active"]?.jsonPrimitive?.booleanOrNull?.let { a -> it[active] = a }
                        obj["password"]?.jsonPrimitive?.contentOrNull?.let { p -> it[passwordHash] = BCrypt.hashpw(p, BCrypt.gensalt()) }
                        it[updatedAt] = kotlinx.datetime.Clock.System.now()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
            }

            delete("/team/{id}") {
                val admin = call.principal<AdminPrincipal>()!!
                if (!admin.isSuperAdmin) return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Super admin only"))
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID required"))
                if (id == admin.adminId) return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot delete yourself"))
                transaction {
                    AdminRefreshTokensTable.deleteWhere { AdminRefreshTokensTable.adminId eq UUID.fromString(id) }
                    AdminUsersTable.deleteWhere { AdminUsersTable.id eq UUID.fromString(id) }
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
            }
        }
    }
}

private fun dashFormatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
        bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
        else -> "$bytes B"
    }
}

// ════════════════════════════════════════════════════════════════
// Admin Layout (sidebar + header, matching CRM pattern)
// ════════════════════════════════════════════════════════════════

private fun adminLayout(title: String, admin: AdminPrincipal, activeTab: String, content: String): String {
    val roleDisplay = if (admin.isSuperAdmin) "مدير عام" else "دعم فني"

    fun navLink(tab: String, label: String, icon: String, href: String): String {
        val active = if (tab == activeTab) "bg-emerald-500/10 text-emerald-400 font-medium" else "text-zinc-400 hover:text-white hover:bg-white/5"
        return """<a href="$href" class="flex items-center gap-3 px-3 py-2 rounded-md $active transition-all text-sm">
            <span class="text-sm">$icon</span><span>$label</span>
        </a>"""
    }

    val navLinks = buildString {
        append(navLink("dashboard", "لوحة التحكم", "📊", "/admin/dashboard"))
        append(navLink("vendors", "العملاء", "🏪", "/admin/vendors"))
        if (admin.isSuperAdmin) {
            append(navLink("team", "فريق الإدارة", "👥", "/admin/team"))
        }
        append(navLink("plans", "الباقات", "📋", "/admin/plans"))
        append(navLink("logs", "السجلات", "📝", "/admin/logs"))
    }

    return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - وصلك Admin</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
    tailwind.config = { darkMode: 'class' };
    if (localStorage.getItem('darkMode') === 'true') document.documentElement.classList.add('dark');
    </script>
    <style>
        * { font-family: 'Inter', -apple-system, 'Segoe UI', sans-serif; }
        dialog::backdrop { background: rgba(0,0,0,0.3); }
        dialog { border: none; border-radius: 0.75rem; padding: 0; max-width: 600px; width: 90%; box-shadow: 0 20px 60px -15px rgba(0,0,0,0.2); }
        ::-webkit-scrollbar { width: 5px; }
        ::-webkit-scrollbar-thumb { background: #d4d4d8; border-radius: 10px; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .spinner { display: inline-block; width: 24px; height: 24px; border: 3px solid #e5e7eb; border-top-color: #059669; border-radius: 50%; animation: spin 0.8s linear infinite; }
        @media (max-width: 767px) { dialog { max-width: 100%; width: 100%; margin: 0; border-radius: 0.5rem; } }

        /* Stripe-inspired clean design */
        body { background: #f6f8fa; }
        .sidebar, .glass-sidebar { background: #0f172a; }
        .kpi-card { transition: box-shadow 0.15s; border: 1px solid #e5e7eb; }
        .kpi-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
        .badge-active { background: #ecfdf5; color: #059669; font-weight: 500; }
        .badge-suspended { background: #fef2f2; color: #dc2626; font-weight: 500; }
        .badge-super_admin { background: #ecfdf5; color: #059669; font-weight: 500; }
        .badge-support { background: #f0fdf4; color: #16a34a; font-weight: 500; }
        table { border-collapse: separate; border-spacing: 0; }
        table thead th { font-weight: 500; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: #6b7280; padding: 0.75rem 1rem; }
        table tbody td { padding: 0.75rem 1rem; }
        table tbody tr { transition: background 0.1s; border-bottom: 1px solid #f3f4f6; }
        table tbody tr:hover { background: #f9fafb; }
        input, select, textarea { border-radius: 0.5rem; border: 1px solid #d1d5db; padding: 0.5rem 0.75rem; font-size: 0.875rem; transition: border-color 0.15s, box-shadow 0.15s; }
        input:focus, select:focus, textarea:focus { border-color: #059669; box-shadow: 0 0 0 3px rgba(5,150,105,0.1); outline: none; }
        .btn-primary { background: #059669; color: white; font-weight: 500; border-radius: 0.5rem; padding: 0.5rem 1rem; transition: background 0.15s; }
        .btn-primary:hover { background: #047857; }

        /* Dark mode */
        html.dark body { background: #0a0a0a; color: #e5e7eb; }
        html.dark .sidebar, html.dark .glass-sidebar { background: #0a0a0a; border-left: 1px solid #1f2937; }
        html.dark .bg-white { background: #111111 !important; color: #e5e7eb; border: 1px solid #1f2937; }
        html.dark .bg-gray-50 { background: #0a0a0a !important; }
        html.dark .text-gray-800 { color: #f3f4f6 !important; }
        html.dark .text-gray-700 { color: #e5e7eb !important; }
        html.dark .text-gray-600 { color: #d1d5db !important; }
        html.dark .text-gray-500 { color: #9ca3af !important; }
        html.dark .border, html.dark .border-b, html.dark .border-t { border-color: #1f2937 !important; }
        html.dark .kpi-card { border-color: #1f2937 !important; background: #111111; }
        html.dark .shadow { box-shadow: none !important; }
        html.dark table thead th { color: #9ca3af !important; background: #111111 !important; }
        html.dark table tbody tr { border-color: #1f2937 !important; }
        html.dark table tbody tr:hover { background: #1a1a1a !important; }
        html.dark input, html.dark select, html.dark textarea { background: #1a1a1a !important; color: #e5e7eb !important; border-color: #374151 !important; }
        html.dark input:focus, html.dark select:focus { border-color: #059669 !important; box-shadow: 0 0 0 3px rgba(5,150,105,0.2) !important; }
        html.dark dialog { background: #111111 !important; color: #e5e7eb !important; border: 1px solid #1f2937; }
        html.dark h1, html.dark h2, html.dark h3 { color: #f3f4f6 !important; }
    </style>
</head>
<body class="min-h-screen transition-colors">
    <!-- Mobile header -->
    <div class="md:hidden fixed top-0 right-0 left-0 z-50 glass-sidebar flex items-center justify-between p-3">
        <div class="flex items-center gap-2">
            <img src="/landing/waslek_logo_sm.png" class="w-7 h-7 rounded-lg bg-white/10 p-0.5">
            <span class="text-white font-bold text-sm">وصلك</span>
        </div>
        <div class="text-white text-xs">
            <div class="font-medium leading-tight">${admin.email}</div>
            <div class="text-white/50 leading-tight text-[10px]">$roleDisplay</div>
        </div>
        <button onclick="document.getElementById('mobileSidebar').classList.toggle('hidden')" class="text-white text-xl bg-white/10 w-8 h-8 rounded-lg flex items-center justify-center">☰</button>
    </div>
    <!-- Mobile sidebar overlay -->
    <div id="mobileSidebar" class="hidden fixed inset-0 z-40 md:hidden">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" onclick="this.parentElement.classList.add('hidden')"></div>
        <aside class="glass-sidebar absolute right-0 top-0 bottom-0 w-72 text-white flex flex-col">
            <div class="p-5 border-b border-white/10 flex items-center gap-3">
                <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-10 h-10 rounded-xl bg-white/10 p-1">
                <div>
                    <h1 class="text-lg font-bold">وصلك Admin</h1>
                    <p class="text-xs text-white/50">لوحة تحكم الإدارة</p>
                </div>
            </div>
            <nav class="flex-1 p-3 space-y-0.5">$navLinks</nav>
            <div class="p-4 border-t border-white/10">
                <div class="flex items-center gap-3 mb-3">
                    <div class="w-9 h-9 rounded-lg bg-emerald-600 flex items-center justify-center text-white font-bold text-sm">${admin.email.first().uppercase()}</div>
                    <div class="text-sm">
                        <p class="font-medium text-white/90">${admin.email}</p>
                        <p class="text-white/50 text-xs">$roleDisplay</p>
                    </div>
                </div>
                <div class="flex gap-1.5 mt-2">
                    <button onclick="toggleDarkMode()" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition" id="darkBtn">🌙</button>
                    <button onclick="toggleLang()" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition" id="langBtn">EN</button>
                    <form method="POST" action="/admin/logout" class="inline">
                        <button type="submit" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition">🚪</button>
                    </form>
                </div>
            </div>
        </aside>
    </div>

    <div class="flex min-h-screen">
        <!-- Desktop Sidebar -->
        <aside class="glass-sidebar w-64 min-h-screen text-white hidden md:flex flex-col flex-shrink-0">
            <div class="p-5 border-b border-white/10 flex items-center gap-3">
                <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-10 h-10 rounded-xl bg-white/10 p-1">
                <div>
                    <h1 class="text-lg font-bold">وصلك Admin</h1>
                    <p class="text-xs text-white/50">لوحة تحكم الإدارة</p>
                </div>
            </div>
            <nav class="flex-1 p-3 space-y-0.5">$navLinks</nav>
            <div class="p-4 border-t border-white/10">
                <div class="flex items-center gap-3 mb-3">
                    <div class="w-9 h-9 rounded-lg bg-emerald-600 flex items-center justify-center text-white font-bold text-sm">${admin.email.first().uppercase()}</div>
                    <div class="text-sm">
                        <p class="font-medium text-white/90">${admin.email}</p>
                        <p class="text-white/50 text-xs">$roleDisplay</p>
                    </div>
                </div>
                <div class="flex gap-1.5 mt-2">
                    <button onclick="toggleDarkMode()" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition" id="darkBtn">🌙</button>
                    <button onclick="toggleLang()" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition" id="langBtn">EN</button>
                    <form method="POST" action="/admin/logout" class="inline">
                        <button type="submit" class="text-xs text-white/50 hover:text-white px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 transition">🚪</button>
                    </form>
                </div>
            </div>
        </aside>

        <!-- Main Content -->
        <main class="flex-1 p-4 md:p-8 pt-16 md:pt-8 overflow-auto">
            <div class="mb-6">
                <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-100">$title</h2>
            </div>
            $content
        </main>
    </div>

    <script>
    // ─── Dark Mode & Language ───────────────────────────────
    function toggleDarkMode() {
        document.documentElement.classList.toggle('dark');
        const isDark = document.documentElement.classList.contains('dark');
        localStorage.setItem('darkMode', isDark);
        document.querySelectorAll('#darkBtn').forEach(b => b.textContent = isDark ? '☀️' : '🌙');
    }
    if (localStorage.getItem('darkMode') === 'true') {
        document.querySelectorAll('#darkBtn').forEach(b => b.textContent = '☀️');
    }
    const t = {
        'لوحة التحكم':'Dashboard','العملاء (Vendors)':'Vendors','العملاء':'Vendors',
        'فريق الإدارة':'Admin Team','الباقات':'Plans','السجلات':'Logs',
        'لوحة تحكم الإدارة':'Admin Control Panel','وصلك Admin':'Waselak Admin',
        'إجمالي العملاء':'Total Vendors','عملاء نشطين':'Active Vendors',
        'إجمالي المستخدمين':'Total Users','عملاء موقوفين':'Suspended',
        'إدارة العملاء':'Manage Vendors','الباقات':'Plans','آخر العملاء المضافين':'Recent Vendors',
        'عرض الكل':'View All','الاسم':'Name','النوع':'Type','المستخدمين':'Users',
        'الحالة':'Status','تاريخ الإنشاء':'Created','الهاتف':'Phone',
        'نشط':'Active','موقوف':'Suspended','تفاصيل':'Details',
        'تعليق':'Suspend','حذف':'Delete','إضافة عميل جديد':'Add New Vendor',
        'إضافة مستخدم':'Add User','تسجيل الخروج':'Logout',
        'دعوة عضو جديد':'Invite Member','مدير عام':'Super Admin',
        'دعم فني':'Support','البريد':'Email','الدور':'Role',
        'إجراءات':'Actions','تعطيل':'Deactivate','تفعيل':'Activate',
        'كلمة السر':'Password','كلمة المرور':'Password',
        'تسجيل الدخول':'Login','العنوان':'Address',
        'اسم العميل':'Vendor Name','اسم المدير':'Manager Name',
        'هاتف المدير':'Manager Phone','كلمة مرور المدير':'Manager Password',
        'نوع النشاط':'Business Type',
    };
    const tRev = {}; for (const [ar, en] of Object.entries(t)) tRev[en] = ar;
    let isArabic = localStorage.getItem('lang') !== 'en';

    function translatePage(toEn) {
        const map = toEn ? t : tRev;
        const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        while (walk.nextNode()) {
            const node = walk.currentNode;
            const trimmed = node.textContent.trim();
            if (!trimmed) continue;
            if (map[trimmed]) { node.textContent = node.textContent.replace(trimmed, map[trimmed]); continue; }
            let txt = node.textContent;
            for (const [from, to] of Object.entries(map)) { if (txt.includes(from)) txt = txt.replaceAll(from, to); }
            node.textContent = txt;
        }
        document.querySelectorAll('input[placeholder]').forEach(el => { if (map[el.placeholder.trim()]) el.placeholder = map[el.placeholder.trim()]; });
        document.querySelectorAll('select option').forEach(o => { if (map[o.textContent.trim()]) o.textContent = map[o.textContent.trim()]; });
    }

    function toggleLang() {
        isArabic = !isArabic;
        document.documentElement.dir = isArabic ? 'rtl' : 'ltr';
        document.documentElement.lang = isArabic ? 'ar' : 'en';
        document.body.style.fontFamily = isArabic ? "'Segoe UI', Tahoma, Arial, sans-serif" : "Inter, 'Segoe UI', system-ui, sans-serif";
        document.querySelectorAll('#langBtn').forEach(b => b.textContent = isArabic ? 'EN' : 'عربي');
        localStorage.setItem('lang', isArabic ? 'ar' : 'en');
        translatePage(!isArabic);
    }
    if (!isArabic) { isArabic = true; setTimeout(() => toggleLang(), 100); }

    // ─── Global Helpers ─────────────────────────────────────
    async function apiFetch(url, options = {}) {
        const defaults = { headers: { 'Content-Type': 'application/json' } };
        const res = await fetch(url, { ...defaults, ...options });
        if (res.status === 401) { window.location.href = '/admin/login'; return null; }
        return res;
    }
    async function cmsApiFetch(url, options = {}) {
        const token = getToken();
        const defaults = { headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token } };
        const merged = { ...defaults, ...options, headers: { ...defaults.headers, ...(options.headers || {}) } };
        const res = await fetch(url, merged);
        if (res.status === 401) { window.location.href = '/admin/login'; return null; }
        return res;
    }
    function escHtml(s) {
        if (!s) return '';
        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function formatDate(ms) {
        if (!ms) return '-';
        return new Date(ms).toLocaleDateString('ar-EG', { year:'numeric', month:'short', day:'numeric' });
    }
    function formatDateTime(iso) {
        if (!iso) return '-';
        return new Date(iso).toLocaleString('ar-EG');
    }
    function showToast(msg, type) {
        const t = document.createElement('div');
        t.className = 'fixed top-4 left-1/2 -translate-x-1/2 z-[9999] px-6 py-3 rounded-lg shadow-lg text-white text-sm font-medium transition-all';
        t.style.background = type === 'error' ? '#dc2626' : '#16a34a';
        t.textContent = msg;
        document.body.appendChild(t);
        setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, 3000);
    }
    </script>
</body>
</html>"""
}

// ════════════════════════════════════════════════════════════════
// Login Page
// ════════════════════════════════════════════════════════════════

private fun loginPageHtml(error: String? = null): String {
    val errorBlock = if (error != null) {
        """<div class="bg-red-100 text-red-700 p-3 rounded-lg text-sm mb-4">$error</div>"""
    } else ""

    return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>تسجيل الدخول - وصلك Admin</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; }</style>
</head>
<body class="min-h-screen flex items-center justify-center" style="background: linear-gradient(135deg, #0f172a 0%, #1B3A5C 100%)">
    <div class="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md">
        <div class="text-center mb-8">
            <img src="/landing/waslek_logo_sm.png" alt="وصلك" class="w-20 h-20 mx-auto mb-4 rounded-xl shadow-lg">
            <h1 class="text-2xl font-bold" style="color:#1B3A5C">وصلك Admin</h1>
            <p class="text-gray-500 mt-2">لوحة تحكم الإدارة</p>
        </div>
        $errorBlock
        <form method="POST" action="/admin/login">
            <div class="mb-4">
                <label class="block text-sm font-medium text-gray-700 mb-2">البريد الإلكتروني</label>
                <input type="email" name="email" required autofocus class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-transparent outline-none" placeholder="admin@waselak.com">
            </div>
            <div class="mb-6">
                <label class="block text-sm font-medium text-gray-700 mb-2">كلمة المرور</label>
                <input type="password" name="password" required class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-transparent outline-none" placeholder="••••••••">
            </div>
            <button type="submit" class="w-full text-white py-3 rounded-lg font-medium hover:opacity-90 transition" style="background: linear-gradient(135deg, #f97316, #ea580c)">تسجيل الدخول</button>
        </form>
    </div>
</body>
</html>"""
}

// ════════════════════════════════════════════════════════════════
// Dashboard Overview Content
// ════════════════════════════════════════════════════════════════

private fun dashboardContent(): String = """
<div id="dashLoading" class="text-center py-12"><div class="spinner"></div><p class="text-gray-500 mt-3">جاري التحميل...</p></div>
<div id="dashContent" style="display:none">
    <!-- KPI Cards -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div class="kpi-card bg-white rounded-xl shadow p-5 border-r-4 border-blue-600">
            <p class="text-gray-500 text-sm">إجمالي العملاء</p>
            <p class="text-3xl font-bold text-gray-800 mt-1" id="kpiTotalVendors">0</p>
        </div>
        <div class="kpi-card bg-white rounded-xl shadow p-5 border-r-4 border-green-600">
            <p class="text-gray-500 text-sm">عملاء نشطين</p>
            <p class="text-3xl font-bold text-gray-800 mt-1" id="kpiActiveVendors">0</p>
        </div>
        <div class="kpi-card bg-white rounded-xl shadow p-5 border-r-4 border-orange-500">
            <p class="text-gray-500 text-sm">إجمالي المستخدمين</p>
            <p class="text-3xl font-bold text-gray-800 mt-1" id="kpiTotalUsers">0</p>
        </div>
        <div class="kpi-card bg-white rounded-xl shadow p-5 border-r-4 border-red-500">
            <p class="text-gray-500 text-sm">عملاء موقوفين</p>
            <p class="text-3xl font-bold text-gray-800 mt-1" id="kpiSuspendedVendors">0</p>
        </div>
    </div>

    <!-- Quick Actions -->
    <div class="flex flex-wrap gap-3 mb-8">
        <a href="/admin/vendors" class="bg-blue-600 text-white px-5 py-2.5 rounded-lg text-sm font-medium hover:bg-blue-700 transition">🏪 إدارة العملاء</a>
        <a href="/admin/logs" class="bg-gray-700 text-white px-5 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-800 transition">📝 السجلات</a>
        <a href="/admin/plans" class="bg-orange-500 text-white px-5 py-2.5 rounded-lg text-sm font-medium hover:bg-orange-600 transition">📋 الباقات</a>
    </div>

    <!-- Recent Vendors -->
    <div class="bg-white rounded-xl shadow overflow-hidden">
        <div class="p-4 border-b flex justify-between items-center">
            <h3 class="font-bold text-gray-800">آخر العملاء المضافين</h3>
            <a href="/admin/vendors" class="text-blue-600 text-sm hover:underline">عرض الكل</a>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead>
                    <tr class="bg-gray-50 border-b">
                        <th class="p-3 text-right font-medium text-gray-600">الاسم</th>
                        <th class="p-3 text-right font-medium text-gray-600">النوع</th>
                        <th class="p-3 text-right font-medium text-gray-600">الباقة</th>
                        <th class="p-3 text-right font-medium text-gray-600">المستخدمين</th>
                        <th class="p-3 text-right font-medium text-gray-600">الحالة</th>
                        <th class="p-3 text-right font-medium text-gray-600">تاريخ الإنشاء</th>
                    </tr>
                </thead>
                <tbody id="recentVendorsBody">
                    <tr><td colspan="6" class="text-center py-8 text-gray-400">جاري التحميل...</td></tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', async () => {
    try {
        const [vendorsRes, statsRes] = await Promise.all([
            apiFetch('/admin/api/vendors/full'),
            apiFetch('/admin/api/logs/stats')
        ]);
        const vendors = vendorsRes ? await vendorsRes.json() : [];
        const stats = statsRes ? await statsRes.json() : {};

        document.getElementById('kpiTotalVendors').textContent = vendors.length;
        document.getElementById('kpiActiveVendors').textContent = vendors.filter(v => !v.isSuspended).length;
        document.getElementById('kpiTotalUsers').textContent = vendors.reduce((s, v) => s + (v.usersCount || 0), 0);
        document.getElementById('kpiSuspendedVendors').textContent = vendors.filter(v => v.isSuspended).length;

        // Recent vendors (last 10 by createdAt)
        const sorted = [...vendors].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0)).slice(0, 10);
        const tbody = document.getElementById('recentVendorsBody');
        if (!sorted.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-gray-400">لا يوجد عملاء</td></tr>';
        } else {
            tbody.innerHTML = sorted.map(v => {
                const statusBadge = v.isSuspended
                    ? '<span class="badge-suspended px-2 py-1 rounded-full text-xs font-medium">موقوف</span>'
                    : '<span class="badge-active px-2 py-1 rounded-full text-xs font-medium">نشط</span>';
                return '<tr class="border-b hover:bg-gray-50">' +
                    '<td class="p-3 font-medium">' + escHtml(v.name) + '</td>' +
                    '<td class="p-3">' + escHtml(v.businessType || '-') + '</td>' +
                    '<td class="p-3"><span class="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium">' + escHtml(v.planDisplayName) + '</span></td>' +
                    '<td class="p-3">' + (v.usersCount || 0) + '</td>' +
                    '<td class="p-3">' + statusBadge + '</td>' +
                    '<td class="p-3 text-gray-500">' + formatDate(v.createdAt) + '</td>' +
                    '</tr>';
            }).join('');
        }

        document.getElementById('dashLoading').style.display = 'none';
        document.getElementById('dashContent').style.display = '';
    } catch(e) {
        document.getElementById('dashLoading').innerHTML = '<p class="text-red-500">حدث خطأ في تحميل البيانات</p>';
    }
});
</script>
"""

// ════════════════════════════════════════════════════════════════
// Vendors List Content
// ════════════════════════════════════════════════════════════════

private fun vendorsContent(admin: AdminPrincipal): String {
    val createBtn = if (admin.isSuperAdmin) """
        <button onclick="document.getElementById('createVendorModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800 text-sm font-medium">+ إضافة عميل جديد</button>
    """ else ""

    return """
<div class="flex justify-between items-center mb-4">
    <div></div>
    $createBtn
</div>

<!-- Search & Filter -->
<div class="bg-white rounded-xl shadow p-4 mb-4 flex flex-wrap gap-3 items-end">
    <div class="flex-1 min-w-[200px]">
        <label class="block text-xs font-medium text-gray-500 mb-1">بحث</label>
        <input type="text" id="vendorSearch" placeholder="اسم أو رقم الهاتف..." class="w-full px-3 py-2 border rounded-lg text-sm" oninput="filterVendors()">
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">النوع</label>
        <select id="vendorTypeFilter" class="px-3 py-2 border rounded-lg text-sm" onchange="filterVendors()">
            <option value="">الكل</option>
            <option value="RESTAURANT">RESTAURANT</option>
            <option value="CAFE">CAFE</option>
            <option value="PHARMACY">PHARMACY</option>
            <option value="RETAIL">RETAIL</option>
            <option value="SUPERMARKET">SUPERMARKET</option>
            <option value="BAKERY">BAKERY</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">الباقة</label>
        <select id="vendorPlanFilter" class="px-3 py-2 border rounded-lg text-sm" onchange="filterVendors()">
            <option value="">الكل</option>
            <option value="STARTER">STARTER</option>
            <option value="BUSINESS">BUSINESS</option>
            <option value="ENTERPRISE">ENTERPRISE</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">الحالة</label>
        <select id="vendorStatusFilter" class="px-3 py-2 border rounded-lg text-sm" onchange="filterVendors()">
            <option value="">الكل</option>
            <option value="active">نشط</option>
            <option value="suspended">موقوف</option>
        </select>
    </div>
</div>

<!-- Stats -->
<div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
    <div class="bg-white rounded-lg shadow p-3 text-center">
        <p class="text-gray-500 text-xs">الإجمالي</p>
        <p class="text-2xl font-bold" id="vStatTotal">0</p>
    </div>
    <div class="bg-white rounded-lg shadow p-3 text-center">
        <p class="text-gray-500 text-xs">Starter</p>
        <p class="text-2xl font-bold text-gray-600" id="vStatStarter">0</p>
    </div>
    <div class="bg-white rounded-lg shadow p-3 text-center">
        <p class="text-gray-500 text-xs">Business</p>
        <p class="text-2xl font-bold text-blue-600" id="vStatBusiness">0</p>
    </div>
    <div class="bg-white rounded-lg shadow p-3 text-center">
        <p class="text-gray-500 text-xs">Enterprise</p>
        <p class="text-2xl font-bold text-orange-600" id="vStatEnterprise">0</p>
    </div>
</div>

<!-- Vendors Table -->
<div class="bg-white rounded-xl shadow overflow-hidden">
    <div class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
                <tr class="bg-gray-50 border-b">
                    <th class="p-3 text-right font-medium text-gray-600">الاسم</th>
                    <th class="p-3 text-right font-medium text-gray-600">الهاتف</th>
                    <th class="p-3 text-right font-medium text-gray-600">النوع</th>
                    <th class="p-3 text-right font-medium text-gray-600">الباقة</th>
                    <th class="p-3 text-right font-medium text-gray-600">المستخدمين</th>
                    <th class="p-3 text-right font-medium text-gray-600">الطلبات/شهر</th>
                    <th class="p-3 text-right font-medium text-gray-600">الحالة</th>
                    <th class="p-3 text-right font-medium text-gray-600">إجراءات</th>
                </tr>
            </thead>
            <tbody id="vendorsTableBody">
                <tr><td colspan="8" class="text-center py-8 text-gray-400"><div class="spinner"></div><br>جاري التحميل...</td></tr>
            </tbody>
        </table>
    </div>
</div>

<!-- Create Vendor Modal -->
<dialog id="createVendorModal" class="rounded-2xl shadow-2xl">
    <div class="p-6">
        <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">إضافة عميل جديد</h3>
            <button onclick="this.closest('dialog').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <form onsubmit="createVendor(event)">
            <div class="grid grid-cols-2 gap-3">
                <div class="col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">اسم المحل *</label>
                    <input name="vendor_name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">العنوان *</label>
                    <input name="vendor_address" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">هاتف المحل *</label>
                    <input name="vendor_phone" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">نوع النشاط</label>
                    <select name="business_type" class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="RESTAURANT">RESTAURANT</option>
                        <option value="CAFE">CAFE</option>
                        <option value="PHARMACY">PHARMACY</option>
                        <option value="RETAIL">RETAIL</option>
                        <option value="SUPERMARKET">SUPERMARKET</option>
                        <option value="BAKERY">BAKERY</option>
                    </select>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الباقة</label>
                    <select name="plan" class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="STARTER">STARTER</option>
                        <option value="BUSINESS">BUSINESS</option>
                        <option value="ENTERPRISE">ENTERPRISE</option>
                    </select>
                </div>
                <div class="col-span-2 border-t pt-3 mt-2">
                    <p class="text-sm font-bold text-gray-700 mb-2">بيانات المدير</p>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">اسم المدير *</label>
                    <input name="manager_name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">هاتف المدير *</label>
                    <input name="manager_phone" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div class="col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة مرور المدير *</label>
                    <input name="manager_password" type="password" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
            </div>
            <div class="flex justify-end gap-2 mt-5">
                <button type="button" onclick="this.closest('dialog').close()" class="px-4 py-2 border rounded-lg text-sm">إلغاء</button>
                <button type="submit" class="bg-green-700 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-green-800">إنشاء</button>
            </div>
        </form>
    </div>
</dialog>

<script>
let allVendors = [];

document.addEventListener('DOMContentLoaded', loadVendorsList);

async function loadVendorsList() {
    const res = await apiFetch('/admin/api/vendors/full');
    if (!res) return;
    allVendors = await res.json();

    document.getElementById('vStatTotal').textContent = allVendors.length;
    document.getElementById('vStatStarter').textContent = allVendors.filter(v => v.planName === 'STARTER').length;
    document.getElementById('vStatBusiness').textContent = allVendors.filter(v => v.planName === 'BUSINESS').length;
    document.getElementById('vStatEnterprise').textContent = allVendors.filter(v => v.planName === 'ENTERPRISE').length;

    renderVendors(allVendors);
}

function filterVendors() {
    const search = document.getElementById('vendorSearch').value.toLowerCase();
    const typeFilter = document.getElementById('vendorTypeFilter').value;
    const planFilter = document.getElementById('vendorPlanFilter').value;
    const statusFilter = document.getElementById('vendorStatusFilter').value;

    let filtered = allVendors.filter(v => {
        if (search && !v.name.toLowerCase().includes(search) && !(v.phone || '').includes(search)) return false;
        if (typeFilter && v.businessType !== typeFilter) return false;
        if (planFilter && v.planName !== planFilter) return false;
        if (statusFilter === 'active' && v.isSuspended) return false;
        if (statusFilter === 'suspended' && !v.isSuspended) return false;
        return true;
    });
    renderVendors(filtered);
}

function renderVendors(vendors) {
    const tbody = document.getElementById('vendorsTableBody');
    if (!vendors.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-gray-400">لا توجد نتائج</td></tr>';
        return;
    }
    const isSuperAdmin = ${admin.isSuperAdmin};
    tbody.innerHTML = vendors.map(v => {
        const statusBadge = v.isSuspended
            ? '<span class="badge-suspended px-2 py-1 rounded-full text-xs font-medium">موقوف</span>'
            : '<span class="badge-active px-2 py-1 rounded-full text-xs font-medium">نشط</span>';
        let actions = '<a href="/admin/vendors/' + v.id + '" class="text-blue-600 hover:underline text-xs">تفاصيل</a>';
        if (isSuperAdmin) {
            const suspendLabel = v.isSuspended ? 'تفعيل' : 'إيقاف';
            const suspendClass = v.isSuspended ? 'text-green-600' : 'text-yellow-600';
            actions += ' <button onclick="toggleSuspend(\'' + v.id + '\',' + v.isSuspended + ')" class="' + suspendClass + ' hover:underline text-xs">' + suspendLabel + '</button>';
            actions += ' <button onclick="deleteVendor(\'' + v.id + '\',\'' + escHtml(v.name).replace(/'/g,"\\'") + '\')" class="text-red-600 hover:underline text-xs">حذف</button>';
        }
        return '<tr class="border-b hover:bg-gray-50">' +
            '<td class="p-3 font-medium">' + escHtml(v.name) + '</td>' +
            '<td class="p-3" dir="ltr">' + escHtml(v.phone || '-') + '</td>' +
            '<td class="p-3">' + escHtml(v.businessType || '-') + '</td>' +
            '<td class="p-3"><span class="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium">' + escHtml(v.planDisplayName) + '</span></td>' +
            '<td class="p-3">' + (v.usersCount || 0) + '</td>' +
            '<td class="p-3">' + (v.monthlyOrders || 0) + '</td>' +
            '<td class="p-3">' + statusBadge + '</td>' +
            '<td class="p-3 flex gap-2">' + actions + '</td>' +
            '</tr>';
    }).join('');
}

async function toggleSuspend(id, currentlySuspended) {
    const action = currentlySuspended ? 'تفعيل' : 'إيقاف';
    if (!confirm('هل تريد ' + action + ' هذا العميل؟')) return;
    const res = await fetch('/admin/api/vendors/' + id + '/suspend', { method: 'POST', headers: {'Content-Type': 'application/json'} });
    if (res && res.ok) { showToast('تم ' + action + ' العميل', 'success'); loadVendorsList(); }
    else { showToast('حدث خطأ', 'error'); }
}

async function deleteVendor(id, name) {
    if (!confirm('هل أنت متأكد من حذف "' + name + '"؟ لا يمكن التراجع.')) return;
    if (!confirm('تأكيد نهائي: سيتم حذف جميع بيانات العميل. متابعة؟')) return;
    const res = await fetch('/admin/api/vendors/' + id, { method: 'DELETE' });
    if (res && res.ok) { showToast('تم حذف العميل', 'success'); loadVendorsList(); }
    else { showToast('حدث خطأ في الحذف', 'error'); }
}

async function createVendor(e) {
    e.preventDefault();
    const form = e.target;
    const data = Object.fromEntries(new FormData(form));
    const res = await fetch('/admin/api/vendors', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res && res.ok) {
        form.closest('dialog').close();
        showToast('تم إنشاء العميل بنجاح', 'success');
        loadVendorsList();
    } else {
        const err = res ? await res.json().catch(() => ({})) : {};
        showToast(err.message || err.error || 'حدث خطأ', 'error');
    }
}
</script>
"""
}

// ════════════════════════════════════════════════════════════════
// Vendor Detail Content
// ════════════════════════════════════════════════════════════════

private fun vendorDetailContent(vendorId: String, admin: AdminPrincipal): String = """
<div class="flex justify-between items-center mb-4">
    <a href="/admin/vendors" class="text-blue-600 hover:underline text-sm">&larr; العودة للقائمة</a>
    <a href="/admin/vendors/$vendorId/edit" class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">تعديل البيانات</a>
</div>

<div id="detailLoading" class="text-center py-12"><div class="spinner"></div><p class="text-gray-500 mt-3">جاري التحميل...</p></div>
<div id="detailContent" style="display:none">
    <!-- Vendor Info Card -->
    <div class="bg-white rounded-xl shadow p-6 mb-6">
        <div class="flex justify-between items-start mb-4">
            <div>
                <h3 class="text-xl font-bold text-gray-800" id="vdName"></h3>
                <p class="text-gray-500 text-sm mt-1" id="vdMeta"></p>
            </div>
            <div id="vdStatusBadge"></div>
        </div>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div><span class="text-gray-500">الهاتف:</span> <span id="vdPhone" dir="ltr"></span></div>
            <div><span class="text-gray-500">العنوان:</span> <span id="vdAddress"></span></div>
            <div><span class="text-gray-500">نوع النشاط:</span> <span id="vdType"></span></div>
            <div><span class="text-gray-500">الباقة:</span> <span id="vdPlan"></span></div>
            <div><span class="text-gray-500">تاريخ الإنشاء:</span> <span id="vdCreated"></span></div>
            <div><span class="text-gray-500">الأصناف:</span> <span id="vdItems"></span></div>
            <div><span class="text-gray-500">الطلبات/شهر:</span> <span id="vdOrders"></span></div>
        </div>
    </div>

    <!-- Users Table -->
    <div class="bg-white rounded-xl shadow overflow-hidden mb-6">
        <div class="p-4 border-b flex justify-between items-center">
            <h3 class="font-bold text-gray-800">المستخدمين</h3>
            ${if (admin.isSuperAdmin) """<button onclick="document.getElementById('addUserModal').showModal()" class="bg-green-700 text-white px-3 py-1.5 rounded-lg text-xs font-medium hover:bg-green-800">+ إضافة مستخدم</button>""" else ""}
        </div>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead>
                    <tr class="bg-gray-50 border-b">
                        <th class="p-3 text-right font-medium text-gray-600">الاسم</th>
                        <th class="p-3 text-right font-medium text-gray-600">الهاتف</th>
                        <th class="p-3 text-right font-medium text-gray-600">الدور</th>
                        <th class="p-3 text-right font-medium text-gray-600">الحالة</th>
                        ${if (admin.isSuperAdmin) """<th class="p-3 text-right font-medium text-gray-600">إجراءات</th>""" else ""}
                    </tr>
                </thead>
                <tbody id="vdUsersBody">
                    <tr><td colspan="5" class="text-center py-6 text-gray-400">جاري التحميل...</td></tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<!-- Add User Modal -->
<dialog id="addUserModal" class="rounded-2xl shadow-2xl">
    <div class="p-6">
        <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">إضافة مستخدم</h3>
            <button onclick="this.closest('dialog').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <form onsubmit="addVendorUser(event)">
            <div class="grid grid-cols-2 gap-3">
                <div class="col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">الاسم *</label>
                    <input name="name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الهاتف *</label>
                    <input name="phone" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة المرور *</label>
                    <input name="password" type="password" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الدور *</label>
                    <select name="role" required class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="MANAGER">MANAGER</option>
                        <option value="CASHIER">CASHIER</option>
                        <option value="DELIVERY">DELIVERY</option>
                        <option value="KITCHEN">KITCHEN</option>
                    </select>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">نوع الراتب</label>
                    <select name="salary_type" class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="NONE">بدون</option>
                        <option value="FIXED">ثابت</option>
                        <option value="HOURLY">بالساعة</option>
                    </select>
                </div>
                <div class="col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">مبلغ الراتب</label>
                    <input name="salary_amount" type="number" step="0.01" value="0" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
            </div>
            <div class="flex justify-end gap-2 mt-5">
                <button type="button" onclick="this.closest('dialog').close()" class="px-4 py-2 border rounded-lg text-sm">إلغاء</button>
                <button type="submit" class="bg-green-700 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-green-800">إضافة</button>
            </div>
        </form>
    </div>
</dialog>

<script>
const VENDOR_ID = '$vendorId';
const IS_SUPER_ADMIN = ${admin.isSuperAdmin};

document.addEventListener('DOMContentLoaded', loadVendorDetail);

async function loadVendorDetail() {
    try {
        const [v, usersRes] = await Promise.all([
            fetch('/admin/api/vendors/full').then(r => r.json()).then(all => all.find(x => x.id === VENDOR_ID) || null),
            fetch('/admin/api/vendors/' + VENDOR_ID + '/users')
        ]);

        if (v) {
            document.getElementById('vdName').textContent = v.name || '-';
            document.getElementById('vdMeta').textContent = (v.businessType || '') + ' - ID: ' + VENDOR_ID.substring(0, 8) + '...';
            document.getElementById('vdPhone').textContent = v.phone || '-';
            document.getElementById('vdAddress').textContent = v.address || '-';
            document.getElementById('vdType').textContent = v.businessType || '-';
            document.getElementById('vdPlan').textContent = v.planDisplayName || v.plan || '-';
            document.getElementById('vdCreated').textContent = v.createdAt ? formatDate(v.createdAt) : '-';
            document.getElementById('vdItems').textContent = v.menuItems || 0;
            document.getElementById('vdOrders').textContent = v.monthlyOrders || 0;
            const suspended = v.isSuspended || false;
            document.getElementById('vdStatusBadge').innerHTML = suspended
                ? '<span class="badge-suspended px-3 py-1 rounded-full text-sm font-medium">موقوف</span>'
                : '<span class="badge-active px-3 py-1 rounded-full text-sm font-medium">نشط</span>';
        }

        if (usersRes && usersRes.ok) {
            const users = await usersRes.json();
            renderVendorUsers(users);
        }

        document.getElementById('detailLoading').style.display = 'none';
        document.getElementById('detailContent').style.display = '';
    } catch(e) {
        document.getElementById('detailLoading').innerHTML = '<p class="text-red-500">حدث خطأ في تحميل البيانات</p>';
    }
}

function renderVendorUsers(users) {
    const tbody = document.getElementById('vdUsersBody');
    const list = Array.isArray(users) ? users : (users.users || []);
    if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-6 text-gray-400">لا يوجد مستخدمين</td></tr>';
        return;
    }
    const roleMap = { MANAGER: 'مدير', CASHIER: 'كاشير', DELIVERY: 'توصيل', KITCHEN: 'مطبخ' };
    tbody.innerHTML = list.map(u => {
        const active = u.is_active !== false && u.isActive !== false;
        const statusBadge = active
            ? '<span class="badge-active px-2 py-1 rounded-full text-xs font-medium">نشط</span>'
            : '<span class="badge-suspended px-2 py-1 rounded-full text-xs font-medium">غير نشط</span>';
        const role = u.role || '-';
        const roleName = roleMap[role] || role;
        let actions = '';
        if (IS_SUPER_ADMIN && active) {
            actions = '<button onclick="deactivateUser(\'' + (u.id || u.user_id) + '\')" class="text-red-600 hover:underline text-xs">تعطيل</button>';
        }
        return '<tr class="border-b hover:bg-gray-50">' +
            '<td class="p-3 font-medium">' + escHtml(u.name || '-') + '</td>' +
            '<td class="p-3" dir="ltr">' + escHtml(u.phone || '-') + '</td>' +
            '<td class="p-3">' + escHtml(roleName) + '</td>' +
            '<td class="p-3">' + statusBadge + '</td>' +
            (IS_SUPER_ADMIN ? '<td class="p-3">' + actions + '</td>' : '') +
            '</tr>';
    }).join('');
}

async function addVendorUser(e) {
    e.preventDefault();
    const form = e.target;
    const data = Object.fromEntries(new FormData(form));
    data.salary_amount = parseFloat(data.salary_amount) || 0;
    const res = await fetch('/admin/api/vendors/' + VENDOR_ID + '/users', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res && res.ok) {
        form.closest('dialog').close();
        form.reset();
        showToast('تم إضافة المستخدم', 'success');
        loadVendorDetail();
    } else {
        const err = res ? await res.json().catch(() => ({})) : {};
        showToast(err.message || err.error || 'حدث خطأ', 'error');
    }
}

async function deactivateUser(userId) {
    if (!confirm('هل تريد تعطيل هذا المستخدم؟')) return;
    const res = await fetch('/admin/api/vendors/' + VENDOR_ID + '/users/' + userId, { method: 'DELETE' });
    if (res && res.ok) {
        showToast('تم تعطيل المستخدم', 'success');
        loadVendorDetail();
    } else {
        showToast('حدث خطأ', 'error');
    }
}
</script>
"""

// ════════════════════════════════════════════════════════════════
// Vendor Edit Content
// ════════════════════════════════════════════════════════════════

private fun vendorEditContent(vendorId: String): String {
    val vendor = transaction {
        VendorsTable.selectAll()
            .where { VendorsTable.id eq UUID.fromString(vendorId) }
            .firstOrNull()
    } ?: return """<p class="text-red-500 text-center py-12">العميل غير موجود</p>"""

    val v = vendor
    fun esc(s: String?): String = (s ?: "").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    fun bool(b: Boolean): String = if (b) "checked" else ""
    fun selected(current: String?, value: String): String = if (current == value) "selected" else ""

    val name = esc(v[VendorsTable.name])
    val address = esc(v[VendorsTable.address])
    val contactPhone = esc(v[VendorsTable.contactPhone])
    val walletPhone = esc(v[VendorsTable.walletPhone])
    val businessType = v[VendorsTable.businessType]
    val storeType = esc(v[VendorsTable.storeType])
    val defaultDeliveryFee = v[VendorsTable.defaultDeliveryFee].toDouble()
    val taxEnabled = v[VendorsTable.taxEnabled]
    val defaultTaxPercent = v[VendorsTable.defaultTaxPercent].toDouble()
    val stockMode = v[VendorsTable.stockMode]

    val enableDineIn = v[VendorsTable.enableDineIn]
    val enableDelivery = v[VendorsTable.enableDelivery]
    val enableTakeaway = v[VendorsTable.enableTakeaway]
    val enableInStore = v[VendorsTable.enableInStore]
    val enablePickupLater = v[VendorsTable.enablePickupLater]

    val enableTables = v[VendorsTable.enableTables]
    val enableKds = v[VendorsTable.enableKds]
    val enableRecipe = v[VendorsTable.enableRecipe]
    val enableSplitPayment = v[VendorsTable.enableSplitPayment]
    val enableCashDrawer = v[VendorsTable.enableCashDrawer]
    val enableReturns = v[VendorsTable.enableReturns]
    val enableCustomerCredit = v[VendorsTable.enableCustomerCredit]
    val enableInstallments = v[VendorsTable.enableInstallments]
    val enableManualDiscount = v[VendorsTable.enableManualDiscount]
    val enableStock = v[VendorsTable.enableStock]
    val enableCustomers = v[VendorsTable.enableCustomers]
    val enableLoyalty = v[VendorsTable.enableLoyalty]
    val enableOffers = v[VendorsTable.enableOffers]
    val enableDigitalMenu = v[VendorsTable.enableDigitalMenu]
    val enableDigitalReceipt = v[VendorsTable.enableDigitalReceipt]
    val enableWhatsappReceipt = v[VendorsTable.enableWhatsappReceipt]
    val enableAttendance = v[VendorsTable.enableAttendance]
    val enableOvertime = v[VendorsTable.enableOvertime]
    val enableSalary = v[VendorsTable.enableSalary]
    val enableWorkerQrcode = v[VendorsTable.enableWorkerQrcode]
    val enablePreOrders = v[VendorsTable.enablePreOrders]
    val enableScheduledOrders = v[VendorsTable.enableScheduledOrders]
    val enableAnnouncements = v[VendorsTable.enableAnnouncements]
    val enableSuppliers = v[VendorsTable.enableSuppliers]
    val enableExport = v[VendorsTable.enableExport]
    val enableAnalytics = v[VendorsTable.enableAnalytics]
    val enablePrescriptions = v[VendorsTable.enablePrescriptions]
    val enableDrugInteractions = v[VendorsTable.enableDrugInteractions]

    val loyaltyEnabled = v[VendorsTable.loyaltyEnabled]
    val pointsEarnRate = v[VendorsTable.pointsEarnRate].toDouble()
    val pointsRedeemRate = v[VendorsTable.pointsRedeemRate].toDouble()
    val minPointsRedeem = v[VendorsTable.minPointsRedeem]
    val maxManualDiscountPercent = v[VendorsTable.maxManualDiscountPercent].toDouble()
    val manualDiscountRequiresPin = v[VendorsTable.manualDiscountRequiresPin]

    fun toggleSwitch(fieldName: String, label: String, checked: Boolean): String = """
        <label class="relative inline-flex items-center cursor-pointer">
            <input type="checkbox" class="sr-only peer" name="$fieldName" ${if (checked) "checked" else ""}>
            <div class="w-11 h-6 bg-gray-200 dark:bg-gray-600 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-green-600"></div>
            <span class="ms-3 text-sm font-medium text-gray-700 dark:text-gray-300">$label</span>
        </label>
    """

    return """
<div class="flex justify-between items-center mb-6">
    <a href="/admin/vendors/$vendorId" class="text-blue-600 hover:underline text-sm">&larr; العودة</a>
    <h2 class="text-lg font-bold text-gray-800 dark:text-gray-100">تعديل بيانات: $name</h2>
</div>

<form id="vendorEditForm" class="space-y-6">

    <!-- Section 1: Basic Info -->
    <details open class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">المعلومات الاساسية</summary>
        <div class="p-4 grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">اسم المحل</label>
                <input type="text" name="name" value="$name" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">العنوان</label>
                <input type="text" name="address" value="$address" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">رقم الهاتف</label>
                <input type="text" name="contact_phone" value="$contactPhone" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">رقم المحفظة</label>
                <input type="text" name="wallet_phone" value="$walletPhone" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">نوع النشاط</label>
                <select name="business_type" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
                    <option value="RESTAURANT" ${selected(businessType, "RESTAURANT")}>RESTAURANT</option>
                    <option value="CAFE" ${selected(businessType, "CAFE")}>CAFE</option>
                    <option value="PHARMACY" ${selected(businessType, "PHARMACY")}>PHARMACY</option>
                    <option value="RETAIL" ${selected(businessType, "RETAIL")}>RETAIL</option>
                    <option value="SUPERMARKET" ${selected(businessType, "SUPERMARKET")}>SUPERMARKET</option>
                    <option value="BAKERY" ${selected(businessType, "BAKERY")}>BAKERY</option>
                </select>
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">نوع المتجر</label>
                <input type="text" name="store_type" value="$storeType" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">رسوم التوصيل الافتراضية</label>
                <input type="number" name="default_delivery_fee" value="$defaultDeliveryFee" step="0.01" min="0" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
        </div>
    </details>

    <!-- Section 2: Sales Channels -->
    <details open class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">قنوات البيع</summary>
        <div class="p-4 flex flex-wrap gap-6">
            ${toggleSwitch("enable_dine_in", "داخل المحل (Dine In)", enableDineIn)}
            ${toggleSwitch("enable_delivery", "توصيل (Delivery)", enableDelivery)}
            ${toggleSwitch("enable_takeaway", "تيك اواي (Takeaway)", enableTakeaway)}
            ${toggleSwitch("enable_in_store", "داخل المتجر (In Store)", enableInStore)}
            ${toggleSwitch("enable_pickup_later", "استلام لاحق (Pickup Later)", enablePickupLater)}
        </div>
    </details>

    <!-- Section 3: Features -->
    <details open class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">المميزات</summary>
        <div class="p-4 space-y-6">

            <!-- Kitchen & Tables -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">المطبخ والطاولات</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_tables", "الطاولات", enableTables)}
                    ${toggleSwitch("enable_kds", "شاشة المطبخ (KDS)", enableKds)}
                    ${toggleSwitch("enable_recipe", "الوصفات", enableRecipe)}
                </div>
            </div>

            <!-- Payments -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">المدفوعات</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_split_payment", "تقسيم الدفع", enableSplitPayment)}
                    ${toggleSwitch("enable_cash_drawer", "درج النقد", enableCashDrawer)}
                    ${toggleSwitch("enable_returns", "المرتجعات", enableReturns)}
                    ${toggleSwitch("enable_customer_credit", "رصيد العملاء", enableCustomerCredit)}
                    ${toggleSwitch("enable_installments", "التقسيط", enableInstallments)}
                    ${toggleSwitch("enable_manual_discount", "الخصم اليدوي", enableManualDiscount)}
                </div>
            </div>

            <!-- Stock -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">المخزون</h4>
                <div class="flex flex-wrap gap-6 items-center">
                    ${toggleSwitch("enable_stock", "ادارة المخزون", enableStock)}
                    <div>
                        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">وضع المخزون</label>
                        <select name="stock_mode" class="px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
                            <option value="NONE" ${selected(stockMode, "NONE")}>NONE</option>
                            <option value="WARN" ${selected(stockMode, "WARN")}>WARN</option>
                            <option value="ENFORCE" ${selected(stockMode, "ENFORCE")}>ENFORCE</option>
                        </select>
                    </div>
                </div>
            </div>

            <!-- Customers & Offers -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">العملاء والعروض</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_customers", "العملاء", enableCustomers)}
                    ${toggleSwitch("enable_loyalty", "الولاء", enableLoyalty)}
                    ${toggleSwitch("enable_offers", "العروض", enableOffers)}
                    ${toggleSwitch("enable_digital_menu", "القائمة الرقمية", enableDigitalMenu)}
                    ${toggleSwitch("enable_digital_receipt", "الايصال الرقمي", enableDigitalReceipt)}
                    ${toggleSwitch("enable_whatsapp_receipt", "مشاركة الفاتورة واتساب", enableWhatsappReceipt)}
                </div>
            </div>

            <!-- Staff -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">الموظفين</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_attendance", "الحضور والانصراف", enableAttendance)}
                    ${toggleSwitch("enable_overtime", "العمل الاضافي", enableOvertime)}
                    ${toggleSwitch("enable_salary", "الرواتب", enableSalary)}
                    ${toggleSwitch("enable_worker_qrcode", "QR الموظف", enableWorkerQrcode)}
                </div>
            </div>

            <!-- Orders -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">الطلبات</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_pre_orders", "الطلبات المسبقة", enablePreOrders)}
                    ${toggleSwitch("enable_scheduled_orders", "الطلبات المجدولة", enableScheduledOrders)}
                    ${toggleSwitch("enable_announcements", "الاعلانات", enableAnnouncements)}
                </div>
            </div>

            <!-- Suppliers & Export -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">الموردين والتصدير</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_suppliers", "الموردين", enableSuppliers)}
                    ${toggleSwitch("enable_export", "التصدير", enableExport)}
                    ${toggleSwitch("enable_analytics", "التحليلات", enableAnalytics)}
                </div>
            </div>

            <!-- Pharmacy -->
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
                <h4 class="text-sm font-bold text-gray-600 dark:text-gray-300 mb-3">الصيدلية</h4>
                <div class="flex flex-wrap gap-6">
                    ${toggleSwitch("enable_prescriptions", "الوصفات الطبية", enablePrescriptions)}
                    ${toggleSwitch("enable_drug_interactions", "التفاعلات الدوائية", enableDrugInteractions)}
                </div>
            </div>
        </div>
    </details>

    <!-- Section 4: Tax Settings -->
    <details class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">اعدادات الضريبة</summary>
        <div class="p-4 flex flex-wrap gap-6 items-center">
            ${toggleSwitch("tax_enabled", "تفعيل الضريبة", taxEnabled)}
            <div>
                <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">نسبة الضريبة الافتراضية (%)</label>
                <input type="number" name="default_tax_percent" value="$defaultTaxPercent" step="0.01" min="0" max="100" dir="ltr" class="px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm w-32 bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
        </div>
    </details>

    <!-- Section 5: Loyalty Settings -->
    <details class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">اعدادات الولاء</summary>
        <div class="p-4 space-y-4">
            <div class="flex flex-wrap gap-6 items-center">
                ${toggleSwitch("loyalty_enabled", "تفعيل نظام الولاء", loyaltyEnabled)}
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                    <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">معدل كسب النقاط</label>
                    <input type="number" name="points_earn_rate" value="$pointsEarnRate" step="0.01" min="0" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
                </div>
                <div>
                    <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">معدل استبدال النقاط</label>
                    <input type="number" name="points_redeem_rate" value="$pointsRedeemRate" step="0.01" min="0" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
                </div>
                <div>
                    <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">الحد الادنى للاستبدال</label>
                    <input type="number" name="min_points_redeem" value="$minPointsRedeem" min="0" dir="ltr" class="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-700 dark:text-gray-100">
                </div>
            </div>
        </div>
    </details>

    <!-- Section 6: Discount Settings -->
    <details class="bg-white dark:bg-gray-800 rounded-xl shadow overflow-hidden">
        <summary class="p-4 cursor-pointer font-bold text-gray-800 dark:text-gray-100 border-b dark:border-gray-700 select-none hover:bg-gray-50 dark:hover:bg-gray-700">اعدادات الخصم</summary>
        <div class="p-4 flex flex-wrap gap-6 items-center">
            <div>
                <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">اقصى نسبة خصم يدوي (%)</label>
                <input type="number" name="max_manual_discount_percent" value="$maxManualDiscountPercent" step="0.01" min="0" max="100" dir="ltr" class="px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm w-40 bg-white dark:bg-gray-700 dark:text-gray-100">
            </div>
            ${toggleSwitch("manual_discount_requires_pin", "الخصم اليدوي يتطلب PIN", manualDiscountRequiresPin)}
        </div>
    </details>

    <!-- Submit -->
    <div class="flex justify-between items-center">
        <a href="/admin/vendors/$vendorId" class="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 text-sm">الغاء</a>
        <button type="submit" id="saveBtn" class="bg-green-700 text-white px-8 py-3 rounded-xl text-sm font-bold hover:bg-green-800 transition">حفظ التعديلات</button>
    </div>
</form>

<div id="editToast" class="fixed bottom-6 left-1/2 -translate-x-1/2 px-6 py-3 rounded-xl text-white text-sm font-medium shadow-lg z-50 transition-all duration-300 opacity-0 pointer-events-none"></div>

<script>
document.getElementById('vendorEditForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const btn = document.getElementById('saveBtn');
    btn.disabled = true;
    btn.textContent = 'جاري الحفظ...';

    const form = e.target;
    const data = {};

    // Text & number inputs
    form.querySelectorAll('input[type="text"], input[type="number"]').forEach(function(el) {
        const val = el.value.trim();
        if (el.type === 'number') {
            data[el.name] = val === '' ? null : parseFloat(val);
        } else {
            data[el.name] = val;
        }
    });

    // Selects
    form.querySelectorAll('select').forEach(function(el) {
        data[el.name] = el.value;
    });

    // Toggle switches (checkboxes)
    form.querySelectorAll('input[type="checkbox"]').forEach(function(el) {
        data[el.name] = el.checked;
    });

    // Special: min_points_redeem should be integer
    if (data.min_points_redeem !== null && data.min_points_redeem !== undefined) {
        data.min_points_redeem = Math.round(data.min_points_redeem);
    }

    try {
        const res = await apiFetch('/admin/api/vendors/$vendorId', {
            method: 'PUT',
            body: JSON.stringify(data)
        });
        if (res && res.ok) {
            showEditToast('تم حفظ التعديلات بنجاح', 'success');
            setTimeout(function() { window.location.href = '/admin/vendors/$vendorId'; }, 1200);
        } else {
            const err = res ? await res.json().catch(function() { return {}; }) : {};
            showEditToast(err.error || err.message || 'حدث خطا', 'error');
            btn.disabled = false;
            btn.textContent = 'حفظ التعديلات';
        }
    } catch(ex) {
        showEditToast('حدث خطا في الاتصال', 'error');
        btn.disabled = false;
        btn.textContent = 'حفظ التعديلات';
    }
});

function showEditToast(msg, type) {
    const t = document.getElementById('editToast');
    t.textContent = msg;
    t.className = 'fixed bottom-6 left-1/2 -translate-x-1/2 px-6 py-3 rounded-xl text-white text-sm font-medium shadow-lg z-50 transition-all duration-300 opacity-100';
    t.classList.add(type === 'success' ? 'bg-green-600' : 'bg-red-600');
    setTimeout(function() { t.classList.remove('opacity-100'); t.classList.add('opacity-0'); }, 3000);
}
</script>
"""
}

// ════════════════════════════════════════════════════════════════
// Team Management Content (super_admin only)
// ════════════════════════════════════════════════════════════════

private fun teamContent(admin: AdminPrincipal): String = """
<div class="flex justify-between items-center mb-4">
    <div></div>
    <button onclick="document.getElementById('addTeamModal').showModal()" class="bg-green-700 text-white px-4 py-2 rounded-lg hover:bg-green-800 text-sm font-medium">+ إضافة عضو</button>
</div>

<div class="bg-white rounded-xl shadow overflow-hidden">
    <div class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
                <tr class="bg-gray-50 border-b">
                    <th class="p-3 text-right font-medium text-gray-600">الاسم</th>
                    <th class="p-3 text-right font-medium text-gray-600">البريد</th>
                    <th class="p-3 text-right font-medium text-gray-600">الدور</th>
                    <th class="p-3 text-right font-medium text-gray-600">الحالة</th>
                    <th class="p-3 text-right font-medium text-gray-600">إجراءات</th>
                </tr>
            </thead>
            <tbody id="teamTableBody">
                <tr><td colspan="5" class="text-center py-8 text-gray-400"><div class="spinner"></div><br>جاري التحميل...</td></tr>
            </tbody>
        </table>
    </div>
</div>

<!-- Add Team Member Modal -->
<dialog id="addTeamModal" class="rounded-2xl shadow-2xl">
    <div class="p-6">
        <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">إضافة عضو جديد</h3>
            <button onclick="this.closest('dialog').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <form onsubmit="createTeamMember(event)">
            <div class="space-y-3">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الاسم *</label>
                    <input name="name" required class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">البريد الإلكتروني *</label>
                    <input name="email" type="email" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة المرور *</label>
                    <input name="password" type="password" required class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الدور *</label>
                    <select name="role" required class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="super_admin">مدير عام (Super Admin)</option>
                        <option value="support">دعم فني (Support)</option>
                    </select>
                </div>
            </div>
            <div class="flex justify-end gap-2 mt-5">
                <button type="button" onclick="this.closest('dialog').close()" class="px-4 py-2 border rounded-lg text-sm">إلغاء</button>
                <button type="submit" class="bg-green-700 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-green-800">إضافة</button>
            </div>
        </form>
    </div>
</dialog>

<!-- Edit Team Member Modal -->
<dialog id="editTeamModal" class="rounded-2xl shadow-2xl">
    <div class="p-6">
        <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">تعديل العضو</h3>
            <button onclick="this.closest('dialog').close()" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <form onsubmit="updateTeamMember(event)">
            <input type="hidden" name="id" id="editTeamId">
            <div class="space-y-3">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الاسم</label>
                    <input name="name" id="editTeamName" class="w-full px-3 py-2 border rounded-lg text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">الدور</label>
                    <select name="role" id="editTeamRole" class="w-full px-3 py-2 border rounded-lg text-sm">
                        <option value="super_admin">مدير عام (Super Admin)</option>
                        <option value="support">دعم فني (Support)</option>
                    </select>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">كلمة مرور جديدة (اتركها فارغة للإبقاء)</label>
                    <input name="password" type="password" class="w-full px-3 py-2 border rounded-lg text-sm" dir="ltr">
                </div>
            </div>
            <div class="flex justify-end gap-2 mt-5">
                <button type="button" onclick="this.closest('dialog').close()" class="px-4 py-2 border rounded-lg text-sm">إلغاء</button>
                <button type="submit" class="bg-blue-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-blue-700">حفظ</button>
            </div>
        </form>
    </div>
</dialog>

<script>
const MY_ADMIN_ID = '${admin.adminId}';

document.addEventListener('DOMContentLoaded', loadTeam);

async function loadTeam() {
    const res = await fetch('/admin/api/team');
    if (!res) return;
    const team = await res.json();
    const list = Array.isArray(team) ? team : (team.members || team.data || []);
    const tbody = document.getElementById('teamTableBody');

    if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-8 text-gray-400">لا يوجد أعضاء</td></tr>';
        return;
    }

    const roleMap = { super_admin: 'مدير عام', support: 'دعم فني' };
    tbody.innerHTML = list.map(m => {
        const role = m.role || 'support';
        const roleName = roleMap[role] || role;
        const roleBadge = '<span class="badge-' + role + ' px-2 py-1 rounded-full text-xs font-medium">' + roleName + '</span>';
        const active = m.is_active !== false && m.isActive !== false;
        const statusBadge = active
            ? '<span class="badge-active px-2 py-1 rounded-full text-xs font-medium">نشط</span>'
            : '<span class="badge-suspended px-2 py-1 rounded-full text-xs font-medium">غير نشط</span>';
        const memberId = m.id || m.admin_id;
        const isMe = memberId === MY_ADMIN_ID;
        let actions = '<button onclick="openEditTeam(\'' + memberId + '\',\'' + escHtml(m.name || '').replace(/'/g,"\\'") + '\',\'' + role + '\')" class="text-blue-600 hover:underline text-xs">تعديل</button>';
        if (!isMe) {
            actions += ' <button onclick="deleteTeamMember(\'' + memberId + '\',\'' + escHtml(m.name || '').replace(/'/g,"\\'") + '\')" class="text-red-600 hover:underline text-xs">حذف</button>';
        }
        return '<tr class="border-b hover:bg-gray-50">' +
            '<td class="p-3 font-medium">' + escHtml(m.name || '-') + (isMe ? ' <span class="text-xs text-gray-400">(أنت)</span>' : '') + '</td>' +
            '<td class="p-3" dir="ltr">' + escHtml(m.email || '-') + '</td>' +
            '<td class="p-3">' + roleBadge + '</td>' +
            '<td class="p-3">' + statusBadge + '</td>' +
            '<td class="p-3">' + actions + '</td>' +
            '</tr>';
    }).join('');
}

async function createTeamMember(e) {
    e.preventDefault();
    const form = e.target;
    const data = Object.fromEntries(new FormData(form));
    const res = await fetch('/admin/api/team', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res && res.ok) {
        form.closest('dialog').close();
        form.reset();
        showToast('تم إضافة العضو', 'success');
        loadTeam();
    } else {
        const err = res ? await res.json().catch(() => ({})) : {};
        showToast(err.message || err.error || 'حدث خطأ', 'error');
    }
}

function openEditTeam(id, name, role) {
    document.getElementById('editTeamId').value = id;
    document.getElementById('editTeamName').value = name;
    document.getElementById('editTeamRole').value = role;
    document.getElementById('editTeamModal').showModal();
}

async function updateTeamMember(e) {
    e.preventDefault();
    const form = e.target;
    const data = Object.fromEntries(new FormData(form));
    const id = data.id; delete data.id;
    if (!data.password) delete data.password;
    const res = await fetch('/admin/api/team/' + id, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
    if (res && res.ok) {
        form.closest('dialog').close();
        showToast('تم تحديث العضو', 'success');
        loadTeam();
    } else {
        const err = res ? await res.json().catch(() => ({})) : {};
        showToast(err.message || err.error || 'حدث خطأ', 'error');
    }
}

async function deleteTeamMember(id, name) {
    if (!confirm('هل أنت متأكد من حذف "' + name + '"؟')) return;
    const res = await fetch('/admin/api/team/' + id, { method: 'DELETE' });
    if (res && res.ok) {
        showToast('تم حذف العضو', 'success');
        loadTeam();
    } else {
        showToast('حدث خطأ', 'error');
    }
}
</script>
"""

// ════════════════════════════════════════════════════════════════
// Plans Content
// ════════════════════════════════════════════════════════════════

private fun plansContent(): String = """
<div id="plansLoading" class="text-center py-12"><div class="spinner"></div><p class="text-gray-500 mt-3">جاري التحميل...</p></div>
<div id="plansContent" style="display:none">
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6" id="plansGrid"></div>

    <!-- Plans Detail Table -->
    <div class="bg-white rounded-xl shadow overflow-hidden mt-8">
        <div class="p-4 border-b">
            <h3 class="font-bold text-gray-800">مقارنة الباقات</h3>
        </div>
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead>
                    <tr class="bg-gray-50 border-b">
                        <th class="p-3 text-right font-medium text-gray-600">الميزة</th>
                        <th class="p-3 text-center font-medium text-gray-600" id="planHead0">-</th>
                        <th class="p-3 text-center font-medium text-gray-600" id="planHead1">-</th>
                        <th class="p-3 text-center font-medium text-gray-600" id="planHead2">-</th>
                    </tr>
                </thead>
                <tbody id="plansTableBody"></tbody>
            </table>
        </div>
    </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', loadPlans);

async function loadPlans() {
    const res = await apiFetch('/admin/api/plans');
    if (!res) return;
    const plans = await res.json();

    // Plan cards
    const colors = ['border-gray-400', 'border-blue-500', 'border-orange-500'];
    const bgColors = ['bg-gray-50', 'bg-blue-50', 'bg-orange-50'];
    const grid = document.getElementById('plansGrid');
    grid.innerHTML = plans.map((p, i) => {
        return '<div class="bg-white rounded-xl shadow overflow-hidden border-t-4 ' + (colors[i] || '') + '">' +
            '<div class="p-6 ' + (bgColors[i] || '') + '">' +
            '<h3 class="text-lg font-bold text-gray-800">' + escHtml(p.displayName) + '</h3>' +
            '<p class="text-3xl font-bold mt-2">' + (p.priceEgp || 0) + ' <span class="text-sm text-gray-500 font-normal">ج.م/شهر</span></p>' +
            '</div>' +
            '<div class="p-6 text-sm space-y-2">' +
            '<div class="flex justify-between"><span class="text-gray-500">المديرين</span><span class="font-medium">' + limitDisplay(p.maxManagers) + '</span></div>' +
            '<div class="flex justify-between"><span class="text-gray-500">الكاشير</span><span class="font-medium">' + limitDisplay(p.maxCashiers) + '</span></div>' +
            '<div class="flex justify-between"><span class="text-gray-500">التوصيل</span><span class="font-medium">' + limitDisplay(p.maxDelivery) + '</span></div>' +
            '<div class="flex justify-between"><span class="text-gray-500">الأصناف</span><span class="font-medium">' + limitDisplay(p.maxMenuItems) + '</span></div>' +
            '<div class="flex justify-between"><span class="text-gray-500">الطلبات/شهر</span><span class="font-medium">' + limitDisplay(p.maxOrdersPerMonth) + '</span></div>' +
            '<div class="flex justify-between"><span class="text-gray-500">الفروع</span><span class="font-medium">' + limitDisplay(p.maxBranches) + '</span></div>' +
            '<div class="border-t pt-2 mt-2"></div>' +
            featureRow('إدارة المخزون', p.stockManagement) +
            featureRow('حضور الموظفين', p.workerAttendance) +
            featureRow('التوصيل', p.deliveryModule) +
            featureRow('التحليلات', p.analytics) +
            featureRow('المنيو الرقمي', p.digitalMenu) +
            '</div></div>';
    }).join('');

    // Comparison table
    plans.forEach((p, i) => {
        const head = document.getElementById('planHead' + i);
        if (head) head.textContent = p.displayName;
    });

    const features = [
        { label: 'السعر', key: 'priceEgp', fmt: v => v + ' ج.م' },
        { label: 'المديرين', key: 'maxManagers', fmt: limitDisplay },
        { label: 'الكاشير', key: 'maxCashiers', fmt: limitDisplay },
        { label: 'التوصيل', key: 'maxDelivery', fmt: limitDisplay },
        { label: 'الأصناف', key: 'maxMenuItems', fmt: limitDisplay },
        { label: 'الطلبات/شهر', key: 'maxOrdersPerMonth', fmt: limitDisplay },
        { label: 'الفروع', key: 'maxBranches', fmt: limitDisplay },
        { label: 'إدارة المخزون', key: 'stockManagement', fmt: boolDisplay },
        { label: 'حضور الموظفين', key: 'workerAttendance', fmt: boolDisplay },
        { label: 'التوصيل', key: 'deliveryModule', fmt: boolDisplay },
        { label: 'التحليلات', key: 'analytics', fmt: boolDisplay },
        { label: 'المنيو الرقمي', key: 'digitalMenu', fmt: boolDisplay },
    ];

    const tbody = document.getElementById('plansTableBody');
    tbody.innerHTML = features.map(f => {
        let row = '<tr class="border-b hover:bg-gray-50"><td class="p-3 font-medium text-gray-700">' + f.label + '</td>';
        plans.forEach(p => {
            row += '<td class="p-3 text-center">' + f.fmt(p[f.key]) + '</td>';
        });
        // Fill remaining columns if less than 3 plans
        for (let i = plans.length; i < 3; i++) row += '<td class="p-3 text-center">-</td>';
        return row + '</tr>';
    }).join('');

    document.getElementById('plansLoading').style.display = 'none';
    document.getElementById('plansContent').style.display = '';
}

function limitDisplay(val) {
    if (val === -1 || val === null || val === undefined) return '<span class="text-green-600 font-bold">غير محدود</span>';
    return val;
}

function boolDisplay(val) {
    return val ? '<span class="text-green-600 font-bold">&#10003;</span>' : '<span class="text-gray-300">&#10007;</span>';
}

function featureRow(label, val) {
    const icon = val ? '<span class="text-green-600">&#10003;</span>' : '<span class="text-gray-300">&#10007;</span>';
    return '<div class="flex justify-between"><span class="text-gray-500">' + label + '</span>' + icon + '</div>';
}
</script>
"""

// ════════════════════════════════════════════════════════════════
// Logs Content
// ════════════════════════════════════════════════════════════════

private fun logsContent(): String = """
<!-- Stats Cards -->
<div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
    <div class="bg-white rounded-xl shadow p-4">
        <p class="text-gray-500 text-xs">إجمالي الطلبات</p>
        <p class="text-2xl font-bold mt-1" id="logStatTotal">-</p>
    </div>
    <div class="bg-white rounded-xl shadow p-4">
        <p class="text-gray-500 text-xs">نسبة الأخطاء</p>
        <p class="text-2xl font-bold mt-1" id="logStatErrorRate">-</p>
        <p class="text-xs text-gray-400 mt-1" id="logStatErrorCount"></p>
    </div>
    <div class="bg-white rounded-xl shadow p-4">
        <p class="text-gray-500 text-xs">متوسط وقت الاستجابة</p>
        <p class="text-2xl font-bold mt-1" id="logStatAvgDuration">-</p>
        <p class="text-xs text-gray-400 mt-1">مللي ثانية</p>
    </div>
    <div class="bg-white rounded-xl shadow p-4">
        <p class="text-gray-500 text-xs">توزيع الحالات</p>
        <div class="mt-1 text-sm space-y-1" id="logStatStatus"></div>
    </div>
</div>

<!-- Clear Actions -->
<div class="flex flex-wrap gap-2 mb-4">
    <button onclick="clearVendorLogs()" class="text-xs px-3 py-1.5 border border-yellow-500 text-yellow-600 rounded-lg hover:bg-yellow-50">مسح سجلات عميل</button>
    <button onclick="clearAdminLogs()" class="text-xs px-3 py-1.5 border border-orange-500 text-orange-600 rounded-lg hover:bg-orange-50">مسح سجلات النظام</button>
    <button onclick="clearAllLogs()" class="text-xs px-3 py-1.5 border border-red-500 text-red-600 rounded-lg hover:bg-red-50">مسح جميع السجلات</button>
    <label class="flex items-center gap-2 text-xs text-gray-500 mr-auto">
        <input type="checkbox" id="autoRefresh" class="accent-orange-500"> تحديث تلقائي
    </label>
</div>

<!-- Filters -->
<div class="bg-white rounded-xl shadow p-4 mb-4 flex flex-wrap gap-3 items-end">
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">العميل</label>
        <select id="logFilterVendor" class="px-3 py-2 border rounded-lg text-sm">
            <option value="">الكل</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">المستخدم</label>
        <select id="logFilterUser" class="px-3 py-2 border rounded-lg text-sm">
            <option value="">الكل</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">Method</label>
        <select id="logFilterMethod" class="px-3 py-2 border rounded-lg text-sm">
            <option value="">All</option>
            <option>GET</option><option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">Status</label>
        <select id="logFilterStatus" class="px-3 py-2 border rounded-lg text-sm">
            <option value="">All</option>
            <option value="2xx">2xx</option>
            <option value="4xx">4xx</option>
            <option value="5xx">5xx</option>
        </select>
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">Path</label>
        <input type="text" id="logFilterPath" placeholder="..." class="px-3 py-2 border rounded-lg text-sm w-32" dir="ltr">
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">من</label>
        <input type="datetime-local" id="logFilterStart" class="px-3 py-2 border rounded-lg text-sm">
    </div>
    <div>
        <label class="block text-xs font-medium text-gray-500 mb-1">إلى</label>
        <input type="datetime-local" id="logFilterEnd" class="px-3 py-2 border rounded-lg text-sm">
    </div>
    <button onclick="applyLogFilters()" class="bg-orange-500 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-orange-600">بحث</button>
    <button onclick="clearLogFilters()" class="border px-3 py-2 rounded-lg text-sm text-gray-500">مسح</button>
</div>

<!-- Tabs: Request Logs vs Log Files -->
<div class="flex gap-2 mb-4">
    <button id="tabReqLogs" class="px-4 py-2 rounded-lg text-sm font-medium bg-orange-500 text-white" onclick="switchLogTab('requests')">سجل الطلبات</button>
    <button id="tabLogFiles" class="px-4 py-2 rounded-lg text-sm font-medium border text-gray-500" onclick="switchLogTab('files')">ملفات السجلات</button>
</div>

<!-- Request Logs Table -->
<div id="requestLogsSection">
    <div class="bg-white rounded-xl shadow overflow-hidden">
        <div class="overflow-x-auto">
            <table class="w-full text-xs">
                <thead>
                    <tr class="bg-gray-50 border-b">
                        <th class="p-2 text-right font-medium text-gray-600">الوقت</th>
                        <th class="p-2 text-right font-medium text-gray-600">Method</th>
                        <th class="p-2 text-right font-medium text-gray-600">الوصف</th>
                        <th class="p-2 text-right font-medium text-gray-600">Path</th>
                        <th class="p-2 text-right font-medium text-gray-600">Status</th>
                        <th class="p-2 text-right font-medium text-gray-600">Duration</th>
                        <th class="p-2 text-right font-medium text-gray-600">المستخدم</th>
                        <th class="p-2 text-right font-medium text-gray-600">العميل</th>
                    </tr>
                </thead>
                <tbody id="logTableBody">
                    <tr><td colspan="8" class="text-center py-8 text-gray-400"><div class="spinner"></div></td></tr>
                </tbody>
            </table>
        </div>
        <div class="p-3 border-t flex justify-between items-center text-xs text-gray-500" id="logPagination"></div>
    </div>
</div>

<!-- Log Files Section -->
<div id="logFilesSection" style="display:none">
    <div class="grid grid-cols-3 gap-3 mb-4">
        <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-gray-500 text-xs">عدد الملفات</p>
            <p class="text-xl font-bold" id="lfCount">-</p>
        </div>
        <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-gray-500 text-xs">الحجم الكلي</p>
            <p class="text-xl font-bold" id="lfSize">-</p>
        </div>
        <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-gray-500 text-xs">عملاء بسجلات</p>
            <p class="text-xl font-bold" id="lfVendors">-</p>
        </div>
    </div>
    <div id="logFilesTree"><div class="text-center py-8 text-gray-400"><div class="spinner"></div></div></div>

    <!-- File Viewer -->
    <dialog id="fileViewerDialog" class="rounded-2xl shadow-2xl" style="max-width:900px;width:95%">
        <div class="p-4">
            <div class="flex justify-between items-center mb-3">
                <span id="fileViewerTitle" class="font-mono text-sm font-bold"></span>
                <div class="flex gap-2">
                    <a id="fileViewerDownload" href="#" download class="text-orange-500 text-xs border border-orange-500 px-2 py-1 rounded">تحميل</a>
                    <button onclick="this.closest('dialog').close()" class="text-gray-400 hover:text-gray-600 text-lg">&times;</button>
                </div>
            </div>
            <pre id="fileViewerContent" class="bg-gray-50 rounded-lg p-4 text-xs font-mono overflow-auto max-h-[60vh] whitespace-pre-wrap break-all"></pre>
        </div>
    </dialog>
</div>

<script>
let logCurrentPage = 1;
let logAutoRefreshInterval = null;
const logVendorCache = {};
const logUserCache = {};

document.addEventListener('DOMContentLoaded', () => {
    loadLogVendors();
    loadLogStats();
    loadLogEntries();

    document.getElementById('logFilterVendor').addEventListener('change', e => {
        loadLogUsers(e.target.value);
    });

    document.getElementById('autoRefresh').addEventListener('change', e => {
        if (e.target.checked) {
            logAutoRefreshInterval = setInterval(() => { loadLogStats(); loadLogEntries(); }, 10000);
        } else {
            clearInterval(logAutoRefreshInterval);
            logAutoRefreshInterval = null;
        }
    });
});

function switchLogTab(tab) {
    document.getElementById('requestLogsSection').style.display = tab === 'requests' ? '' : 'none';
    document.getElementById('logFilesSection').style.display = tab === 'files' ? '' : 'none';
    document.getElementById('tabReqLogs').className = tab === 'requests' ? 'px-4 py-2 rounded-lg text-sm font-medium bg-orange-500 text-white' : 'px-4 py-2 rounded-lg text-sm font-medium border text-gray-500';
    document.getElementById('tabLogFiles').className = tab === 'files' ? 'px-4 py-2 rounded-lg text-sm font-medium bg-orange-500 text-white' : 'px-4 py-2 rounded-lg text-sm font-medium border text-gray-500';
    if (tab === 'files') loadLogFiles();
}

async function loadLogVendors() {
    const res = await apiFetch('/admin/api/vendors');
    if (!res) return;
    const data = await res.json();
    const sel = document.getElementById('logFilterVendor');
    data.forEach(v => {
        logVendorCache[v.id] = v.name;
        const opt = document.createElement('option');
        opt.value = v.id; opt.textContent = v.name;
        sel.appendChild(opt);
    });
}

async function loadLogUsers(vendorId) {
    const sel = document.getElementById('logFilterUser');
    sel.innerHTML = '<option value="">الكل</option>';
    if (!vendorId) return;
    const res = await apiFetch('/admin/api/users?vendorId=' + vendorId);
    if (!res) return;
    const data = await res.json();
    data.forEach(u => {
        logUserCache[u.id] = u.name + ' (' + u.role + ')';
        const opt = document.createElement('option');
        opt.value = u.id; opt.textContent = u.name + ' (' + u.role + ')';
        sel.appendChild(opt);
    });
}

async function loadLogStats() {
    const vendorId = document.getElementById('logFilterVendor').value;
    let url = '/admin/api/logs/stats';
    if (vendorId) url += '?vendorId=' + vendorId;
    const res = await apiFetch(url);
    if (!res) return;
    const data = await res.json();

    document.getElementById('logStatTotal').textContent = (data.totalRequests || 0).toLocaleString();
    document.getElementById('logStatErrorRate').textContent = (data.errorRate || 0).toFixed(1) + '%';
    document.getElementById('logStatErrorCount').textContent = (data.errorCount || 0) + ' errors';
    document.getElementById('logStatAvgDuration').textContent = Math.round(data.avgDurationMs || 0);

    const bd = data.statusBreakdown || {};
    document.getElementById('logStatStatus').innerHTML =
        '<span class="text-green-600">2xx: ' + (bd['2xx'] || 0) + '</span><br>' +
        '<span class="text-yellow-600">4xx: ' + (bd['4xx'] || 0) + '</span><br>' +
        '<span class="text-red-600">5xx: ' + (bd['5xx'] || 0) + '</span>';
}

async function loadLogEntries(page) {
    if (page) logCurrentPage = page;
    const params = new URLSearchParams();
    params.set('page', logCurrentPage);
    params.set('pageSize', '50');

    const vendorId = document.getElementById('logFilterVendor').value;
    const userId = document.getElementById('logFilterUser').value;
    const method = document.getElementById('logFilterMethod').value;
    const status = document.getElementById('logFilterStatus').value;
    const path = document.getElementById('logFilterPath').value;
    const startDate = document.getElementById('logFilterStart').value;
    const endDate = document.getElementById('logFilterEnd').value;

    if (vendorId) params.set('vendorId', vendorId);
    if (userId) params.set('userId', userId);
    if (method) params.set('method', method);
    if (status) params.set('status', status);
    if (path) params.set('path', path);
    if (startDate) params.set('startDate', new Date(startDate).toISOString());
    if (endDate) params.set('endDate', new Date(endDate).toISOString());

    const res = await apiFetch('/admin/api/logs?' + params.toString());
    if (!res) return;
    const data = await res.json();
    renderLogTable(data.logs || []);
    renderLogPagination(data.page, data.totalPages, data.total);
}

function renderLogTable(logs) {
    const tbody = document.getElementById('logTableBody');
    if (!logs.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-gray-400">لا توجد سجلات</td></tr>';
        return;
    }
    tbody.innerHTML = logs.map((log, i) => {
        const time = new Date(log.createdAt).toLocaleString();
        const sc = log.statusCode;
        const statusColor = sc >= 500 ? 'text-red-600 bg-red-50' : sc >= 400 ? 'text-yellow-600 bg-yellow-50' : 'text-green-600 bg-green-50';
        const methodColors = { GET: 'text-green-700 bg-green-50', POST: 'text-blue-700 bg-blue-50', PUT: 'text-yellow-700 bg-yellow-50', PATCH: 'text-purple-700 bg-purple-50', DELETE: 'text-red-700 bg-red-50' };
        const mColor = methodColors[log.method] || 'text-gray-700 bg-gray-50';
        const durClass = log.durationMs > 2000 ? 'text-red-600' : log.durationMs > 500 ? 'text-yellow-600' : 'text-gray-600';
        const vendorName = logVendorCache[log.vendorId] || (log.vendorId ? log.vendorId.substring(0,8) + '...' : '-');
        const userName = logUserCache[log.userId] || (log.userId ? log.userId.substring(0,8) + '...' : '-');
        const desc = log.description || (log.resource ? log.resource + ' / ' + log.action : '-');

        let traceCount = 0;
        try { if (log.traceLog) { traceCount = JSON.parse(log.traceLog).length; } } catch(e) {}
        const traceBadge = traceCount > 0 ? '<span class="bg-orange-500 text-white px-1 rounded text-[9px] font-bold ml-1">' + traceCount + '</span>' : '';

        return '<tr class="border-b hover:bg-gray-50 cursor-pointer" onclick="toggleLogDetail(' + i + ')">' +
            '<td class="p-2 text-gray-400 whitespace-nowrap">' + time + '</td>' +
            '<td class="p-2"><span class="px-1.5 py-0.5 rounded font-bold text-[10px] ' + mColor + '">' + log.method + '</span></td>' +
            '<td class="p-2 max-w-[200px] truncate" title="' + escHtml(desc) + '">' + traceBadge + escHtml(desc) + '</td>' +
            '<td class="p-2 font-mono max-w-[180px] truncate" dir="ltr" title="' + escHtml(log.path) + '">' + escHtml(log.path) + '</td>' +
            '<td class="p-2"><span class="px-1.5 py-0.5 rounded font-bold text-[10px] ' + statusColor + '">' + sc + '</span></td>' +
            '<td class="p-2 font-mono ' + durClass + '">' + log.durationMs + 'ms</td>' +
            '<td class="p-2">' + (log.userRole ? '<span class="bg-gray-200 text-gray-600 px-1 rounded text-[9px] uppercase">' + log.userRole + '</span> ' : '') + escHtml(userName) + '</td>' +
            '<td class="p-2">' + escHtml(vendorName) + '</td>' +
            '</tr>' +
            '<tr id="logDetail-' + i + '" style="display:none" class="bg-gray-50"><td colspan="8" class="p-0">' +
            '<div class="p-4 border-r-4 border-orange-500 text-xs">' +
            renderLogDetail(log) +
            '</div></td></tr>';
    }).join('');
    window._logData = logs;
}

function toggleLogDetail(idx) {
    const el = document.getElementById('logDetail-' + idx);
    el.style.display = el.style.display === 'none' ? '' : 'none';
}

function renderLogDetail(log) {
    let html = '';
    const desc = log.description || (log.resource ? log.resource + ' / ' + log.action : '-');
    html += '<div class="mb-3"><span class="font-bold text-gray-600">الوصف:</span> <span class="text-gray-800">' + escHtml(desc) + '</span></div>';

    // Trace log
    if (log.traceLog) {
        try {
            const steps = JSON.parse(log.traceLog);
            if (steps && steps.length) {
                const totalMs = steps[steps.length - 1].elapsed_ms || 0;
                html += '<div class="mb-3"><span class="font-bold text-gray-600">Trace (' + steps.length + ' steps, ' + totalMs + 'ms):</span>';
                html += '<div class="mt-2 space-y-1">';
                steps.forEach((s, idx) => {
                    const color = idx === 0 ? 'text-green-600' : idx === steps.length - 1 ? 'text-orange-600' : 'text-gray-700';
                    const icon = idx === 0 ? '▶' : idx === steps.length - 1 ? '✔' : '→';
                    html += '<div class="flex gap-2 items-start"><span class="font-bold ' + color + '">' + icon + ' [' + s.step + ']</span> <span>' + escHtml(s.message) + '</span> <span class="text-gray-400">(+' + s.elapsed_ms + 'ms)</span></div>';
                    if (s.data && Object.keys(s.data).length > 0) {
                        html += '<div class="mr-6 text-gray-500">';
                        Object.keys(s.data).forEach(k => {
                            html += '<span class="text-orange-600">' + escHtml(k) + '</span>=<span>' + escHtml(s.data[k] || 'null') + '</span> &nbsp;';
                        });
                        html += '</div>';
                    }
                });
                html += '</div></div>';
            }
        } catch(e) {
            html += '<div class="mb-3"><span class="font-bold text-gray-600">Trace:</span> <pre class="bg-white p-2 rounded mt-1 overflow-auto">' + escHtml(log.traceLog) + '</pre></div>';
        }
    }

    if (log.requestBody) html += '<div class="mb-3"><span class="font-bold text-gray-600">Request Body:</span><pre class="bg-white p-2 rounded mt-1 overflow-auto max-h-40">' + formatJson(log.requestBody) + '</pre></div>';
    if (log.responseBody) html += '<div class="mb-3"><span class="font-bold text-gray-600">Response Body:</span><pre class="bg-white p-2 rounded mt-1 overflow-auto max-h-40">' + formatJson(log.responseBody) + '</pre></div>';
    if (log.errorMessage) html += '<div class="mb-3"><span class="font-bold text-red-600">Error:</span><pre class="bg-red-50 text-red-700 p-2 rounded mt-1">' + escHtml(log.errorMessage) + '</pre></div>';

    html += '<div class="text-gray-400 space-x-4">';
    html += '<span>IP: ' + escHtml(log.clientIp || '-') + '</span>';
    html += '<span>UA: ' + escHtml((log.userAgent || '-').substring(0, 60)) + '</span>';
    html += '<span>Query: ' + escHtml(log.queryParams || '-') + '</span>';
    html += '</div>';

    return html;
}

function formatJson(s) {
    if (!s) return '';
    try { return escHtml(JSON.stringify(JSON.parse(s), null, 2)); } catch(e) { return escHtml(s); }
}

function renderLogPagination(page, totalPages, total) {
    const el = document.getElementById('logPagination');
    if (totalPages <= 1) { el.innerHTML = '<span>' + total + ' total</span><span></span>'; return; }
    let html = '<span>' + total + ' total - صفحة ' + page + '/' + totalPages + '</span><div class="flex gap-1">';
    html += '<button onclick="loadLogEntries(' + (page - 1) + ')" class="px-2 py-1 border rounded text-xs" ' + (page <= 1 ? 'disabled style="opacity:0.3"' : '') + '>السابق</button>';
    const start = Math.max(1, page - 2);
    const end = Math.min(totalPages, page + 2);
    for (let i = start; i <= end; i++) {
        html += '<button onclick="loadLogEntries(' + i + ')" class="px-2 py-1 border rounded text-xs ' + (i === page ? 'bg-orange-500 text-white border-orange-500' : '') + '">' + i + '</button>';
    }
    html += '<button onclick="loadLogEntries(' + (page + 1) + ')" class="px-2 py-1 border rounded text-xs" ' + (page >= totalPages ? 'disabled style="opacity:0.3"' : '') + '>التالي</button>';
    html += '</div>';
    el.innerHTML = html;
}

function applyLogFilters() { logCurrentPage = 1; loadLogStats(); loadLogEntries(); }
function clearLogFilters() {
    document.getElementById('logFilterVendor').value = '';
    document.getElementById('logFilterUser').innerHTML = '<option value="">الكل</option>';
    document.getElementById('logFilterMethod').value = '';
    document.getElementById('logFilterStatus').value = '';
    document.getElementById('logFilterPath').value = '';
    document.getElementById('logFilterStart').value = '';
    document.getElementById('logFilterEnd').value = '';
    applyLogFilters();
}

// ─── Clear Log Functions ───────────────────────────────
async function clearAllLogs() {
    if (!confirm('هل أنت متأكد من حذف جميع السجلات؟ لا يمكن التراجع.')) return;
    if (!confirm('تأكيد نهائي: سيتم حذف جميع السجلات. متابعة؟')) return;
    const res = await apiFetch('/admin/api/logs/clear-all', { method: 'DELETE' });
    if (!res) return;
    const data = await res.json();
    if (data.success) { showToast('تم مسح ' + data.deleted + ' سجل', 'success'); loadLogStats(); loadLogEntries(); }
    else { showToast('حدث خطأ', 'error'); }
}

async function clearAdminLogs() {
    if (!confirm('هل تريد مسح سجلات النظام (بدون عميل)؟')) return;
    const res = await apiFetch('/admin/api/logs/clear-admin', { method: 'DELETE' });
    if (!res) return;
    const data = await res.json();
    if (data.success) { showToast('تم مسح ' + data.deleted + ' سجل', 'success'); loadLogStats(); loadLogEntries(); }
    else { showToast('حدث خطأ', 'error'); }
}

async function clearVendorLogs() {
    const vendorId = document.getElementById('logFilterVendor').value;
    if (!vendorId) { alert('اختر عميل من الفلتر أولا'); return; }
    const name = logVendorCache[vendorId] || vendorId;
    if (!confirm('هل تريد مسح سجلات "' + name + '"؟')) return;
    const res = await apiFetch('/admin/api/logs/clear-vendor/' + vendorId, { method: 'DELETE' });
    if (!res) return;
    const data = await res.json();
    if (data.success) { showToast('تم مسح ' + data.deleted + ' سجل', 'success'); loadLogStats(); loadLogEntries(); }
    else { showToast('حدث خطأ', 'error'); }
}

// ─── Log Files ─────────────────────────────────────────
async function loadLogFiles() {
    const res = await apiFetch('/admin/api/logs/files');
    if (!res) return;
    const data = await res.json();

    document.getElementById('lfCount').textContent = data.length;
    const totalSize = data.reduce((s, f) => s + (f.size || 0), 0);
    document.getElementById('lfSize').textContent = formatSize(totalSize);

    const vendorMap = {};
    data.forEach(f => {
        if (!vendorMap[f.vendorFolder]) vendorMap[f.vendorFolder] = [];
        vendorMap[f.vendorFolder].push(f);
    });
    document.getElementById('lfVendors').textContent = Object.keys(vendorMap).length;

    const tree = document.getElementById('logFilesTree');
    if (!data.length) {
        tree.innerHTML = '<div class="text-center py-8 text-gray-400">لا توجد ملفات سجلات</div>';
        return;
    }

    let html = '';
    Object.keys(vendorMap).sort().forEach(vf => {
        const files = vendorMap[vf].sort((a, b) => (b.lastModified || 0) - (a.lastModified || 0));
        const vendorSize = files.reduce((s, f) => s + (f.size || 0), 0);
        let displayName = vf;
        const uuidMatch = vf.match(/-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i);
        if (uuidMatch) displayName = vf.substring(0, vf.length - uuidMatch[0].length);

        html += '<div class="bg-white rounded-xl shadow overflow-hidden mb-3">';
        html += '<div class="flex justify-between items-center p-3 border-b cursor-pointer hover:bg-gray-50" onclick="toggleVF(\'' + escHtml(vf).replace(/'/g,"\\'") + '\')">';
        html += '<div class="flex items-center gap-2">';
        html += '<span id="vfArrow-' + escHtml(vf) + '" class="text-gray-400 transition-transform text-xs">▶</span>';
        html += '<span class="font-medium">' + escHtml(displayName) + '</span>';
        html += '<span class="bg-orange-100 text-orange-700 px-2 py-0.5 rounded-full text-xs font-medium">' + files.length + '</span>';
        html += '<span class="text-gray-400 text-xs">' + formatSize(vendorSize) + '</span>';
        html += '</div>';
        html += '<button class="text-red-500 text-xs hover:underline" onclick="event.stopPropagation();clearVendorLogFiles(\'' + escHtml(vf).replace(/'/g,"\\'") + '\')">حذف الكل</button>';
        html += '</div>';

        html += '<div id="vfFiles-' + escHtml(vf) + '" style="display:none">';
        files.forEach(f => {
            const lastMod = new Date(f.lastModified).toLocaleString('ar-EG');
            html += '<div class="flex justify-between items-center p-3 border-b text-sm">';
            html += '<div class="flex items-center gap-2 min-w-0 flex-1">';
            html += '<span class="font-mono text-xs truncate">' + escHtml(f.fileName) + '</span>';
            html += '<span class="text-gray-400 text-xs">' + escHtml(f.sizeFormatted) + '</span>';
            html += '<span class="text-gray-400 text-xs">' + lastMod + '</span>';
            html += '</div>';
            html += '<div class="flex gap-1 flex-shrink-0">';
            html += '<button class="text-blue-500 text-xs hover:underline" onclick="viewLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">عرض</button>';
            html += '<button class="text-green-500 text-xs hover:underline" onclick="downloadLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">تحميل</button>';
            html += '<button class="text-red-500 text-xs hover:underline" onclick="deleteLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">حذف</button>';
            html += '</div></div>';
        });
        html += '</div></div>';
    });
    tree.innerHTML = html;
}

function toggleVF(vf) {
    const el = document.getElementById('vfFiles-' + vf);
    const arrow = document.getElementById('vfArrow-' + vf);
    if (!el) return;
    if (el.style.display === 'none') {
        el.style.display = '';
        if (arrow) arrow.style.transform = 'rotate(90deg)';
    } else {
        el.style.display = 'none';
        if (arrow) arrow.style.transform = '';
    }
}

async function viewLogFile(filePath, fileName) {
    document.getElementById('fileViewerTitle').textContent = fileName;
    document.getElementById('fileViewerDownload').href = '/admin/api/logs/files/view?path=' + encodeURIComponent(filePath) + '&download=true';
    document.getElementById('fileViewerDownload').download = fileName;
    document.getElementById('fileViewerContent').textContent = 'جاري التحميل...';
    document.getElementById('fileViewerDialog').showModal();
    try {
        const res = await fetch('/admin/api/logs/files/view?path=' + encodeURIComponent(filePath));
        if (!res.ok) { document.getElementById('fileViewerContent').textContent = 'Error: ' + res.statusText; return; }
        let text = await res.text();
        const lines = text.split('\n');
        if (lines.length > 5000) text = '... (showing last 5000 of ' + lines.length + ' lines) ...\n\n' + lines.slice(-5000).join('\n');
        document.getElementById('fileViewerContent').textContent = text;
    } catch(e) {
        document.getElementById('fileViewerContent').textContent = 'Error: ' + e.message;
    }
}

function downloadLogFile(filePath, fileName) {
    const a = document.createElement('a');
    a.href = '/admin/api/logs/files/view?path=' + encodeURIComponent(filePath) + '&download=true';
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

async function deleteLogFile(filePath, fileName) {
    if (!confirm('حذف "' + fileName + '"؟')) return;
    const res = await apiFetch('/admin/api/logs/files/delete', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: filePath })
    });
    if (!res) return;
    const data = await res.json();
    if (data.success) { showToast('تم حذف الملف', 'success'); loadLogFiles(); }
    else { showToast(data.error || 'حدث خطأ', 'error'); }
}

async function clearVendorLogFiles(vendorFolder) {
    if (!confirm('حذف جميع ملفات "' + vendorFolder + '"؟')) return;
    const res = await apiFetch('/admin/api/logs/files/clear-vendor/' + encodeURIComponent(vendorFolder), { method: 'DELETE' });
    if (!res) return;
    const data = await res.json();
    if (data.success) { showToast('تم مسح ' + data.deleted + ' ملف', 'success'); loadLogFiles(); }
    else { showToast(data.error || 'حدث خطأ', 'error'); }
}

function formatSize(bytes) {
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
    if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return bytes + ' B';
}
</script>
"""
