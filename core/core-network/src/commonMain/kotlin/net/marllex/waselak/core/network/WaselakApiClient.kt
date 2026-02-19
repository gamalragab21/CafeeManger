package net.marllex.waselak.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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

    // ─── Vendor ──────────────────────────────────────────────────

    suspend fun getMyVendor(): VendorResponse =
        client.get("api/v1/vendors/me").body()

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
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getOrder(id: String): OrderResponse =
        client.get("api/v1/orders/$id").body()

    suspend fun createOrder(request: CreateOrderRequest): OrderResponse =
        client.post("api/v1/orders") {
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

    suspend fun assignDeliveryUser(id: String, request: AssignDeliveryRequest): OrderResponse =
        client.patch("api/v1/orders/$id/assign") {
            setBody(request)
        }.body()

    suspend fun shareReceipt(id: String): ShareReceiptResponse =
        client.post("api/v1/orders/$id/share").body()

    suspend fun getDeliveryDashboard(): List<DeliveryDashboardItemResponse> =
        client.get("api/v1/orders/delivery-dashboard").body()

    suspend fun getMyDeliveryOrders(status: String? = null): List<OrderResponse> =
        client.get("api/v1/orders/delivery/mine") {
            parameter("status", status)
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

    // ─── Chatbot ─────────────────────────────────────────────────

    suspend fun sendChatbotQuery(request: ChatbotQueryRequest): ChatbotQueryResponse =
        client.post("api/v1/chatbot/query") {
            setBody(request)
        }.body()

    suspend fun getChatbotSuggestions(): ChatbotSuggestionsResponse =
        client.get("api/v1/chatbot/suggestions").body()
}
