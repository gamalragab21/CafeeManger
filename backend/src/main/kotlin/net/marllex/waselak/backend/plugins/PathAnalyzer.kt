package net.marllex.waselak.backend.plugins

import kotlinx.serialization.json.*

/**
 * Analyzes API request paths, query parameters, and request bodies
 * to extract business-level resource names, actions, and meaningful tags.
 */
object PathAnalyzer {

    data class AnalysisResult(
        val resource: String,
        val action: String,
        val tags: Map<String, String>
    )

    // Path patterns: regex -> (resource, action-override or null)
    // Order matters: more specific patterns first
    private data class PathPattern(
        val regex: Regex,
        val resource: String,
        val actionOverride: String? = null,
    )

    private val patterns = listOf(
        // Auth
        PathPattern(Regex("^/api/v1/auth/login$"), "auth", "login"),
        PathPattern(Regex("^/api/v1/auth/register$"), "auth", "register"),
        PathPattern(Regex("^/api/v1/auth/refresh$"), "auth", "refresh-token"),
        PathPattern(Regex("^/api/v1/auth/me$"), "auth", "profile"),

        // Orders
        PathPattern(Regex("^/api/v1/orders/[^/]+/status$"), "orders", "update-status"),
        PathPattern(Regex("^/api/v1/orders/[^/]+/payment$"), "orders", "update-payment"),
        PathPattern(Regex("^/api/v1/orders/[^/]+/assign$"), "orders", "assign-delivery"),
        PathPattern(Regex("^/api/v1/orders/[^/]+/refund$"), "orders", "refund"),
        PathPattern(Regex("^/api/v1/orders/[^/]+$"), "orders"),
        PathPattern(Regex("^/api/v1/orders$"), "orders"),

        // Menu / Categories / Items
        PathPattern(Regex("^/api/v1/categories/[^/]+/items$"), "items", "list-by-category"),
        PathPattern(Regex("^/api/v1/categories/[^/]+$"), "categories"),
        PathPattern(Regex("^/api/v1/categories$"), "categories"),
        PathPattern(Regex("^/api/v1/items/search$"), "items", "search"),
        PathPattern(Regex("^/api/v1/items/[^/]+$"), "items"),
        PathPattern(Regex("^/api/v1/items$"), "items"),
        PathPattern(Regex("^/api/v1/menu$"), "menu", "full-menu"),

        // Stock
        PathPattern(Regex("^/api/v1/stock/[^/]+/transactions$"), "stock", "transactions"),
        PathPattern(Regex("^/api/v1/stock/[^/]+$"), "stock"),
        PathPattern(Regex("^/api/v1/stock$"), "stock"),

        // Workers
        PathPattern(Regex("^/api/v1/workers/[^/]+/qr$"), "workers", "generate-qr"),
        PathPattern(Regex("^/api/v1/workers/[^/]+/pin$"), "workers", "set-pin"),
        PathPattern(Regex("^/api/v1/workers/[^/]+$"), "workers"),
        PathPattern(Regex("^/api/v1/workers$"), "workers"),
        PathPattern(Regex("^/api/v1/worker-roles"), "worker-roles"),

        // Attendance
        PathPattern(Regex("^/api/v1/attendance/check-in$"), "attendance", "check-in"),
        PathPattern(Regex("^/api/v1/attendance/check-out$"), "attendance", "check-out"),
        PathPattern(Regex("^/api/v1/attendance"), "attendance"),

        // Overtime
        PathPattern(Regex("^/api/v1/overtime"), "overtime"),

        // Customers
        PathPattern(Regex("^/api/v1/customers/[^/]+/addresses"), "customer-addresses"),
        PathPattern(Regex("^/api/v1/customers"), "customers"),

        // Tables
        PathPattern(Regex("^/api/v1/tables"), "tables"),

        // Tax places
        PathPattern(Regex("^/api/v1/tax-places"), "tax-places"),

        // Vendors
        PathPattern(Regex("^/api/v1/vendors/me/plan$"), "vendor-plan", "get-plan"),
        PathPattern(Regex("^/api/v1/vendors/me$"), "vendor-profile"),
        PathPattern(Regex("^/api/v1/vendors"), "vendors"),

        // Users management
        PathPattern(Regex("^/api/v1/users"), "users"),

        // Analytics
        PathPattern(Regex("^/api/v1/analytics/dashboard$"), "analytics", "dashboard"),
        PathPattern(Regex("^/api/v1/analytics"), "analytics"),

        // Export
        PathPattern(Regex("^/api/v1/export"), "export"),

        // Uploads
        PathPattern(Regex("^/api/v1/upload"), "upload"),

        // Digital menu (public)
        PathPattern(Regex("^/menu/"), "digital-menu", "public-view"),
    )

