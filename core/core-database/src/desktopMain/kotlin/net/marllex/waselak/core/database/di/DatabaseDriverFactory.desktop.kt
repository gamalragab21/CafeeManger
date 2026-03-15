package net.marllex.waselak.core.database.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.marllex.waselak.core.database.WaselakDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = File(System.getProperty("user.home"), ".waselak")
        dbPath.mkdirs()
        val dbFile = File(dbPath, "waselak.db")
        val isNewDb = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNewDb) {
            WaselakDatabase.Schema.create(driver)
        } else {
            runMigrations(driver)
        }
        return driver
    }

    private fun runMigrations(driver: SqlDriver) {
        // ── CREATE missing tables (for DBs created before these tables existed) ──
        val createStatements = listOf(
            """CREATE TABLE IF NOT EXISTS vendors (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                logo_url TEXT,
                address TEXT NOT NULL,
                contact_phone TEXT NOT NULL,
                wallet_phone TEXT,
                default_delivery_fee REAL NOT NULL DEFAULT 0.0,
                store_type TEXT,
                enable_tables INTEGER NOT NULL DEFAULT 1,
                enable_kds INTEGER NOT NULL DEFAULT 1,
                enable_dine_in INTEGER NOT NULL DEFAULT 1,
                enable_delivery INTEGER NOT NULL DEFAULT 1,
                enable_takeaway INTEGER NOT NULL DEFAULT 1,
                enable_in_store INTEGER NOT NULL DEFAULT 0,
                enable_pickup_later INTEGER NOT NULL DEFAULT 0,
                business_type TEXT NOT NULL DEFAULT 'RESTAURANT',
                tax_enabled INTEGER NOT NULL DEFAULT 0,
                default_tax_percent REAL NOT NULL DEFAULT 0.0,
                stock_mode TEXT NOT NULL DEFAULT 'NONE',
                offline_mode_enabled INTEGER NOT NULL DEFAULT 0,
                biometric_required INTEGER NOT NULL DEFAULT 0,
                enable_offline_mode INTEGER NOT NULL DEFAULT 0,
                digital_menu_url TEXT,
                loyalty_enabled INTEGER NOT NULL DEFAULT 0,
                points_earn_rate REAL NOT NULL DEFAULT 1.0,
                points_redeem_rate REAL NOT NULL DEFAULT 0.1,
                min_points_redeem INTEGER NOT NULL DEFAULT 100,
                max_manual_discount_percent REAL NOT NULL DEFAULT 100.0,
                manual_discount_requires_pin INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER
            )""",
            """CREATE TABLE IF NOT EXISTS users (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                role TEXT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                photo_url TEXT,
                active INTEGER NOT NULL,
                created_at INTEGER
            )""",
            """CREATE TABLE IF NOT EXISTS workers (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                worker_id TEXT NOT NULL,
                full_name TEXT NOT NULL,
                phone TEXT,
                description TEXT,
                role TEXT NOT NULL,
                salary_type TEXT NOT NULL,
                salary_amount REAL NOT NULL,
                active INTEGER NOT NULL,
                user_id TEXT,
                is_login_enabled INTEGER NOT NULL DEFAULT 0,
                has_pin INTEGER NOT NULL DEFAULT 0,
                pin_sha256 TEXT,
                qr_code_version INTEGER NOT NULL DEFAULT 1,
                photo_url TEXT,
                pin_updated_at INTEGER,
                created_at INTEGER,
                updated_at INTEGER
            )""",
            """CREATE TABLE IF NOT EXISTS worker_roles (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                created_at INTEGER
            )""",
            """CREATE TABLE IF NOT EXISTS attendance (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                worker_id TEXT NOT NULL,
                worker_name TEXT,
                worker_role TEXT,
                date TEXT NOT NULL,
                check_in INTEGER NOT NULL,
                check_out INTEGER,
                worked_minutes INTEGER,
                recorded_by TEXT NOT NULL,
                auth_method TEXT NOT NULL DEFAULT 'MANUAL',
                note TEXT,
                created_at INTEGER,
                sync_status TEXT NOT NULL DEFAULT 'SYNCED'
            )""",
            """CREATE TABLE IF NOT EXISTS salary_payments (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                worker_id TEXT NOT NULL,
                worker_name TEXT,
                period_type TEXT NOT NULL,
                period_start TEXT NOT NULL,
                period_end TEXT NOT NULL,
                worked_days INTEGER NOT NULL,
                worked_hours INTEGER,
                amount REAL NOT NULL,
                overtime_hours REAL NOT NULL DEFAULT 0.0,
                overtime_amount REAL NOT NULL DEFAULT 0.0,
                paid INTEGER NOT NULL,
                paid_at INTEGER,
                paid_by TEXT,
                note TEXT,
                created_at INTEGER
            )""",
            """CREATE TABLE IF NOT EXISTS categories (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                name TEXT NOT NULL,
                display_order INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS items (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                category_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                price REAL NOT NULL,
                cost_price REAL,
                sku TEXT,
                barcode TEXT,
                image_url TEXT,
                available INTEGER NOT NULL,
                stock_behavior TEXT NOT NULL DEFAULT 'NONE'
            )""",
            """CREATE TABLE IF NOT EXISTS tables (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                number TEXT NOT NULL,
                capacity INTEGER NOT NULL,
                status TEXT NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS orders (
                id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                channel TEXT NOT NULL,
                status TEXT NOT NULL,
                table_id TEXT,
                table_number TEXT,
                cashier_id TEXT NOT NULL,
                cashier_name TEXT,
                delivery_user_id TEXT,
                delivery_user_name TEXT,
                client_name TEXT,
                client_phone TEXT,
                client_address TEXT,
                customer_id TEXT,
                geo_lat REAL,
                geo_lng REAL,
                payment_method TEXT NOT NULL,
                payment_status TEXT NOT NULL DEFAULT 'PENDING',
                payment_timing TEXT NOT NULL DEFAULT 'PAY_NOW',
                payment_confirmed_at INTEGER,
                payment_confirmed_by TEXT,
                subtotal REAL NOT NULL,
                delivery_fee REAL NOT NULL DEFAULT 0.0,
                discount REAL NOT NULL DEFAULT 0.0,
                discount_type TEXT NOT NULL DEFAULT 'FIXED',
                tax REAL NOT NULL,
                tax_percent REAL NOT NULL DEFAULT 0.0,
                total REAL NOT NULL,
                notes TEXT,
                offer_id TEXT,
                points_earned INTEGER NOT NULL DEFAULT 0,
                points_redeemed INTEGER NOT NULL DEFAULT 0,
                discount_reason TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER,
                refunded_at INTEGER,
                refunded_by TEXT,
                refund_reason TEXT,
                sync_status TEXT NOT NULL DEFAULT 'SYNCED'
            )""",
            """CREATE TABLE IF NOT EXISTS order_items (
                id TEXT NOT NULL PRIMARY KEY,
                order_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                item_name_snapshot TEXT NOT NULL,
                item_price_snapshot REAL NOT NULL,
                quantity INTEGER NOT NULL,
                note TEXT,
                variant_options_snapshot TEXT
            )""",
        )
        for (sql in createStatements) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Exception) {
                // Table already exists — ignore
            }
        }

        // ── ALTER existing tables (add new columns) ──
        val alterStatements = listOf(
            "ALTER TABLE vendors ADD COLUMN enable_kds INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE overtime_entries ADD COLUMN paid INTEGER NOT NULL DEFAULT 0",
        )
        for (sql in alterStatements) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Exception) {
                // Column already exists — ignore
            }
        }
    }
}
