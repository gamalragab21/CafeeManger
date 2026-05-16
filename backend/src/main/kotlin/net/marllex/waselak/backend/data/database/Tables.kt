package net.marllex.waselak.backend.data.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
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
    val enableKds = bool("enable_kds").default(true)
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
    val enableDigitalMenu = bool("enable_digital_menu").default(true)
    val enableRecipe = bool("enable_recipe").default(true)
    // Feature toggles (vendor-level ON/OFF — must also pass PlanService check)
    val enableSplitPayment = bool("enable_split_payment").default(true)
    val enableCashDrawer = bool("enable_cash_drawer").default(true)
    val enableReturns = bool("enable_returns").default(true)
    val enableCustomerCredit = bool("enable_customer_credit").default(false)
    val enablePreOrders = bool("enable_pre_orders").default(false)
    val enableScheduledOrders = bool("enable_scheduled_orders").default(false)
    val enableSuppliers = bool("enable_suppliers").default(true)
    val enableDrugInteractions = bool("enable_drug_interactions").default(false)
    val enablePrescriptions = bool("enable_prescriptions").default(false)
    val enableAnalytics = bool("enable_analytics").default(true)
    val enableAnnouncements = bool("enable_announcements").default(true)
    val enableInstallments = bool("enable_installments").default(false)
    val enableStock = bool("enable_stock").default(true)
    val enableAttendance = bool("enable_attendance").default(true)
    val enableOvertime = bool("enable_overtime").default(false)
    val enableSalary = bool("enable_salary").default(false)
    val enableCustomers = bool("enable_customers").default(true)
    val enableExport = bool("enable_export").default(true)
    val enableDigitalReceipt = bool("enable_digital_receipt").default(false)
    val enableWhatsappReceipt = bool("enable_whatsapp_receipt").default(false)
    val enableWorkerQrcode = bool("enable_worker_qrcode").default(false)
    val enableLoyalty = bool("enable_loyalty").default(false)
    val enableManualDiscount = bool("enable_manual_discount").default(true)
    val enableOffers = bool("enable_offers").default(true)
    // Loyalty & discount settings
    val loyaltyEnabled = bool("loyalty_enabled").default(false)
    val pointsEarnRate = decimal("points_earn_rate", 10, 2).default(java.math.BigDecimal("1.0"))
    val pointsRedeemRate = decimal("points_redeem_rate", 10, 2).default(java.math.BigDecimal("0.1"))
    val minPointsRedeem = integer("min_points_redeem").default(100)
    val maxManualDiscountPercent = decimal("max_manual_discount_percent", 5, 2).default(java.math.BigDecimal("100.0"))
    val manualDiscountRequiresPin = bool("manual_discount_requires_pin").default(false)
    // Social links
    val facebookUrl = varchar("facebook_url", 500).nullable()
    val landingPageUrl = varchar("landing_page_url", 500).nullable()
    val instagramUrl = varchar("instagram_url", 500).nullable()
    val whatsappNumber = varchar("whatsapp_number", 50).nullable()
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
    // Manager POS-override PIN. Used by cashier terminals to authorise discounts:
    // the cashier asks a manager for their 4-6 digit PIN, we verify it here and issue
    // a short-lived token that gets attached to the next create-order request. Nullable
    // because only managers set it; non-managers never need one.
    val overridePinHash = varchar("override_pin_hash", 255).nullable()
    val overridePinSetAt = timestamp("override_pin_set_at").nullable()
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
    // Per-vendor per-day order counter. Reset each calendar day at midnight
    // in the vendor's local timezone — the merchant uses this as the
    // human-friendly "Order #1, #2, #3…" identifier that appears on the
    // POS screen, orders list, and printed receipt. The UUID `id` column
    // remains the canonical opaque identifier; `dailySeq` is purely a
    // display aid that's much easier to call out to a customer.
    val dailySeq = integer("daily_seq").default(0)
    val dailySeqDate = varchar("daily_seq_date", 10).default("") // YYYY-MM-DD
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
    // Records which manager authorised the discount on this order (POS override flow).
    // Null when the order has no discount, or when the cashier themselves is a manager.
    val discountApprovedBy = reference("discount_approved_by", UsersTable).nullable()
    val doctorName = varchar("doctor_name", 255).nullable()
    val diagnosis = varchar("diagnosis", 500).nullable()
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
    val kitchenStatus = varchar("kitchen_status", 20).default("PENDING") // PENDING, COOKING, READY, SERVED
    val kitchenStation = varchar("kitchen_station", 50).nullable()       // e.g., GRILL, DRINKS, DESSERT
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Order Payments (Split Payments) ────────────────────────────
object OrderPaymentsTable : UUIDTable("order_payments") {
    val orderId = reference("order_id", OrdersTable)
    val vendorId = reference("vendor_id", VendorsTable)
    val paymentMethod = varchar("payment_method", 30)       // CASH, CARD, WALLET, BANK_TRANSFER
    val amount = decimal("amount", 10, 2)
    val paidBy = reference("paid_by", UsersTable).nullable()
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Cash Drawer Sessions ───────────────────────────────────────
object CashDrawerSessionsTable : UUIDTable("cash_drawer_sessions") {
    val vendorId = reference("vendor_id", VendorsTable)
    val cashierId = reference("cashier_id", UsersTable)
    val openedAt = timestamp("opened_at").default(Clock.System.now())
    val closedAt = timestamp("closed_at").nullable()
    val openingBalance = decimal("opening_balance", 10, 2).default(java.math.BigDecimal.ZERO)
    val closingBalance = decimal("closing_balance", 10, 2).nullable()
    val expectedBalance = decimal("expected_balance", 10, 2).nullable()
    val difference = decimal("difference", 10, 2).nullable()  // closingBalance - expectedBalance
    val status = varchar("status", 20).default("OPEN")        // OPEN, CLOSED
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Cash Drawer Movements ──────────────────────────────────────
object CashMovementsTable : UUIDTable("cash_movements") {
    val sessionId = reference("session_id", CashDrawerSessionsTable)
    val vendorId = reference("vendor_id", VendorsTable)
    val type = varchar("type", 20)                           // CASH_IN, CASH_OUT, SALE, REFUND, ADJUSTMENT
    val amount = decimal("amount", 10, 2)
    val reason = text("reason").nullable()
    val orderId = reference("order_id", OrdersTable).nullable()
    val createdBy = reference("created_by", UsersTable)
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
    val batchId = reference("batch_id", StockBatchesTable).nullable() // Track which batch was affected
    val type = varchar("type", 30) // ADD, DEDUCT, ADJUST, PURCHASE, SALE_DIRECT, SALE_RECIPE, RETURN, BATCH_DEDUCT
    val quantity = decimal("quantity", 10, 3)
    val previousQuantity = decimal("previous_quantity", 10, 3)
    val orderId = reference("order_id", OrdersTable).nullable()
    val recipeId = reference("recipe_id", RecipesTable).nullable() // Track which recipe caused the deduction
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Stock Batches (Lot/Expiry Tracking) ────────────────────────
object StockBatchesTable : UUIDTable("stock_batches") {
    val stockId = reference("stock_id", StockTable, onDelete = ReferenceOption.CASCADE)
    val vendorId = reference("vendor_id", VendorsTable)
    val batchNumber = varchar("batch_number", 100).nullable()   // Supplier lot number
    val quantity = decimal("quantity", 10, 3)                    // Remaining quantity in this batch
    val initialQuantity = decimal("initial_quantity", 10, 3)    // Original quantity when received
    val costPrice = decimal("cost_price", 10, 2).default(java.math.BigDecimal.ZERO) // Cost per unit for this batch
    val expiryDate = date("expiry_date").nullable()             // Null = no expiry
    val receivedAt = timestamp("received_at").default(Clock.System.now()) // When batch was received
    val status = varchar("status", 20).default("ACTIVE")        // ACTIVE, DEPLETED, EXPIRED, DISPOSED
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
    val paid = bool("paid").default(false)
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
    val role = varchar("role", 30).default("super_admin") // super_admin, support
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
    val cashDrawer = bool("cash_drawer").default(false)
    val splitPayment = bool("split_payment").default(false)
    val customerCredit = bool("customer_credit").default(false)
    val installments = bool("installments").default(false)
    val suppliers = bool("suppliers").default(false)
    val returns = bool("returns").default(false)
    val prescriptions = bool("prescriptions").default(false)
    val drugInteractions = bool("drug_interactions").default(false)
    val scheduledOrders = bool("scheduled_orders").default(false)
    val kds = bool("kds").default(false)
    val notifications = bool("notifications").default(true)
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

// ─── Product Returns ────────────────────────────────────────────
object ProductReturnsTable : UUIDTable("product_returns") {
    val vendorId = reference("vendor_id", VendorsTable)
    val orderId = reference("order_id", OrdersTable)
    val customerId = reference("customer_id", CustomersTable).nullable()
    val returnType = varchar("return_type", 20).default("RETURN")  // RETURN, EXCHANGE
    val status = varchar("status", 20).default("PENDING")          // PENDING, APPROVED, REJECTED, COMPLETED
    val reason = text("reason")
    val refundAmount = decimal("refund_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val refundMethod = varchar("refund_method", 30).nullable()     // CASH, CARD, CREDIT, ORIGINAL_METHOD
    val processedBy = reference("processed_by", UsersTable).nullable()
    val processedAt = timestamp("processed_at").nullable()
    val notes = text("notes").nullable()
    // Exchange: replacement item
    val exchangeItemId = reference("exchange_item_id", ItemsTable).nullable()
    val exchangeQuantity = integer("exchange_quantity").default(0)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Return Items (line items within a return) ──────────────────
object ReturnItemsTable : UUIDTable("return_items") {
    val returnId = reference("return_id", ProductReturnsTable, onDelete = ReferenceOption.CASCADE)
    val orderItemId = reference("order_item_id", OrderItemsTable)
    val itemId = reference("item_id", ItemsTable)
    val quantity = integer("quantity")                              // How many units returned
    val reason = varchar("reason", 100).nullable()                 // DEFECTIVE, WRONG_ITEM, CHANGED_MIND, EXPIRED, OTHER
    val condition = varchar("item_condition", 30).default("GOOD")  // GOOD (restock), DAMAGED, EXPIRED (no restock)
    val restockable = bool("restockable").default(true)            // Whether to add back to inventory
    val refundAmount = decimal("refund_amount", 10, 2)             // Per-item refund
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Prescriptions ─────────────────────────────────────────────
object PrescriptionsTable : UUIDTable("prescriptions") {
    val vendorId = reference("vendor_id", VendorsTable)
    val customerId = reference("customer_id", CustomersTable).nullable()
    val orderId = reference("order_id", OrdersTable).nullable()       // Linked order once dispensed
    val doctorName = varchar("doctor_name", 255).nullable()
    val doctorPhone = varchar("doctor_phone", 20).nullable()
    val patientName = varchar("patient_name", 255)
    val patientPhone = varchar("patient_phone", 20).nullable()
    val patientAge = integer("patient_age").nullable()
    val diagnosis = text("diagnosis").nullable()
    val notes = text("notes").nullable()
    val imageUrl = text("image_url").nullable()                       // Photo of paper prescription
    val status = varchar("status", 20).default("PENDING")             // PENDING, DISPENSED, PARTIALLY_DISPENSED, CANCELLED, EXPIRED
    val expiresAt = timestamp("expires_at").nullable()                 // Prescription validity expiry
    val dispensedAt = timestamp("dispensed_at").nullable()
    val dispensedBy = reference("dispensed_by", UsersTable).nullable()
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Prescription Items (Medicines in a prescription) ──────────
object PrescriptionItemsTable : UUIDTable("prescription_items") {
    val prescriptionId = reference("prescription_id", PrescriptionsTable, onDelete = ReferenceOption.CASCADE)
    val itemId = reference("item_id", ItemsTable)                     // Medicine/product
    val quantity = integer("quantity")
    val dosage = varchar("dosage", 255).nullable()                    // e.g. "500mg", "1 tablet"
    val frequency = varchar("frequency", 255).nullable()              // e.g. "3 times daily", "every 8 hours"
    val duration = varchar("duration", 255).nullable()                // e.g. "7 days", "2 weeks"
    val instructions = text("instructions").nullable()                // Special instructions
    val dispensedQuantity = integer("dispensed_quantity").default(0)   // How many actually given
    val status = varchar("status", 20).default("PENDING")             // PENDING, DISPENSED, UNAVAILABLE, SUBSTITUTED
    val substituteItemId = reference("substitute_item_id", ItemsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Drug Interactions ─────────────────────────────────────────
object DrugInteractionsTable : UUIDTable("drug_interactions") {
    val vendorId = reference("vendor_id", VendorsTable)
    val itemIdA = reference("item_id_a", ItemsTable)                  // First drug
    val itemIdB = reference("item_id_b", ItemsTable)                  // Second drug
    val severity = varchar("severity", 20).default("MODERATE")        // MILD, MODERATE, SEVERE, CONTRAINDICATED
    val description = text("description")                              // What happens with this interaction
    val descriptionAr = text("description_ar").nullable()             // Arabic description
    val recommendation = text("recommendation").nullable()             // What to do instead
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, itemIdA, itemIdB)
    }
}

// ─── Customer Credit ──────────────────────────────────────────
object CustomerCreditsTable : UUIDTable("customer_credits") {
    val vendorId = reference("vendor_id", VendorsTable)
    val customerId = reference("customer_id", CustomersTable)
    val balance = decimal("balance", 10, 2).default(java.math.BigDecimal.ZERO) // Current credit balance
    val creditLimit = decimal("credit_limit", 10, 2).default(java.math.BigDecimal("500.00"))
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, customerId)
    }
}

// ─── Credit Transactions ──────────────────────────────────────
object CreditTransactionsTable : UUIDTable("credit_transactions") {
    val creditId = reference("credit_id", CustomerCreditsTable)
    val vendorId = reference("vendor_id", VendorsTable)
    val orderId = reference("order_id", OrdersTable).nullable()
    val type = varchar("type", 20)                                     // CHARGE, PAYMENT, ADJUSTMENT
    val amount = decimal("amount", 10, 2)
    val previousBalance = decimal("previous_balance", 10, 2)
    val newBalance = decimal("new_balance", 10, 2)
    val note = text("note").nullable()
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Pre-Orders / Scheduled Orders ────────────────────────────
object ScheduledOrdersTable : UUIDTable("scheduled_orders") {
    val vendorId = reference("vendor_id", VendorsTable)
    val customerId = reference("customer_id", CustomersTable).nullable()
    val clientName = varchar("client_name", 255).nullable()
    val clientPhone = varchar("client_phone", 20).nullable()
    val channel = varchar("channel", 20).default("PICKUP_LATER")    // PICKUP_LATER, DELIVERY, DINE_IN
    val scheduledFor = timestamp("scheduled_for")                     // When the order should be ready
    val reminderSentAt = timestamp("reminder_sent_at").nullable()    // When reminder was sent
    val status = varchar("status", 20).default("SCHEDULED")          // SCHEDULED, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED
    val notes = text("notes").nullable()
    val paymentMethod = varchar("payment_method", 20).default("CASH")
    val paymentStatus = varchar("payment_status", 20).default("PENDING")  // PENDING, PAID, REFUNDED
    val subtotal = decimal("subtotal", 10, 2).default(java.math.BigDecimal.ZERO)
    val total = decimal("total", 10, 2).default(java.math.BigDecimal.ZERO)
    val discount = decimal("discount", 10, 2).default(java.math.BigDecimal.ZERO)
    val tax = decimal("tax", 10, 2).default(java.math.BigDecimal.ZERO)
    val deliveryFee = decimal("delivery_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val orderId = reference("order_id", OrdersTable).nullable()      // Linked actual order when converted
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Scheduled Order Items ────────────────────────────────────
object ScheduledOrderItemsTable : UUIDTable("scheduled_order_items") {
    val scheduledOrderId = reference("scheduled_order_id", ScheduledOrdersTable, onDelete = ReferenceOption.CASCADE)
    val itemId = reference("item_id", ItemsTable)
    val itemName = varchar("item_name", 255)
    val itemPrice = decimal("item_price", 10, 2)
    val quantity = integer("quantity")
    val note = text("note").nullable()
    val variantOptions = text("variant_options").nullable()          // JSON snapshot of selected variants
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Suppliers ────────────────────────────────────────────────
object SuppliersTable : UUIDTable("suppliers") {
    val vendorId = reference("vendor_id", VendorsTable)
    val name = varchar("name", 255)
    val contactName = varchar("contact_name", 255).nullable()
    val phone = varchar("phone", 20).nullable()
    val email = varchar("email", 255).nullable()
    val address = text("address").nullable()
    val notes = text("notes").nullable()
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(vendorId, name)
    }
}

// ─── Purchase Orders ──────────────────────────────────────────
object PurchaseOrdersTable : UUIDTable("purchase_orders") {
    val vendorId = reference("vendor_id", VendorsTable)
    val supplierId = reference("supplier_id", SuppliersTable)
    val orderNumber = varchar("order_number", 50)                    // Human-readable PO number
    val status = varchar("status", 20).default("DRAFT")              // DRAFT, SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    val notes = text("notes").nullable()
    val subtotal = decimal("subtotal", 10, 2).default(java.math.BigDecimal.ZERO)
    val tax = decimal("tax", 10, 2).default(java.math.BigDecimal.ZERO)
    val total = decimal("total", 10, 2).default(java.math.BigDecimal.ZERO)
    val expectedDeliveryDate = date("expected_delivery_date").nullable()
    val receivedAt = timestamp("received_at").nullable()
    val createdBy = reference("created_by", UsersTable)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Purchase Order Items ─────────────────────────────────────
object PurchaseOrderItemsTable : UUIDTable("purchase_order_items") {
    val purchaseOrderId = reference("purchase_order_id", PurchaseOrdersTable, onDelete = ReferenceOption.CASCADE)
    val stockId = reference("stock_id", StockTable)                  // Which stock item to receive
    val requestedQuantity = decimal("requested_quantity", 10, 3)
    val receivedQuantity = decimal("received_quantity", 10, 3).default(java.math.BigDecimal.ZERO)
    val unitCost = decimal("unit_cost", 10, 2).default(java.math.BigDecimal.ZERO)
    val totalCost = decimal("total_cost", 10, 2).default(java.math.BigDecimal.ZERO)
    val unit = varchar("unit", 50).default("PIECE")
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Notifications ─────────────────────────────────────────────
object NotificationsTable : UUIDTable("notifications") {
    val vendorId = reference("vendor_id", VendorsTable)
    val userId = reference("user_id", UsersTable).nullable()          // Specific user, or null for broadcast
    val type = varchar("type", 50)                                     // ORDER_NEW, ORDER_STATUS, LOW_STOCK, EXPIRY_ALERT, SCHEDULED_ORDER, PRESCRIPTION, ANNOUNCEMENT, SYSTEM
    val title = varchar("title", 500)
    val body = text("body")
    val data = text("data").nullable()                                 // JSON payload with context (orderId, itemId, etc.)
    val channel = varchar("channel", 20).default("IN_APP")             // IN_APP, PUSH, BOTH
    val priority = varchar("priority", 20).default("NORMAL")           // LOW, NORMAL, HIGH, URGENT
    val read = bool("read").default(false)
    val readAt = timestamp("read_at").nullable()
    val actionUrl = text("action_url").nullable()                      // Deep link path (e.g., "/orders/{id}")
    val platform = varchar("platform", 20).nullable()                    // null=all, ANDROID, DESKTOP, IOS
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Device Tokens (for push notifications) ────────────────────
object DeviceTokensTable : UUIDTable("device_tokens") {
    val userId = reference("user_id", UsersTable)
    val vendorId = reference("vendor_id", VendorsTable)
    val token = text("token")                                          // FCM/APNs token
    val platform = varchar("platform", 20)                             // ANDROID, IOS, WEB
    val deviceName = varchar("device_name", 255).nullable()
    val active = bool("active").default(true)
    val lastUsedAt = timestamp("last_used_at").default(Clock.System.now())
    val createdAt = timestamp("created_at").default(Clock.System.now())

    init {
        uniqueIndex(userId, token)
    }
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

// ─── App Releases (version management for auto-update) ──────────
object AppReleasesTable : UUIDTable("app_releases") {
    val versionName = varchar("version_name", 20)       // "1.2.0"
    val versionCode = integer("version_code")            // 3
    val updateStatus = varchar("update_status", 20).default("OPTIONAL") // OPTIONAL, MANDATORY
    val releaseNotes = text("release_notes").nullable()
    val releaseNotesAr = text("release_notes_ar").nullable()
    val minVersionCode = integer("min_version_code").default(1) // Below this = forced update
    val driveFolderId = varchar("drive_folder_id", 100).nullable()
    val releasedDate = varchar("released_date", 6).nullable() // DDMMYY format for download URL
    val isActive = bool("is_active").default(true)
    val releasedAt = timestamp("released_at").default(Clock.System.now())
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Installment Plans ─────────────────────────────────────────────
object InstallmentPlansTable : UUIDTable("installment_plans") {
    val vendorId = reference("vendor_id", VendorsTable)
    val customerId = reference("customer_id", CustomersTable)
    val orderId = optReference("order_id", OrdersTable, onDelete = ReferenceOption.SET_NULL)
    val totalAmount = decimal("total_amount", 10, 2)
    val downPayment = decimal("down_payment", 10, 2).default(java.math.BigDecimal.ZERO)
    val remainingAmount = decimal("remaining_amount", 10, 2)
    val numInstallments = integer("num_installments")
    val installmentAmount = decimal("installment_amount", 10, 2)
    val lateFeePercent = decimal("late_fee_percent", 5, 2).default(java.math.BigDecimal.ZERO)
    val status = varchar("status", 20).default("ACTIVE")
    val startDate = long("start_date")
    val createdBy = reference("created_by", UsersTable)
    val createdAt = long("created_at").clientDefault { Clock.System.now().toEpochMilliseconds() }
    val updatedAt = long("updated_at").clientDefault { Clock.System.now().toEpochMilliseconds() }
}

object InstallmentPaymentsTable : UUIDTable("installment_payments") {
    val planId = reference("plan_id", InstallmentPlansTable, onDelete = ReferenceOption.CASCADE)
    val vendorId = reference("vendor_id", VendorsTable)
    val dueDate = long("due_date")
    val amount = decimal("amount", 10, 2)
    val paidAmount = decimal("paid_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val lateFee = decimal("late_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val status = varchar("status", 20).default("PENDING")
    val paidAt = long("paid_at").nullable()
    val paidBy = optReference("paid_by", UsersTable, onDelete = ReferenceOption.SET_NULL)
    val note = text("note").nullable()
    val lateFeeEnabled = bool("late_fee_enabled").default(true)
    val createdAt = long("created_at").clientDefault { Clock.System.now().toEpochMilliseconds() }
}

// ─── App Settings (Global, singleton row) ────────────────────────
object AppSettingsTable : UUIDTable("app_settings") {
    val facebookUrl = text("facebook_url").nullable()
    val landingPageUrl = text("landing_page_url").nullable()
    val instagramUrl = text("instagram_url").nullable()
    val whatsappNumber = varchar("whatsapp_number", 20).nullable()
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Organizations (multi-tenant root) ─────────────────────
// One row per CRM customer. The Waselak team's own data lives under a single seeded
// "Waselak" organization; sold/licensed copies of the CRM each get their own row, and
// every CRM record (agents, clients, activities, invoices …) is scoped to it via
// `organization_id`. Deleting an organization is intentionally not exposed via API —
// soft-delete with `active = false` to preserve history.
object CrmOrganizationsTable : UUIDTable("crm_organizations") {
    val name = varchar("name", 255)
    val contactEmail = varchar("contact_email", 255).nullable()
    val contactPhone = varchar("contact_phone", 30).nullable()
    /** Free-text plan tier for our own bookkeeping ("internal", "starter", "pro", …). */
    val planTier = varchar("plan_tier", 50).default("internal")
    val active = bool("active").default(true)
    // Optional logo URL. Uploaded via the super-admin Edit modal — stored in
    // uploads/crm-photos/ and served from the same /uploads/ static route used
    // for agent photos. Shown next to the org name in the super-admin table and
    // (eventually) on the tenant's own login page.
    // (Use line comments rather than KDoc here — the path contains a `/` followed
    // by `*` which Kotlin parses as a nested block-comment opener inside KDoc and
    // would silently swallow the rest of the file.)
    val logoUrl = text("logo_url").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Sales Agents ─────────────────────────────────────────
object SalesAgentsTable : UUIDTable("sales_agents") {
    val name = varchar("name", 255)
    // NOTE: this used to be globally unique. With multi-tenancy, two organisations
    // legitimately want different `owner@example.com` agents. Exposed migrations
    // can't easily drop a unique index, so we keep the column itself non-unique here
    // and rely on the lookup-by-email login flow scoping by org once we know which
    // org the request is for. For Waselak's own data the index is already in place
    // and harmless. NEW orgs are only created via the super-admin endpoint which
    // generates a unique email per agent (the operator picks one).
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50) // owner, مدير مبيعات, مندوب مبيعات, كول سنتر
    val photoUrl = text("photo_url").nullable()
    // Optional phone — captured at provisioning time so the super-admin who
    // sells the CRM has a contact channel for the customer's primary owner
    // without needing to dig through the customer's own clients table. Org
    // owners can later edit their own from the profile page. Nullable for
    // backwards-compat with rows created before v1.10.
    val phone = varchar("phone", 30).nullable()
    val active = bool("active").default(true)
    // Multi-tenant scope. Nullable while the migration backfills, but every row in
    // production carries a value pointing at CrmOrganizationsTable. New rows always
    // receive `principal.organizationId`. NULL is safe to read but should never be
    // returned to a CRM session — service queries filter on it.
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Clients ───────────────────────────────────────────────
object CrmClientsTable : UUIDTable("crm_clients") {
    val clientName = varchar("client_name", 255)
    val phone = varchar("phone", 20)
    val whatsapp = bool("whatsapp").default(false)
    val businessName = varchar("business_name", 255).nullable()
    val businessType = varchar("business_type", 100).nullable()
    val city = varchar("city", 100).nullable()
    val governorate = varchar("governorate", 100).nullable()
    val status = varchar("status", 50).default("عميل جديد")
    val plan = varchar("plan", 100).nullable()
    val monthlyAmount = decimal("monthly_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val discountPercent = integer("discount_percent").default(0)
    val paymentMethod = varchar("payment_method", 50).nullable()
    val assignedTo = reference("assigned_to", SalesAgentsTable).nullable()
    val leadSource = varchar("source", 50).nullable()
    val notes = text("notes").nullable()
    val firstContactAt = timestamp("first_contact_at").nullable()
    val lastContactAt = timestamp("last_contact_at").nullable()
    val nextActionDate = date("next_action_date").nullable()
    val interactionCount = integer("interaction_count").default(0)
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Activities ────────────────────────────────────────────
object CrmActivitiesTable : UUIDTable("crm_activities") {
    val agentId = reference("agent_id", SalesAgentsTable)
    val clientId = reference("client_id", CrmClientsTable)
    val actionType = varchar("action_type", 50).nullable()
    val channel = varchar("channel", 50).nullable()
    val previousStatus = varchar("previous_status", 50).nullable()
    val newStatus = varchar("new_status", 50).nullable()
    val planOffered = varchar("plan_offered", 100).nullable()
    val amount = decimal("amount", 10, 2).nullable()
    val discountPercent = integer("discount_percent").nullable()
    val callDuration = varchar("call_duration", 50).nullable()
    val result = varchar("result", 100).nullable()
    val nextStep = text("next_step").nullable()
    val nextDate = date("next_date").nullable()
    val notes = text("notes").nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── CRM: Invoices / Billing ────────────────────────────────────
object CrmInvoicesTable : UUIDTable("crm_invoices") {
    val clientId = reference("client_id", CrmClientsTable)
    val invoiceNumber = varchar("invoice_number", 50) // INV-2026-001
    val plan = varchar("plan", 100)
    val period = varchar("period", 50) // أبريل 2026, مايو 2026
    val amount = decimal("amount", 10, 2) // المبلغ الأصلي
    val discountPercent = integer("discount_percent").default(0)
    val finalAmount = decimal("final_amount", 10, 2) // المبلغ بعد الخصم
    val paidAmount = decimal("paid_amount", 10, 2).default(java.math.BigDecimal.ZERO)
    val status = varchar("status", 30).default("غير مدفوع") // غير مدفوع, مدفوع جزئي, مدفوع, متأخر, ملغي
    val dueDate = date("due_date")
    val paidDate = date("paid_date").nullable()
    val paymentMethod = varchar("payment_method", 50).nullable()
    val notes = text("notes").nullable()
    val createdBy = reference("created_by", SalesAgentsTable).nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Payment History ───────────────────────────────────────
object CrmPaymentsTable : UUIDTable("crm_payments") {
    val invoiceId = reference("invoice_id", CrmInvoicesTable)
    val clientId = reference("client_id", CrmClientsTable)
    val amount = decimal("amount", 10, 2)
    val paymentMethod = varchar("payment_method", 50)
    val notes = text("notes").nullable()
    val receivedBy = reference("received_by", SalesAgentsTable).nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── CRM: Agent Targets (monthly goals) ─────────────────────────
object CrmAgentTargetsTable : UUIDTable("crm_agent_targets") {
    val agentId = reference("agent_id", SalesAgentsTable)
    val month = varchar("month", 20) // "2026-04"
    val targetClients = integer("target_clients").default(0)
    val targetSubscriptions = integer("target_subscriptions").default(0)
    val targetRevenue = decimal("target_revenue", 10, 2).default(java.math.BigDecimal.ZERO)
    val notes = text("notes").nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())

    init {
        uniqueIndex(agentId, month)
    }
}

// ─── CRM: Agent Reviews (monthly score + report) ────────────────
object CrmAgentReviewsTable : UUIDTable("crm_agent_reviews") {
    val agentId = reference("agent_id", SalesAgentsTable)
    val month = varchar("month", 20) // "2026-04"
    val score = integer("score") // 1-10
    val review = text("review")
    val pinned = bool("pinned").default(false)
    val createdBy = reference("created_by", SalesAgentsTable)
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Salary Config (per agent) ────────────────────────────
object CrmSalaryConfigsTable : UUIDTable("crm_salary_configs") {
    val agentId = reference("agent_id", SalesAgentsTable).uniqueIndex()
    val baseSalary = decimal("base_salary", 10, 2).default(java.math.BigDecimal.ZERO)
    val commissionPercent = decimal("commission_percent", 5, 2).default(java.math.BigDecimal.ZERO)
    val commissionType = varchar("commission_type", 30).default("NONE") // NONE, FIRST_ONLY, FIXED_MONTHS, FOREVER
    val commissionMonths = integer("commission_months").default(0)
    val commissionBase = varchar("commission_base", 20).default("FINAL") // FINAL, ORIGINAL
    val notes = text("notes").nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── CRM: Salary Records (monthly) ─────────────────────────────
object CrmSalaryRecordsTable : UUIDTable("crm_salary_records") {
    val agentId = reference("agent_id", SalesAgentsTable)
    val month = varchar("month", 20) // "2026-04"
    val baseSalary = decimal("base_salary", 10, 2)
    val commissionTotal = decimal("commission_total", 10, 2).default(java.math.BigDecimal.ZERO)
    val bonus = decimal("bonus", 10, 2).default(java.math.BigDecimal.ZERO)
    val deductions = decimal("deductions", 10, 2).default(java.math.BigDecimal.ZERO)
    val deductionReason = text("deduction_reason").nullable()
    val bonusReason = text("bonus_reason").nullable()
    val finalSalary = decimal("final_salary", 10, 2)
    val status = varchar("status", 20).default("معلق") // معلق, مدفوع
    val paidDate = date("paid_date").nullable()
    val notes = text("notes").nullable()
    val createdBy = reference("created_by", SalesAgentsTable).nullable()
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(agentId, month)
    }
}

// ─── CRM: Commission Details (per client per month) ─────────────
object CrmCommissionDetailsTable : UUIDTable("crm_commission_details") {
    val salaryRecordId = reference("salary_record_id", CrmSalaryRecordsTable)
    val agentId = reference("agent_id", SalesAgentsTable)
    val clientId = reference("client_id", CrmClientsTable)
    val clientName = varchar("client_name", 255)
    val plan = varchar("plan", 100).nullable()
    val clientAmount = decimal("client_amount", 10, 2)
    val commissionPercent = decimal("commission_percent", 5, 2)
    val commissionAmount = decimal("commission_amount", 10, 2)
    val commissionType = varchar("commission_type", 30)
    val monthNumber = integer("month_number") // الشهر رقم كام من الاشتراك
    val isActive = bool("is_active").default(true)
    /** Multi-tenant scope. See CrmOrganizationsTable. */
    val organizationId = reference("organization_id", CrmOrganizationsTable).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Public landing-page leads ────────────────────────────────────
// Captures business + contact info from the lead form on the public
// landing page. NOT scoped to a vendor (these are pre-signup); the
// sales team triages them and creates Vendor accounts manually.
object LeadsTable : UUIDTable("leads") {
    /** Business / brand name (pharmacy, restaurant, cafe, retail). */
    val businessName = varchar("business_name", 255)
    /** Business contact phone — public-facing line. */
    val businessPhone = varchar("business_phone", 32)
    /** Owner / contact person full name. */
    val contactName = varchar("contact_name", 255)
    /** Owner / contact direct phone. */
    val contactPhone = varchar("contact_phone", 32)
    /** Optional free-text notes from the form. */
    val notes = text("notes").nullable()
    /** Where the lead came from — defaults to "landing" but lets us
     *  attribute social/ad campaigns later via a `?source=...` query
     *  param on the landing URL. Property is `channel` instead of
     *  `source` because Exposed's parent ColumnSet already exposes a
     *  `source` member and reusing the name shadows it. */
    val channel = varchar("source", 64).default("landing")
    /** Set when the sales team marks a lead as actioned, so the same
     *  lead can't be worked twice. */
    val status = varchar("status", 32).default("NEW") // NEW | CONTACTED | CONVERTED | REJECTED
    /** Remote IP that submitted — light spam attribution; we do not
     *  rate-limit on this yet, just log. */
    val ipAddress = varchar("ip_address", 64).nullable()
    /** Raw User-Agent of the submitter (browser/device). */
    val userAgent = text("user_agent").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}