    /**
     * Analyze a request and extract resource, action, and tags.
     */
    fun analyze(
        method: String,
        path: String,
        queryParams: String?,
        requestBody: String?
    ): AnalysisResult {
        val matched = patterns.firstOrNull { it.regex.containsMatchIn(path) }
        val resource = matched?.resource ?: extractFallbackResource(path)
        val action = matched?.actionOverride ?: inferAction(method, path)
        val tags = mutableMapOf<String, String>()

        // Extract ID from path if present
        val idMatch = Regex("/api/v1/[^/]+/([0-9a-fA-F-]{36})").find(path)
        idMatch?.groupValues?.getOrNull(1)?.let { tags["entityId"] = it }

        // Extract query parameters as tags
        extractQueryTags(queryParams, tags)

        // Extract meaningful fields from request body
        extractBodyTags(requestBody, resource, tags)

        return AnalysisResult(resource = resource, action = action, tags = tags)
    }

    private fun extractFallbackResource(path: String): String {
        // Extract from /api/v1/{resource}/...
        val match = Regex("^/api/v1/([^/]+)").find(path)
        return match?.groupValues?.getOrNull(1) ?: "unknown"
    }

    private fun inferAction(method: String, path: String): String {
        // Check if path ends with a UUID (specific entity)
        val endsWithId = Regex("/[0-9a-fA-F-]{36}(/[a-z-]+)?$").containsMatchIn(path)

        return when (method.uppercase()) {
            "GET" -> if (endsWithId) "get" else "list"
            "POST" -> "create"
            "PUT" -> "update"
            "PATCH" -> "patch"
            "DELETE" -> "delete"
            else -> method.lowercase()
        }
    }

