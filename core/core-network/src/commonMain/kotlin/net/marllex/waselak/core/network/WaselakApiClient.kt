package net.marllex.waselak.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import net.marllex.waselak.core.network.dto.*

class WaselakApiClient(private val client: HttpClient) {

    // ─── Authentication ──────────────────────────────────────────

    suspend fun login(request: LoginRequest): AuthResponse =
        client.post("api/v1/auth/login") {
            setBody(request)
        }.body()

    suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse =
        client.post("api/v1/auth/refresh") {
            setBody(request)
        }.body()

    suspend fun logout(): ApiSuccessResponse =
        client.post("api/v1/auth/logout").body()

    /**
     * Cheap authenticated heartbeat. Returns 200 normally; returns 403 with
     * `ACCOUNT_SUSPENDED` if the vendor has been suspended — and that response is
     * intercepted in NetworkModule which clears tokens, flipping `isLoggedIn` to
     * false so the NavHost boots the user to login. Called on a 60-second interval
     * from the foreground so suspension is detected even when the user is idle.
     */
    suspend fun pingAuth(): ApiSuccessResponse =
        client.get("api/v1/auth/ping").body()

    // ─── Manager Override PIN ────────────────────────────────────

    /**
     * Manager sets or changes their own POS-override PIN. Re-authenticates with the
     * current password to prevent an unlocked device from silently overwriting the PIN.
     */
    suspend fun setMyOverridePin(request: SetOverridePinRequest): SetOverridePinResponse =
        client.post("api/v1/users/me/override-pin") {
            setBody(request)
        }.body()

    /**
     * Cashier calls this at the POS to redeem a PIN for an approval token. Returns
     * 403 on invalid PIN. The token is valid for ~90 seconds and must be attached as
     * `managerOverrideToken` on the next `createOrder(...)` call.
     */
    suspend fun verifyOverridePin(request: VerifyOverridePinRequest): VerifyOverridePinResponse =
        client.post("api/v1/auth/verify-override-pin") {
            setBody(request)
        }.body()

    /**
     * Admin-only: wipe another manager's override PIN hash. The caller never receives
     * the PIN itself. Used when a manager forgets or leaves.
     */
    suspend fun resetManagerOverridePin(userId: String): ResetOverridePinResponse =
        client.post("api/v1/users/$userId/reset-override-pin").body()

    // ─── Vendor ──────────────────────────────────────────────────

    suspend fun getMyVendor(): VendorResponse =
        client.get("api/v1/vendors/me").body()

    suspend fun getMyPlan(): PlanFeaturesResponse =
        client.get("api/v1/vendors/me/plan").body()

    suspend fun getAllPlans(): List<PlanSummaryDto> =
        client.get("api/v1/vendors/plans").body()

    suspend fun updateMyVendor(request: UpdateVendorRequest): VendorResponse =
        client.put("api/v1/vendors/me") {
            setBody(request)
        }.body()

    // ─── Categories ──────────────────────────────────────────────

    suspend fun getCategories(): List<CategoryResponse> =
        client.get("api/v1/categories").body()

    suspend fun createCategory(request: CreateCategoryRequest): CategoryResponse =
        client.post("api/v1/categories") {
            setBody(request)
        }.body()

    suspend fun updateCategory(id: String, request: UpdateCategoryRequest): CategoryResponse =
        client.put("api/v1/categories/$id") {
            setBody(request)
        }.body()

    suspend fun deleteCategory(id: String): ApiSuccessResponse =
        client.delete("api/v1/categories/$id").body()

    suspend fun reorderCategories(request: ReorderCategoriesRequest): List<CategoryResponse> =
        client.post("api/v1/categories/reorder") {
            setBody(request)
        }.body()

    // ─── Items ───────────────────────────────────────────────────

    suspend fun getItems(
        categoryId: String? = null,
        available: Boolean? = null
    ): List<ItemResponse> =
        client.get("api/v1/items") {
            parameter("category_id", categoryId)
            parameter("available", available)
        }.body()

    suspend fun getItem(id: String): ItemResponse =
        client.get("api/v1/items/$id").body()

    suspend fun createItem(request: CreateItemRequest): ItemResponse =
        client.post("api/v1/items") {
            setBody(request)
        }.body()

    suspend fun updateItem(id: String, request: UpdateItemRequest): ItemResponse =
        client.put("api/v1/items/$id") {
            setBody(request)
        }.body()

    suspend fun deleteItem(id: String): ApiSuccessResponse =
        client.delete("api/v1/items/$id").body()

    suspend fun toggleItemAvailability(id: String, request: UpdateItemRequest): ItemResponse =
        client.patch("api/v1/items/$id/availability") {
            setBody(request)
        }.body()

    // ─── Item Variants ────────────────────────────────────────────

    suspend fun updateItemVariants(itemId: String, groups: List<CreateVariantGroupRequest>): ItemResponse =
        client.put("api/v1/items/$itemId/variants") {
            setBody(groups)
        }.body()

    // ─── Tables ──────────────────────────────────────────────────

    suspend fun getTables(status: String? = null): List<TableResponse> =
        client.get("api/v1/tables") {
            parameter("status", status)
        }.body()

    suspend fun createTable(request: CreateTableRequest): TableResponse =
        client.post("api/v1/tables") {
            setBody(request)
        }.body()

    suspend fun updateTable(id: String, request: UpdateTableRequest): TableResponse =
        client.put("api/v1/tables/$id") {
            setBody(request)
        }.body()

    suspend fun deleteTable(id: String): ApiSuccessResponse =
        client.delete("api/v1/tables/$id").body()

    suspend fun updateTableStatus(id: String, request: UpdateTableStatusRequest): TableResponse =
        client.patch("api/v1/tables/$id/status") {
            setBody(request)
        }.body()

