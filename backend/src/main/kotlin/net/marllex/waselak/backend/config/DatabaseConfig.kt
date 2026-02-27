package net.marllex.waselak.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.datetime.Clock
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AuthService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseConfig {

    fun init(config: ApplicationConfig,resetOnStart: Boolean) {
        val dbConfig = config.config("database")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dbConfig.property("url").getString()
            username = dbConfig.property("user").getString()
            password = dbConfig.property("password").getString()
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = dbConfig.propertyOrNull("maxPoolSize")?.getString()?.toInt() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        if (resetOnStart) {
            clearAllDatabase()
        }
        // Create tables if not exist
        transaction {
            SchemaUtils.create(
                VendorsTable,
                UsersTable,
                CategoriesTable,
                ItemsTable,
                TablesTable,
                TaxPlacesTable,
                CustomersTable,
                CustomerAddressesTable,
                OrdersTable,
                OrderItemsTable,
                StockTable,
                RecipesTable,
                RecipeIngredientsTable,
                StockTransactionsTable,
                WorkersTable,
                WorkerRolesTable,
                AttendanceTable,
                AttendanceAuthLogsTable,
                SalaryPaymentsTable,
                OvertimeTable,
                ActivityLogsTable,
                RefreshTokensTable,
                AnnouncementsTable,
                AnnouncementReadsTable,
                OvertimeTable,
            )
            // Add any new columns to existing tables
            SchemaUtils.createMissingTablesAndColumns(
                VendorsTable, OrdersTable, ItemsTable, StockTable,
                RecipesTable, RecipeIngredientsTable, StockTransactionsTable,
                WorkersTable, WorkerRolesTable, AttendanceTable, AttendanceAuthLogsTable,
                SalaryPaymentsTable, OvertimeTable,
                AnnouncementsTable, AnnouncementReadsTable,
                CustomersTable, CustomerAddressesTable,
            )

            // Add enable_offline_mode to vendors
            exec("ALTER TABLE vendors ADD COLUMN IF NOT EXISTS enable_offline_mode BOOLEAN DEFAULT FALSE")

            // Add overtime columns to salary_payments
            exec("ALTER TABLE salary_payments ADD COLUMN IF NOT EXISTS overtime_hours DECIMAL(5,2) DEFAULT 0.0")
            exec("ALTER TABLE salary_payments ADD COLUMN IF NOT EXISTS overtime_amount DECIMAL(10,2) DEFAULT 0.0")

            // Migrate ON_TABLE → SERVED (status rename)
            exec("UPDATE orders SET status = 'SERVED' WHERE status = 'ON_TABLE'")

            // Backfill payment_status for existing orders
            exec("UPDATE orders SET payment_status = 'PAID' WHERE status = 'COMPLETED' AND payment_status = 'PENDING'")
            exec("UPDATE orders SET payment_timing = 'PAY_NOW' WHERE payment_timing IS NULL")

            // Recipe/BOM migration: backfill stockBehavior for items that already have stock entries
            exec("UPDATE items SET stock_behavior = 'DIRECT' WHERE id IN (SELECT item_id FROM stock WHERE item_id IS NOT NULL) AND stock_behavior = 'NONE'")
            // Normalize unit values from legacy "pcs" to "PIECE"
            exec("UPDATE stock SET unit = 'PIECE' WHERE unit = 'pcs'")
            exec("UPDATE stock SET base_unit = 'PIECE' WHERE base_unit IS NULL OR base_unit = ''")

            // ─── Unit Simplification Migration (20 → 6 units) ───────────
            // Recipe ingredients FIRST (need quantity conversion before unit rename)
            exec("UPDATE recipe_ingredients SET quantity = quantity * 240, unit = 'MILLILITER' WHERE unit = 'CUP'")
            exec("UPDATE recipe_ingredients SET quantity = quantity * 15, unit = 'MILLILITER' WHERE unit = 'TABLESPOON'")
            exec("UPDATE recipe_ingredients SET quantity = quantity * 5, unit = 'MILLILITER' WHERE unit = 'TEASPOON'")
            exec("UPDATE recipe_ingredients SET quantity = quantity * 12, unit = 'PIECE' WHERE unit = 'DOZEN'")
            exec("UPDATE recipe_ingredients SET unit = 'PIECE' WHERE unit = 'PLATE'")
            exec("UPDATE recipe_ingredients SET unit = 'PACK' WHERE unit IN ('BOX','BAG','BOTTLE','CAN','CARTON','SACK','TRAY','BUCKET','ROLL')")

            // Stock table
            exec("UPDATE stock SET quantity = quantity * 240, min_quantity = min_quantity * 240, unit = 'MILLILITER', base_unit = 'MILLILITER', conversion_rate = 1.0 WHERE unit = 'CUP'")
            exec("UPDATE stock SET quantity = quantity * 15, min_quantity = min_quantity * 15, unit = 'MILLILITER', base_unit = 'MILLILITER', conversion_rate = 1.0 WHERE unit = 'TABLESPOON'")
            exec("UPDATE stock SET quantity = quantity * 5, min_quantity = min_quantity * 5, unit = 'MILLILITER', base_unit = 'MILLILITER', conversion_rate = 1.0 WHERE unit = 'TEASPOON'")
            exec("UPDATE stock SET quantity = quantity * 12, min_quantity = min_quantity * 12, unit = 'PIECE', base_unit = 'PIECE', conversion_rate = 1.0 WHERE unit = 'DOZEN'")
            exec("UPDATE stock SET unit = 'PIECE', base_unit = 'PIECE', conversion_rate = 1.0 WHERE unit = 'PLATE'")
            exec("UPDATE stock SET unit = 'PACK', base_unit = 'PACK', conversion_rate = 1.0 WHERE unit IN ('BOX','BAG','BOTTLE','CAN','CARTON','SACK','TRAY','BUCKET','ROLL')")
        }

        // Auto-seed demo data if database is empty
//        seedIfEmpty()
    }
    fun clearAllDatabase() {
        transaction {
            SchemaUtils.drop(
                AnnouncementReadsTable,
                AnnouncementsTable,
                RefreshTokensTable,
                ActivityLogsTable,
                SalaryPaymentsTable,
                AttendanceAuthLogsTable,
                AttendanceTable,
                WorkerRolesTable,
                WorkersTable,
                StockTransactionsTable,
                RecipeIngredientsTable,
                RecipesTable,
                StockTable,
                OrderItemsTable,
                OrdersTable,
                CustomerAddressesTable,
                CustomersTable,
                TaxPlacesTable,
                TablesTable,
                ItemsTable,
                CategoriesTable,
                UsersTable,
                VendorsTable,
            )
        }
    }

    private val logger = LoggerFactory.getLogger("DatabaseConfig")

    private fun seedIfEmpty() {
        transaction {
            val vendorCount = VendorsTable.selectAll().count()
            if (vendorCount > 0L) return@transaction

            logger.info("Database is empty. Seeding demo data...")

            // 1. Create demo vendor
            val vendorId = VendorsTable.insertAndGetId {
                it[name] = "Demo Store"
                it[address] = "123 Main Street, Downtown"
                it[contactPhone] = "+1234567890"
                it[walletPhone] = "+1234567890"
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }

            // 2. Create demo users (password: "password123")
            val passwordHash = AuthService.hashPassword("password123")
            val demoUsers = listOf(
                Triple("MANAGER", "Demo Manager", "+1111111111"),
                Triple("CASHIER", "Demo Cashier", "+2222222222"),
                Triple("DELIVERY", "Demo Delivery", "+3333333333"),
            )
            demoUsers.forEach { (role, userName, phone) ->
                UsersTable.insertAndGetId {
                    it[UsersTable.vendorId] = vendorId.value
                    it[UsersTable.role] = role
                    it[UsersTable.name] = userName
                    it[UsersTable.phone] = phone
                    it[email] = "${role.lowercase()}@demo.com"
                    it[UsersTable.passwordHash] = passwordHash
                    it[active] = true
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }

            // 3. Create demo categories
            data class CategorySeed(val catName: String, val order: Int)
            val categories = listOf(
                CategorySeed("Burgers", 1),
                CategorySeed("Drinks", 2),
                CategorySeed("Desserts", 3),
            )
            val categoryIds = categories.associate { cat ->
                cat.catName to CategoriesTable.insertAndGetId {
                    it[CategoriesTable.vendorId] = vendorId.value
                    it[name] = cat.catName
                    it[displayOrder] = cat.order
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }

            // 4. Create demo items
            data class ItemSeed(val category: String, val itemName: String, val desc: String, val itemPrice: Double)
            val items = listOf(
                ItemSeed("Burgers", "Classic Burger", "Beef patty with lettuce and tomato", 8.99),
                ItemSeed("Burgers", "Cheese Burger", "Classic with extra cheese", 10.99),
                ItemSeed("Drinks", "Cola", "Refreshing cola drink", 2.99),
                ItemSeed("Drinks", "Fresh Juice", "Orange juice", 4.99),
                ItemSeed("Desserts", "Chocolate Cake", "Rich chocolate layer cake", 6.99),
            )
            items.forEach { item ->
                ItemsTable.insertAndGetId {
                    it[ItemsTable.vendorId] = vendorId.value
                    it[categoryId] = categoryIds[item.category]!!.value
                    it[name] = item.itemName
                    it[description] = item.desc
                    it[price] = item.itemPrice.toBigDecimal()
                    it[available] = true
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }

            // 5. Create demo tables
            (1..4).forEach { num ->
                TablesTable.insertAndGetId {
                    it[TablesTable.vendorId] = vendorId.value
                    it[number] = "T$num"
                    it[capacity] = when (num) { 2 -> 2; 3 -> 6; else -> 4 }
                    it[status] = "AVAILABLE"
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }

            logger.info("Demo data seeded successfully! Login with phone: +1111111111, password: password123")
        }
    }
}
