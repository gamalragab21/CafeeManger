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
            attendanceAdapter = Attendance.Adapter(
                worked_minutesAdapter = intAdapter,
            ),
            categoriesAdapter = Categories.Adapter(
                display_orderAdapter = intAdapter,
            ),
            order_itemsAdapter = Order_items.Adapter(
                quantityAdapter = intAdapter,
            ),
            salary_paymentsAdapter = Salary_payments.Adapter(
                worked_daysAdapter = intAdapter,
                worked_hoursAdapter = intAdapter,
            ),
            stockAdapter = Stock.Adapter(
                quantityAdapter = intAdapter,
                min_quantityAdapter = intAdapter,
            ),
            stock_transactionsAdapter = Stock_transactions.Adapter(
                quantityAdapter = intAdapter,
                previous_quantityAdapter = intAdapter,
            ),
            tablesAdapter = Tables.Adapter(
                capacityAdapter = intAdapter,
            ),
            workersAdapter = Workers.Adapter(
                qr_code_versionAdapter = intAdapter,
            ),
            customersAdapter = Customers.Adapter(
                order_countAdapter = intAdapter,
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
    single { WorkerDao(get()) }
    single { CustomerDao(get()) }
}
