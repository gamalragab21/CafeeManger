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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
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
        get("/admin/dashboard") {
            val trace = call.routeTrace()
            trace.step("Serve admin dashboard started")
            val admin = call.principal<AdminPrincipal>()!!
            trace.step("Admin authenticated", mapOf("adminEmail" to admin.email))
            call.respondText(dashboardPageHtml(admin.email), ContentType.Text.Html)
            trace.step("Serve admin dashboard completed")
        }

        // ─── API Endpoints ───────────────────────────────────────
        get("/admin/api/logs") {
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

        get("/admin/api/logs/stats") {
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

        get("/admin/api/vendors") {
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

        get("/admin/api/users") {
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

        // ─── Vendor & Plan Management APIs ─────────────────────
        get("/admin/api/plans") {
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

        get("/admin/api/vendors/full") {
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
                        // Plan info
                        put("planName", planRow?.get(SubscriptionPlansTable.name) ?: "NONE")
                        put("planDisplayName", planRow?.get(SubscriptionPlansTable.displayName) ?: "No Plan")
                        put("subscriptionStatus", subscription?.get(VendorSubscriptionsTable.status) ?: "NONE")
                        // Usage
                        put("managers", usage["managers"].toString().toInt())
                        put("cashiers", usage["cashiers"].toString().toInt())
                        put("delivery", usage["delivery"].toString().toInt())
                        put("menuItems", usage["menuItems"].toString().toInt())
                        put("monthlyOrders", usage["monthlyOrders"].toString().toInt())
                        // Limits
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

        put("/admin/api/vendors/{id}/plan") {
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
        delete("/admin/api/logs/clear-all") {
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

        // ─── Log Management: Clear Vendor Request Logs ──────────────
        delete("/admin/api/logs/clear-vendor/{vendorId}") {
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

        // ─── Log Management: Clear Admin/System Logs ────────────────
        delete("/admin/api/logs/clear-admin") {
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

        // ─── Log Files: List All ────────────────────────────────────
        get("/admin/api/logs/files") {
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

            // Also check old-style vendor log dirs at top level
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

        // ─── Log Files: Delete Single File ──────────────────────────
        delete("/admin/api/logs/files/delete") {
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

        // ─── Log Files: Clear All for Vendor Folder ─────────────────
        delete("/admin/api/logs/files/clear-vendor/{vendorFolder}") {
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

        // ─── Log Files: View/Download File Content ──────────────────
        get("/admin/api/logs/files/view") {
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
    }
}

private fun dashFormatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
        bytes >= 1_024 -> "%.1f KB".format(bytes.toDouble() / 1_024)
        else -> "$bytes B"
    }
}

// ─── HTML Templates ──────────────────────────────────────────────

private fun loginPageHtml(error: String? = null): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Waselak Admin</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #0f172a;
            color: #e2e8f0;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .login-card {
            background: #1e293b;
            border-radius: 16px;
            padding: 48px;
            width: 100%;
            max-width: 420px;
            box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5);
        }
        .logo {
            text-align: center;
            margin-bottom: 32px;
        }
        .logo h1 {
            font-size: 28px;
            font-weight: 700;
            background: linear-gradient(135deg, #f97316, #fb923c);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .logo p { color: #94a3b8; margin-top: 8px; font-size: 14px; }
        .error {
            background: #991b1b33;
            border: 1px solid #dc2626;
            color: #fca5a5;
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
        }
        .field { margin-bottom: 20px; }
        .field label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: #94a3b8;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .field input {
            width: 100%;
            padding: 12px 16px;
            background: #0f172a;
            border: 1px solid #334155;
            border-radius: 8px;
            color: #e2e8f0;
            font-size: 15px;
            transition: border-color 0.2s;
        }
        .field input:focus {
            outline: none;
            border-color: #f97316;
        }
        .btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #f97316, #ea580c);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: opacity 0.2s;
        }
        .btn:hover { opacity: 0.9; }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="logo">
            <h1>Waselak Admin</h1>
            <p>Sign in to access the dashboard</p>
        </div>
        ${if (error != null) """<div class="error">$error</div>""" else ""}
        <form method="POST" action="/admin/login">
            <div class="field">
                <label>Email</label>
                <input type="email" name="email" required autocomplete="email" autofocus />
            </div>
            <div class="field">
                <label>Password</label>
                <input type="password" name="password" required autocomplete="current-password" />
            </div>
            <button type="submit" class="btn">Sign In</button>
        </form>
    </div>
</body>
</html>
""".trimIndent()

private fun dashboardPageHtml(adminEmail: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Waselak Admin Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        :root {
            --bg: #0f172a;
            --surface: #1e293b;
            --surface2: #334155;
            --border: #475569;
            --text: #e2e8f0;
            --text2: #94a3b8;
            --accent: #f97316;
            --green: #22c55e;
            --yellow: #eab308;
            --red: #ef4444;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            min-height: 100vh;
        }

        /* ─── Top Nav ─────────────────── */
        .topnav {
            background: var(--surface);
            border-bottom: 1px solid var(--surface2);
            padding: 0 24px;
            height: 56px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 100;
        }
        .topnav .brand {
            font-size: 18px;
            font-weight: 700;
            background: linear-gradient(135deg, #f97316, #fb923c);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .topnav .right { display: flex; align-items: center; gap: 16px; }
        .topnav .admin-email { color: var(--text2); font-size: 13px; }
        .topnav .auto-refresh-toggle {
            display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text2);
        }
        .topnav .auto-refresh-toggle input { accent-color: var(--accent); }
        .btn-logout {
            background: transparent;
            border: 1px solid var(--surface2);
            color: var(--text2);
            padding: 6px 14px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 13px;
        }
        .btn-logout:hover { border-color: var(--red); color: var(--red); }

        /* ─── Content ─────────────────── */
        .container { max-width: 1440px; margin: 0 auto; padding: 24px; }

        /* ─── Stats Cards ─────────────── */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }
        .stat-card {
            background: var(--surface);
            border-radius: 12px;
            padding: 20px;
            border: 1px solid var(--surface2);
        }
        .stat-card .label { font-size: 12px; color: var(--text2); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
        .stat-card .value { font-size: 28px; font-weight: 700; }
        .stat-card .sub { font-size: 12px; color: var(--text2); margin-top: 4px; }

        /* ─── Filters ─────────────────── */
        .filters {
            background: var(--surface);
            border-radius: 12px;
            padding: 16px 20px;
            margin-bottom: 20px;
            border: 1px solid var(--surface2);
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            align-items: end;
        }
        .filter-group { display: flex; flex-direction: column; gap: 4px; }
        .filter-group label { font-size: 11px; color: var(--text2); text-transform: uppercase; letter-spacing: 0.5px; }
        .filter-group select, .filter-group input {
            background: var(--bg);
            border: 1px solid var(--surface2);
            border-radius: 6px;
            color: var(--text);
            padding: 8px 12px;
            font-size: 13px;
            min-width: 140px;
        }
        .filter-group select:focus, .filter-group input:focus { outline: none; border-color: var(--accent); }
        .btn-filter {
            background: var(--accent);
            border: none;
            color: white;
            padding: 8px 20px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 13px;
            font-weight: 600;
            height: 35px;
        }
        .btn-clear {
            background: transparent;
            border: 1px solid var(--surface2);
            color: var(--text2);
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 13px;
            height: 35px;
        }

        /* ─── Table ───────────────────── */
        .table-wrapper {
            background: var(--surface);
            border-radius: 12px;
            border: 1px solid var(--surface2);
            overflow: hidden;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        th {
            background: var(--surface2);
            padding: 12px 16px;
            text-align: left;
            font-weight: 600;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text2);
            white-space: nowrap;
        }
        td {
            padding: 10px 16px;
            border-bottom: 1px solid #1e293b;
            vertical-align: top;
        }
        tr:hover td { background: rgba(255,255,255,0.02); }
        tr.expanded td { background: rgba(249,115,22,0.05); }

        .method {
            font-weight: 700;
            font-size: 11px;
            padding: 2px 8px;
            border-radius: 4px;
            display: inline-block;
        }
        .method-GET { background: #22c55e22; color: var(--green); }
        .method-POST { background: #3b82f622; color: #3b82f6; }
        .method-PUT { background: #eab30822; color: var(--yellow); }
        .method-PATCH { background: #a855f722; color: #a855f7; }
        .method-DELETE { background: #ef444422; color: var(--red); }

        .status {
            font-weight: 700;
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 4px;
            display: inline-block;
        }
        .status-2xx { background: #22c55e22; color: var(--green); }
        .status-4xx { background: #eab30822; color: var(--yellow); }
        .status-5xx { background: #ef444422; color: var(--red); }

        .duration { color: var(--text2); font-variant-numeric: tabular-nums; }
        .duration.slow { color: var(--yellow); }
        .duration.very-slow { color: var(--red); }

        .path-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: 'SF Mono', Monaco, monospace; font-size: 12px; }
        .time-cell { font-size: 12px; color: var(--text2); white-space: nowrap; }
        .role-badge { font-size: 10px; padding: 2px 6px; border-radius: 3px; background: var(--surface2); color: var(--text2); text-transform: uppercase; }

        /* ─── Detail Row ──────────────── */
        .detail-row td { padding: 0; }
        .detail-content {
            padding: 16px 24px;
            background: #0f172a;
            border-left: 3px solid var(--accent);
        }
        .detail-section { margin-bottom: 12px; }
        .detail-section h4 { font-size: 11px; color: var(--accent); text-transform: uppercase; margin-bottom: 6px; }
        .detail-section pre {
            background: var(--surface);
            padding: 12px;
            border-radius: 6px;
            font-size: 12px;
            font-family: 'SF Mono', Monaco, monospace;
            overflow-x: auto;
            max-height: 300px;
            white-space: pre-wrap;
            word-break: break-all;
        }

        /* ─── Trace Log Timeline ─────── */
        .trace-timeline { position: relative; padding-left: 28px; }
        .trace-timeline::before {
            content: '';
            position: absolute;
            left: 10px;
            top: 8px;
            bottom: 8px;
            width: 2px;
            background: var(--surface2);
        }
        .trace-step {
            position: relative;
            margin-bottom: 8px;
            padding: 8px 12px;
            background: var(--surface);
            border-radius: 8px;
            border: 1px solid var(--surface2);
            transition: border-color 0.2s;
        }
        .trace-step:hover { border-color: var(--accent); }
        .trace-step:first-child { border-left: 3px solid var(--green); }
        .trace-step:last-child { border-left: 3px solid var(--accent); }
        .trace-step::before {
            content: '';
            position: absolute;
            left: -23px;
            top: 14px;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--accent);
            border: 2px solid var(--bg);
        }
        .trace-step:first-child::before { background: var(--green); }
        .trace-step:last-child::before { background: var(--accent); }
        .trace-step-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }
        .trace-step-num {
            font-size: 10px;
            font-weight: 700;
            background: var(--accent);
            color: white;
            width: 20px;
            height: 20px;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }
        .trace-step-msg {
            flex: 1;
            font-size: 13px;
            font-weight: 600;
            color: var(--text);
        }
        .trace-step-time {
            font-size: 11px;
            color: var(--text2);
            font-family: 'SF Mono', Monaco, monospace;
            white-space: nowrap;
        }
        .trace-step-data {
            margin-top: 6px;
            padding: 6px 10px;
            background: var(--bg);
            border-radius: 6px;
            font-size: 11px;
            font-family: 'SF Mono', Monaco, monospace;
            display: flex;
            flex-wrap: wrap;
            gap: 6px 16px;
        }
        .trace-data-item { color: var(--text2); }
        .trace-data-item .key { color: var(--accent); }
        .trace-data-item .val { color: var(--text); }
        .trace-summary {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 10px;
            font-size: 12px;
            color: var(--text2);
        }
        .trace-summary .badge {
            background: var(--accent);
            color: white;
            padding: 2px 10px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: 700;
        }

        /* ─── Pagination ──────────────── */
        .pagination {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 20px;
            border-top: 1px solid var(--surface2);
            font-size: 13px;
            color: var(--text2);
        }
        .pagination .pages { display: flex; gap: 4px; }
        .pagination button {
            background: var(--bg);
            border: 1px solid var(--surface2);
            color: var(--text2);
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        }
        .pagination button.active { background: var(--accent); color: white; border-color: var(--accent); }
        .pagination button:disabled { opacity: 0.3; cursor: not-allowed; }

        /* ─── Tabs ────────────────────── */
        .tab-btn {
            background: transparent;
            border: 1px solid var(--surface2);
            color: var(--text2);
            padding: 6px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 13px;
            font-weight: 600;
            transition: all 0.2s;
        }
        .tab-btn:hover { border-color: var(--accent); color: var(--accent); }
        .tab-btn.active { background: var(--accent); color: white; border-color: var(--accent); }

        /* ─── Plan Badges ────────────── */
        .plan-badge {
            font-size: 11px;
            font-weight: 700;
            padding: 3px 10px;
            border-radius: 12px;
            text-transform: uppercase;
            display: inline-block;
        }
        .plan-STARTER { background: #64748b22; color: #94a3b8; }
        .plan-BUSINESS { background: #3b82f622; color: #60a5fa; }
        .plan-ENTERPRISE { background: #f9731622; color: var(--accent); }

        /* ─── Usage Bars ─────────────── */
        .usage-bar {
            height: 6px;
            background: var(--surface2);
            border-radius: 3px;
            overflow: hidden;
            margin-top: 2px;
        }
        .usage-bar .fill {
            height: 100%;
            border-radius: 3px;
            transition: width 0.3s;
        }
        .usage-text { font-size: 11px; color: var(--text2); }

        .plan-select {
            background: var(--bg);
            border: 1px solid var(--surface2);
            border-radius: 4px;
            color: var(--text);
            padding: 4px 8px;
            font-size: 12px;
        }

        /* ─── Loading ─────────────────── */
        .loading { text-align: center; padding: 40px; color: var(--text2); }
        .spinner {
            display: inline-block;
            width: 24px; height: 24px;
            border: 3px solid var(--surface2);
            border-top-color: var(--accent);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* ─── Responsive ──────────────── */
        @media (max-width: 768px) {
            .filters { flex-direction: column; }
            .filter-group select, .filter-group input { min-width: 100%; }
            .container { padding: 12px; }
            .stats-grid { grid-template-columns: repeat(2, 1fr); }
        }
    </style>
</head>
<body>
    <nav class="topnav">
        <span class="brand">Waselak Admin</span>
        <div class="right">
            <div style="display:flex;gap:8px;margin-right:12px;">
                <button class="tab-btn active" onclick="switchTab('logs',this)">Logs</button>
                <button class="tab-btn" onclick="switchTab('vendors',this)">Vendors</button>
                <button class="tab-btn" onclick="switchTab('logfiles',this)">Log Files</button>
            </div>
            <label class="auto-refresh-toggle">
                <input type="checkbox" id="autoRefresh" /> Auto-refresh
            </label>
            <span class="admin-email">${adminEmail}</span>
            <form method="POST" action="/admin/logout" style="display:inline">
                <button type="submit" class="btn-logout">Logout</button>
            </form>
        </div>
    </nav>

    <div class="container">
        <!-- ═══ Vendors Tab ═══ -->
        <div id="vendorsTab" style="display:none">
            <div class="stats-grid" id="vendorStats">
                <div class="stat-card"><div class="label">Total Vendors</div><div class="value" id="vendorCount">-</div></div>
                <div class="stat-card"><div class="label">Starter Plans</div><div class="value" id="starterCount">-</div></div>
                <div class="stat-card"><div class="label">Business Plans</div><div class="value" id="businessCount">-</div></div>
                <div class="stat-card"><div class="label">Enterprise Plans</div><div class="value" id="enterpriseCount">-</div></div>
            </div>
            <div class="table-wrapper">
                <table>
                    <thead><tr>
                        <th>Vendor</th><th>Plan</th><th>Users</th><th>Items</th><th>Orders/Mo</th><th>Status</th><th>Actions</th>
                    </tr></thead>
                    <tbody id="vendorTableBody">
                        <tr><td colspan="7" class="loading"><div class="spinner"></div><br>Loading...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- ═══ Log Files Tab ═══ -->
        <div id="logfilesTab" style="display:none">
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="label">Total Log Files</div>
                    <div class="value" id="logFilesCount">-</div>
                </div>
                <div class="stat-card">
                    <div class="label">Total Size</div>
                    <div class="value" id="logFilesSize">-</div>
                </div>
                <div class="stat-card">
                    <div class="label">Vendors with Logs</div>
                    <div class="value" id="logFilesVendors">-</div>
                </div>
            </div>
            <!-- Vendor file tree -->
            <div id="logFilesTree" style="margin-top:16px">
                <div style="text-align:center;padding:40px;color:var(--text2)"><div class="spinner"></div><br>Loading...</div>
            </div>
            <!-- File viewer modal -->
            <div id="fileViewerOverlay" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.7);z-index:1000;padding:20px;overflow:auto" onclick="if(event.target===this)closeFileViewer()">
                <div style="max-width:1100px;margin:0 auto;background:var(--card);border-radius:12px;border:1px solid var(--border);overflow:hidden">
                    <div style="display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid var(--border);background:var(--bg2)">
                        <span id="fileViewerTitle" style="font-weight:600;font-family:'SF Mono',Monaco,monospace;font-size:13px"></span>
                        <div style="display:flex;gap:8px;align-items:center">
                            <a id="fileViewerDownload" href="#" download style="color:var(--accent);font-size:12px;text-decoration:none;padding:4px 10px;border:1px solid var(--accent);border-radius:6px">⬇ Download</a>
                            <button onclick="closeFileViewer()" style="background:none;border:1px solid var(--border);color:var(--text2);padding:4px 10px;border-radius:6px;cursor:pointer;font-size:12px">✕ Close</button>
                        </div>
                    </div>
                    <pre id="fileViewerContent" style="padding:16px;margin:0;font-family:'SF Mono',Monaco,monospace;font-size:12px;line-height:1.6;max-height:70vh;overflow:auto;white-space:pre-wrap;word-break:break-all;color:var(--text1)"></pre>
                </div>
            </div>
        </div>

        <!-- ═══ Logs Tab ═══ -->
        <div id="logsTab">
        <!-- Clear Logs Actions -->
        <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap">
            <button class="btn-clear" onclick="clearVendorLogs()" style="border-color:var(--yellow);color:var(--yellow)">🗑 Clear Vendor Logs</button>
            <button class="btn-clear" onclick="clearAdminLogs()" style="border-color:var(--accent);color:var(--accent)">🗑 Clear Admin Logs</button>
            <button class="btn-clear" onclick="clearAllLogs()" style="border-color:var(--red);color:var(--red)">⚠ Clear ALL Logs</button>
        </div>
        <!-- Stats Cards -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="label">Total Requests</div>
                <div class="value" id="statTotal">-</div>
                <div class="sub" id="statBreakdown"></div>
            </div>
            <div class="stat-card">
                <div class="label">Error Rate</div>
                <div class="value" id="statErrorRate">-</div>
                <div class="sub" id="statErrorCount"></div>
            </div>
            <div class="stat-card">
                <div class="label">Avg Response Time</div>
                <div class="value" id="statAvgDuration">-</div>
                <div class="sub">milliseconds</div>
            </div>
            <div class="stat-card">
                <div class="label">Status Breakdown</div>
                <div class="value" id="statStatus" style="font-size:14px;line-height:1.8">-</div>
            </div>
        </div>

        <!-- Filters -->
        <div class="filters">
            <div class="filter-group">
                <label>Vendor</label>
                <select id="filterVendor"><option value="">All Vendors</option></select>
            </div>
            <div class="filter-group">
                <label>User</label>
                <select id="filterUser"><option value="">All Users</option></select>
            </div>
            <div class="filter-group">
                <label>Method</label>
                <select id="filterMethod">
                    <option value="">All</option>
                    <option>GET</option><option>POST</option>
                    <option>PUT</option><option>PATCH</option><option>DELETE</option>
                </select>
            </div>
            <div class="filter-group">
                <label>Status</label>
                <select id="filterStatus">
                    <option value="">All</option>
                    <option value="2xx">2xx Success</option>
                    <option value="4xx">4xx Client Error</option>
                    <option value="5xx">5xx Server Error</option>
                </select>
            </div>
            <div class="filter-group">
                <label>Path</label>
                <input type="text" id="filterPath" placeholder="Search path..." />
            </div>
            <div class="filter-group">
                <label>Start Date</label>
                <input type="datetime-local" id="filterStartDate" />
            </div>
            <div class="filter-group">
                <label>End Date</label>
                <input type="datetime-local" id="filterEndDate" />
            </div>
            <button class="btn-filter" onclick="applyFilters()">Filter</button>
            <button class="btn-clear" onclick="clearFilters()">Clear</button>
        </div>

        <!-- Log Table -->
        <div class="table-wrapper">
            <table>
                <thead>
                    <tr>
                        <th>Time</th>
                        <th>Method</th>
                        <th>Description</th>
                        <th>Path</th>
                        <th>Status</th>
                        <th>Duration</th>
                        <th>User</th>
                        <th>Vendor</th>
                    </tr>
                </thead>
                <tbody id="logTableBody">
                    <tr><td colspan="8" class="loading"><div class="spinner"></div><br>Loading...</td></tr>
                </tbody>
            </table>
            <div class="pagination" id="pagination"></div>
        </div>
        </div><!-- /logsTab -->
    </div>

<script>
let currentPage = 1;
let autoRefreshInterval = null;
const vendorCache = {};
const userCache = {};

// ─── Init ──────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    loadVendors();
    loadStats();
    loadLogs();

    document.getElementById('filterVendor').addEventListener('change', (e) => {
        loadUsers(e.target.value);
    });

    document.getElementById('autoRefresh').addEventListener('change', (e) => {
        if (e.target.checked) {
            autoRefreshInterval = setInterval(() => { loadStats(); loadLogs(); }, 10000);
        } else {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
        }
    });
});

// ─── API Calls ──────────────────────────────────────────
async function api(path) {
    const res = await fetch(path);
    if (res.status === 401) { window.location.href = '/admin/login'; return null; }
    return res.json();
}

async function loadVendors() {
    const data = await api('/admin/api/vendors');
    if (!data) return;
    const sel = document.getElementById('filterVendor');
    data.forEach(v => {
        vendorCache[v.id] = v.name;
        const opt = document.createElement('option');
        opt.value = v.id; opt.textContent = v.name;
        sel.appendChild(opt);
    });
}

async function loadUsers(vendorId) {
    const sel = document.getElementById('filterUser');
    sel.innerHTML = '<option value="">All Users</option>';
    if (!vendorId) return;

    const data = await api('/admin/api/users?vendorId=' + vendorId);
    if (!data) return;
    data.forEach(u => {
        userCache[u.id] = u.name + ' (' + u.role + ')';
        const opt = document.createElement('option');
        opt.value = u.id; opt.textContent = u.name + ' (' + u.role + ')';
        sel.appendChild(opt);
    });
}

async function loadStats() {
    const vendorId = document.getElementById('filterVendor').value;
    let url = '/admin/api/logs/stats';
    if (vendorId) url += '?vendorId=' + vendorId;

    const data = await api(url);
    if (!data) return;

    document.getElementById('statTotal').textContent = data.totalRequests.toLocaleString();
    document.getElementById('statErrorRate').textContent = data.errorRate.toFixed(1) + '%';
    document.getElementById('statErrorCount').textContent = data.errorCount + ' errors';
    document.getElementById('statAvgDuration').textContent = Math.round(data.avgDurationMs);

    const bd = data.statusBreakdown || {};
    document.getElementById('statStatus').innerHTML =
        '<span style="color:var(--green)">2xx: ' + (bd['2xx']||0) + '</span> &nbsp; ' +
        '<span style="color:var(--yellow)">4xx: ' + (bd['4xx']||0) + '</span> &nbsp; ' +
        '<span style="color:var(--red)">5xx: ' + (bd['5xx']||0) + '</span>';
}

async function loadLogs(page) {
    if (page) currentPage = page;
    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('pageSize', '50');

    const vendorId = document.getElementById('filterVendor').value;
    const userId = document.getElementById('filterUser').value;
    const method = document.getElementById('filterMethod').value;
    const status = document.getElementById('filterStatus').value;
    const path = document.getElementById('filterPath').value;
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    if (vendorId) params.set('vendorId', vendorId);
    if (userId) params.set('userId', userId);
    if (method) params.set('method', method);
    if (status) params.set('status', status);
    if (path) params.set('path', path);
    if (startDate) params.set('startDate', new Date(startDate).toISOString());
    if (endDate) params.set('endDate', new Date(endDate).toISOString());

    const data = await api('/admin/api/logs?' + params.toString());
    if (!data) return;

    renderTable(data.logs);
    renderPagination(data.page, data.totalPages, data.total);
}

// ─── Rendering ──────────────────────────────────────────
function renderTable(logs) {
    const tbody = document.getElementById('logTableBody');
    if (!logs.length) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:40px;color:var(--text2)">No logs found</td></tr>';
        return;
    }

    tbody.innerHTML = logs.map((log, i) => {
        const time = new Date(log.createdAt).toLocaleString();
        const sc = log.statusCode;
        const statusClass = sc >= 500 ? 'status-5xx' : sc >= 400 ? 'status-4xx' : 'status-2xx';
        const durClass = log.durationMs > 2000 ? 'very-slow' : log.durationMs > 500 ? 'slow' : '';
        const vendorName = vendorCache[log.vendorId] || (log.vendorId ? log.vendorId.substring(0,8)+'...' : '-');
        const userName = userCache[log.userId] || (log.userId ? log.userId.substring(0,8)+'...' : '-');

        var descText = log.description || (log.resource ? log.resource + ' / ' + log.action : '-');
        var descClass = sc >= 500 ? 'color:var(--red)' : sc >= 400 ? 'color:var(--yellow)' : 'color:var(--green)';
        var traceCount = 0;
        try { if (log.traceLog) { traceCount = JSON.parse(log.traceLog).length; } } catch(e) {}

        return '<tr onclick="toggleDetail(this,' + i + ')" style="cursor:pointer" data-idx="' + i + '">' +
            '<td class="time-cell">' + time + '</td>' +
            '<td><span class="method method-' + log.method + '">' + log.method + '</span></td>' +
            '<td class="desc-cell" style="' + descClass + ';max-width:320px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis" title="' + escHtml(descText) + '">' + (traceCount > 0 ? '<span style="font-size:9px;background:var(--accent);color:white;padding:1px 5px;border-radius:8px;margin-right:5px;font-weight:700">' + traceCount + '</span>' : '') + escHtml(descText) + '</td>' +
            '<td class="path-cell" title="' + escHtml(log.path) + (log.queryParams ? '?' + escHtml(log.queryParams) : '') + '">' + escHtml(log.path) + '</td>' +
            '<td><span class="status ' + statusClass + '">' + sc + '</span></td>' +
            '<td class="duration ' + durClass + '">' + log.durationMs + 'ms</td>' +
            '<td>' + (log.userRole ? '<span class="role-badge">' + log.userRole + '</span> ' : '') + escHtml(userName) + '</td>' +
            '<td>' + escHtml(vendorName) + '</td>' +
            '</tr>' +
            '<tr class="detail-row" id="detail-' + i + '" style="display:none"><td colspan="8"><div class="detail-content">' +
                '<div class="detail-section"><h4>📋 What Happened</h4><pre style="' + descClass + '">' + escHtml(descText) + '</pre></div>' +
                renderTraceLog(log.traceLog) +
                (log.tags ? '<div class="detail-section"><h4>🏷️ Tags</h4><pre>' + formatJson(log.tags) + '</pre></div>' : '') +
                (log.requestBody ? '<div class="detail-section"><h4>📤 Request Body</h4><pre>' + formatJson(log.requestBody) + '</pre></div>' : '') +
                (log.responseBody ? '<div class="detail-section"><h4>📥 Response Body</h4><pre>' + formatJson(log.responseBody) + '</pre></div>' : '') +
                (log.errorMessage ? '<div class="detail-section"><h4>❌ Error</h4><pre style="color:var(--red)">' + escHtml(log.errorMessage) + '</pre></div>' : '') +
                '<div class="detail-section"><h4>🔍 Details</h4><pre>' +
                    'Resource: ' + escHtml(log.resource || '-') + '\n' +
                    'Action: ' + escHtml(log.action || '-') + '\n' +
                    'IP: ' + escHtml(log.clientIp || '-') + '\n' +
                    'User Agent: ' + escHtml(log.userAgent || '-') + '\n' +
                    'Query: ' + escHtml(log.queryParams || '-') + '\n' +
                    'User ID: ' + escHtml(log.userId || '-') + '\n' +
                    'Vendor ID: ' + escHtml(log.vendorId || '-') +
                '</pre></div>' +
            '</div></td></tr>';
    }).join('');

    // Store logs for detail toggling
    window._logs = logs;
}

function toggleDetail(tr, idx) {
    const detailRow = document.getElementById('detail-' + idx);
    if (detailRow.style.display === 'none') {
        detailRow.style.display = '';
        tr.classList.add('expanded');
    } else {
        detailRow.style.display = 'none';
        tr.classList.remove('expanded');
    }
}

function renderPagination(page, totalPages, total) {
    const el = document.getElementById('pagination');
    if (totalPages <= 1) { el.innerHTML = '<span>' + total + ' total</span><span></span>'; return; }

    let html = '<span>' + total + ' total &middot; Page ' + page + '/' + totalPages + '</span><div class="pages">';
    html += '<button onclick="loadLogs(' + (page-1) + ')"' + (page <= 1 ? ' disabled' : '') + '>&laquo; Prev</button>';

    const start = Math.max(1, page - 2);
    const end = Math.min(totalPages, page + 2);
    for (let i = start; i <= end; i++) {
        html += '<button onclick="loadLogs(' + i + ')" class="' + (i===page?'active':'') + '">' + i + '</button>';
    }

    html += '<button onclick="loadLogs(' + (page+1) + ')"' + (page >= totalPages ? ' disabled' : '') + '>Next &raquo;</button>';
    html += '</div>';
    el.innerHTML = html;
}

// ─── Helpers ────────────────────────────────────────────
function applyFilters() { currentPage = 1; loadStats(); loadLogs(); }
function clearFilters() {
    document.getElementById('filterVendor').value = '';
    document.getElementById('filterUser').innerHTML = '<option value="">All Users</option>';
    document.getElementById('filterMethod').value = '';
    document.getElementById('filterStatus').value = '';
    document.getElementById('filterPath').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    applyFilters();
}

function escHtml(s) {
    if (!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatJson(s) {
    if (!s) return '';
    try {
        return escHtml(JSON.stringify(JSON.parse(s), null, 2));
    } catch(e) {
        return escHtml(s);
    }
}

function renderTraceLog(traceLog) {
    if (!traceLog) return '';
    try {
        var steps = JSON.parse(traceLog);
        if (!steps || !steps.length) return '';

        var totalTime = steps.length > 0 ? steps[steps.length - 1].elapsed_ms : 0;
        var html = '<div class="detail-section"><h4>📍 Request Trace (' + steps.length + ' steps, ' + totalTime + 'ms total)</h4>';
        html += '<pre style="background:var(--surface);padding:16px;border-radius:8px;font-size:12px;font-family:\'SF Mono\',Monaco,monospace;line-height:1.8;overflow-x:auto;max-height:500px;white-space:pre-wrap;word-break:break-all">';
        html += '<span style="color:var(--green);font-weight:700">════════════════════ START ════════════════════</span>\n';
        html += '<span style="color:var(--text2)">Steps: ' + steps.length + '  |  Total Time: ' + totalTime + 'ms</span>\n';
        html += '<span style="color:var(--surface2)">──────────────────────────────────────────────</span>\n';

        steps.forEach(function(step, idx) {
            var isFirst = idx === 0;
            var isLast = idx === steps.length - 1;
            var stepColor = isFirst ? 'var(--green)' : isLast ? 'var(--accent)' : 'var(--text)';
            var icon = isFirst ? '▶' : isLast ? '✔' : '→';

            html += '<span style="color:' + stepColor + ';font-weight:700">' + icon + ' [Step ' + step.step + ']</span> ';
            html += '<span style="color:' + stepColor + '">' + escHtml(step.message) + '</span>';
            html += '  <span style="color:var(--text2);font-size:11px">(+' + step.elapsed_ms + 'ms)</span>\n';

            if (step.data && Object.keys(step.data).length > 0) {
                Object.keys(step.data).forEach(function(key) {
                    html += '    <span style="color:var(--accent)">' + escHtml(key) + '</span>';
                    html += '<span style="color:var(--text2)"> = </span>';
                    html += '<span style="color:var(--text)">' + escHtml(step.data[key] || 'null') + '</span>\n';
                });
            }

            if (!isLast) {
                html += '<span style="color:var(--surface2)">  │</span>\n';
            }
        });

        html += '<span style="color:var(--surface2)">──────────────────────────────────────────────</span>\n';
        html += '<span style="color:var(--accent);font-weight:700">═════════════════════ END ═════════════════════</span>';
        html += '</pre></div>';
        return html;
    } catch(e) {
        return '<div class="detail-section"><h4>📍 Request Trace</h4><pre>' + escHtml(traceLog) + '</pre></div>';
    }
}

// ─── Tab Switching ───────────────────────────────────────
function switchTab(tab, btn) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('logsTab').style.display = tab === 'logs' ? '' : 'none';
    document.getElementById('vendorsTab').style.display = tab === 'vendors' ? '' : 'none';
    document.getElementById('logfilesTab').style.display = tab === 'logfiles' ? '' : 'none';
    if (tab === 'vendors') loadVendorsFull();
    if (tab === 'logfiles') loadLogFiles();
}

// ─── Clear Logs Functions ────────────────────────────────
async function clearAllLogs() {
    if (!confirm('⚠ Are you sure you want to delete ALL request logs? This cannot be undone.')) return;
    if (!confirm('⚠ FINAL WARNING: This will permanently delete ALL logs. Continue?')) return;
    const res = await fetch('/admin/api/logs/clear-all', { method: 'DELETE' });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        alert('✅ Cleared ' + data.deleted + ' logs');
        loadStats(); loadLogs();
    } else {
        alert('❌ Error: ' + (data.error || 'Unknown error'));
    }
}

async function clearAdminLogs() {
    if (!confirm('Are you sure you want to delete all admin/system logs (logs with no vendor)?')) return;
    const res = await fetch('/admin/api/logs/clear-admin', { method: 'DELETE' });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        alert('✅ Cleared ' + data.deleted + ' admin logs');
        loadStats(); loadLogs();
    } else {
        alert('❌ Error: ' + (data.error || 'Unknown error'));
    }
}

async function clearVendorLogs() {
    var vendorId = document.getElementById('filterVendor').value;
    if (!vendorId) {
        alert('Please select a vendor from the filter dropdown first.');
        return;
    }
    var vendorName = vendorCache[vendorId] || vendorId;
    if (!confirm('Are you sure you want to delete all request logs for vendor "' + vendorName + '"?')) return;
    const res = await fetch('/admin/api/logs/clear-vendor/' + vendorId, { method: 'DELETE' });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        alert('✅ Cleared ' + data.deleted + ' logs for ' + vendorName);
        loadStats(); loadLogs();
    } else {
        alert('❌ Error: ' + (data.error || 'Unknown error'));
    }
}

// ─── Log Files Tab ───────────────────────────────────────
let logFilesData = [];

async function loadLogFiles() {
    const data = await api('/admin/api/logs/files');
    if (!data) return;
    logFilesData = data;

    document.getElementById('logFilesCount').textContent = data.length;
    var totalSize = data.reduce(function(sum, f) { return sum + (f.size || 0); }, 0);
    document.getElementById('logFilesSize').textContent = formatSize(totalSize);

    // Group by vendor
    var vendorMap = {};
    data.forEach(function(f) {
        if (!vendorMap[f.vendorFolder]) vendorMap[f.vendorFolder] = [];
        vendorMap[f.vendorFolder].push(f);
    });
    document.getElementById('logFilesVendors').textContent = Object.keys(vendorMap).length;

    var tree = document.getElementById('logFilesTree');
    if (!data.length) {
        tree.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text2)">No uploaded log files found</div>';
        return;
    }

    var html = '';
    var vendorFolders = Object.keys(vendorMap).sort();
    vendorFolders.forEach(function(vf) {
        var files = vendorMap[vf];
        var vendorSize = files.reduce(function(s, f) { return s + (f.size || 0); }, 0);
        // Extract vendor name from folder (format: vendorname-uuid)
        var displayName = vf;
        var uuidMatch = vf.match(/-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i);
        if (uuidMatch) displayName = vf.substring(0, vf.length - uuidMatch[0].length);

        html += '<div style="background:var(--card);border:1px solid var(--border);border-radius:10px;margin-bottom:12px;overflow:hidden">';
        // Vendor header
        html += '<div style="display:flex;justify-content:space-between;align-items:center;padding:12px 16px;background:var(--bg2);border-bottom:1px solid var(--border);cursor:pointer" onclick="toggleVendorFiles(\'' + escHtml(vf).replace(/'/g,"\\'") + '\')">';
        html += '<div style="display:flex;align-items:center;gap:10px">';
        html += '<span id="arrow-' + escHtml(vf) + '" style="transition:transform 0.2s;display:inline-block">▶</span>';
        html += '<span style="font-weight:600;font-size:14px">🏪 ' + escHtml(displayName) + '</span>';
        html += '<span style="background:var(--accent);color:#000;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:600">' + files.length + ' file' + (files.length > 1 ? 's' : '') + '</span>';
        html += '<span style="color:var(--text2);font-size:12px">' + formatSize(vendorSize) + '</span>';
        html += '</div>';
        html += '<button class="btn-clear" style="padding:4px 10px;font-size:11px;border-color:var(--red);color:var(--red)" onclick="event.stopPropagation();clearVendorLogFiles(\'' + escHtml(vf).replace(/'/g,"\\'") + '\')">🗑 Delete All</button>';
        html += '</div>';

        // Files list (collapsed by default)
        html += '<div id="files-' + escHtml(vf) + '" style="display:none">';
        files.sort(function(a, b) { return (b.lastModified || 0) - (a.lastModified || 0); });
        files.forEach(function(f) {
            var lastMod = new Date(f.lastModified).toLocaleString();
            // Determine log source from filename (e.g., manager-logs, cashier-logs, delivery-logs)
            var sourceIcon = '📄';
            var sourceName = '';
            if (f.fileName.startsWith('manager')) { sourceIcon = '👔'; sourceName = 'Manager'; }
            else if (f.fileName.startsWith('cashier')) { sourceIcon = '💳'; sourceName = 'Cashier'; }
            else if (f.fileName.startsWith('delivery')) { sourceIcon = '🚚'; sourceName = 'Delivery'; }

            html += '<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 16px;border-bottom:1px solid var(--border)">';
            html += '<div style="display:flex;align-items:center;gap:10px;flex:1;min-width:0">';
            html += '<span style="font-size:16px">' + sourceIcon + '</span>';
            html += '<div style="min-width:0">';
            html += '<div style="font-family:\'SF Mono\',Monaco,monospace;font-size:12px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + escHtml(f.fileName) + '</div>';
            html += '<div style="font-size:11px;color:var(--text2);display:flex;gap:8px;margin-top:2px">';
            if (sourceName) html += '<span style="background:var(--bg2);padding:1px 6px;border-radius:4px">' + sourceName + '</span>';
            html += '<span>' + escHtml(f.sizeFormatted) + '</span>';
            html += '<span>' + lastMod + '</span>';
            html += '</div></div></div>';
            // Actions
            html += '<div style="display:flex;gap:6px;flex-shrink:0">';
            html += '<button class="btn-clear" style="padding:4px 10px;font-size:11px;border-color:var(--accent);color:var(--accent)" onclick="viewLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">👁 View</button>';
            html += '<button class="btn-clear" style="padding:4px 10px;font-size:11px;border-color:var(--green);color:var(--green)" onclick="downloadLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">⬇ Download</button>';
            html += '<button class="btn-clear" style="padding:4px 10px;font-size:11px;border-color:var(--red);color:var(--red)" onclick="deleteLogFile(\'' + escHtml(f.path).replace(/'/g,"\\'") + '\',\'' + escHtml(f.fileName).replace(/'/g,"\\'") + '\')">🗑</button>';
            html += '</div></div>';
        });
        html += '</div></div>';
    });

    tree.innerHTML = html;
}

function toggleVendorFiles(vendorFolder) {
    var el = document.getElementById('files-' + vendorFolder);
    var arrow = document.getElementById('arrow-' + vendorFolder);
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
    document.getElementById('fileViewerContent').textContent = 'Loading...';
    document.getElementById('fileViewerOverlay').style.display = '';
    try {
        const res = await fetch('/admin/api/logs/files/view?path=' + encodeURIComponent(filePath));
        if (res.status === 401) { window.location.href = '/admin/login'; return; }
        if (!res.ok) {
            document.getElementById('fileViewerContent').textContent = 'Error: ' + res.statusText;
            return;
        }
        var text = await res.text();
        // Limit display to last 5000 lines for performance
        var lines = text.split('\n');
        if (lines.length > 5000) {
            text = '... (showing last 5000 of ' + lines.length + ' lines) ...\n\n' + lines.slice(-5000).join('\n');
        }
        document.getElementById('fileViewerContent').textContent = text;
    } catch(e) {
        document.getElementById('fileViewerContent').textContent = 'Error loading file: ' + e.message;
    }
}

function closeFileViewer() {
    document.getElementById('fileViewerOverlay').style.display = 'none';
}

function downloadLogFile(filePath, fileName) {
    var a = document.createElement('a');
    a.href = '/admin/api/logs/files/view?path=' + encodeURIComponent(filePath) + '&download=true';
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

async function deleteLogFile(filePath, fileName) {
    if (!confirm('Delete log file "' + fileName + '"? This cannot be undone.')) return;
    const res = await fetch('/admin/api/logs/files/delete', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: filePath })
    });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        alert('✅ File deleted');
        loadLogFiles();
    } else {
        alert('❌ Error: ' + (data.error || data.message || 'Unknown error'));
    }
}

async function clearVendorLogFiles(vendorFolder) {
    if (!confirm('Delete ALL log files for "' + vendorFolder + '"? This cannot be undone.')) return;
    const res = await fetch('/admin/api/logs/files/clear-vendor/' + encodeURIComponent(vendorFolder), { method: 'DELETE' });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        alert('✅ Cleared ' + data.deleted + ' files');
        loadLogFiles();
    } else {
        alert('❌ Error: ' + (data.error || 'Unknown error'));
    }
}

function formatSize(bytes) {
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
    if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return bytes + ' B';
}

// ─── Vendors Tab ─────────────────────────────────────────
let plansCache = [];

async function loadVendorsFull() {
    if (!plansCache.length) {
        plansCache = await api('/admin/api/plans') || [];
    }
    const data = await api('/admin/api/vendors/full');
    if (!data) return;

    // Stats
    document.getElementById('vendorCount').textContent = data.length;
    document.getElementById('starterCount').textContent = data.filter(v => v.planName === 'STARTER').length;
    document.getElementById('businessCount').textContent = data.filter(v => v.planName === 'BUSINESS').length;
    document.getElementById('enterpriseCount').textContent = data.filter(v => v.planName === 'ENTERPRISE').length;

    // Table
    const tbody = document.getElementById('vendorTableBody');
    if (!data.length) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text2)">No vendors</td></tr>';
        return;
    }

    tbody.innerHTML = data.map(v => {
        const planClass = 'plan-' + (v.planName || 'STARTER');
        const usageBars = buildUsageBars(v);
        const planOptions = ['STARTER','BUSINESS','ENTERPRISE'].map(p =>
            '<option value="' + p + '"' + (p === v.planName ? ' selected' : '') + '>' + p + '</option>'
        ).join('');

        return '<tr>' +
            '<td><strong>' + escHtml(v.name) + '</strong><br><span style="font-size:11px;color:var(--text2)">' + escHtml(v.businessType) + ' &bull; ' + escHtml(v.phone) + '</span></td>' +
            '<td><span class="plan-badge ' + planClass + '">' + escHtml(v.planDisplayName) + '</span></td>' +
            '<td>' + usageBars + '</td>' +
            '<td>' + usageCell(v.menuItems, v.maxMenuItems, 'items') + '</td>' +
            '<td>' + usageCell(v.monthlyOrders, v.maxOrdersPerMonth, 'orders') + '</td>' +
            '<td>' + (v.isSuspended ? '<span style="color:var(--red)">Suspended</span>' : '<span style="color:var(--green)">Active</span>') + '</td>' +
            '<td><select class="plan-select" onchange="changePlan(\'' + v.id + '\', this.value)">' + planOptions + '</select></td>' +
            '</tr>';
    }).join('');
}

function buildUsageBars(v) {
    let html = '';
    html += miniBar('M', v.managers, v.maxManagers);
    html += miniBar('C', v.cashiers, v.maxCashiers);
    html += miniBar('D', v.delivery, v.maxDelivery);
    return html;
}

function miniBar(label, current, max) {
    if (max === -1) return '<span class="usage-text">' + label + ': ' + current + '/&infin;</span><br>';
    const pct = max > 0 ? Math.min(100, Math.round(current / max * 100)) : 0;
    const color = pct >= 90 ? 'var(--red)' : pct >= 70 ? 'var(--yellow)' : 'var(--green)';
    return '<span class="usage-text">' + label + ': ' + current + '/' + max + '</span>' +
        '<div class="usage-bar"><div class="fill" style="width:' + pct + '%;background:' + color + '"></div></div>';
}

function usageCell(current, max, label) {
    if (max === undefined || max === null) return '<span class="usage-text">' + current + '</span>';
    if (max === -1) return '<span class="usage-text">' + current + '/&infin;</span>';
    const pct = max > 0 ? Math.min(100, Math.round(current / max * 100)) : 0;
    const color = pct >= 90 ? 'var(--red)' : pct >= 70 ? 'var(--yellow)' : 'var(--green)';
    return '<span class="usage-text">' + current + '/' + max + '</span>' +
        '<div class="usage-bar"><div class="fill" style="width:' + pct + '%;background:' + color + '"></div></div>';
}

async function changePlan(vendorId, plan) {
    const res = await fetch('/admin/api/vendors/' + vendorId + '/plan', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ plan: plan })
    });
    if (res.status === 401) { window.location.href = '/admin/login'; return; }
    const data = await res.json();
    if (data.success) {
        loadVendorsFull();
    } else {
        alert('Error: ' + (data.message || data.error || 'Unknown error'));
    }
}
</script>
</body>
</html>
""".trimIndent()
