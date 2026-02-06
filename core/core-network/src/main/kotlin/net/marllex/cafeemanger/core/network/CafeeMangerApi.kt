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
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): AnalyticsSummaryResponse

    @GET("api/v1/analytics/settlements")
    suspend fun getSettlements(
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): SettlementsResponse

    @GET("api/v1/analytics/delivery-performance")
    suspend fun getDeliveryPerformance(
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null
    ): List<DeliveryPerformanceResponse>

    @GET("api/v1/analytics/daily")
    suspend fun getDailyAnalytics(
        @Query("from") from: Long,
        @Query("to") to: Long
    ): List<DailyAnalyticsResponse>
}
