package net.marllex.waselak.admin.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.core.common.logging.AppLogger

class AdminApiClient(
    private val baseUrl: String,
    private val sessionManager: AdminSessionManager,
) {

    private val TAG = "AdminApi"
    private var currentTokens: BearerTokens? = null

    /** Callback invoked when both access & refresh tokens are invalid (user must re-login). */
    var onSessionExpired: (() -> Unit)? = null

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    Logger.d("KtorHttp") { message }
                    AppLogger.d("HTTP", message)
                }
            }
            level = LogLevel.ALL
        }
        install(Auth) {
            bearer {
                loadTokens {
                    currentTokens
                }
                refreshTokens {
                    Logger.i(TAG) { "Access token expired, attempting refresh..." }
                    val refreshToken = sessionManager.getRefreshToken()
                    if (refreshToken == null) {
                        Logger.w(TAG) { "No refresh token available" }
                        onSessionExpired?.invoke()
                        return@refreshTokens null
                    }
                    try {
                        val response: HttpResponse = client.post("$baseUrl/api/v1/cms/auth/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshTokenRequest(refresh_token = refreshToken))
                            markAsRefreshTokenRequest()
                        }
                        if (response.status.isSuccess()) {
                            val result: RefreshTokenResponse = response.body()
                            Logger.i(TAG) { "Token refresh SUCCESS" }
                            sessionManager.saveToken(result.token)
                            sessionManager.saveRefreshToken(result.refresh_token)
                            currentTokens = BearerTokens(result.token, result.refresh_token)
                            currentTokens
                        } else {
                            Logger.e(TAG) { "Token refresh FAILED: ${response.status.value}" }
                            sessionManager.clearToken()
                            currentTokens = null
                            onSessionExpired?.invoke()
                            null
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, e) { "Token refresh EXCEPTION: ${e.message}" }
                        // Don't clear tokens on network error — keep them for retry
                        null
                    }
                }
            }
        }
    }

    init {
        Logger.i(TAG) { "AdminApiClient initialized with baseUrl: $baseUrl" }
        AppLogger.i(TAG, "AdminApiClient initialized with baseUrl: $baseUrl")
        // Restore tokens from session
        val savedToken = sessionManager.getToken()
        val savedRefresh = sessionManager.getRefreshToken()
        if (savedToken != null && savedRefresh != null) {
            currentTokens = BearerTokens(savedToken, savedRefresh)
            Logger.d(TAG) { "Restored tokens from session" }
        }
    }

    fun setToken(token: String) {
        val refresh = sessionManager.getRefreshToken() ?: ""
        currentTokens = BearerTokens(token, refresh)
        Logger.d(TAG) { "Token set (${token.take(20)}...)" }
    }

    fun clearToken() {
        currentTokens = null
        Logger.d(TAG) { "Token cleared" }
    }

    // --- Auth ---

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        val url = "$baseUrl/api/v1/cms/auth/login"
        Logger.i(TAG) { "POST $url (email=$email)" }
        AppLogger.i(TAG, "POST $url (email=$email)")
        return try {
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email = email, password = password))
            }
            val statusCode = response.status.value
            val body = response.bodyAsText()
            Logger.i(TAG) { "Login response: $statusCode | body=$body" }
            AppLogger.i(TAG, "Login response: $statusCode | body=${body.take(500)}")

            if (response.status.isSuccess()) {
                val loginResponse: LoginResponse = response.body()
                currentTokens = BearerTokens(loginResponse.token, loginResponse.refresh_token)
                sessionManager.saveToken(loginResponse.token)
                sessionManager.saveRefreshToken(loginResponse.refresh_token)
                Logger.i(TAG) { "Login SUCCESS for ${loginResponse.email}" }
                AppLogger.i(TAG, "Login SUCCESS for ${loginResponse.email}")
                Result.success(loginResponse)
            } else {
                Logger.e(TAG) { "Login FAILED: HTTP $statusCode | $body" }
                AppLogger.e(TAG, "Login FAILED: HTTP $statusCode | $body")
                Result.failure(Exception("HTTP $statusCode: $body"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Login EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "Login EXCEPTION: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getProfile(): AdminProfile? {
        val url = "$baseUrl/api/v1/cms/auth/profile"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "Profile response: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getProfile EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getProfile EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        val url = "$baseUrl/api/v1/cms/auth/password"
        Logger.d(TAG) { "PUT $url" }
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(ChangePasswordRequest(current_password = currentPassword, new_password = newPassword))
            }
            val success = response.status.isSuccess()
            Logger.i(TAG) { "changePassword: ${response.status.value} success=$success" }
            success
        } catch (e: Exception) {
            Logger.e(TAG, e) { "changePassword EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "changePassword EXCEPTION: ${e.message}", e)
            false
        }
    }

    // --- Plans ---

    suspend fun getPlans(): List<PlanDto> {
        val url = "$baseUrl/api/v1/cms/plans"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getPlans: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getPlans EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getPlans EXCEPTION: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updatePlan(planName: String, update: PlanUpdateDto): PlanDto? {
        val url = "$baseUrl/api/v1/cms/plans/$planName"
        Logger.d(TAG) { "PUT $url" }
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(update)
            }
            Logger.d(TAG) { "updatePlan: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "updatePlan EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "updatePlan EXCEPTION: ${e.message}", e)
            null
        }
    }

    // --- Vendors ---

    suspend fun getVendors(): List<VendorDto> {
        val url = "$baseUrl/api/v1/cms/vendors"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getVendors: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendors EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getVendors EXCEPTION: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createVendor(request: CreateVendorRequest): CreateVendorResponse? {
        val url = "$baseUrl/api/v1/cms/vendors"
        Logger.d(TAG) { "POST $url" }
        return try {
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            Logger.d(TAG) { "createVendor: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "createVendor EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "createVendor EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun updateVendor(id: String, request: UpdateVendorRequest): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$id"
        Logger.d(TAG) { "PUT $url" }
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "updateVendor EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "updateVendor EXCEPTION: ${e.message}", e)
            false
        }
    }

    suspend fun suspendVendor(id: String, suspended: Boolean, reason: String?): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$id/suspend"
        Logger.d(TAG) { "PUT $url suspended=$suspended" }
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(SuspendVendorRequest(suspended = suspended, reason = reason))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "suspendVendor EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "suspendVendor EXCEPTION: ${e.message}", e)
            false
        }
    }

    suspend fun deleteVendor(id: String): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$id"
        Logger.d(TAG) { "DELETE $url" }
        return try {
            val response: HttpResponse = client.delete(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "deleteVendor EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "deleteVendor EXCEPTION: ${e.message}", e)
            false
        }
    }

    suspend fun changeVendorPlan(vendorId: String, plan: String, notes: String?): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/plan"
        Logger.d(TAG) { "PUT $url plan=$plan" }
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(ChangeVendorPlanRequest(plan = plan, notes = notes))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "changeVendorPlan EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "changeVendorPlan EXCEPTION: ${e.message}", e)
            false
        }
    }

    suspend fun getVendorUsage(vendorId: String): VendorUsageDto? {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/usage"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorUsage EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getVendorUsage EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun getVendorDetail(vendorId: String): VendorDetailDto? {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/detail"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getVendorDetail: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorDetail EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getVendorDetail EXCEPTION: ${e.message}", e)
            null
        }
    }

    // --- Analytics ---

    suspend fun getAnalyticsOverview(): AnalyticsOverviewDto? {
        val url = "$baseUrl/api/v1/cms/analytics/overview"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getAnalyticsOverview: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getAnalyticsOverview EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getAnalyticsOverview EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun getPlatformAnalytics(): PlatformAnalyticsDto? {
        val url = "$baseUrl/api/v1/cms/analytics/platform"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getPlatformAnalytics: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getPlatformAnalytics EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getPlatformAnalytics EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun getPlatformAlerts(): List<PlatformAlertDto> {
        val url = "$baseUrl/api/v1/cms/alerts"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getPlatformAlerts: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getPlatformAlerts EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getPlatformAlerts EXCEPTION: ${e.message}", e)
            emptyList()
        }
    }

    // --- Logs ---

    suspend fun getLogs(
        vendorId: String? = null,
        userId: String? = null,
        method: String? = null,
        path: String? = null,
        statusGroup: String? = null,
        resource: String? = null,
        action: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): PaginatedLogsDto? {
        val params = buildList {
            add("page=$page")
            add("pageSize=$pageSize")
            vendorId?.let { add("vendorId=$it") }
            userId?.let { add("userId=$it") }
            method?.let { add("method=$it") }
            path?.let { add("path=$it") }
            statusGroup?.let { add("statusGroup=$it") }
            resource?.let { add("resource=$it") }
            action?.let { add("action=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs?$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getLogs: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getLogs EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getLogs EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun getLogStats(vendorId: String? = null): LogStatsDto? {
        val params = vendorId?.let { "?vendorId=$it" } ?: ""
        val url = "$baseUrl/api/v1/cms/logs/stats$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            Logger.d(TAG) { "getLogStats: ${response.status.value}" }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getLogStats EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "getLogStats EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun getTopEndpoints(limit: Int = 10, vendorId: String? = null): List<EndpointStatDto> {
        val params = buildList {
            add("limit=$limit")
            vendorId?.let { add("vendorId=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs/top-endpoints?$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getTopEndpoints EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getSlowestEndpoints(limit: Int = 10, vendorId: String? = null): List<EndpointStatDto> {
        val params = buildList {
            add("limit=$limit")
            vendorId?.let { add("vendorId=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs/slowest-endpoints?$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getSlowestEndpoints EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getErrorEndpoints(limit: Int = 10, vendorId: String? = null): List<EndpointStatDto> {
        val params = buildList {
            add("limit=$limit")
            vendorId?.let { add("vendorId=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs/error-endpoints?$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getErrorEndpoints EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getRequestTimeline(hours: Int = 24, vendorId: String? = null): List<TimelinePointDto> {
        val params = buildList {
            add("hours=$hours")
            vendorId?.let { add("vendorId=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs/timeline?$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getRequestTimeline EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getLogVendors(): List<LogVendorDto> {
        val url = "$baseUrl/api/v1/cms/logs/vendors"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getLogVendors EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getLogVendorUsers(vendorId: String): List<LogUserDto> {
        val url = "$baseUrl/api/v1/cms/logs/vendors/$vendorId/users"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getLogVendorUsers EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getResourceBreakdown(vendorId: String? = null): List<ResourceStatDto> {
        val params = vendorId?.let { "?vendorId=$it" } ?: ""
        val url = "$baseUrl/api/v1/cms/logs/resources$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getResourceBreakdown EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getActionBreakdown(resource: String? = null, vendorId: String? = null): List<ActionStatDto> {
        val params = buildList {
            resource?.let { add("resource=$it") }
            vendorId?.let { add("vendorId=$it") }
        }.joinToString("&")
        val url = "$baseUrl/api/v1/cms/logs/actions${if (params.isNotEmpty()) "?$params" else ""}"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getActionBreakdown EXCEPTION: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getLiveMonitoring(vendorId: String? = null): LiveMonitoringDto? {
        val params = vendorId?.let { "?vendorId=$it" } ?: ""
        val url = "$baseUrl/api/v1/cms/logs/monitoring$params"
        Logger.d(TAG) { "GET $url" }
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getLiveMonitoring EXCEPTION: ${e.message}" }
            null
        }
    }

    suspend fun cleanupLogs(days: Int = 30): Boolean {
        val url = "$baseUrl/api/v1/cms/logs/cleanup?days=$days"
        Logger.d(TAG) { "DELETE $url" }
        return try {
            val response: HttpResponse = client.delete(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "cleanupLogs EXCEPTION: ${e.message}" }
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Vendor Analytics (CMS wrapper endpoints)
    // ═══════════════════════════════════════════════════════════════════

    private fun analyticsUrl(vendorId: String, type: String, from: Long? = null, to: Long? = null): String {
        val base = "$baseUrl/api/v1/cms/vendors/$vendorId/analytics/$type"
        val params = mutableListOf<String>()
        if (from != null) params.add("from=$from")
        if (to != null) params.add("to=$to")
        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }

    suspend fun getVendorExecutiveSummary(vendorId: String, from: Long? = null, to: Long? = null): ExecutiveSummaryDto? {
        val url = analyticsUrl(vendorId, "executive-summary", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorExecutiveSummary EXCEPTION" }
            null
        }
    }

    suspend fun getVendorRevenueProfit(vendorId: String, from: Long? = null, to: Long? = null): RevenueProfitDto? {
        val url = analyticsUrl(vendorId, "revenue-profit", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorRevenueProfit EXCEPTION" }
            null
        }
    }

    suspend fun getVendorOrdersIntelligence(vendorId: String, from: Long? = null, to: Long? = null): OrdersIntelligenceDto? {
        val url = analyticsUrl(vendorId, "orders-intelligence", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorOrdersIntelligence EXCEPTION" }
            null
        }
    }

    suspend fun getVendorPeakTimes(vendorId: String, from: Long? = null, to: Long? = null): PeakTimeAnalysisDto? {
        val url = analyticsUrl(vendorId, "peak-times", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorPeakTimes EXCEPTION" }
            null
        }
    }

    suspend fun getVendorCashierPerformance(vendorId: String, from: Long? = null, to: Long? = null): List<CashierPerformanceDto> {
        val url = analyticsUrl(vendorId, "cashier-performance", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorCashierPerformance EXCEPTION" }
            emptyList()
        }
    }

    suspend fun getVendorDeliveryPerformance(vendorId: String, from: Long? = null, to: Long? = null): List<DeliveryPerformanceDto> {
        val url = analyticsUrl(vendorId, "delivery-performance", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorDeliveryPerformance EXCEPTION" }
            emptyList()
        }
    }

    suspend fun getVendorProductIntelligence(vendorId: String, from: Long? = null, to: Long? = null): ProductIntelligenceDto? {
        val url = analyticsUrl(vendorId, "product-intelligence", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorProductIntelligence EXCEPTION" }
            null
        }
    }

    suspend fun getVendorCustomerIntelligence(vendorId: String, from: Long? = null, to: Long? = null): CustomerIntelligenceDto? {
        val url = analyticsUrl(vendorId, "customer-intelligence", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorCustomerIntelligence EXCEPTION" }
            null
        }
    }

    suspend fun getVendorAlerts(vendorId: String, from: Long? = null, to: Long? = null): AlertsResponseDto? {
        val url = analyticsUrl(vendorId, "alerts", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorAlerts EXCEPTION" }
            null
        }
    }

    suspend fun getVendorStockOverview(vendorId: String): StockOverviewDto? {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/analytics/stock-overview"
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorStockOverview EXCEPTION" }
            null
        }
    }

    suspend fun getVendorOffersAnalytics(vendorId: String, from: Long? = null, to: Long? = null): OffersAnalyticsDto? {
        val url = analyticsUrl(vendorId, "offers-analytics", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorOffersAnalytics EXCEPTION" }
            null
        }
    }

    suspend fun getVendorDiscountAnalytics(vendorId: String, from: Long? = null, to: Long? = null): DiscountAnalyticsDto? {
        val url = analyticsUrl(vendorId, "discount-analytics", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorDiscountAnalytics EXCEPTION" }
            null
        }
    }

    suspend fun getVendorLoyaltyAnalytics(vendorId: String, from: Long? = null, to: Long? = null): LoyaltyAnalyticsDto? {
        val url = analyticsUrl(vendorId, "loyalty-analytics", from, to)
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorLoyaltyAnalytics EXCEPTION" }
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Vendor Orders, Customers, Workers (CMS endpoints)
    // ═══════════════════════════════════════════════════════════════════

    suspend fun getVendorOrders(
        vendorId: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
        channel: String? = null,
        search: String? = null,
        from: Long? = null,
        to: Long? = null,
    ): CmsOrderListResponse? {
        val params = mutableListOf("page=$page", "pageSize=$pageSize")
        if (status != null) params.add("status=$status")
        if (channel != null) params.add("channel=$channel")
        if (search != null) params.add("search=$search")
        if (from != null) params.add("from=$from")
        if (to != null) params.add("to=$to")
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/orders?${params.joinToString("&")}"
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorOrders EXCEPTION" }
            null
        }
    }

    suspend fun getVendorOrderDetail(vendorId: String, orderId: String): CmsOrderDetailDto? {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/orders/$orderId"
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorOrderDetail EXCEPTION" }
            null
        }
    }

    suspend fun getVendorCustomers(
        vendorId: String,
        page: Int = 1,
        pageSize: Int = 20,
        search: String? = null,
        sortBy: String = "total_spent",
        sortDir: String = "desc",
    ): CmsCustomerListResponse? {
        val params = mutableListOf("page=$page", "pageSize=$pageSize", "sortBy=$sortBy", "sortDir=$sortDir")
        if (search != null) params.add("search=$search")
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/customers?${params.joinToString("&")}"
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorCustomers EXCEPTION" }
            null
        }
    }

    suspend fun getVendorWorkers(vendorId: String): CmsWorkerListResponse? {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/workers"
        return try {
            val response: HttpResponse = client.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "getVendorWorkers EXCEPTION" }
            null
        }
    }
    // ── User Management (CMS) ──────────────────────────────────────────

    suspend fun createVendorUser(
        vendorId: String,
        name: String,
        phone: String,
        password: String,
        role: String = "CASHIER",
        email: String? = null,
    ): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/users"
        return try {
            val body = buildMap<String, String> {
                put("name", name)
                put("phone", phone)
                put("password", password)
                put("role", role)
                if (email != null) put("email", email)
            }
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "createVendorUser EXCEPTION" }
            false
        }
    }

    suspend fun updateVendorUser(vendorId: String, userId: String, name: String? = null, email: String? = null, active: Boolean? = null): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/users/$userId"
        return try {
            val body = buildMap<String, Any?> {
                if (name != null) put("name", name)
                if (email != null) put("email", email)
                if (active != null) put("active", active)
            }
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "updateVendorUser EXCEPTION" }
            false
        }
    }

    suspend fun resetVendorUserPassword(vendorId: String, userId: String, newPassword: String): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/users/$userId/reset-password"
        return try {
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("new_password" to newPassword))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "resetVendorUserPassword EXCEPTION" }
            false
        }
    }

    suspend fun deactivateVendorUser(vendorId: String, userId: String): Boolean {
        val url = "$baseUrl/api/v1/cms/vendors/$vendorId/users/$userId"
        return try {
            val response: HttpResponse = client.delete(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "deactivateVendorUser EXCEPTION" }
            false
        }
    }

    // ─── Notifications ───────────────────────────────────────────
    suspend fun sendNotification(request: AdminSendNotificationRequest): Boolean {
        val url = "$baseUrl/api/v1/cms/notifications/send"
        Logger.d(TAG) { "POST $url type=${request.type}" }
        return try {
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val success = response.status.isSuccess()
            Logger.i(TAG) { "sendNotification: ${response.status.value} success=$success" }
            success
        } catch (e: Exception) {
            Logger.e(TAG, e) { "sendNotification EXCEPTION: ${e.message}" }
            AppLogger.e(TAG, "sendNotification EXCEPTION: ${e.message}", e)
            false
        }
    }

    // ─── App Releases ──────────────────────────────────────────────

    suspend fun getReleases(): List<AppReleaseDto> = try {
        client.get("api/v1/admin/releases").body()
    } catch (e: Exception) {
        AppLogger.e(TAG, "getReleases failed: ${e.message}", e)
        emptyList()
    }

    suspend fun createRelease(request: CreateReleaseRequest): AppReleaseDto? = try {
        client.post("api/v1/admin/releases") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    } catch (e: Exception) {
        AppLogger.e(TAG, "createRelease failed: ${e.message}", e)
        null
    }

    suspend fun updateRelease(id: String, request: UpdateReleaseRequest): AppReleaseDto? = try {
        client.put("api/v1/admin/releases/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    } catch (e: Exception) {
        AppLogger.e(TAG, "updateRelease failed: ${e.message}", e)
        null
    }

    suspend fun deleteRelease(id: String): Boolean = try {
        client.delete("api/v1/admin/releases/$id")
        true
    } catch (e: Exception) {
        AppLogger.e(TAG, "deleteRelease failed: ${e.message}", e)
        false
    }
}

// Internal request DTOs used only by the API client

@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
internal data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
internal data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String
)

@Serializable
internal data class SuspendVendorRequest(
    val suspended: Boolean,
    val reason: String? = null
)

@Serializable
internal data class ChangeVendorPlanRequest(
    val plan: String,
    val notes: String? = null
)