    // ─── Reservations ────────────────────────────────────────────

    suspend fun getReservations(
        date: String? = null,
        tableId: String? = null,
        status: String? = null,
    ): List<ReservationResponse> =
        client.get("api/v1/reservations") {
            parameter("date", date)
            parameter("table_id", tableId)
            parameter("status", status)
        }.body()

    suspend fun getReservation(id: String): ReservationResponse =
        client.get("api/v1/reservations/$id").body()

    suspend fun createReservation(request: CreateReservationRequest): ReservationResponse =
        client.post("api/v1/reservations") {
            setBody(request)
        }.body()

    suspend fun updateReservation(id: String, request: UpdateReservationRequest): ReservationResponse =
        client.put("api/v1/reservations/$id") {
            setBody(request)
        }.body()

    suspend fun updateReservationStatus(id: String, request: UpdateReservationStatusRequest): ReservationResponse =
        client.patch("api/v1/reservations/$id/status") {
            setBody(request)
        }.body()

    suspend fun deleteReservation(id: String): ApiSuccessResponse =
        client.delete("api/v1/reservations/$id").body()

    // ─── Users (Manager only) ────────────────────────────────────

    suspend fun getUsers(role: String? = null): List<UserResponse> =
        client.get("api/v1/users") {
            parameter("role", role)
        }.body()

    suspend fun getUser(id: String): UserResponse =
        client.get("api/v1/users/$id").body()

    suspend fun createUser(request: CreateUserRequest): UserResponse =
        client.post("api/v1/users") {
            setBody(request)
        }.body()

    suspend fun updateMyProfile(request: UpdateUserRequest): UserResponse =
        client.put("api/v1/users/me") {
            setBody(request)
        }.body()

    suspend fun updateUser(id: String, request: UpdateUserRequest): UserResponse =
        client.put("api/v1/users/$id") {
            setBody(request)
        }.body()

    suspend fun deleteUser(id: String): ApiSuccessResponse =
        client.delete("api/v1/users/$id").body()

    // ─── Orders ──────────────────────────────────────────────────

