package net.marllex.waselak.backend.data.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock

// ─── Vendors ─────────────────────────────────────────────────────
object VendorsTable : UUIDTable("vendors") {
    val name = varchar("name", 255)
    val logoUrl = text("logo_url").nullable()
    val address = text("address")
    val contactPhone = varchar("contact_phone", 20)
    val walletPhone = varchar("wallet_phone", 20).nullable()
    val defaultDeliveryFee = decimal("default_delivery_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val storeType = varchar("store_type", 50).nullable()
    val enableTables = bool("enable_tables").default(true)
    val enableDineIn = bool("enable_dine_in").default(true)
    val enableDelivery = bool("enable_delivery").default(true)
    val enableTakeaway = bool("enable_takeaway").default(true)
    val enableInStore = bool("enable_in_store").default(false)
    val enablePickupLater = bool("enable_pickup_later").default(false)
    val businessType = varchar("business_type", 30).default("RESTAURANT")
    val taxEnabled = bool("tax_enabled").default(false)
    val defaultTaxPercent = decimal("default_tax_percent", 5, 2).default(java.math.BigDecimal.ZERO)
    val stockMode = varchar("stock_mode", 20).default("NONE") // NONE, WARN, ENFORCE
    val offlineModeEnabled = bool("offline_mode_enabled").default(false)
    val biometricRequired = bool("biometric_required").default(false)
    val isSuspended = bool("is_suspended").default(false)
    val suspensionReason = text("suspension_reason").nullable()
    val enableOfflineMode = bool("enable_offline_mode").default(false)
    val digitalMenuUrl = text("digital_menu_url").nullable()
    // Loyalty & discount settings
    val loyaltyEnabled = bool("loyalty_enabled").default(false)
    val pointsEarnRate = decimal("points_earn_rate", 10, 2).default(java.math.BigDecimal("1.0"))
    val pointsRedeemRate = decimal("points_redeem_rate", 10, 2).default(java.math.BigDecimal("0.1"))
    val minPointsRedeem = integer("min_points_redeem").default(100)
    val maxManualDiscountPercent = decimal("max_manual_discount_percent", 5, 2).default(java.math.BigDecimal("100.0"))
    val manualDiscountRequiresPin = bool("manual_discount_requires_pin").default(false)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Users ───────────────────────────────────────────────────────
object UsersTable : UUIDTable("users") {
    val vendorId = reference("vendor_id", VendorsTable)
    val role = varchar("role", 20)
    val name = varchar("name", 255)
    val phone = varchar("phone", 20)
    val email = varchar("email", 255).nullable()
    val passwordHash = varchar("password_hash", 255)
    val photoUrl = text("photo_url").nullable()
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, phone)
    }
}

// ─── Categories ──────────────────────────────────────────────────
object CategoriesTable : UUIDTable("categories") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 255)
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, name)
    }
}

