package net.marllex.waselak.core.database.di

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import net.marllex.waselak.core.database.*
import net.marllex.waselak.core.database.dao.*
import org.koin.dsl.module

private val intAdapter = object : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}

private val booleanAdapter = object : ColumnAdapter<Boolean, Long> {
    override fun decode(databaseValue: Long): Boolean = databaseValue != 0L
    override fun encode(value: Boolean): Long = if (value) 1L else 0L
}

/**
 * Run safe ALTER TABLE migrations for schema changes.
 * Each statement is wrapped in try-catch so it's idempotent
 * (no-op if column/table already exists).
 */
private fun migrateIfNeeded(driver: SqlDriver) {
    val migrations = listOf(
        // v2: add enable_takeaway to vendors
        "ALTER TABLE vendors ADD COLUMN enable_takeaway INTEGER NOT NULL DEFAULT 1",
        // v2: add customer_id to orders
        "ALTER TABLE orders ADD COLUMN customer_id TEXT",
        // v2: drop old customers tables (may have wrong schema from earlier migration)
        "DROP TABLE IF EXISTS customer_addresses",
        "DROP TABLE IF EXISTS customers",
        // v2: create customers table (must match Customer.sq exactly)
        """CREATE TABLE IF NOT EXISTS customers (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            name TEXT,
            phone TEXT NOT NULL,
            notes TEXT,
            order_count INTEGER NOT NULL DEFAULT 0,
            total_spent REAL NOT NULL DEFAULT 0.0,
            last_order_at INTEGER,
            created_at INTEGER NOT NULL,
            updated_at INTEGER
        )""",
        // v2: create customer_addresses table (must match CustomerAddress.sq exactly)
        """CREATE TABLE IF NOT EXISTS customer_addresses (
            id TEXT NOT NULL PRIMARY KEY,
            customer_id TEXT NOT NULL,
            label TEXT,
            address TEXT NOT NULL,
            geo_lat REAL,
            geo_lng REAL,
            delivery_zone_id TEXT,
            delivery_fee REAL,
            is_default INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )""",
        // v3: add payment status columns to orders
        "ALTER TABLE orders ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'PENDING'",
        "ALTER TABLE orders ADD COLUMN payment_timing TEXT NOT NULL DEFAULT 'PAY_NOW'",
        "ALTER TABLE orders ADD COLUMN payment_confirmed_at INTEGER",
        "ALTER TABLE orders ADD COLUMN payment_confirmed_by TEXT",
        // v4: add auth_method column to attendance
        "ALTER TABLE attendance ADD COLUMN auth_method TEXT NOT NULL DEFAULT 'MANUAL'",
        // v5: add new order channel flags to vendors
        "ALTER TABLE vendors ADD COLUMN enable_in_store INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_pickup_later INTEGER NOT NULL DEFAULT 0",
        // v6: Phase 2 - business type and tax/stock settings on vendors
        "ALTER TABLE vendors ADD COLUMN business_type TEXT NOT NULL DEFAULT 'RESTAURANT'",
        "ALTER TABLE vendors ADD COLUMN tax_enabled INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN default_tax_percent REAL NOT NULL DEFAULT 0.0",
        "ALTER TABLE vendors ADD COLUMN stock_mode TEXT NOT NULL DEFAULT 'NONE'",
        // v7: Phase 3 - discount and tax_percent on orders
        "ALTER TABLE orders ADD COLUMN discount REAL NOT NULL DEFAULT 0.0",
        "ALTER TABLE orders ADD COLUMN discount_type TEXT NOT NULL DEFAULT 'FIXED'",
        "ALTER TABLE orders ADD COLUMN tax_percent REAL NOT NULL DEFAULT 0.0",
        // v8: Phase 4 - enhanced item model
        "ALTER TABLE items ADD COLUMN cost_price REAL",
        "ALTER TABLE items ADD COLUMN sku TEXT",
        "ALTER TABLE items ADD COLUMN barcode TEXT",
        // v9: Recipe-based inventory / BOM
        "ALTER TABLE items ADD COLUMN stock_behavior TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE stock ADD COLUMN base_unit TEXT NOT NULL DEFAULT 'PIECE'",
        "ALTER TABLE stock ADD COLUMN conversion_rate REAL NOT NULL DEFAULT 1.0",
        "ALTER TABLE stock_transactions ADD COLUMN recipe_id TEXT",
        // Drop and recreate stock tables to change INTEGER→REAL columns
        // (SQLite doesn't support ALTER COLUMN, so we recreate)
        "DROP TABLE IF EXISTS stock_transactions",
        """CREATE TABLE IF NOT EXISTS stock_transactions (
            id TEXT NOT NULL PRIMARY KEY,
            stock_id TEXT NOT NULL,
            item_name TEXT,
            type TEXT NOT NULL,
            quantity REAL NOT NULL,
            previous_quantity REAL NOT NULL,
            order_id TEXT,
            recipe_id TEXT,
            note TEXT,
            created_at INTEGER NOT NULL
        )""",
        "DROP TABLE IF EXISTS stock",
        """CREATE TABLE IF NOT EXISTS stock (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            item_id TEXT,
            item_name TEXT NOT NULL,
            quantity REAL NOT NULL,
            min_quantity REAL NOT NULL,
            cost_price REAL NOT NULL,
            unit TEXT NOT NULL,
            base_unit TEXT NOT NULL DEFAULT 'PIECE',
            conversion_rate REAL NOT NULL DEFAULT 1.0,
            is_menu_item INTEGER NOT NULL DEFAULT 1,
            alert_enabled INTEGER NOT NULL DEFAULT 1,
            last_updated_at INTEGER NOT NULL
        )""",
        // Create recipe tables
        """CREATE TABLE IF NOT EXISTS recipes (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            item_id TEXT NOT NULL,
            item_name TEXT NOT NULL,
            name TEXT NOT NULL,
            description TEXT,
            yield_quantity REAL NOT NULL DEFAULT 1.0,
            yield_unit TEXT NOT NULL DEFAULT 'PIECE',
            active INTEGER NOT NULL DEFAULT 1,
            total_cost REAL NOT NULL DEFAULT 0.0,
            created_at INTEGER,
            updated_at INTEGER
        )""",
        """CREATE TABLE IF NOT EXISTS recipe_ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id TEXT NOT NULL,
            stock_id TEXT NOT NULL,
            stock_item_name TEXT NOT NULL,
            quantity REAL NOT NULL,
            unit TEXT NOT NULL,
            display_order INTEGER NOT NULL DEFAULT 0,
            available_quantity REAL NOT NULL DEFAULT 0.0,
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
        )""",
        // v10: Recipe status field (active → status) — drop & recreate since column type changed
        "DROP TABLE IF EXISTS recipe_ingredients",
        "DROP TABLE IF EXISTS recipes",
        """CREATE TABLE IF NOT EXISTS recipes (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            item_id TEXT NOT NULL,
            item_name TEXT NOT NULL,
            name TEXT NOT NULL,
            description TEXT,
            yield_quantity REAL NOT NULL DEFAULT 1.0,
            yield_unit TEXT NOT NULL DEFAULT 'PIECE',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            total_cost REAL NOT NULL DEFAULT 0.0,
            created_at INTEGER,
            updated_at INTEGER
        )""",
        """CREATE TABLE IF NOT EXISTS recipe_ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id TEXT NOT NULL,
            stock_id TEXT NOT NULL,
            stock_item_name TEXT NOT NULL,
            quantity REAL NOT NULL,
            unit TEXT NOT NULL,
            display_order INTEGER NOT NULL DEFAULT 0,
            available_quantity REAL NOT NULL DEFAULT 0.0,
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
        )""",
        // v11: fixed_quantity on recipe_ingredients — drop & recreate (it's a cache)
        "DROP TABLE IF EXISTS recipe_ingredients",
        """CREATE TABLE IF NOT EXISTS recipe_ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id TEXT NOT NULL,
            stock_id TEXT NOT NULL,
            stock_item_name TEXT NOT NULL,
            quantity REAL NOT NULL,
            unit TEXT NOT NULL,
            fixed_quantity INTEGER NOT NULL DEFAULT 0,
            display_order INTEGER NOT NULL DEFAULT 0,
            available_quantity REAL NOT NULL DEFAULT 0.0,
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
        )""",
        // v12: refund columns on orders
        "ALTER TABLE orders ADD COLUMN refunded_at INTEGER",
        "ALTER TABLE orders ADD COLUMN refunded_by TEXT",
        "ALTER TABLE orders ADD COLUMN refund_reason TEXT",
        // v13: offline mode + biometric config flags on vendors
        "ALTER TABLE vendors ADD COLUMN offline_mode_enabled INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN biometric_required INTEGER NOT NULL DEFAULT 0",
        // v13: sync_status on orders and attendance + pending_sync table
        "ALTER TABLE orders ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'SYNCED'",
        "ALTER TABLE attendance ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'SYNCED'",
        """CREATE TABLE IF NOT EXISTS pending_sync (
            id TEXT NOT NULL PRIMARY KEY,
            type TEXT NOT NULL,
            payload TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            retry_count INTEGER NOT NULL DEFAULT 0,
            last_error TEXT
        )""",
        // v14: add pin_sha256 to workers for offline PIN verification
        "ALTER TABLE workers ADD COLUMN pin_sha256 TEXT",
        // v15: create pending_attendance table for offline sync
        """CREATE TABLE IF NOT EXISTS pending_attendance (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            worker_id TEXT NOT NULL,
            worker_name TEXT,
            worker_role TEXT,
            action TEXT NOT NULL,
            date TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            linked_attendance_id TEXT,
            note TEXT,
            retry_count INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )""",
        // v16: add enable_offline_mode to vendors
        "ALTER TABLE vendors ADD COLUMN enable_offline_mode INTEGER NOT NULL DEFAULT 0",
        // v17: create overtime_entries table
        """CREATE TABLE IF NOT EXISTS overtime_entries (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            worker_id TEXT NOT NULL,
            worker_name TEXT,
            date TEXT NOT NULL,
            hours REAL NOT NULL,
            rate_per_hour REAL NOT NULL,
            amount REAL NOT NULL,
            note TEXT,
            created_by TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""",
        // v18: Item Variants - variant groups and options tables
        """CREATE TABLE IF NOT EXISTS item_variant_groups (
            id TEXT NOT NULL PRIMARY KEY,
            item_id TEXT NOT NULL,
            name TEXT NOT NULL,
            required INTEGER NOT NULL DEFAULT 0,
            display_order INTEGER NOT NULL DEFAULT 0
        )""",
        """CREATE TABLE IF NOT EXISTS item_variant_options (
            id TEXT NOT NULL PRIMARY KEY,
            group_id TEXT NOT NULL,
            name TEXT NOT NULL,
            price_adjustment REAL NOT NULL DEFAULT 0.0,
            is_default INTEGER NOT NULL DEFAULT 0,
            display_order INTEGER NOT NULL DEFAULT 0
        )""",
        // v18: variant_options_snapshot on order_items
        "ALTER TABLE order_items ADD COLUMN variant_options_snapshot TEXT",
        // v19: photo_url on users and workers
        "ALTER TABLE users ADD COLUMN photo_url TEXT",
        "ALTER TABLE workers ADD COLUMN photo_url TEXT",
        // v20: overtime tracking columns on salary_payments
        "ALTER TABLE salary_payments ADD COLUMN overtime_hours REAL NOT NULL DEFAULT 0.0",
        "ALTER TABLE salary_payments ADD COLUMN overtime_amount REAL NOT NULL DEFAULT 0.0",
        // v22: offers and offer_items tables
        """CREATE TABLE IF NOT EXISTS offers (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            name TEXT NOT NULL,
            description TEXT,
            image_url TEXT,
            discount_type TEXT NOT NULL,
            discount_value REAL NOT NULL,
            active INTEGER NOT NULL DEFAULT 1,
            expires_at INTEGER,
            display_order INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER,
            updated_at INTEGER
        )""",
        """CREATE TABLE IF NOT EXISTS offer_items (
            id TEXT NOT NULL PRIMARY KEY,
            offer_id TEXT NOT NULL,
            item_id TEXT NOT NULL,
            item_name TEXT NOT NULL,
            item_price REAL NOT NULL,
            quantity INTEGER NOT NULL DEFAULT 1
        )""",
        // v23: Phase 3 - loyalty, discounts, offers enhancements
        "ALTER TABLE vendors ADD COLUMN loyalty_enabled INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN points_earn_rate REAL NOT NULL DEFAULT 1.0",
        "ALTER TABLE vendors ADD COLUMN points_redeem_rate REAL NOT NULL DEFAULT 0.1",
        "ALTER TABLE vendors ADD COLUMN min_points_redeem INTEGER NOT NULL DEFAULT 100",
        "ALTER TABLE vendors ADD COLUMN max_manual_discount_percent REAL NOT NULL DEFAULT 100.0",
        "ALTER TABLE vendors ADD COLUMN manual_discount_requires_pin INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE customers ADD COLUMN points_balance INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE orders ADD COLUMN offer_id TEXT",
        "ALTER TABLE orders ADD COLUMN points_earned INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE orders ADD COLUMN points_redeemed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE orders ADD COLUMN discount_reason TEXT",
        "ALTER TABLE offers ADD COLUMN promo_code TEXT",
        "ALTER TABLE offers ADD COLUMN max_uses INTEGER",
        "ALTER TABLE offers ADD COLUMN used_count INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE offers ADD COLUMN starts_at INTEGER",
        // v21: reservations table
        """CREATE TABLE IF NOT EXISTS reservations (
            id TEXT NOT NULL PRIMARY KEY,
            vendor_id TEXT NOT NULL,
            table_id TEXT NOT NULL,
            table_number TEXT,
            client_name TEXT NOT NULL,
            client_phone TEXT,
            reservation_date TEXT NOT NULL,
            reservation_time TEXT NOT NULL,
            number_of_guests INTEGER NOT NULL DEFAULT 1,
            notes TEXT,
            status TEXT NOT NULL DEFAULT 'PENDING',
            order_id TEXT,
            created_by TEXT NOT NULL,
            created_at INTEGER,
            updated_at INTEGER
        )""",
        // v10: Vendor feature flags
        "ALTER TABLE vendors ADD COLUMN enable_digital_menu INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_recipe INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_split_payment INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_cash_drawer INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_returns INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_customer_credit INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_installments INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_pre_orders INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_scheduled_orders INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_suppliers INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_drug_interactions INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_prescriptions INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_analytics INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_announcements INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_stock INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_attendance INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_overtime INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_salary INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_customers INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_export INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_digital_receipt INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_worker_qrcode INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_loyalty INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE vendors ADD COLUMN enable_manual_discount INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE vendors ADD COLUMN enable_offers INTEGER NOT NULL DEFAULT 1",
    )
    migrations.forEach { sql ->
        try {
            driver.execute(null, sql, 0)
        } catch (_: Exception) {
            // Column/table already exists — ignore
        }
    }
}