    suspend fun getOrders(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        tableId: String? = null,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 50,
        offset: Int = 0
    ): PaginatedOrdersResponse =
        client.get("api/v1/orders") {
            parameter("status", status)
            parameter("channel", channel)
            parameter("cashier_id", cashierId)
            parameter("delivery_user_id", deliveryUserId)
            parameter("table_id", tableId)
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getOrder(id: String): OrderResponse =
        client.get("api/v1/orders/$id").body()

    suspend fun createOrder(
        request: CreateOrderRequest,
        /**
         * Short-lived manager approval token returned by `verifyOverridePin()`. Required
         * when the order has a discount and the cashier is not themselves a manager;
         * otherwise the server responds 403 `DISCOUNT_REQUIRES_MANAGER`. Null for plain
         * no-discount orders.
         */
        managerOverrideToken: String? = null,
    ): OrderResponse =
        client.post("api/v1/orders") {
            if (managerOverrideToken != null) header("X-Manager-Override", managerOverrideToken)
            setBody(request)
        }.body()

    suspend fun updateOrder(id: String, request: UpdateOrderRequest): OrderResponse =
        client.put("api/v1/orders/$id") {
            setBody(request)
        }.body()

    suspend fun updateOrderStatus(id: String, request: UpdateOrderStatusRequest): OrderResponse =
        client.patch("api/v1/orders/$id/status") {
            setBody(request)
        }.body()

    suspend fun updatePaymentStatus(id: String, request: UpdatePaymentStatusRequest): OrderResponse =
        client.patch("api/v1/orders/$id/payment-status") {
            setBody(request)
        }.body()

    suspend fun refundOrder(id: String, request: RefundOrderRequest): OrderResponse =
        client.post("api/v1/orders/$id/refund") {
            setBody(request)
        }.body()

    suspend fun assignDeliveryUser(id: String, request: AssignDeliveryRequest): OrderResponse =
        client.patch("api/v1/orders/$id/assign") {
            setBody(request)
        }.body()

    suspend fun shareReceipt(id: String): ShareReceiptResponse =
        client.post("api/v1/orders/$id/share").body()

    suspend fun getDeliveryDashboard(): List<DeliveryDashboardItemResponse> =
        client.get("api/v1/orders/delivery-dashboard").body()

    suspend fun getMyDeliveryOrders(status: String? = null, limit: Int = 50, offset: Int = 0): PaginatedOrdersResponse =
        client.get("api/v1/orders/delivery/mine") {
            parameter("status", status)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getAvailableDeliveryOrders(): List<OrderResponse> =
        client.get("api/v1/orders/delivery/available").body()

    // ─── Analytics ───────────────────────────────────────────────

    suspend fun getAnalyticsSummary(
        from: Long? = null,
        to: Long? = null
    ): AnalyticsSummaryResponse =
        client.get("api/v1/analytics/summary") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getFilteredAnalyticsSummary(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): AnalyticsSummaryResponse =
        client.get("api/v1/analytics/filtered-summary") {
            parameter("status", status)
            parameter("channel", channel)
            parameter("cashier_id", cashierId)
            parameter("delivery_user_id", deliveryUserId)
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getSettlements(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): SettlementsResponse =
        client.get("api/v1/analytics/settlements") {
            parameter("status", status)
            parameter("channel", channel)
            parameter("cashier_id", cashierId)
            parameter("delivery_user_id", deliveryUserId)
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getDeliveryPerformance(
        status: String? = null,
        cashierId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): List<DeliveryPerformanceResponse> =
        client.get("api/v1/analytics/delivery-performance") {
            parameter("status", status)
            parameter("cashier_id", cashierId)
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getDailyAnalytics(from: Long, to: Long): List<DailyAnalyticsResponse> =
        client.get("api/v1/analytics/daily") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getMyShiftSummary(from: Long? = null, scope: String? = null): ShiftSummaryResponse =
        client.get("api/v1/orders/my-shift-summary") {
            parameter("from", from)
            scope?.let { parameter("scope", it) }
        }.body()

    suspend fun getUserShiftSummary(userId: String, from: Long? = null): ShiftSummaryResponse =
        client.get("api/v1/orders/shift-summary/$userId") {
            parameter("from", from)
        }.body()

    suspend fun getCashierPerformance(
        status: String? = null,
        channel: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): List<DeliveryPerformanceResponse> =
        client.get("api/v1/analytics/cashier-performance") {
            parameter("status", status)
            parameter("channel", channel)
            parameter("delivery_user_id", deliveryUserId)
            parameter("from", from)
            parameter("to", to)
        }.body()

    // ─── Analytics Dashboard V2 ───────────────────────────────────

    suspend fun getExecutiveSummary(
        from: Long? = null,
        to: Long? = null,
    ): ExecutiveSummaryResponse =
        client.get("api/v1/analytics/dashboard/executive-summary") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getRevenueProfit(
        from: Long? = null,
        to: Long? = null,
    ): RevenueProfitResponse =
        client.get("api/v1/analytics/dashboard/revenue-profit") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getOrdersIntelligence(
        from: Long? = null,
        to: Long? = null,
    ): OrdersIntelligenceResponse =
        client.get("api/v1/analytics/dashboard/orders-intelligence") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getPeakTimeAnalysis(
        from: Long? = null,
        to: Long? = null,
    ): PeakTimeAnalysisResponse =
        client.get("api/v1/analytics/dashboard/peak-times") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getCashierPerformanceV2(
        from: Long? = null,
        to: Long? = null,
    ): List<CashierPerformanceV2Response> =
        client.get("api/v1/analytics/dashboard/cashier-performance-v2") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getDeliveryPerformanceV2(
        from: Long? = null,
        to: Long? = null,
    ): List<DeliveryPerformanceV2Response> =
        client.get("api/v1/analytics/dashboard/delivery-performance-v2") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getProductIntelligence(
        from: Long? = null,
        to: Long? = null,
        limit: Int? = null,
    ): ProductIntelligenceResponse =
        client.get("api/v1/analytics/dashboard/product-intelligence") {
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit)
        }.body()

    suspend fun getCustomerIntelligence(
        from: Long? = null,
        to: Long? = null,
    ): CustomerIntelligenceResponse =
        client.get("api/v1/analytics/dashboard/customer-intelligence") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getAnalyticsAlerts(
        from: Long? = null,
        to: Long? = null,
    ): AlertsResponse =
        client.get("api/v1/analytics/dashboard/alerts") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getStockOverview(): StockOverviewResponse =
        client.get("api/v1/analytics/dashboard/stock-overview").body()

    suspend fun getOffersAnalytics(
        from: Long? = null,
        to: Long? = null,
    ): OffersAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/offers-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getDiscountAnalytics(
        from: Long? = null,
        to: Long? = null,
    ): DiscountAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/discount-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getLoyaltyAnalytics(
        from: Long? = null,
        to: Long? = null,
    ): LoyaltyAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/loyalty-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getStaffCostsAnalytics(
        from: Long? = null,
        to: Long? = null,
    ): StaffCostsAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/staff-costs") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getSupplierAnalytics(
        from: Long? = null,
        to: Long? = null,
    ): SupplierAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/supplier-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getCreditAnalytics(from: Long, to: Long): CreditAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/credit-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getReturnsAnalytics(from: Long, to: Long): ReturnsAnalyticsResponse =
        client.get("api/v1/analytics/dashboard/returns-analytics") {
            parameter("from", from)
            parameter("to", to)
        }.body()

    suspend fun getDoctorStats(from: String? = null, to: String? = null): List<DoctorStatsResponse> =
        client.get("api/v1/analytics/doctors") {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()

    // ─── App Updates ────────────────────────────────────────────
    suspend fun checkForUpdate(app: String, version: String, versionCode: Int, platform: String = detectPlatform()): CheckUpdateResponse =
        client.get("api/v1/app/check-update") {
            parameter("app", app)
            parameter("version", version)
            parameter("version_code", versionCode)
            parameter("platform", platform)
        }.body()

    /**
     * Download a file from URL with progress callback.
     * Returns the saved file path in Downloads folder.
     */
    suspend fun downloadFile(url: String, onProgress: (Float) -> Unit): String? {
        // App-update download. The actual file write is platform-specific —
        // JVM (Android + Desktop) writes to ~/Downloads, iOS no-ops because
        // App Store handles updates there. The helper hides the details.
        val response: HttpResponse = client.get(url)
        val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val filename = url.substringAfterLast("/").substringBefore("?").ifBlank { "update.bin" }
        return saveDownloadToFile(filename, response.bodyAsChannel(), contentLength, onProgress)
    }

    // ─── Export (streaming) ──────────────────────────────────────

    suspend fun exportOrdersPDF(from: Long, to: Long): HttpResponse =
        client.get("api/v1/export/orders/pdf") {
            parameter("from", from)
            parameter("to", to)
        }

    suspend fun exportOrdersExcel(from: Long, to: Long): HttpResponse =
        client.get("api/v1/export/orders/excel") {
            parameter("from", from)
            parameter("to", to)
        }

    suspend fun getExportPreview(from: Long, to: Long): HttpResponse =
        client.get("api/v1/export/orders/preview") {
            parameter("from", from)
            parameter("to", to)
        }

    // ─── Stock ─────────────────────────────────────────────────────

    suspend fun getStock(): List<StockResponse> =
        client.get("api/v1/stock").body()

    suspend fun getStockItem(id: String): StockResponse =
        client.get("api/v1/stock/$id").body()

    suspend fun createStock(request: CreateStockRequest): StockResponse =
        client.post("api/v1/stock") {
            setBody(request)
        }.body()

    suspend fun updateStock(id: String, request: UpdateStockRequest): StockResponse =
        client.put("api/v1/stock/$id") {
            setBody(request)
        }.body()

    suspend fun addStockQuantity(id: String, request: AdjustQuantityRequest): StockResponse =
        client.patch("api/v1/stock/$id/add") {
            setBody(request)
        }.body()

    suspend fun deductStockQuantity(id: String, request: AdjustQuantityRequest): StockResponse =
        client.patch("api/v1/stock/$id/deduct") {
            setBody(request)
        }.body()

    suspend fun deleteStock(id: String): ApiSuccessResponse =
        client.delete("api/v1/stock/$id").body()

    suspend fun getStockItemTransactions(id: String): List<StockTransactionResponse> =
        client.get("api/v1/stock/$id/transactions").body()

    // ─── Stock Analytics ──────────────────────────────────────────────

    suspend fun getStockTransactions(
        stockId: String? = null,
        type: String? = null,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 100
    ): List<StockTransactionResponse> =
        client.get("api/v1/stock/analytics/transactions") {
            parameter("stock_id", stockId)
            parameter("type", type)
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit)
        }.body()

    suspend fun getStockAlerts(): List<StockAlertResponse> =
        client.get("api/v1/stock/analytics/alerts").body()

    suspend fun getStockAnalyticsSummary(): StockAnalyticsSummaryResponse =
        client.get("api/v1/stock/analytics/summary").body()

    // ─── Recipes ────────────────────────────────────────────────────

    suspend fun getRecipes(): List<RecipeResponse> =
        client.get("api/v1/recipes").body()

    suspend fun getRecipe(id: String): RecipeResponse =
        client.get("api/v1/recipes/$id").body()

    suspend fun getRecipeByItemId(itemId: String): RecipeResponse =
        client.get("api/v1/recipes/by-item/$itemId").body()

    suspend fun createRecipe(request: CreateRecipeRequest): RecipeResponse =
        client.post("api/v1/recipes") {
            setBody(request)
        }.body()

    suspend fun updateRecipe(id: String, request: UpdateRecipeRequest): RecipeResponse =
        client.put("api/v1/recipes/$id") {
            setBody(request)
        }.body()

    suspend fun deleteRecipe(id: String): ApiSuccessResponse =
        client.delete("api/v1/recipes/$id").body()

    suspend fun checkRecipeAvailability(itemId: String, servings: Double = 1.0): RecipeAvailabilityResponse =
        client.get("api/v1/recipes/check-availability/$itemId") {
            parameter("servings", servings)
        }.body()

    // ─── Workers ───────────────────────────────────────────────────

    suspend fun getWorkers(active: Boolean? = null): List<WorkerResponse> =
        client.get("api/v1/workers") {
            parameter("active", active)
        }.body()

    suspend fun getWorker(id: String): WorkerResponse =
        client.get("api/v1/workers/$id").body()

    suspend fun createWorker(request: CreateWorkerRequest): WorkerResponse =
        client.post("api/v1/workers") {
            setBody(request)
        }.body()

    suspend fun updateWorker(id: String, request: UpdateWorkerRequest): WorkerResponse =
        client.put("api/v1/workers/$id") {
            setBody(request)
        }.body()

    suspend fun deleteWorker(id: String): ApiSuccessResponse =
        client.delete("api/v1/workers/$id").body()

    // PIN & QR Code Management

    suspend fun updateWorkerPin(workerId: String, request: UpdatePinRequest): ApiSuccessResponse =
        client.post("api/v1/workers/$workerId/pin") {
            setBody(request)
        }.body()

    suspend fun getWorkerQrCode(workerId: String): HttpResponse =
        client.get("api/v1/workers/$workerId/qr-code")

    suspend fun regenerateWorkerQrCode(workerId: String): QrCodeResponse =
        client.post("api/v1/workers/$workerId/qr-code/regenerate").body()

    // ─── Worker Roles ───────────────────────────────────────────────

    suspend fun getWorkerRoles(): List<WorkerRoleResponse> =
        client.get("api/v1/worker-roles").body()

    suspend fun createWorkerRole(request: CreateWorkerRoleRequest): WorkerRoleResponse =
        client.post("api/v1/worker-roles") {
            setBody(request)
        }.body()

    suspend fun deleteWorkerRole(id: String): ApiSuccessResponse =
        client.delete("api/v1/worker-roles/$id").body()

    // ─── Attendance ─────────────────────────────────────────────────

    suspend fun getAttendance(
        workerId: String? = null,
        date: String? = null,
        fromDate: String? = null,
        toDate: String? = null
    ): List<AttendanceResponse> =
        client.get("api/v1/attendance") {
            parameter("worker_id", workerId)
            parameter("date", date)
            parameter("from_date", fromDate)
            parameter("to_date", toDate)
        }.body()

    suspend fun getTodayAttendance(): List<AttendanceSummaryResponse> =
        client.get("api/v1/attendance/today").body()

    suspend fun getAttendanceSummary(
        workerId: String,
        fromDate: String? = null,
        toDate: String? = null
    ): AttendanceSummaryResponse =
        client.get("api/v1/attendance/summary/$workerId") {
            parameter("from_date", fromDate)
            parameter("to_date", toDate)
        }.body()

    suspend fun checkIn(request: CheckInRequest): AttendanceResponse =
        client.post("api/v1/attendance/check-in") {
            setBody(request)
        }.body()

    suspend fun checkInWithPin(request: CheckInWithPinRequest): AttendanceResponse =
        client.post("api/v1/attendance/check-in/pin") {
            setBody(request)
        }.body()

    suspend fun checkInWithQr(request: CheckInWithQrRequest): AttendanceResponse =
        client.post("api/v1/attendance/check-in/qr") {
            setBody(request)
        }.body()

    suspend fun checkOut(attendanceId: String, request: CheckOutRequest): AttendanceResponse =
        client.post("api/v1/attendance/check-out/$attendanceId") {
            setBody(request)
        }.body()

    suspend fun checkOutWithPin(
        attendanceId: String,
        request: CheckOutWithPinRequest
    ): AttendanceResponse =
        client.post("api/v1/attendance/check-out/$attendanceId/pin") {
            setBody(request)
        }.body()

    suspend fun checkOutWithQr(request: CheckOutWithQrRequest): AttendanceResponse =
        client.post("api/v1/attendance/check-out/qr") {
            setBody(request)
        }.body()

    suspend fun deleteAttendance(id: String): ApiSuccessResponse =
        client.delete("api/v1/attendance/$id").body()

    // ─── Salary Payments ────────────────────────────────────────────

    suspend fun getSalaryPayments(
        workerId: String? = null,
        paid: Boolean? = null,
        periodType: String? = null
    ): List<SalaryPaymentResponse> =
        client.get("api/v1/salary-payments") {
            parameter("worker_id", workerId)
            parameter("paid", paid)
            parameter("period_type", periodType)
        }.body()

    suspend fun batchPaySalaries(request: BatchPayRequest): List<SalaryPaymentResponse> =
        client.patch("api/v1/salary-payments/batch-pay") {
            setBody(request)
        }.body()

    suspend fun markSalaryPaid(id: String, request: MarkPaidRequest): SalaryPaymentResponse =
        client.patch("api/v1/salary-payments/$id/pay") {
            setBody(request)
        }.body()

    suspend fun markSalaryUnpaid(id: String): SalaryPaymentResponse =
        client.patch("api/v1/salary-payments/$id/unpay").body()

    // ─── Overtime ─────────────────────────────────────────────────────

    suspend fun getOvertime(
        workerId: String? = null,
        fromDate: String? = null,
        toDate: String? = null
    ): List<OvertimeResponse> =
        client.get("api/v1/overtime") {
            parameter("worker_id", workerId)
            parameter("from_date", fromDate)
            parameter("to_date", toDate)
        }.body()

    suspend fun createOvertime(request: CreateOvertimeRequest): OvertimeResponse =
        client.post("api/v1/overtime") {
            setBody(request)
        }.body()

    suspend fun updateOvertime(id: String, request: UpdateOvertimeRequest): OvertimeResponse =
        client.patch("api/v1/overtime/$id") {
            setBody(request)
        }.body()

    suspend fun deleteOvertime(id: String): ApiSuccessResponse =
        client.delete("api/v1/overtime/$id").body()

    suspend fun markOvertimePaid(id: String): OvertimeResponse =
        client.patch("api/v1/overtime/$id/pay").body()

    suspend fun markOvertimeUnpaid(id: String): OvertimeResponse =
        client.patch("api/v1/overtime/$id/unpay").body()

    suspend fun batchPayOvertime(ids: List<String>): List<OvertimeResponse> =
        client.patch("api/v1/overtime/batch-pay") {
            setBody(BatchPayOvertimeRequest(ids = ids))
        }.body()

    // ─── Tax Places ──────────────────────────────────────────────────

    suspend fun getTaxPlaces(): List<TaxPlaceResponse> =
        client.get("api/v1/tax-places").body()

    suspend fun createTaxPlace(request: CreateTaxPlaceRequest): TaxPlaceResponse =
        client.post("api/v1/tax-places") {
            setBody(request)
        }.body()

    suspend fun updateTaxPlace(id: String, request: UpdateTaxPlaceRequest): TaxPlaceResponse =
        client.put("api/v1/tax-places/$id") {
            setBody(request)
        }.body()

    suspend fun deleteTaxPlace(id: String): ApiSuccessResponse =
        client.delete("api/v1/tax-places/$id").body()

    // ─── Announcements ─────────────────────────────────────────────

    suspend fun getAnnouncements(): List<AnnouncementResponse> =
        client.get("api/v1/announcements").body()

    suspend fun createAnnouncement(request: CreateAnnouncementRequest): AnnouncementResponse =
        client.post("api/v1/announcements") {
            setBody(request)
        }.body()

    suspend fun getUnreadAnnouncementCount(): UnreadCountResponse =
        client.get("api/v1/announcements/unread-count").body()

    suspend fun markAnnouncementRead(id: String): ApiSuccessResponse =
        client.post("api/v1/announcements/$id/read").body()

    suspend fun deleteAnnouncement(id: String): ApiSuccessResponse =
        client.delete("api/v1/announcements/$id").body()

    // ─── Customers ────────────────────────────────────────────────

    suspend fun getCustomers(search: String? = null): List<CustomerResponse> =
        client.get("api/v1/customers") {
            parameter("search", search)
        }.body()

    suspend fun getCustomer(id: String): CustomerResponse =
        client.get("api/v1/customers/$id").body()

    suspend fun getCustomerByPhone(phone: String): CustomerResponse? =
        try {
            client.get("api/v1/customers/by-phone") {
                parameter("phone", phone)
            }.body<CustomerResponse>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }

    suspend fun createCustomer(request: CreateCustomerRequest): CustomerResponse =
        client.post("api/v1/customers") {
            setBody(request)
        }.body()

    suspend fun updateCustomer(id: String, request: UpdateCustomerRequest): CustomerResponse =
        client.put("api/v1/customers/$id") {
            setBody(request)
        }.body()

    suspend fun deleteCustomer(id: String): ApiSuccessResponse =
        client.delete("api/v1/customers/$id").body()

    suspend fun getCustomerAddresses(customerId: String): List<CustomerAddressResponse> =
        client.get("api/v1/customers/$customerId/addresses").body()

    suspend fun createCustomerAddress(
        customerId: String,
        request: CreateCustomerAddressRequest
    ): CustomerAddressResponse =
        client.post("api/v1/customers/$customerId/addresses") {
            setBody(request)
        }.body()

    suspend fun deleteCustomerAddress(
        customerId: String,
        addressId: String
    ): ApiSuccessResponse =
        client.delete("api/v1/customers/$customerId/addresses/$addressId").body()

    suspend fun getCustomerOrders(
        customerId: String,
        limit: Int = 3
    ): CustomerOrderHistoryResponse =
        client.get("api/v1/customers/$customerId/orders") {
            parameter("limit", limit)
        }.body()

    suspend fun getCustomerPointsHistory(customerId: String): List<PointsTransactionResponse> =
        client.get("api/v1/customers/$customerId/points-history").body()

    suspend fun getCustomerDiscountOrders(
        customerId: String,
        limit: Int = 20,
    ): List<OrderResponse> =
        client.get("api/v1/customers/$customerId/discount-orders") {
            parameter("limit", limit)
        }.body()

    // ─── Offers ──────────────────────────────────────────────────

    suspend fun getOffers(active: Boolean? = null): List<OfferResponse> =
        client.get("api/v1/offers") {
            parameter("active", active)
        }.body()

    suspend fun getOffer(id: String): OfferResponse =
        client.get("api/v1/offers/$id").body()

    suspend fun createOffer(request: CreateOfferRequest): OfferResponse =
        client.post("api/v1/offers") {
            setBody(request)
        }.body()

    suspend fun updateOffer(id: String, request: UpdateOfferRequest): OfferResponse =
        client.put("api/v1/offers/$id") {
            setBody(request)
        }.body()

    suspend fun deleteOffer(id: String): ApiSuccessResponse =
        client.delete("api/v1/offers/$id").body()

    suspend fun toggleOffer(id: String): OfferResponse =
        client.patch("api/v1/offers/$id/toggle").body()

    suspend fun applyPromoCode(code: String): OfferResponse =
        client.post("api/v1/offers/apply-promo") {
            setBody(mapOf("code" to code))
        }.body()

    // ─── Manager PIN Verify ─────────────────────────────────────────

    suspend fun verifyManagerPin(pin: String): ApiSuccessResponse =
        client.post("api/v1/workers/verify-manager-pin") {
            setBody(mapOf("pin" to pin))
        }.body()

    // ─── Chatbot ─────────────────────────────────────────────────

    suspend fun sendChatbotQuery(request: ChatbotQueryRequest): ChatbotQueryResponse =
        client.post("api/v1/chatbot/query") {
            setBody(request)
        }.body()

    suspend fun getChatbotSuggestions(): ChatbotSuggestionsResponse =
        client.get("api/v1/chatbot/suggestions").body()

    // ─── File Upload ──────────────────────────────────────────────

    suspend fun uploadImage(imageBytes: ByteArray, fileName: String): UploadResponse =
        client.submitFormWithBinaryData(
            url = "api/v1/upload",
            formData = formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/${fileName.substringAfterLast('.', "jpg")}")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ).body()

    // ─── Log Upload ──────────────────────────────────────────────

    suspend fun uploadLogFile(logBytes: ByteArray, fileName: String): LogUploadResponse {
        // Extract system name from filename like "waselak_manager.log" -> "manager"
        val systemName = fileName.removePrefix("waselak_").removeSuffix(".log")
        return client.submitFormWithBinaryData(
            url = "api/v1/logs/upload",
            formData = formData {
                append("system_name", systemName)
                append("file", logBytes, Headers.build {
                    append(HttpHeaders.ContentType, "text/plain")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        ).body()
    }

    // ─── KDS (Kitchen Display) ──────────────────────────────────

    suspend fun getKdsOrders(
        station: String? = null,
        status: String? = null,
    ): List<KdsOrderResponse> =
        client.get("api/v1/kds/orders") {
            parameter("station", station)
            parameter("status", status)
        }.body()

    suspend fun updateKdsItemStatus(itemId: String, request: UpdateKitchenStatusRequest): ApiSuccessResponse =
        client.patch("api/v1/kds/items/$itemId/status") {
            setBody(request)
        }.body()

    suspend fun bulkUpdateKdsStatus(orderId: String, request: BulkUpdateKitchenStatusRequest): ApiSuccessResponse =
        client.patch("api/v1/kds/orders/$orderId/bulk-status") {
            setBody(request)
        }.body()

    suspend fun getKdsSummary(): KdsSummaryResponse =
        client.get("api/v1/kds/summary").body()

    suspend fun assignKdsStation(itemId: String, request: AssignStationRequest): ApiSuccessResponse =
        client.patch("api/v1/kds/items/$itemId/station") {
            setBody(request)
        }.body()

    // ─── Cash Drawer ────────────────────────────────────────────

    suspend fun openCashDrawer(request: OpenDrawerRequest): CashDrawerSessionResponse =
        client.post("api/v1/cash-drawer/open") {
            setBody(request)
        }.body()

    suspend fun closeCashDrawer(request: CloseDrawerRequest): CashDrawerSessionResponse =
        client.post("api/v1/cash-drawer/close") {
            setBody(request)
        }.body()

    suspend fun getAllOpenDrawerSessions(): List<CashDrawerSessionResponse> =
        client.get("api/v1/cash-drawer/current-all").body()

    suspend fun getCurrentDrawerSession(cashierId: String? = null): CashDrawerSessionResponse? {
        val response = client.get("api/v1/cash-drawer/current") {
            cashierId?.let { parameter("cashier_id", it) }
        }
        return if (response.status.value == 204) null else response.body()
    }

    suspend fun createCashMovement(request: CreateCashMovementRequest): CashMovementResponse =
        client.post("api/v1/cash-drawer/movements") {
            setBody(request)
        }.body()

    suspend fun getCashMovements(
        sessionId: String? = null,
        type: String? = null,
    ): List<CashMovementResponse> =
        client.get("api/v1/cash-drawer/movements") {
            parameter("session_id", sessionId)
            parameter("type", type)
        }.body()

    suspend fun getCashDrawerSessions(
        limit: Int = 20,
        offset: Int = 0,
        cashierId: String? = null,
    ): List<CashDrawerSessionResponse> =
        client.get("api/v1/cash-drawer/sessions") {
            parameter("limit", limit)
            parameter("offset", offset)
            cashierId?.let { parameter("cashier_id", it) }
        }.body()

    suspend fun getCashDrawerSummary(cashierId: String? = null): DrawerSummaryResponse =
        client.get("api/v1/cash-drawer/summary") {
            cashierId?.let { parameter("cashier_id", it) }
        }.body()

    // ─── Split Payments ─────────────────────────────────────────

    suspend fun getOrderPayments(orderId: String): SplitPaymentSummaryResponse =
        client.get("api/v1/orders/$orderId/payments").body()

    suspend fun addOrderPayment(orderId: String, request: CreateOrderPaymentRequest): AddPaymentResponse =
        client.post("api/v1/orders/$orderId/payments") {
            setBody(request)
        }.body()

    suspend fun deleteOrderPayment(orderId: String, paymentId: String): ApiSuccessResponse =
        client.delete("api/v1/orders/$orderId/payments/$paymentId").body()

    // ─── Prescriptions ──────────────────────────────────────────

    suspend fun getPrescriptions(
        status: String? = null,
        customerId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<PrescriptionResponse> =
        client.get("api/v1/prescriptions") {
            parameter("status", status)
            parameter("customer_id", customerId)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getPrescription(id: String): PrescriptionResponse =
        client.get("api/v1/prescriptions/$id").body()

    suspend fun createPrescription(request: CreatePrescriptionRequest): PrescriptionResponse =
        client.post("api/v1/prescriptions") {
            setBody(request)
        }.body()

    suspend fun dispensePrescription(id: String, request: DispensePrescriptionRequest): PrescriptionResponse =
        client.patch("api/v1/prescriptions/$id/dispense") {
            setBody(request)
        }.body()

    suspend fun cancelPrescription(id: String): PrescriptionResponse =
        client.patch("api/v1/prescriptions/$id/cancel").body()

    // ─── Drug Interactions ──────────────────────────────────────

    suspend fun getDrugInteractions(): List<DrugInteractionResponse> =
        client.get("api/v1/drug-interactions").body()

    suspend fun createDrugInteraction(request: CreateDrugInteractionRequest): DrugInteractionResponse =
        client.post("api/v1/drug-interactions") {
            setBody(request)
        }.body()

    suspend fun checkDrugInteractions(request: CheckInteractionsRequest): InteractionCheckResultResponse =
        client.post("api/v1/drug-interactions/check") {
            setBody(request)
        }.body()

    suspend fun deleteDrugInteraction(id: String): ApiSuccessResponse =
        client.delete("api/v1/drug-interactions/$id").body()

    suspend fun toggleDrugInteraction(id: String): DrugInteractionResponse =
        client.patch("api/v1/drug-interactions/$id/toggle").body()

    // ─── Customer Credit ────────────────────────────────────────

    suspend fun getCustomerCredit(customerId: String): CustomerCreditResponse =
        client.get("api/v1/customers/$customerId/credit").body()

    suspend fun setCustomerCreditLimit(customerId: String, request: SetCreditLimitRequest): CustomerCreditResponse =
        client.put("api/v1/customers/$customerId/credit/limit") {
            setBody(request)
        }.body()

    suspend fun chargeCustomerCredit(customerId: String, request: CreditChargeRequest): CreditTransactionResponse =
        client.post("api/v1/customers/$customerId/credit/charge") {
            setBody(request)
        }.body()

    suspend fun payCustomerCredit(customerId: String, request: CreditPaymentRequest): CreditTransactionResponse =
        client.post("api/v1/customers/$customerId/credit/payment") {
            setBody(request)
        }.body()

    suspend fun getCustomerCreditTransactions(
        customerId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<CreditTransactionResponse> =
        client.get("api/v1/customers/$customerId/credit/transactions") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getCreditDebtors(): List<CustomerCreditResponse> =
        client.get("api/v1/credit/debtors").body()

    // ─── Installments ───────────────────────────────────────────

    suspend fun createInstallmentPlan(request: CreateInstallmentPlanRequest): InstallmentPlanResponse =
        client.post("api/v1/installments") { setBody(request) }.body()

    suspend fun getInstallmentPlans(status: String? = null): List<InstallmentPlanResponse> =
        client.get("api/v1/installments") {
            status?.let { parameter("status", it) }
        }.body()

    suspend fun getInstallmentPlan(planId: String): InstallmentPlanResponse =
        client.get("api/v1/installments/$planId").body()

    suspend fun recordInstallmentPayment(planId: String, request: RecordInstallmentPaymentRequest): InstallmentPaymentResponse =
        client.post("api/v1/installments/$planId/payments") { setBody(request) }.body()

    suspend fun updateInstallmentStatus(planId: String, request: UpdateInstallmentStatusRequest): InstallmentPlanResponse =
        client.patch("api/v1/installments/$planId/status") { setBody(request) }.body()

    suspend fun applyInstallmentLateFee(planId: String, paymentId: String? = null): InstallmentPlanResponse =
        client.post("api/v1/installments/$planId/apply-late-fee") {
            if (paymentId != null) setBody(ApplyLateFeeRequest(paymentId))
        }.body()

    suspend fun toggleInstallmentLateFee(planId: String, paymentId: String, enabled: Boolean): InstallmentPlanResponse =
        client.patch("api/v1/installments/$planId/payments/$paymentId/late-fee-toggle") {
            setBody(mapOf("enabled" to enabled))
        }.body()

    suspend fun getCustomerInstallments(customerId: String): List<InstallmentPlanResponse> =
        client.get("api/v1/customers/$customerId/installments").body()

    suspend fun getInstallmentAnalytics(from: Long? = null, to: Long? = null): InstallmentAnalyticsResponse =
        client.get("api/v1/analytics/installments") {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()

    // ─── Scheduled Orders ───────────────────────────────────────

    suspend fun getScheduledOrders(
        status: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<ScheduledOrderResponse> =
        client.get("api/v1/scheduled-orders") {
            parameter("status", status)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getScheduledOrder(id: String): ScheduledOrderResponse =
        client.get("api/v1/scheduled-orders/$id").body()

    suspend fun createScheduledOrder(request: CreateScheduledOrderRequest): ScheduledOrderResponse =
        client.post("api/v1/scheduled-orders") {
            setBody(request)
        }.body()

    suspend fun updateScheduledOrderStatus(id: String, request: UpdateScheduledOrderStatusRequest): ScheduledOrderResponse =
        client.patch("api/v1/scheduled-orders/$id/status") {
            setBody(request)
        }.body()

    suspend fun deleteScheduledOrder(id: String): ApiSuccessResponse =
        client.delete("api/v1/scheduled-orders/$id").body()

    // ─── Suppliers ──────────────────────────────────────────────

    suspend fun getSuppliers(active: Boolean? = null): List<SupplierResponse> =
        client.get("api/v1/suppliers") {
            parameter("active", active)
        }.body()

    suspend fun getSupplier(id: String): SupplierResponse =
        client.get("api/v1/suppliers/$id").body()

    suspend fun createSupplier(request: CreateSupplierRequest): SupplierResponse =
        client.post("api/v1/suppliers") {
            setBody(request)
        }.body()

    suspend fun updateSupplier(id: String, request: UpdateSupplierRequest): SupplierResponse =
        client.put("api/v1/suppliers/$id") {
            setBody(request)
        }.body()

    suspend fun deleteSupplier(id: String): ApiSuccessResponse =
        client.delete("api/v1/suppliers/$id").body()

    // ─── Purchase Orders ────────────────────────────────────────

    suspend fun getPurchaseOrders(
        status: String? = null,
        supplierId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<PurchaseOrderResponse> =
        client.get("api/v1/purchase-orders") {
            parameter("status", status)
            parameter("supplier_id", supplierId)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getPurchaseOrder(id: String): PurchaseOrderResponse =
        client.get("api/v1/purchase-orders/$id").body()

    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): PurchaseOrderResponse =
        client.post("api/v1/purchase-orders") {
            setBody(request)
        }.body()

    suspend fun submitPurchaseOrder(id: String): PurchaseOrderResponse =
        client.patch("api/v1/purchase-orders/$id/submit").body()

    suspend fun receivePurchaseOrder(id: String, request: ReceivePurchaseOrderRequest): PurchaseOrderResponse =
        client.post("api/v1/purchase-orders/$id/receive") {
            setBody(request)
        }.body()

    suspend fun deletePurchaseOrder(id: String): ApiSuccessResponse =
        client.delete("api/v1/purchase-orders/$id").body()

    // ─── Returns & Exchanges ────────────────────────────────────

    suspend fun getReturns(
        status: String? = null,
        orderId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<ProductReturnResponse> =
        client.get("api/v1/returns") {
            parameter("status", status)
            parameter("order_id", orderId)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getReturn(id: String): ProductReturnResponse =
        client.get("api/v1/returns/$id").body()

    suspend fun createReturn(request: CreateReturnRequest): ProductReturnResponse =
        client.post("api/v1/returns") {
            setBody(request)
        }.body()

    suspend fun processReturn(id: String, request: ProcessReturnRequest): ProductReturnResponse =
        client.patch("api/v1/returns/$id/process") {
            setBody(request)
        }.body()

    suspend fun getReturnsSummary(): ReturnsSummaryResponse =
        client.get("api/v1/returns/summary").body()

    // ─── Notifications ──────────────────────────────────────────

    suspend fun getNotifications(
        type: String? = null,
        unreadOnly: Boolean? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<NotificationResponse> =
        client.get("api/v1/notifications") {
            parameter("type", type)
            parameter("unread_only", unreadOnly)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getNotificationCount(): NotificationCountResponse =
        client.get("api/v1/notifications/count").body()

    suspend fun markNotificationRead(id: String): ApiSuccessResponse =
        client.patch("api/v1/notifications/$id/read").body()

    suspend fun markAllNotificationsRead(): ApiSuccessResponse =
        client.patch("api/v1/notifications/read-all").body()

    suspend fun createNotification(request: CreateNotificationRequest): NotificationResponse =
        client.post("api/v1/notifications") {
            setBody(request)
        }.body()

    suspend fun deleteNotification(id: String): ApiSuccessResponse =
        client.delete("api/v1/notifications/$id").body()

    suspend fun registerDevice(request: RegisterDeviceRequest): DeviceTokenResponse =
        client.post("api/v1/devices/register") {
            setBody(request)
        }.body()

    suspend fun unregisterDevice(token: String): ApiSuccessResponse =
        client.delete("api/v1/devices/$token").body()

    // (duplicate removed — checkForUpdate is defined above)
}

// Detected at runtime via the multiplatform helper in PlatformDetect.kt.
// JVM looks at System.getProperty("os.name") to distinguish android / mac /
// windows / linux; iOS returns "ios" directly.
private fun detectPlatform(): String = currentPlatformName()