    private fun extractQueryTags(queryParams: String?, tags: MutableMap<String, String>) {
        if (queryParams.isNullOrBlank()) return

        queryParams.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "search", "q", "query" -> tags["search"] = value
                    "status" -> tags["filterStatus"] = value
                    "channel" -> tags["filterChannel"] = value
                    "category", "categoryId", "category_id" -> tags["filterCategory"] = value
                    "date", "start_date", "startDate" -> tags["filterDateStart"] = value
                    "end_date", "endDate" -> tags["filterDateEnd"] = value
                    "page" -> tags["page"] = value
                    "pageSize", "page_size", "limit" -> tags["pageSize"] = value
                    "sort", "sortBy", "sort_by" -> tags["sort"] = value
                    "role" -> tags["filterRole"] = value
                    "workerId", "worker_id" -> tags["workerId"] = value
                    "type" -> tags["filterType"] = value
                    "available" -> tags["filterAvailable"] = value
                    "period" -> tags["period"] = value
                }
            }
        }
    }

    private fun extractBodyTags(body: String?, resource: String, tags: MutableMap<String, String>) {
        if (body.isNullOrBlank()) return
        try {
            val json = Json.parseToJsonElement(body)
            if (json !is JsonObject) return

            // Common fields across resources
            json["name"]?.jsonPrimitive?.contentOrNull?.let { tags["name"] = it.take(50) }
            json["status"]?.jsonPrimitive?.contentOrNull?.let { tags["status"] = it }
            json["channel"]?.jsonPrimitive?.contentOrNull?.let { tags["channel"] = it }
            json["role"]?.jsonPrimitive?.contentOrNull?.let { tags["role"] = it }

            when (resource) {
                "orders" -> {
                    json["payment_method"]?.jsonPrimitive?.contentOrNull?.let { tags["paymentMethod"] = it }
                    json["payment_status"]?.jsonPrimitive?.contentOrNull?.let { tags["paymentStatus"] = it }
                    json["total"]?.jsonPrimitive?.contentOrNull?.let { tags["total"] = it }
                    json["subtotal"]?.jsonPrimitive?.contentOrNull?.let { tags["subtotal"] = it }
                    json["table_id"]?.jsonPrimitive?.contentOrNull?.let { tags["tableId"] = it }
                    json["items"]?.jsonArray?.let { tags["itemCount"] = "${it.size}" }
                    json["client_name"]?.jsonPrimitive?.contentOrNull?.let { tags["clientName"] = it.take(30) }
                    json["delivery_fee"]?.jsonPrimitive?.contentOrNull?.let { tags["deliveryFee"] = it }
                }
                "items" -> {
                    json["price"]?.jsonPrimitive?.contentOrNull?.let { tags["price"] = it }
                    json["category_id"]?.jsonPrimitive?.contentOrNull?.let { tags["categoryId"] = it }
                    json["available"]?.jsonPrimitive?.contentOrNull?.let { tags["available"] = it }
                    json["sku"]?.jsonPrimitive?.contentOrNull?.let { tags["sku"] = it }
                    json["barcode"]?.jsonPrimitive?.contentOrNull?.let { tags["barcode"] = it }
                }
                "stock" -> {
                    json["quantity"]?.jsonPrimitive?.contentOrNull?.let { tags["quantity"] = it }
                    json["type"]?.jsonPrimitive?.contentOrNull?.let { tags["transactionType"] = it }
                    json["item_name"]?.jsonPrimitive?.contentOrNull?.let { tags["itemName"] = it.take(50) }
                }
                "workers" -> {
                    json["full_name"]?.jsonPrimitive?.contentOrNull?.let { tags["workerName"] = it.take(30) }
                    json["salary_type"]?.jsonPrimitive?.contentOrNull?.let { tags["salaryType"] = it }
                    json["salary_amount"]?.jsonPrimitive?.contentOrNull?.let { tags["salaryAmount"] = it }
                }
                "attendance" -> {
                    json["worker_id"]?.jsonPrimitive?.contentOrNull?.let { tags["workerId"] = it }
                    json["auth_method"]?.jsonPrimitive?.contentOrNull?.let { tags["authMethod"] = it }
                }
                "customers" -> {
                    json["phone"]?.jsonPrimitive?.contentOrNull?.let { tags["phone"] = it }
                }
                "auth" -> {
                    json["phone"]?.jsonPrimitive?.contentOrNull?.let { tags["phone"] = it }
                    json["email"]?.jsonPrimitive?.contentOrNull?.let { tags["email"] = it }
                }
                "vendor-profile" -> {
                    json["name"]?.jsonPrimitive?.contentOrNull?.let { tags["vendorName"] = it.take(50) }
                    json["address"]?.jsonPrimitive?.contentOrNull?.let { tags["address"] = it.take(50) }
                }
            }
        } catch (_: Exception) {
            // Body is not valid JSON, skip
        }
    }

    /**
     * Serialize tags map to a compact JSON string.
     */
    fun tagsToJson(tags: Map<String, String>): String? {
        if (tags.isEmpty()) return null
        return buildJsonObject {
            tags.forEach { (k, v) -> put(k, v) }
        }.toString()
    }

    // ─── Human-readable descriptions ─────────────────────────────────

    private val resourceLabels = mapOf(
        "orders" to "Order",
        "items" to "Item",
        "categories" to "Category",
        "menu" to "Menu",
        "tables" to "Table",
        "customers" to "Customer",
        "customer-addresses" to "Customer Address",
        "workers" to "Worker",
        "worker-roles" to "Worker Role",
        "attendance" to "Attendance",
        "overtime" to "Overtime",
        "stock" to "Stock",
        "vendors" to "Vendor",
        "vendor-profile" to "Vendor Profile",
        "vendor-plan" to "Vendor Plan",
        "users" to "User",
        "auth" to "Auth",
        "analytics" to "Analytics",
        "export" to "Export",
        "upload" to "Upload",
        "tax-places" to "Tax Place",
        "digital-menu" to "Digital Menu",
        "reservations" to "Reservation",
        "announcements" to "Announcement",
        "recipes" to "Recipe",
        "chatbot" to "AI Chatbot",
    )

    /**
     * Generate a human-readable description of what the API call did.
     * Combines resource, action, status, and tags into a clear summary.
     *
     * Examples:
     * - "Listed 15 orders (status: PENDING, channel: DINE_IN)"
     * - "Created order — 5 items, total: 250 EGP, client: أحمد"
     * - "Updated order status → COMPLETED"
     * - "Deleted item 'بيتزا مارجريتا'"
     * - "Login successful for phone: 01xxxxxxxxx"
     * - "Fetched analytics dashboard"
     */
    fun generateDescription(
        analysis: AnalysisResult,
        statusCode: Int,
        responseBody: String?,
    ): String {
        val label = resourceLabels[analysis.resource] ?: analysis.resource.replaceFirstChar { it.uppercase() }
        val tags = analysis.tags
        val isError = statusCode >= 400

        // Special actions first
        if (isError) {
            return buildErrorDescription(label, analysis, statusCode, responseBody)
        }

        return when (analysis.action) {
            // Auth actions
            "login" -> buildString {
                append("Login successful")
                tags["phone"]?.let { append(" — phone: $it") }
            }
            "register" -> "New account registered"
            "refresh-token" -> "Token refreshed"
            "profile" -> "Fetched auth profile"

            // List actions
            "list" -> buildString {
                append("Listed ${label}s")
                val filters = buildFilterString(tags)
                if (filters.isNotEmpty()) append(" ($filters)")
                extractCount(responseBody)?.let { append(" — $it results") }
            }

            // Get single entity
            "get" -> buildString {
                append("Fetched $label")
                tags["entityId"]?.let { append(" #${it.take(8)}") }
                tags["name"]?.let { append(" '$it'") }
            }

            // Create
            "create" -> buildString {
                append("Created $label")
                appendCreateDetails(this, analysis.resource, tags)
            }

            // Update
            "update" -> buildString {
                append("Updated $label")
                tags["entityId"]?.let { append(" #${it.take(8)}") }
                tags["name"]?.let { append(" '$it'") }
                appendUpdateDetails(this, analysis.resource, tags)
            }

            // Delete
            "delete" -> buildString {
                append("Deleted $label")
                tags["entityId"]?.let { append(" #${it.take(8)}") }
                tags["name"]?.let { append(" '$it'") }
            }

            // Order-specific
            "update-status" -> buildString {
                append("Updated $label status")
                tags["status"]?.let { append(" → $it") }
                tags["entityId"]?.let { append(" (order #${it.take(8)})") }
            }
            "update-payment" -> buildString {
                append("Updated $label payment")
                tags["paymentStatus"]?.let { append(" → $it") }
                tags["paymentMethod"]?.let { append(" ($it)") }
            }
            "assign-delivery" -> buildString {
                append("Assigned delivery driver")
                tags["entityId"]?.let { append(" for order #${it.take(8)}") }
            }
            "refund" -> buildString {
                append("Refunded order")
                tags["entityId"]?.let { append(" #${it.take(8)}") }
            }

            // Item-specific
            "list-by-category" -> buildString {
                append("Listed items by category")
                extractCount(responseBody)?.let { append(" — $it results") }
            }
            "search" -> buildString {
                append("Searched ${label}s")
                tags["search"]?.let { append(" for '$it'") }
                extractCount(responseBody)?.let { append(" — $it results") }
            }

            // Worker-specific
            "generate-qr" -> "Generated QR code for worker"
            "set-pin" -> "Set PIN for worker"

            // Attendance-specific
            "check-in" -> buildString {
                append("Worker checked in")
                tags["authMethod"]?.let { append(" (via $it)") }
            }
            "check-out" -> buildString {
                append("Worker checked out")
                tags["authMethod"]?.let { append(" (via $it)") }
            }

            // Stock-specific
            "transactions" -> buildString {
                append("Fetched stock transactions")
                tags["itemName"]?.let { append(" for '$it'") }
            }

            // Analytics/vendor-specific
            "dashboard" -> "Fetched analytics dashboard"
            "get-plan" -> "Fetched vendor plan"
            "full-menu" -> "Fetched full menu"
            "public-view" -> "Viewed digital menu page"

            // Patch
            "patch" -> buildString {
                append("Patched $label")
                tags["entityId"]?.let { append(" #${it.take(8)}") }
            }

            // Fallback
            else -> "$label — ${analysis.action}"
        }
    }

    private fun buildErrorDescription(
        label: String,
        analysis: AnalysisResult,
        statusCode: Int,
        responseBody: String?,
    ): String {
        val statusText = when (statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            422 -> "Validation Error"
            429 -> "Rate Limited"
            500 -> "Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Error $statusCode"
        }

        val errorMsg = responseBody?.let {
            try {
                val json = Json.parseToJsonElement(it)
                if (json is JsonObject) {
                    json["message"]?.jsonPrimitive?.contentOrNull
                        ?: json["error"]?.jsonPrimitive?.contentOrNull
                } else null
            } catch (_: Exception) { null }
        }

        return buildString {
            append("[$statusText] ${analysis.action} $label failed")
            errorMsg?.let { append(" — $it") }
        }
    }

    private fun buildFilterString(tags: Map<String, String>): String {
        val parts = mutableListOf<String>()
        tags["filterStatus"]?.let { parts += "status: $it" }
        tags["filterChannel"]?.let { parts += "channel: $it" }
        tags["filterCategory"]?.let { parts += "category: ${it.take(8)}" }
        tags["filterRole"]?.let { parts += "role: $it" }
        tags["filterType"]?.let { parts += "type: $it" }
        tags["search"]?.let { parts += "search: '$it'" }
        tags["filterDateStart"]?.let { parts += "from: $it" }
        tags["filterDateEnd"]?.let { parts += "to: $it" }
        tags["period"]?.let { parts += "period: $it" }
        return parts.joinToString(", ")
    }

    private fun appendCreateDetails(sb: StringBuilder, resource: String, tags: Map<String, String>) {
        when (resource) {
            "orders" -> {
                tags["itemCount"]?.let { sb.append(" — $it items") }
                tags["total"]?.let { sb.append(", total: $it") }
                tags["clientName"]?.let { sb.append(", client: $it") }
                tags["channel"]?.let { sb.append(" ($it)") }
                tags["paymentMethod"]?.let { sb.append(", pay: $it") }
            }
            "items" -> {
                tags["name"]?.let { sb.append(" '$it'") }
                tags["price"]?.let { sb.append(" — price: $it") }
            }
            "workers" -> {
                tags["workerName"]?.let { sb.append(" '$it'") }
                tags["role"]?.let { sb.append(" ($it)") }
            }
            "customers" -> {
                tags["name"]?.let { sb.append(" '$it'") }
                tags["phone"]?.let { sb.append(" — phone: $it") }
            }
            "stock" -> {
                tags["itemName"]?.let { sb.append(" for '$it'") }
                tags["quantity"]?.let { sb.append(" — qty: $it") }
                tags["transactionType"]?.let { sb.append(" ($it)") }
            }
            else -> {
                tags["name"]?.let { sb.append(" '$it'") }
            }
        }
    }

    private fun appendUpdateDetails(sb: StringBuilder, resource: String, tags: Map<String, String>) {
        when (resource) {
            "vendor-profile" -> {
                tags["vendorName"]?.let { sb.append(" — name: '$it'") }
                tags["address"]?.let { sb.append(", addr: '$it'") }
            }
            "items" -> {
                tags["price"]?.let { sb.append(" — price: $it") }
                tags["available"]?.let { sb.append(", available: $it") }
            }
            "workers" -> {
                tags["workerName"]?.let { sb.append(" '$it'") }
                tags["salaryAmount"]?.let { sb.append(" — salary: $it") }
            }
            else -> {
                tags["status"]?.let { sb.append(" → $it") }
            }
        }
    }

    /**
     * Try to extract result count from response body.
     * Looks for "total" or array length in JSON response.
     */
    private fun extractCount(responseBody: String?): Int? {
        if (responseBody.isNullOrBlank()) return null
        return try {
            val json = Json.parseToJsonElement(responseBody)
            when (json) {
                is JsonObject -> {
                    json["total"]?.jsonPrimitive?.intOrNull
                        ?: json["data"]?.jsonArray?.size
                }
                is JsonArray -> json.size
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