// ─── Items ───────────────────────────────────────────────────────
object ItemsTable : UUIDTable("items") {
    val vendorId = reference("vendor_id", VendorsTable)
    val categoryId = reference("category_id", CategoriesTable)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val price = decimal("price", 10, 2)
    val costPrice = decimal("cost_price", 10, 2).nullable()
    val sku = varchar("sku", 100).nullable()
    val barcode = varchar("barcode", 100).nullable()
    val imageUrl = text("image_url").nullable()
    val available = bool("available").default(true)
    val stockBehavior = varchar("stock_behavior", 20).default("NONE") // NONE, DIRECT, RECIPE
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Item Variant Groups ────────────────────────────────────────
object ItemVariantGroupsTable : UUIDTable("item_variant_groups") {
    val itemId = reference("item_id", ItemsTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val required = bool("required").default(false)
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Item Variant Options ───────────────────────────────────────
object ItemVariantOptionsTable : UUIDTable("item_variant_options") {
    val groupId = reference("group_id", ItemVariantGroupsTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val priceAdjustment = decimal("price_adjustment", 10, 2).default(java.math.BigDecimal.ZERO)
    val isDefault = bool("is_default").default(false)
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Tables (Store Tables) ───────────────────────────────────────
object TablesTable : UUIDTable("restaurant_tables") {
    val vendorId = reference("vendor_id", VendorsTable)
    val number = varchar("number", 50)
    val capacity = integer("capacity").default(4)
    val status = varchar("status", 20).default("AVAILABLE")
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, number)
    }
}

// ─── Reservations ───────────────────────────────────────────────
object ReservationsTable : UUIDTable("reservations") {
    val vendorId = reference("vendor_id", VendorsTable)
    val tableId = reference("table_id", TablesTable)
    val clientName = varchar("client_name", 255)
    val clientPhone = varchar("client_phone", 20).nullable()
    val reservationDate = varchar("reservation_date", 10) // YYYY-MM-DD
    val reservationTime = varchar("reservation_time", 5) // HH:MM
    val numberOfGuests = integer("number_of_guests").default(1)
    val notes = text("notes").nullable()
    val status = varchar("status", 20).default("PENDING") // PENDING, CONFIRMED, CANCELLED, COMPLETED
    val orderId = reference("order_id", OrdersTable).nullable()
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Tax Places (per-vendor tax rates by place) ───────────────────
object TaxPlacesTable : UUIDTable("tax_places") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 255)
    val taxPercent = decimal("tax_percent", 5, 2)
    val isDefault = bool("is_default").default(false)
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Orders ──────────────────────────────────────────────────────
object OrdersTable : UUIDTable("orders") {
    val vendorId = reference("vendor_id", VendorsTable)
    val channel = varchar("channel", 20)
    val status = varchar("status", 30).default("CREATED")
    val tableId = reference("table_id", TablesTable).nullable()
    val cashierId = reference("cashier_id", UsersTable)
    val deliveryUserId = reference("delivery_user_id", UsersTable).nullable()
    val customerId = reference("customer_id", CustomersTable).nullable()
    val taxPlaceId = reference("tax_place_id", TaxPlacesTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val clientName = varchar("client_name", 255).nullable()
    val clientPhone = varchar("client_phone", 20).nullable()
    val clientAddress = text("client_address").nullable()
    val geoLat = double("geo_lat").nullable()
    val geoLng = double("geo_lng").nullable()
    val paymentMethod = varchar("payment_method", 20)
    val paymentStatus = varchar("payment_status", 20).default("PENDING")
    val paymentTiming = varchar("payment_timing", 20).default("PAY_NOW")
    val paymentConfirmedAt = timestamp("payment_confirmed_at").nullable()
    val paymentConfirmedBy = reference("payment_confirmed_by", UsersTable).nullable()
    val subtotal = decimal("subtotal", 10, 2)
    val deliveryFee = decimal("delivery_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val discount = decimal("discount", 10, 2).default(java.math.BigDecimal.ZERO)
    val discountType = varchar("discount_type", 20).default("FIXED") // FIXED or PERCENT
    val tax = decimal("tax", 10, 2).default(java.math.BigDecimal.ZERO)
    val taxPercent = decimal("tax_percent", 5, 2).default(java.math.BigDecimal.ZERO)
    val total = decimal("total", 10, 2)
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
    val refundedAt = timestamp("refunded_at").nullable()
    val refundedBy = reference("refunded_by", UsersTable).nullable()
    val refundReason = text("refund_reason").nullable()
    val offerId = reference("offer_id", OffersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val pointsEarned = integer("points_earned").default(0)
    val pointsRedeemed = integer("points_redeemed").default(0)
    val discountReason = varchar("discount_reason", 100).nullable()
}

// ─── Order Items ─────────────────────────────────────────────────
object OrderItemsTable : UUIDTable("order_items") {
    val orderId = reference("order_id", OrdersTable)
    val itemId = reference("item_id", ItemsTable)
    val itemNameSnapshot = varchar("item_name_snapshot", 255)
    val itemPriceSnapshot = decimal("item_price_snapshot", 10, 2)
    val quantity = integer("quantity")
    val note = text("note").nullable()
    val variantOptionsSnapshot = text("variant_options_snapshot").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Stock ───────────────────────────────────────────────────────
object StockTable : UUIDTable("stock") {
    val vendorId = reference("vendor_id", VendorsTable)
    val itemId = reference("item_id", ItemsTable).nullable() // Now nullable - can be independent stock item
    val itemName = varchar("item_name", 255)
    val quantity = decimal("quantity", 10, 3).default(java.math.BigDecimal.ZERO)
    val minQuantity = decimal("min_quantity", 10, 3).default(java.math.BigDecimal("5"))
    val costPrice = decimal("cost_price", 10, 2).default(java.math.BigDecimal.ZERO)
    val unit = varchar("unit", 50).default("PIECE") // Display/purchase unit
    val baseUnit = varchar("base_unit", 50).default("PIECE") // Canonical base unit for conversions
    val conversionRate = decimal("conversion_rate", 10, 6).default(java.math.BigDecimal.ONE) // How many base units per 1 purchase unit
    val isMenuItem = bool("is_menu_item").default(true) // true = linked to menu, false = independent stock
    val alertEnabled = bool("alert_enabled").default(true) // Enable low stock alerts
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Recipes (BOM per menu item) ───────────────────────────────
object RecipesTable : UUIDTable("recipes") {
    val vendorId = reference("vendor_id", VendorsTable)
    val itemId = reference("item_id", ItemsTable) // The sellable menu item
    val name = varchar("name", 255) // Display name (defaults to item name)
    val description = text("description").nullable()
    val yieldQuantity = decimal("yield_quantity", 10, 3).default(java.math.BigDecimal.ONE) // Servings per recipe
    val yieldUnit = varchar("yield_unit", 50).default("PIECE")
    val status = varchar("status", 20).default("ACTIVE") // DRAFT, ACTIVE, ARCHIVED
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, itemId) // One recipe per menu item per vendor
    }
}

// ─── Recipe Ingredients ────────────────────────────────────────
object RecipeIngredientsTable : UUIDTable("recipe_ingredients") {
    val recipeId = reference("recipe_id", RecipesTable, onDelete = ReferenceOption.CASCADE)
    val stockId = reference("stock_id", StockTable) // The ingredient (stock item)
    val quantity = decimal("quantity", 10, 3) // Amount of this ingredient needed per yield
    val unit = varchar("unit", 50) // Unit of measurement for this ingredient line
    val fixedQuantity = bool("fixed_quantity").default(false) // If true, quantity is NOT divided by yield
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Stock Transactions ─────────────────────────────────────────
object StockTransactionsTable : UUIDTable("stock_transactions") {
    val stockId = reference("stock_id", StockTable)
    val type = varchar("type", 30) // ADD, DEDUCT, ADJUST, PURCHASE, SALE_DIRECT, SALE_RECIPE, RETURN
    val quantity = decimal("quantity", 10, 3)
    val previousQuantity = decimal("previous_quantity", 10, 3)
    val orderId = reference("order_id", OrdersTable).nullable()
    val recipeId = reference("recipe_id", RecipesTable).nullable() // Track which recipe caused the deduction
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Workers ─────────────────────────────────────────────────────
object WorkersTable : UUIDTable("workers") {
    val vendorId = reference("vendor_id", VendorsTable)
    val userId = reference("user_id", UsersTable).nullable() // Linked user for login-enabled workers
    val workerId = varchar("worker_id", 20) // Auto-generated human-readable ID (e.g. WRK-001)
    val fullName = varchar("full_name", 255)
    val phone = varchar("phone", 20).nullable()
    val description = text("description").nullable()
    val photoUrl = text("photo_url").nullable()
    val role = varchar("role", 100) // Custom role from predefined settings
    val salaryType = varchar("salary_type", 20) // DAILY, MONTHLY
    val salaryAmount = decimal("salary_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val active = bool("active").default(true)
    val pinHash = varchar("pin_hash", 255).nullable() // bcrypt hash of worker PIN
    val pinSha256 = varchar("pin_sha256", 64).nullable() // SHA-256 hex for offline client verification
    val qrCodeData = text("qr_code_data").nullable() // JSON string with worker info
    val qrCodeVersion = integer("qr_code_version").default(1) // Increment on regeneration
    val pinUpdatedAt = timestamp("pin_updated_at").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, workerId)
    }
}

// ─── Worker Roles (Predefined in Settings) ───────────────────────
object WorkerRolesTable : UUIDTable("worker_roles") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, name)
    }
}

// ─── Attendance ──────────────────────────────────────────────────
object AttendanceTable : UUIDTable("attendance") {
    val vendorId = reference("vendor_id", VendorsTable)
    val workerId = reference("worker_id", WorkersTable)
    val date = varchar("date", 10) // YYYY-MM-DD
    val checkIn = timestamp("check_in")
    val checkOut = timestamp("check_out").nullable()
    val workedMinutes = integer("worked_minutes").nullable() // Calculated on check-out
    val recordedBy = reference("recorded_by", UsersTable) // Cashier who recorded
    val authMethod = varchar("auth_method", 20).default("MANUAL") // PIN, QR, MANUAL, BIOMETRIC (legacy)
    val authMetadata = text("auth_metadata").nullable() // JSON with additional auth info
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, workerId, date) // One attendance per worker per day
    }
}

// ─── Attendance Authentication Logs ──────────────────────────────
object AttendanceAuthLogsTable : UUIDTable("attendance_auth_logs") {
    val workerId = reference("worker_id", WorkersTable)
    val cashierId = reference("cashier_id", UsersTable)
    val authMethod = varchar("auth_method", 20) // PIN, QR, MANUAL
    val success = bool("success")
    val failureReason = varchar("failure_reason", 255).nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Salary Payments ─────────────────────────────────────────────
object SalaryPaymentsTable : UUIDTable("salary_payments") {
    val vendorId = reference("vendor_id", VendorsTable)
    val workerId = reference("worker_id", WorkersTable)
    val periodType = varchar("period_type", 20) // DAY, WEEK, MONTH
    val periodStart = varchar("period_start", 10) // YYYY-MM-DD
    val periodEnd = varchar("period_end", 10) // YYYY-MM-DD
    val workedDays = integer("worked_days")
    val workedHours = integer("worked_hours").nullable()
    val amount = decimal("amount", 10, 2)
    val overtimeHours = decimal("overtime_hours", 5, 2).default(java.math.BigDecimal.ZERO)
    val overtimeAmount = decimal("overtime_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val paid = bool("paid").default(false)
    val paidAt = timestamp("paid_at").nullable()
    val paidBy = reference("paid_by", UsersTable).nullable()
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Overtime Entries ────────────────────────────────────────────
object OvertimeTable : UUIDTable("overtime_entries") {
    val vendorId = reference("vendor_id", VendorsTable)
    val workerId = reference("worker_id", WorkersTable)
    val date = varchar("date", 10) // YYYY-MM-DD
    val hours = decimal("hours", 5, 2)
    val ratePerHour = decimal("rate_per_hour", 10, 2)
    val amount = decimal("amount", 10, 2) // hours * ratePerHour
    val note = text("note").nullable()
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Announcements ──────────────────────────────────────────────
object AnnouncementsTable : UUIDTable("announcements") {
    val vendorId = reference("vendor_id", VendorsTable)
    val senderId = reference("sender_id", UsersTable)
    val targetType = varchar("target_type", 20) // ALL, CASHIERS, DELIVERY, SPECIFIC
    val targetUserId = reference("target_user_id", UsersTable).nullable()
    val title = varchar("title", 255)
    val message = text("message")
    val priority = varchar("priority", 20).default("NORMAL") // NORMAL, URGENT
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

object AnnouncementReadsTable : UUIDTable("announcement_reads") {
    val announcementId = reference("announcement_id", AnnouncementsTable)
    val userId = reference("user_id", UsersTable)
    val readAt = timestamp("read_at").default(Clock.System.now())

    init {
        uniqueIndex(announcementId, userId)
    }
}

// ─── Activity Logs ───────────────────────────────────────────────
object ActivityLogsTable : UUIDTable("activity_logs") {
    val orderId = reference("order_id", OrdersTable)
    val userId = reference("user_id", UsersTable)
    val action = varchar("action", 50)
    val payload = text("payload").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Customers ──────────────────────────────────────────────────
object CustomersTable : UUIDTable("customers") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 255).nullable()
    val phone = varchar("phone", 20)
    val notes = text("notes").nullable()
    val orderCount = integer("order_count").default(0)
    val totalSpent = decimal("total_spent", 10, 2).default(java.math.BigDecimal.ZERO)
    val pointsBalance = integer("points_balance").default(0)
    val lastOrderAt = timestamp("last_order_at").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").nullable()

    init {
        uniqueIndex(vendorId, phone)
    }
}

// ─── Customer Addresses ─────────────────────────────────────────
object CustomerAddressesTable : UUIDTable("customer_addresses") {
    val customerId = reference("customer_id", CustomersTable, onDelete = ReferenceOption.CASCADE)
    val label = varchar("label", 255).nullable()
    val address = text("address")
    val geoLat = double("geo_lat").nullable()
    val geoLng = double("geo_lng").nullable()
    val deliveryZoneId = varchar("delivery_zone_id", 100).nullable()
    val deliveryFee = decimal("delivery_fee", 10, 2).nullable()
    val isDefault = bool("is_default").default(false)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Refresh Tokens ──────────────────────────────────────────────
object RefreshTokensTable : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", UsersTable)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Admin Users ─────────────────────────────────────────────────
object AdminUsersTable : UUIDTable("admin_users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val active = bool("active").default(true)
    val lastLoginAt = timestamp("last_login_at").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Admin Refresh Tokens ────────────────────────────────────────
object AdminRefreshTokensTable : UUIDTable("admin_refresh_tokens") {
    val adminId = reference("admin_id", AdminUsersTable)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Subscription Plans ──────────────────────────────────────────
object SubscriptionPlansTable : UUIDTable("subscription_plans") {
    val name = varchar("name", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val priceEgp = integer("price_egp")
    val billingCycle = varchar("billing_cycle", 20).default("MONTHLY")
    val maxManagers = integer("max_managers").default(1)
    val maxCashiers = integer("max_cashiers").default(1)
    val maxDelivery = integer("max_delivery").default(0)
    val maxOrdersPerMonth = integer("max_orders_per_month").default(750)
    val maxMenuItems = integer("max_menu_items").default(50)
    val maxBranches = integer("max_branches").default(1)
    val stockManagement = bool("stock_management").default(false)
    val workerAttendance = bool("worker_attendance").default(false)
    val deliveryModule = bool("delivery_module").default(false)
    val analytics = varchar("analytics", 20).default("NONE")
    val digitalMenu = varchar("digital_menu", 20).default("NONE")
    val overtime = bool("overtime").default(false)
    val salaries = bool("salaries").default(false)
    val customerManagement = bool("customer_management").default(false)
    val tableManagement = bool("table_management").default(false)
    val digitalReceipt = bool("digital_receipt").default(false)
    val workerQrcode = bool("worker_qrcode").default(false)
    val loyaltyPoints = bool("loyalty_points").default(false)
    val manualDiscount = bool("manual_discount").default(false)
    val offersManagement = bool("offers_management").default(false)
    val active = bool("active").default(true)
    val displayOrder = integer("display_order").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Vendor Subscriptions ────────────────────────────────────────
object VendorSubscriptionsTable : UUIDTable("vendor_subscriptions") {
    val vendorId = uuid("vendor_id").uniqueIndex()
    val planId = reference("plan_id", SubscriptionPlansTable)
    val status = varchar("status", 20).default("ACTIVE")
    val startedAt = timestamp("started_at").default(Clock.System.now())
    val expiresAt = timestamp("expires_at").nullable()
    val notes = text("notes").nullable()
    val overrideMaxManagers = integer("override_max_managers").nullable()
    val overrideMaxCashiers = integer("override_max_cashiers").nullable()
    val overrideMaxDelivery = integer("override_max_delivery").nullable()
    val overrideMaxOrders = integer("override_max_orders").nullable()
    val overrideMaxItems = integer("override_max_items").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Offers ─────────────────────────────────────────────────────
object OffersTable : UUIDTable("offers") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val imageUrl = text("image_url").nullable()
    val discountType = varchar("discount_type", 20) // FIXED_PRICE or PERCENT
    val discountValue = decimal("discount_value", 10, 2)
    val active = bool("active").default(true)
    val expiresAt = long("expires_at").nullable()
    val displayOrder = integer("display_order").default(0)
    val promoCode = varchar("promo_code", 50).nullable()
    val maxUses = integer("max_uses").nullable()
    val usedCount = integer("used_count").default(0)
    val startsAt = long("starts_at").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Offer Items ────────────────────────────────────────────────
object OfferItemsTable : UUIDTable("offer_items") {
    val offerId = reference("offer_id", OffersTable, onDelete = ReferenceOption.CASCADE)
    val itemId = reference("item_id", ItemsTable)
    val quantity = integer("quantity").default(1)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Points Transactions ────────────────────────────────────────
object PointsTransactionsTable : UUIDTable("points_transactions") {
    val customerId = reference("customer_id", CustomersTable)
    val vendorId = reference("vendor_id", VendorsTable)
    val orderId = reference("order_id", OrdersTable).nullable()
    val type = varchar("type", 20) // EARN, REDEEM
    val points = integer("points")
    val description = text("description").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Request Logs ────────────────────────────────────────────────
object RequestLogsTable : UUIDTable("request_logs") {
    val vendorId = varchar("vendor_id", 36).nullable().index()
    val userId = varchar("user_id", 36).nullable().index()
    val userRole = varchar("user_role", 50).nullable()
    val method = varchar("method", 10)
    val path = varchar("path", 1024).index()
    val queryParams = text("query_params").nullable()
    val statusCode = integer("status_code").index()
    val durationMs = long("duration_ms")
    val clientIp = varchar("client_ip", 100).nullable()
    val userAgent = text("user_agent").nullable()
    val requestBody = text("request_body").nullable()
    val responseBody = text("response_body").nullable()
    val errorMessage = text("error_message").nullable()
    val resource = varchar("resource", 100).nullable().index()   // e.g. "orders", "items", "workers"
    val action = varchar("action", 100).nullable().index()       // e.g. "list", "create", "update", "delete"
    val tags = text("tags").nullable()                            // JSON map of extracted attributes
    val description = text("description").nullable()             // Human-readable summary of what the API call did
    val traceLog = text("trace_log").nullable()                  // JSON array of step-by-step trace entries from RouteTrace
    val createdAt = timestamp("created_at").default(Clock.System.now()).index()
}
