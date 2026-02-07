package net.marllex.cafeemanger.backend.data.database

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
    val imageUrl = text("image_url").nullable()
    val available = bool("available").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Tables (Restaurant Tables) ──────────────────────────────────
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
    val taxPlaceId = reference("tax_place_id", TaxPlacesTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val clientName = varchar("client_name", 255).nullable()
    val clientPhone = varchar("client_phone", 20).nullable()
    val clientAddress = text("client_address").nullable()
    val geoLat = double("geo_lat").nullable()
    val geoLng = double("geo_lng").nullable()
    val paymentMethod = varchar("payment_method", 20)
    val subtotal = decimal("subtotal", 10, 2)
    val deliveryFee = decimal("delivery_fee", 10, 2).default(java.math.BigDecimal.ZERO)
    val tax = decimal("tax", 10, 2).default(java.math.BigDecimal.ZERO)
    val total = decimal("total", 10, 2)
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Order Items ─────────────────────────────────────────────────
object OrderItemsTable : UUIDTable("order_items") {
    val orderId = reference("order_id", OrdersTable)
    val itemId = reference("item_id", ItemsTable)
    val itemNameSnapshot = varchar("item_name_snapshot", 255)
    val itemPriceSnapshot = decimal("item_price_snapshot", 10, 2)
    val quantity = integer("quantity")
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Stock ───────────────────────────────────────────────────────
object StockTable : UUIDTable("stock") {
    val vendorId = reference("vendor_id", VendorsTable)
    val itemId = reference("item_id", ItemsTable)
    val itemName = varchar("item_name", 255)
    val quantity = integer("quantity").default(0)
    val minQuantity = integer("min_quantity").default(5)
    val costPrice = decimal("cost_price", 10, 2).default(java.math.BigDecimal.ZERO)
    val unit = varchar("unit", 50).default("pcs")
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

// ─── Stock Transactions ─────────────────────────────────────────
object StockTransactionsTable : UUIDTable("stock_transactions") {
    val stockId = reference("stock_id", StockTable)
    val type = varchar("type", 20) // ADD, DEDUCT, ADJUST
    val quantity = integer("quantity")
    val previousQuantity = integer("previous_quantity")
    val orderId = reference("order_id", OrdersTable).nullable()
    val note = text("note").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Activity Logs ───────────────────────────────────────────────
object ActivityLogsTable : UUIDTable("activity_logs") {
    val orderId = reference("order_id", OrdersTable)
    val userId = reference("user_id", UsersTable)
    val action = varchar("action", 50)
    val payload = text("payload").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
}

// ─── Refresh Tokens ──────────────────────────────────────────────
object RefreshTokensTable : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", UsersTable)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").default(Clock.System.now())
}
