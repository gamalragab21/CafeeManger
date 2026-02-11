package net.marllex.cafeemanger.core.network

import net.marllex.cafeemanger.core.network.dto.*
import retrofit2.http.*

interface CafeeMangerApi {

    // ─── Authentication ──────────────────────────────────────────
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(): ApiSuccessResponse

    // ─── Vendor ──────────────────────────────────────────────────
    @GET("api/v1/vendors/me")
    suspend fun getMyVendor(): VendorResponse

    @PUT("api/v1/vendors/me")
    suspend fun updateMyVendor(@Body request: UpdateVendorRequest): VendorResponse

    // ─── Categories ──────────────────────────────────────────────
    @GET("api/v1/categories")
    suspend fun getCategories(): List<CategoryResponse>

    @POST("api/v1/categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): CategoryResponse

    @PUT("api/v1/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body request: UpdateCategoryRequest
    ): CategoryResponse

    @DELETE("api/v1/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): ApiSuccessResponse

    @POST("api/v1/categories/reorder")
    suspend fun reorderCategories(@Body request: ReorderCategoriesRequest): List<CategoryResponse>

    // ─── Items ───────────────────────────────────────────────────
    @GET("api/v1/items")
    suspend fun getItems(
        @Query("category_id") categoryId: String? = null,
        @Query("available") available: Boolean? = null
    ): List<ItemResponse>

    @GET("api/v1/items/{id}")
    suspend fun getItem(@Path("id") id: String): ItemResponse

    @POST("api/v1/items")
    suspend fun createItem(@Body request: CreateItemRequest): ItemResponse

    @PUT("api/v1/items/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Body request: UpdateItemRequest
    ): ItemResponse

    @DELETE("api/v1/items/{id}")
    suspend fun deleteItem(@Path("id") id: String): ApiSuccessResponse

    @PATCH("api/v1/items/{id}/availability")
    suspend fun toggleItemAvailability(
        @Path("id") id: String,
        @Body request: UpdateItemRequest
    ): ItemResponse

    // ─── Tables ──────────────────────────────────────────────────
    @GET("api/v1/tables")
    suspend fun getTables(
        @Query("status") status: String? = null
    ): List<TableResponse>

    @POST("api/v1/tables")
    suspend fun createTable(@Body request: CreateTableRequest): TableResponse

    @PUT("api/v1/tables/{id}")
    suspend fun updateTable(
        @Path("id") id: String,
        @Body request: UpdateTableRequest
    ): TableResponse

    @DELETE("api/v1/tables/{id}")
    suspend fun deleteTable(@Path("id") id: String): ApiSuccessResponse

    @PATCH("api/v1/tables/{id}/status")
    suspend fun updateTableStatus(
        @Path("id") id: String,
        @Body request: UpdateTableStatusRequest
    ): TableResponse

    // ─── Users (Manager only) ────────────────────────────────────
    @GET("api/v1/users")
    suspend fun getUsers(
        @Query("role") role: String? = null
    ): List<UserResponse>

    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") id: String): UserResponse

    @POST("api/v1/users")
    suspend fun createUser(@Body request: CreateUserRequest): UserResponse

    @PUT("api/v1/users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body request: UpdateUserRequest
    ): UserResponse

    @DELETE("api/v1/users/{id}")
    suspend fun deleteUser(@Path("id") id: String): ApiSuccessResponse

    // ─── Orders ──────────────────────────────────────────────────
    @GET("api/v1/orders")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("channel") channel: String? = null,
        @Query("cashier_id") cashierId: String? = null,
        @Query("delivery_user_id") deliveryUserId: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PaginatedOrdersResponse

    @GET("api/v1/orders/{id}")
    suspend fun getOrder(@Path("id") id: String): OrderResponse

    @POST("api/v1/orders")
    suspend fun createOrder(@Body request: CreateOrderRequest): OrderResponse

    @PATCH("api/v1/orders/{id}/status")
    suspend fun updateOrderStatus(
        @Path("id") id: String,
        @Body request: UpdateOrderStatusRequest
    ): OrderResponse

    @PATCH("api/v1/orders/{id}/assign")
    suspend fun assignDeliveryUser(
        @Path("id") id: String,
        @Body request: AssignDeliveryRequest
    ): OrderResponse

    @POST("api/v1/orders/{id}/share")
    suspend fun shareReceipt(
        @Path("id") id: String
    ): ShareReceiptResponse

    @GET("api/v1/orders/delivery/mine")
    suspend fun getMyDeliveryOrders(
        @Query("status") status: String? = null
    ): List<OrderResponse>

    @GET("api/v1/orders/delivery/available")
    suspend fun getAvailableDeliveryOrders(): List<OrderResponse>

    // ─── Analytics ───────────────────────────────────────────────
    @GET("api/v1/analytics/summary")
    suspend fun getAnalyticsSummary(
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): AnalyticsSummaryResponse

    @GET("api/v1/analytics/filtered-summary")
    suspend fun getFilteredAnalyticsSummary(
        @Query("status") status: String? = null,
        @Query("channel") channel: String? = null,
        @Query("cashier_id") cashierId: String? = null,
        @Query("delivery_user_id") deliveryUserId: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): AnalyticsSummaryResponse

    @GET("api/v1/analytics/settlements")
    suspend fun getSettlements(
        @Query("status") status: String? = null,
        @Query("channel") channel: String? = null,
        @Query("cashier_id") cashierId: String? = null,
        @Query("delivery_user_id") deliveryUserId: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): SettlementsResponse

    @GET("api/v1/analytics/delivery-performance")
    suspend fun getDeliveryPerformance(
        @Query("status") status: String? = null,
        @Query("cashier_id") cashierId: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): List<DeliveryPerformanceResponse>

    @GET("api/v1/analytics/daily")
    suspend fun getDailyAnalytics(
        @Query("from") from: Long,
        @Query("to") to: Long
    ): List<DailyAnalyticsResponse>

    @GET("api/v1/analytics/cashier-performance")
    suspend fun getCashierPerformance(
        @Query("status") status: String? = null,
        @Query("channel") channel: String? = null,
        @Query("delivery_user_id") deliveryUserId: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): List<DeliveryPerformanceResponse>

    // ─── Stock ─────────────────────────────────────────────────────
    @GET("api/v1/stock")
    suspend fun getStock(): List<StockResponse>

    @GET("api/v1/stock/{id}")
    suspend fun getStockItem(@Path("id") id: String): StockResponse

    @POST("api/v1/stock")
    suspend fun createStock(@Body request: CreateStockRequest): StockResponse

    @PUT("api/v1/stock/{id}")
    suspend fun updateStock(
        @Path("id") id: String,
        @Body request: UpdateStockRequest
    ): StockResponse

    @PATCH("api/v1/stock/{id}/add")
    suspend fun addStockQuantity(
        @Path("id") id: String,
        @Body request: AdjustQuantityRequest
    ): StockResponse

    @PATCH("api/v1/stock/{id}/deduct")
    suspend fun deductStockQuantity(
        @Path("id") id: String,
        @Body request: AdjustQuantityRequest
    ): StockResponse

    @DELETE("api/v1/stock/{id}")
    suspend fun deleteStock(@Path("id") id: String): ApiSuccessResponse

    @GET("api/v1/stock/{id}/transactions")
    suspend fun getStockItemTransactions(@Path("id") id: String): List<StockTransactionResponse>

    // ─── Stock Analytics ──────────────────────────────────────────────
    @GET("api/v1/stock/analytics/transactions")
    suspend fun getStockTransactions(
        @Query("stock_id") stockId: String? = null,
        @Query("type") type: String? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
        @Query("limit") limit: Int = 100
    ): List<StockTransactionResponse>

    @GET("api/v1/stock/analytics/alerts")
    suspend fun getStockAlerts(): List<StockAlertResponse>

    @GET("api/v1/stock/analytics/summary")
    suspend fun getStockAnalyticsSummary(): StockAnalyticsSummaryResponse

    // ─── Workers ───────────────────────────────────────────────────
    @GET("api/v1/workers")
    suspend fun getWorkers(
        @Query("active") active: Boolean? = null
    ): List<WorkerResponse>

    @GET("api/v1/workers/{id}")
    suspend fun getWorker(@Path("id") id: String): WorkerResponse

    @POST("api/v1/workers")
    suspend fun createWorker(@Body request: CreateWorkerRequest): WorkerResponse

    @PUT("api/v1/workers/{id}")
    suspend fun updateWorker(
        @Path("id") id: String,
        @Body request: UpdateWorkerRequest
    ): WorkerResponse

    @DELETE("api/v1/workers/{id}")
    suspend fun deleteWorker(@Path("id") id: String): ApiSuccessResponse

    // ─── Worker Roles ───────────────────────────────────────────────
    @GET("api/v1/worker-roles")
    suspend fun getWorkerRoles(): List<WorkerRoleResponse>

    @POST("api/v1/worker-roles")
    suspend fun createWorkerRole(@Body request: CreateWorkerRoleRequest): WorkerRoleResponse

    @DELETE("api/v1/worker-roles/{id}")
    suspend fun deleteWorkerRole(@Path("id") id: String): ApiSuccessResponse

    // ─── Attendance ─────────────────────────────────────────────────
    @GET("api/v1/attendance")
    suspend fun getAttendance(
        @Query("worker_id") workerId: String? = null,
        @Query("date") date: String? = null,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null,
    ): List<AttendanceResponse>

    @GET("api/v1/attendance/today")
    suspend fun getTodayAttendance(): List<AttendanceSummaryResponse>

    @GET("api/v1/attendance/summary/{workerId}")
    suspend fun getAttendanceSummary(
        @Path("workerId") workerId: String,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null,
    ): AttendanceSummaryResponse

    @POST("api/v1/attendance/check-in")
    suspend fun checkIn(@Body request: CheckInRequest): AttendanceResponse

    @POST("api/v1/attendance/check-out/{attendanceId}")
    suspend fun checkOut(
        @Path("attendanceId") attendanceId: String,
        @Body request: CheckOutRequest
    ): AttendanceResponse

    @DELETE("api/v1/attendance/{id}")
    suspend fun deleteAttendance(@Path("id") id: String): ApiSuccessResponse

    // ─── Salary Payments ────────────────────────────────────────────
    @GET("api/v1/salary-payments")
    suspend fun getSalaryPayments(
        @Query("worker_id") workerId: String? = null,
        @Query("paid") paid: Boolean? = null,
        @Query("period_type") periodType: String? = null,
    ): List<SalaryPaymentResponse>

    @POST("api/v1/salary-payments")
    suspend fun createSalaryPayment(@Body request: CreateSalaryPaymentRequest): SalaryPaymentResponse

    @PATCH("api/v1/salary-payments/{id}/pay")
    suspend fun markSalaryPaid(
        @Path("id") id: String,
        @Body request: MarkPaidRequest
    ): SalaryPaymentResponse

    @PATCH("api/v1/salary-payments/{id}/unpay")
    suspend fun markSalaryUnpaid(@Path("id") id: String): SalaryPaymentResponse

    // Tax places (manager: CRUD; cashier: list for delivery orders)
    @GET("api/v1/tax-places")
    suspend fun getTaxPlaces(): List<TaxPlaceResponse>

    @POST("api/v1/tax-places")
    suspend fun createTaxPlace(@Body request: CreateTaxPlaceRequest): TaxPlaceResponse

    @PUT("api/v1/tax-places/{id}")
    suspend fun updateTaxPlace(
        @Path("id") id: String,
        @Body request: UpdateTaxPlaceRequest
    ): TaxPlaceResponse

    @DELETE("api/v1/tax-places/{id}")
    suspend fun deleteTaxPlace(@Path("id") id: String): ApiSuccessResponse
}