val databaseModule = module {
    single {
        val driverFactory = get<DatabaseDriverFactory>()
        val driver = driverFactory.createDriver()
        migrateIfNeeded(driver)
        WaselakDatabase(
            driver = driver,
            vendorsAdapter = Vendors.Adapter(
                min_points_redeemAdapter = intAdapter,
            ),
            attendanceAdapter = Attendance.Adapter(
                worked_minutesAdapter = intAdapter,
            ),
            categoriesAdapter = Categories.Adapter(
                display_orderAdapter = intAdapter,
            ),
            ordersAdapter = Orders.Adapter(
                points_earnedAdapter = intAdapter,
                points_redeemedAdapter = intAdapter,
            ),
            order_itemsAdapter = Order_items.Adapter(
                quantityAdapter = intAdapter,
            ),
            salary_paymentsAdapter = Salary_payments.Adapter(
                worked_daysAdapter = intAdapter,
                worked_hoursAdapter = intAdapter,
            ),
            recipe_ingredientsAdapter = Recipe_ingredients.Adapter(
                display_orderAdapter = intAdapter,
            ),
            tablesAdapter = Tables.Adapter(
                capacityAdapter = intAdapter,
            ),
            workersAdapter = Workers.Adapter(
                qr_code_versionAdapter = intAdapter,
            ),
            customersAdapter = Customers.Adapter(
                order_countAdapter = intAdapter,
                points_balanceAdapter = intAdapter,
            ),
            pending_syncAdapter = Pending_sync.Adapter(
                retry_countAdapter = intAdapter,
            ),
            pending_attendanceAdapter = Pending_attendance.Adapter(
                retry_countAdapter = intAdapter,
            ),
            item_variant_groupsAdapter = Item_variant_groups.Adapter(
                display_orderAdapter = intAdapter,
            ),
            item_variant_optionsAdapter = Item_variant_options.Adapter(
                display_orderAdapter = intAdapter,
            ),
            reservationsAdapter = Reservations.Adapter(
                number_of_guestsAdapter = intAdapter,
            ),
            offersAdapter = Offers.Adapter(
                max_usesAdapter = intAdapter,
                used_countAdapter = intAdapter,
                display_orderAdapter = intAdapter,
            ),
            offer_itemsAdapter = Offer_items.Adapter(
                quantityAdapter = intAdapter,
            ),
        )
    }

    single { VendorDao(get()) }
    single { UserDao(get()) }
    single { CategoryDao(get()) }
    single { ItemDao(get()) }
    single { TableDao(get()) }
    single { OrderDao(get()) }
    single { StockDao(get()) }
    single { RecipeDao(get()) }
    single { WorkerDao(get()) }
    single { CustomerDao(get()) }
    single { PendingSyncDao(get()) }
    single { ItemVariantDao(get()) }
    single { ReservationDao(get()) }
    single { OfferDao(get()) }
}
