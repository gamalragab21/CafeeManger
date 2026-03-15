package net.marllex.waselak.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.datetime.Clock
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.AuthService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseConfig {

    fun init(config: ApplicationConfig, resetOnStart: Boolean) {
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
                ItemVariantGroupsTable,
                ItemVariantOptionsTable,
                TablesTable,
                TaxPlacesTable,
                CustomersTable,
                CustomerAddressesTable,
                OrdersTable,
                OrderItemsTable,
                OrderPaymentsTable,
                CashDrawerSessionsTable,
                CashMovementsTable,
                StockTable,
                RecipesTable,
                RecipeIngredientsTable,
                StockBatchesTable,
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
                AdminUsersTable,
                AdminRefreshTokensTable,
                SubscriptionPlansTable,
                VendorSubscriptionsTable,
                OffersTable,
                OfferItemsTable,
                PointsTransactionsTable,
                ProductReturnsTable,
                ReturnItemsTable,
                PrescriptionsTable,
                PrescriptionItemsTable,
                DrugInteractionsTable,
                CustomerCreditsTable,
                CreditTransactionsTable,
                ScheduledOrdersTable,
                ScheduledOrderItemsTable,
                SuppliersTable,
                PurchaseOrdersTable,
                PurchaseOrderItemsTable,
                NotificationsTable,
                DeviceTokensTable,
                RequestLogsTable,
                ReservationsTable,
            )
            // Add any new columns to existing tables
            SchemaUtils.createMissingTablesAndColumns(
                VendorsTable, UsersTable, OrdersTable, OrderItemsTable, ItemsTable,
                ItemVariantGroupsTable, ItemVariantOptionsTable,
                TablesTable, TaxPlacesTable,
                OrderPaymentsTable, CashDrawerSessionsTable, CashMovementsTable,
                StockTable, StockBatchesTable,
                RecipesTable, RecipeIngredientsTable, StockTransactionsTable,
                WorkersTable, WorkerRolesTable, AttendanceTable, AttendanceAuthLogsTable,
                SalaryPaymentsTable, OvertimeTable,
                AnnouncementsTable, AnnouncementReadsTable,
                CustomersTable, CustomerAddressesTable,
                AdminUsersTable, AdminRefreshTokensTable, RequestLogsTable,
                SubscriptionPlansTable, VendorSubscriptionsTable,
                OffersTable, OfferItemsTable, PointsTransactionsTable,
                ProductReturnsTable, ReturnItemsTable,
                PrescriptionsTable, PrescriptionItemsTable,
                DrugInteractionsTable,
                CustomerCreditsTable, CreditTransactionsTable,
                ScheduledOrdersTable, ScheduledOrderItemsTable,
                SuppliersTable, PurchaseOrdersTable, PurchaseOrderItemsTable,
                NotificationsTable, DeviceTokensTable,
                ReservationsTable,
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

            // ─── Plan V2: Add overtime, salaries, customer_management columns ───
            exec("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS overtime BOOLEAN DEFAULT false")
            exec("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS salaries BOOLEAN DEFAULT false")
            exec("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS customer_management BOOLEAN DEFAULT false")
            // Backfill existing plans with correct feature values
            exec("UPDATE subscription_plans SET overtime = false, salaries = false, customer_management = false, table_management = false WHERE name = 'STARTER'")
            exec("UPDATE subscription_plans SET overtime = false, salaries = true, customer_management = true, table_management = true, digital_menu = 'NONE' WHERE name = 'BUSINESS'")
            exec("UPDATE subscription_plans SET overtime = true, salaries = true, customer_management = true, table_management = true WHERE name = 'ENTERPRISE'")

            // ─── Plan V3: Add worker_qrcode and digital_receipt columns ───
            exec("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS worker_qrcode BOOLEAN DEFAULT false")
            exec("UPDATE subscription_plans SET worker_qrcode = false WHERE name = 'STARTER'")
            exec("UPDATE subscription_plans SET worker_qrcode = true WHERE name = 'BUSINESS'")
            exec("UPDATE subscription_plans SET worker_qrcode = true WHERE name = 'ENTERPRISE'")
            // Backfill digital_receipt: Enterprise only
            exec("UPDATE subscription_plans SET digital_receipt = false WHERE name = 'STARTER'")
            exec("UPDATE subscription_plans SET digital_receipt = false WHERE name = 'BUSINESS'")
            exec("UPDATE subscription_plans SET digital_receipt = true WHERE name = 'ENTERPRISE'")

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

        // Seed admin user if not exists
        seedAdminUser(config)

        // Seed default subscription plans
        seedDefaultPlans()

        // Auto-seed demo data if database is empty (debug only)
        val seedDemo = dbConfig.propertyOrNull("seedDemoData")?.getString()?.toBoolean() ?: false
        if (seedDemo) {
            seedIfEmpty()
        }

        // Migrate existing vendors without a subscription to Enterprise plan
        // (must run AFTER seedIfEmpty so new vendors get assigned)
        migrateExistingVendorsToEnterprise()
    }

    private fun seedAdminUser(config: ApplicationConfig) {
        val adminConfig = config.config("admin")
        val name = adminConfig.property("name").getString()
        val email = adminConfig.property("email").getString()
        val password = adminConfig.property("password").getString()

        transaction {
            val exists = AdminUsersTable.selectAll()
                .where { AdminUsersTable.email eq email }
                .count() > 0

            if (!exists) {
                AdminUsersTable.insertAndGetId {
                    it[AdminUsersTable.name] = name
                    it[AdminUsersTable.email] = email
                    it[passwordHash] = AuthService.hashPassword(password)
                    it[active] = true
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                logger.info("Admin user seeded: $email")
            }
        }
    }
    private fun seedDefaultPlans() {
        transaction {
            data class PlanSeed(
                val name: String, val displayName: String, val priceEgp: Int,
                val maxManagers: Int, val maxCashiers: Int, val maxDelivery: Int,
                val maxOrdersPerMonth: Int, val maxMenuItems: Int, val maxBranches: Int,
                val stockManagement: Boolean, val workerAttendance: Boolean, val deliveryModule: Boolean,
                val analytics: String, val digitalMenu: String,
                val overtime: Boolean, val salaries: Boolean, val customerManagement: Boolean,
                val tableManagement: Boolean, val workerQrcode: Boolean, val digitalReceipt: Boolean,
                val loyaltyPoints: Boolean, val manualDiscount: Boolean, val offersManagement: Boolean,
                val cashDrawer: Boolean, val splitPayment: Boolean, val customerCredit: Boolean,
                val suppliers: Boolean, val returns: Boolean, val prescriptions: Boolean,
                val drugInteractions: Boolean, val scheduledOrders: Boolean, val kds: Boolean,
                val notifications: Boolean,
                val displayOrder: Int
            )

            val plans = listOf(
                PlanSeed("STARTER", "Starter", 299,
                    maxManagers = 1, maxCashiers = 1, maxDelivery = 0,
                    maxOrdersPerMonth = 750, maxMenuItems = 50, maxBranches = 1,
                    stockManagement = false, workerAttendance = false, deliveryModule = false,
                    analytics = "NONE", digitalMenu = "NONE",
                    overtime = false, salaries = false, customerManagement = false,
                    tableManagement = false, workerQrcode = false, digitalReceipt = false,
                    loyaltyPoints = true, manualDiscount = true, offersManagement = false,
                    cashDrawer = true, splitPayment = false, customerCredit = false,
                    suppliers = false, returns = false, prescriptions = false,
                    drugInteractions = false, scheduledOrders = false, kds = false,
                    notifications = true,
                    displayOrder = 1),
                PlanSeed("BUSINESS", "Business", 599,
                    maxManagers = 2, maxCashiers = 3, maxDelivery = 3,
                    maxOrdersPerMonth = 3500, maxMenuItems = 200, maxBranches = 1,
                    stockManagement = true, workerAttendance = true, deliveryModule = true,
                    analytics = "FULL", digitalMenu = "NONE",
                    overtime = false, salaries = true, customerManagement = true,
                    tableManagement = true, workerQrcode = true, digitalReceipt = false,
                    loyaltyPoints = true, manualDiscount = true, offersManagement = true,
                    cashDrawer = true, splitPayment = true, customerCredit = true,
                    suppliers = true, returns = true, prescriptions = true,
                    drugInteractions = true, scheduledOrders = false, kds = true,
                    notifications = true,
                    displayOrder = 2),
                PlanSeed("ENTERPRISE", "Enterprise", 999,
                    maxManagers = -1, maxCashiers = -1, maxDelivery = -1,
                    maxOrdersPerMonth = -1, maxMenuItems = -1, maxBranches = 5,
                    stockManagement = true, workerAttendance = true, deliveryModule = true,
                    analytics = "FULL", digitalMenu = "FULL",
                    overtime = true, salaries = true, customerManagement = true,
                    tableManagement = true, workerQrcode = true, digitalReceipt = true,
                    loyaltyPoints = true, manualDiscount = true, offersManagement = true,
                    cashDrawer = true, splitPayment = true, customerCredit = true,
                    suppliers = true, returns = true, prescriptions = true,
                    drugInteractions = true, scheduledOrders = true, kds = true,
                    notifications = true,
                    displayOrder = 3),
            )

            for (plan in plans) {
                val exists = SubscriptionPlansTable.selectAll()
                    .where { SubscriptionPlansTable.name eq plan.name }
                    .count() > 0
                if (!exists) {
                    SubscriptionPlansTable.insertAndGetId {
                        it[name] = plan.name
                        it[displayName] = plan.displayName
                        it[priceEgp] = plan.priceEgp
                        it[billingCycle] = "MONTHLY"
                        it[maxManagers] = plan.maxManagers
                        it[maxCashiers] = plan.maxCashiers
                        it[maxDelivery] = plan.maxDelivery
                        it[maxOrdersPerMonth] = plan.maxOrdersPerMonth
                        it[maxMenuItems] = plan.maxMenuItems
                        it[maxBranches] = plan.maxBranches
                        it[stockManagement] = plan.stockManagement
                        it[workerAttendance] = plan.workerAttendance
                        it[deliveryModule] = plan.deliveryModule
                        it[analytics] = plan.analytics
                        it[digitalMenu] = plan.digitalMenu
                        it[overtime] = plan.overtime
                        it[salaries] = plan.salaries
                        it[customerManagement] = plan.customerManagement
                        it[tableManagement] = plan.tableManagement
                        it[workerQrcode] = plan.workerQrcode
                        it[digitalReceipt] = plan.digitalReceipt
                        it[loyaltyPoints] = plan.loyaltyPoints
                        it[manualDiscount] = plan.manualDiscount
                        it[offersManagement] = plan.offersManagement
                        it[cashDrawer] = plan.cashDrawer
                        it[splitPayment] = plan.splitPayment
                        it[customerCredit] = plan.customerCredit
                        it[suppliers] = plan.suppliers
                        it[returns] = plan.returns
                        it[prescriptions] = plan.prescriptions
                        it[drugInteractions] = plan.drugInteractions
                        it[scheduledOrders] = plan.scheduledOrders
                        it[kds] = plan.kds
                        it[notifications] = plan.notifications
                        it[active] = true
                        it[displayOrder] = plan.displayOrder
                        it[createdAt] = Clock.System.now()
                        it[updatedAt] = Clock.System.now()
                    }
                    logger.info("Subscription plan seeded: ${plan.name} (${plan.priceEgp} EGP/mo)")
                }
            }

            // Migrate existing plans: ensure all feature columns are set correctly
            for (plan in plans) {
                SubscriptionPlansTable.update({ SubscriptionPlansTable.name eq plan.name }) {
                    it[loyaltyPoints] = plan.loyaltyPoints
                    it[manualDiscount] = plan.manualDiscount
                    it[offersManagement] = plan.offersManagement
                    it[cashDrawer] = plan.cashDrawer
                    it[splitPayment] = plan.splitPayment
                    it[customerCredit] = plan.customerCredit
                    it[suppliers] = plan.suppliers
                    it[returns] = plan.returns
                    it[prescriptions] = plan.prescriptions
                    it[drugInteractions] = plan.drugInteractions
                    it[scheduledOrders] = plan.scheduledOrders
                    it[kds] = plan.kds
                    it[notifications] = plan.notifications
                    it[updatedAt] = Clock.System.now()
                }
            }
        }
    }

    private fun migrateExistingVendorsToEnterprise() {
        transaction {
            // Find Enterprise plan
            val enterprisePlan = SubscriptionPlansTable.selectAll()
                .where { SubscriptionPlansTable.name eq "ENTERPRISE" }
                .firstOrNull() ?: return@transaction

            val enterprisePlanId = enterprisePlan[SubscriptionPlansTable.id].value

            // Find all vendors that don't have a subscription yet
            val allVendorIds = VendorsTable.selectAll().map { it[VendorsTable.id].value }
            val subscribedVendorIds = VendorSubscriptionsTable.selectAll()
                .map { it[VendorSubscriptionsTable.vendorId] }
                .toSet()

            val unsubscribedVendorIds = allVendorIds.filter { it !in subscribedVendorIds }

            for (vendorId in unsubscribedVendorIds) {
                VendorSubscriptionsTable.insertAndGetId {
                    it[VendorSubscriptionsTable.vendorId] = vendorId
                    it[planId] = enterprisePlanId
                    it[status] = "ACTIVE"
                    it[startedAt] = Clock.System.now()
                    it[notes] = "Auto-migrated to Enterprise plan"
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                logger.info("Vendor $vendorId migrated to Enterprise plan")
            }
        }
    }

    fun clearAllDatabase() {
        transaction {
            // Use raw SQL DROP CASCADE to avoid foreign key ordering issues
            exec("DROP SCHEMA public CASCADE")
            exec("CREATE SCHEMA public")
            exec("GRANT ALL ON SCHEMA public TO PUBLIC")
        }
    }

    private val logger = LoggerFactory.getLogger("DatabaseConfig")

    private fun seedIfEmpty() {
        transaction {
            val vendorCount = VendorsTable.selectAll().count()
            if (vendorCount > 0L) return@transaction

            logger.info("Database is empty. Seeding comprehensive test data for all vendor types...")

            val passwordHash = AuthService.hashPassword("password123")
            val now = Clock.System.now()

            // ===================================================================
            // Helper data classes
            // ===================================================================
            data class VendorSeed(
                val name: String, val address: String, val phone: String,
                val businessType: String, val enableTables: Boolean, val enableDineIn: Boolean,
                val enableDelivery: Boolean, val enableInStore: Boolean, val taxEnabled: Boolean,
                val defaultTaxPercent: Double, val stockMode: String,
                val loyaltyEnabled: Boolean, val phonePrefix: String
            )

            data class CategorySeed(val catName: String, val order: Int)
            data class ItemSeed(val category: String, val itemName: String, val desc: String, val itemPrice: Double)
            data class CustomerSeed(val custName: String, val custPhone: String, val orderCount: Int, val totalSpent: Double, val points: Int)
            data class StockSeed(val itemName: String, val qty: Double, val minQty: Double, val costPrice: Double, val unit: String)
            data class WorkerSeed(val workerId: String, val fullName: String, val workerPhone: String?, val role: String, val salaryType: String, val salaryAmount: Double)

            // ===================================================================
            // 1. RESTAURANT — مطعم الشام
            // ===================================================================
            val vendors = listOf(
                VendorSeed("مطعم الشام", "١٥ شارع التحرير، وسط البلد، القاهرة", "+20100000001",
                    "RESTAURANT", enableTables = true, enableDineIn = true, enableDelivery = true,
                    enableInStore = false, taxEnabled = false, defaultTaxPercent = 0.0,
                    stockMode = "WARN", loyaltyEnabled = true, phonePrefix = "1"),
                // 2. PHARMACY — صيدلية الشفاء
                VendorSeed("صيدلية الشفاء", "٢٣ شارع الجمهورية، مصر الجديدة، القاهرة", "+20100000002",
                    "PHARMACY", enableTables = false, enableDineIn = false, enableDelivery = true,
                    enableInStore = true, taxEnabled = true, defaultTaxPercent = 14.0,
                    stockMode = "ENFORCE", loyaltyEnabled = true, phonePrefix = "2"),
                // 3. CAFE — كافيه لافندر
                VendorSeed("كافيه لافندر", "٧ شارع البطل أحمد عبدالعزيز، المهندسين", "+20100000003",
                    "CAFE", enableTables = true, enableDineIn = true, enableDelivery = true,
                    enableInStore = false, taxEnabled = false, defaultTaxPercent = 0.0,
                    stockMode = "WARN", loyaltyEnabled = true, phonePrefix = "3"),
                // 4. BAKERY — مخبز السنابل
                VendorSeed("مخبز السنابل", "١٢ شارع فيصل، الجيزة", "+20100000004",
                    "BAKERY", enableTables = false, enableDineIn = false, enableDelivery = true,
                    enableInStore = true, taxEnabled = false, defaultTaxPercent = 0.0,
                    stockMode = "WARN", loyaltyEnabled = false, phonePrefix = "4"),
                // 5. SUPERMARKET — سوبر ماركت الخير
                VendorSeed("سوبر ماركت الخير", "٩ شارع مصطفى النحاس، مدينة نصر", "+20100000005",
                    "SUPERMARKET", enableTables = false, enableDineIn = false, enableDelivery = true,
                    enableInStore = true, taxEnabled = true, defaultTaxPercent = 14.0,
                    stockMode = "ENFORCE", loyaltyEnabled = true, phonePrefix = "5"),
                // 6. RETAIL — محل لعب أطفال (Kids Toy Store)
                VendorSeed("محل لعب أطفال توي لاند", "١٨ شارع العروبة، المعادي، القاهرة", "+20100000006",
                    "RETAIL", enableTables = false, enableDineIn = false, enableDelivery = true,
                    enableInStore = true, taxEnabled = true, defaultTaxPercent = 14.0,
                    stockMode = "ENFORCE", loyaltyEnabled = true, phonePrefix = "6"),
            )

            // Categories per vendor type
            val vendorCategories = mapOf(
                "RESTAURANT" to listOf(CategorySeed("مشويات", 1), CategorySeed("أطباق رئيسية", 2), CategorySeed("مقبلات", 3), CategorySeed("مشروبات", 4), CategorySeed("حلويات", 5)),
                "PHARMACY" to listOf(CategorySeed("مسكنات", 1), CategorySeed("مضادات حيوية", 2), CategorySeed("فيتامينات ومكملات", 3), CategorySeed("أدوية البرد والسعال", 4), CategorySeed("العناية بالبشرة", 5), CategorySeed("مستلزمات طبية", 6)),
                "CAFE" to listOf(CategorySeed("قهوة ساخنة", 1), CategorySeed("قهوة باردة", 2), CategorySeed("مشروبات ساخنة", 3), CategorySeed("عصائر وسموذي", 4), CategorySeed("حلويات ومخبوزات", 5)),
                "BAKERY" to listOf(CategorySeed("خبز طازج", 1), CategorySeed("معجنات", 2), CategorySeed("حلويات شرقية", 3), CategorySeed("حلويات غربية", 4), CategorySeed("فطائر", 5)),
                "SUPERMARKET" to listOf(CategorySeed("ألبان وأجبان", 1), CategorySeed("مشروبات", 2), CategorySeed("معلبات وبقوليات", 3), CategorySeed("منظفات", 4), CategorySeed("خضار وفاكهة", 5), CategorySeed("لحوم ودواجن", 6)),
                "RETAIL" to listOf(CategorySeed("عرائس وشخصيات", 1), CategorySeed("ألعاب تعليمية", 2), CategorySeed("ألعاب تركيب وبازل", 3), CategorySeed("سيارات ومركبات", 4), CategorySeed("ألعاب خارجية", 5), CategorySeed("ألعاب إلكترونية", 6)),
            )

            // Items per vendor type
            val vendorItems = mapOf(
                "RESTAURANT" to listOf(
                    ItemSeed("مشويات", "مشكل مشويات", "لحم - كفتة - فراخ", 250.0),
                    ItemSeed("مشويات", "كباب حلبي", "كباب لحم على الفحم", 180.0),
                    ItemSeed("مشويات", "شيش طاووق", "صدور فراخ متبلة مشوية", 150.0),
                    ItemSeed("أطباق رئيسية", "أرز بسمتي باللحمة", "أرز بسمتي مع قطع اللحم", 120.0),
                    ItemSeed("أطباق رئيسية", "فتة لحمة", "فتة مصرية باللحم البلدي", 95.0),
                    ItemSeed("أطباق رئيسية", "محشي ورق عنب", "ورق عنب محشي أرز ولحمة", 85.0),
                    ItemSeed("مقبلات", "حمص بالطحينة", "حمص طازج مع طحينة وليمون", 40.0),
                    ItemSeed("مقبلات", "بابا غنوج", "باذنجان مشوي مع طحينة", 35.0),
                    ItemSeed("مقبلات", "سلطة فتوش", "سلطة لبنانية طازجة", 30.0),
                    ItemSeed("مشروبات", "عصير مانجو طازج", "مانجو طازج ١٠٠٪", 35.0),
                    ItemSeed("مشروبات", "ليمون بالنعناع", "ليمون طازج بالنعناع", 25.0),
                    ItemSeed("حلويات", "كنافة نابلسية", "كنافة بالجبنة", 55.0),
                    ItemSeed("حلويات", "أم علي", "أم علي بالمكسرات والقشطة", 45.0),
                ),
                "PHARMACY" to listOf(
                    ItemSeed("مسكنات", "بانادول أقراص", "باراسيتامول ٥٠٠ مجم - ٢٤ قرص", 36.0),
                    ItemSeed("مسكنات", "بروفين ٤٠٠", "ايبوبروفين ٤٠٠ مجم - ٢٠ قرص", 42.0),
                    ItemSeed("مسكنات", "كتافلام ٥٠", "ديكلوفيناك ٥٠ مجم - ٢٠ قرص", 54.0),
                    ItemSeed("مضادات حيوية", "أوجمنتين ١ جم", "أموكسيسيلين + كلافيولانيك - ١٤ قرص", 125.0),
                    ItemSeed("مضادات حيوية", "سيفوتاكس حقن", "سيفوتاكسيم ١ جم - أمبول", 38.0),
                    ItemSeed("مضادات حيوية", "فلاجيل ٥٠٠", "ميترونيدازول ٥٠٠ مجم - ٢٠ قرص", 18.0),
                    ItemSeed("فيتامينات ومكملات", "فيتامين سي ١٠٠٠", "فوار فيتامين سي - ٢٠ قرص", 65.0),
                    ItemSeed("فيتامينات ومكملات", "أوميجا ٣", "زيت السمك ١٠٠٠ مجم - ٣٠ كبسولة", 120.0),
                    ItemSeed("فيتامينات ومكملات", "كالسيوم + فيتامين د", "كالسيوم ٦٠٠ مجم - ٣٠ قرص", 85.0),
                    ItemSeed("أدوية البرد والسعال", "كونجستال", "أقراص مزيلة للاحتقان - ٢٠ قرص", 30.0),
                    ItemSeed("أدوية البرد والسعال", "سينلرج شراب", "شراب للبرد والسعال - ١٢٠ مل", 42.0),
                    ItemSeed("العناية بالبشرة", "بيبانثين كريم", "كريم مرطب ومعالج - ٣٠ جم", 95.0),
                    ItemSeed("العناية بالبشرة", "صن بلوك SPF50", "واقي شمس ٥٠ مل", 180.0),
                    ItemSeed("مستلزمات طبية", "كمامات طبية", "عبوة ٥٠ كمامة", 50.0),
                    ItemSeed("مستلزمات طبية", "قفازات لاتكس", "عبوة ١٠٠ قفاز - مقاس M", 75.0),
                    ItemSeed("مستلزمات طبية", "جهاز قياس ضغط", "جهاز ديجيتال لقياس ضغط الدم", 450.0),
                ),
                "CAFE" to listOf(
                    ItemSeed("قهوة ساخنة", "إسبريسو", "شوت إسبريسو مزدوج", 35.0),
                    ItemSeed("قهوة ساخنة", "كابتشينو", "إسبريسو مع حليب مبخر ورغوة", 45.0),
                    ItemSeed("قهوة ساخنة", "لاتيه", "إسبريسو مع حليب ساخن", 50.0),
                    ItemSeed("قهوة ساخنة", "موكا", "إسبريسو + شوكولاتة + حليب", 55.0),
                    ItemSeed("قهوة باردة", "آيس لاتيه", "إسبريسو بارد مع حليب", 55.0),
                    ItemSeed("قهوة باردة", "كولد برو", "قهوة مخمرة على البارد ١٢ ساعة", 60.0),
                    ItemSeed("قهوة باردة", "فرابتشينو كراميل", "قهوة مثلجة بالكراميل والكريمة", 65.0),
                    ItemSeed("مشروبات ساخنة", "شاي أخضر", "شاي أخضر ياباني", 25.0),
                    ItemSeed("مشروبات ساخنة", "هوت شوكولت", "شوكولاتة ساخنة بالكريمة", 50.0),
                    ItemSeed("عصائر وسموذي", "سموذي مانجو", "مانجو + موز + حليب", 55.0),
                    ItemSeed("عصائر وسموذي", "عصير برتقال طازج", "برتقال طازج ١٠٠٪", 40.0),
                    ItemSeed("حلويات ومخبوزات", "تشيز كيك", "تشيز كيك بالتوت", 65.0),
                    ItemSeed("حلويات ومخبوزات", "كروسان شوكولاتة", "كروسان فرنسي بحشو الشوكولاتة", 40.0),
                    ItemSeed("حلويات ومخبوزات", "كوكيز", "كوكيز شوكولاتة طازج", 30.0),
                ),
                "BAKERY" to listOf(
                    ItemSeed("خبز طازج", "عيش بلدي", "رغيف عيش بلدي طازج", 2.0),
                    ItemSeed("خبز طازج", "عيش فينو", "عيش فينو سادة", 3.0),
                    ItemSeed("خبز طازج", "توست أبيض", "توست شرائح - عبوة", 15.0),
                    ItemSeed("معجنات", "كرواسون سادة", "كرواسون فرنسي طازج", 12.0),
                    ItemSeed("معجنات", "باتيه باللحمة", "باتيه محشي لحمة مفرومة", 15.0),
                    ItemSeed("معجنات", "سمبوسك جبنة", "سمبوسك بالجبنة والنعناع", 10.0),
                    ItemSeed("حلويات شرقية", "بسبوسة بالقشطة", "بسبوسة مصرية بالقشطة", 25.0),
                    ItemSeed("حلويات شرقية", "كنافة بالمانجو", "كنافة بحشو المانجو", 35.0),
                    ItemSeed("حلويات شرقية", "بقلاوة", "بقلاوة بالمكسرات - ٦ قطع", 40.0),
                    ItemSeed("حلويات غربية", "تورتة شوكولاتة", "تورتة شوكولاتة - ١ كجم", 250.0),
                    ItemSeed("حلويات غربية", "تارت فراولة", "تارت بالفراولة الطازجة", 45.0),
                    ItemSeed("فطائر", "فطيرة مشلتت", "فطيرة مشلتت بالسمن البلدي", 20.0),
                    ItemSeed("فطائر", "فطيرة بالجبنة والعسل", "فطيرة محشية جبنة وعسل أبيض", 30.0),
                ),
                "SUPERMARKET" to listOf(
                    ItemSeed("ألبان وأجبان", "حليب كامل الدسم ١ لتر", "حليب طازج كامل الدسم", 28.0),
                    ItemSeed("ألبان وأجبان", "جبنة بيضاء ١ كجم", "جبنة بيضاء مصرية", 85.0),
                    ItemSeed("ألبان وأجبان", "زبادي ١٧٠ جم", "زبادي طبيعي", 8.0),
                    ItemSeed("مشروبات", "مياه معدنية ١.٥ لتر", "مياه معدنية نقية", 5.0),
                    ItemSeed("مشروبات", "عصير برتقال ١ لتر", "عصير برتقال طبيعي ١٠٠٪", 35.0),
                    ItemSeed("مشروبات", "بيبسي ٣٣٠ مل", "بيبسي علبة", 10.0),
                    ItemSeed("معلبات وبقوليات", "فول مدمس ٤٠٠ جم", "فول مدمس جاهز", 12.0),
                    ItemSeed("معلبات وبقوليات", "تونة ١٧٠ جم", "تونة بالزيت", 28.0),
                    ItemSeed("معلبات وبقوليات", "عدس أصفر ١ كجم", "عدس أصفر مجروش", 45.0),
                    ItemSeed("منظفات", "صابون سائل ١ لتر", "صابون سائل لغسيل الأطباق", 30.0),
                    ItemSeed("منظفات", "مسحوق غسيل ٢ كجم", "مسحوق غسيل أوتوماتيك", 85.0),
                    ItemSeed("خضار وفاكهة", "طماطم ١ كجم", "طماطم طازجة", 15.0),
                    ItemSeed("خضار وفاكهة", "بطاطس ١ كجم", "بطاطس طازجة", 12.0),
                    ItemSeed("لحوم ودواجن", "صدور فراخ ١ كجم", "صدور فراخ طازجة", 130.0),
                    ItemSeed("لحوم ودواجن", "لحمة بلدي ١ كجم", "لحم بقري طازج", 350.0),
                ),
                "RETAIL" to listOf(
                    ItemSeed("عرائس وشخصيات", "عروسة باربي كلاسيك", "عروسة باربي مع ملابس إضافية", 450.0),
                    ItemSeed("عرائس وشخصيات", "دبدوب كبير ٨٠ سم", "دبدوب قطيفة ناعم", 350.0),
                    ItemSeed("عرائس وشخصيات", "شخصية سبايدرمان أكشن", "مجسم سبايدرمان متحرك ٣٠ سم", 280.0),
                    ItemSeed("ألعاب تعليمية", "صلصال ألوان ١٢ لون", "صلصال آمن للأطفال - ١٢ لون", 85.0),
                    ItemSeed("ألعاب تعليمية", "حروف مغناطيسية عربي", "حروف عربية مغناطيسية للتعلم", 120.0),
                    ItemSeed("ألعاب تعليمية", "ألوان خشبية ٢٤ لون", "ألوان خشبية عالية الجودة", 65.0),
                    ItemSeed("ألعاب تركيب وبازل", "ليجو كلاسيك ٥٠٠ قطعة", "مكعبات ليجو كلاسيكية", 650.0),
                    ItemSeed("ألعاب تركيب وبازل", "بازل ١٠٠ قطعة خريطة مصر", "بازل تعليمي خريطة مصر", 95.0),
                    ItemSeed("ألعاب تركيب وبازل", "مكعبات تركيب خشبية", "مكعبات خشبية ملونة ٥٠ قطعة", 150.0),
                    ItemSeed("سيارات ومركبات", "سيارة ريموت كنترول", "سيارة سباق ريموت كنترول", 380.0),
                    ItemSeed("سيارات ومركبات", "شاحنة نقل كبيرة", "شاحنة نقل بقلاب ٤٠ سم", 220.0),
                    ItemSeed("سيارات ومركبات", "طقم سيارات صغيرة ١٠ قطع", "مجموعة سيارات معدنية مصغرة", 180.0),
                    ItemSeed("ألعاب خارجية", "كورة قدم مقاس ٤", "كورة قدم للأطفال", 150.0),
                    ItemSeed("ألعاب خارجية", "طوق هولا هوب", "طوق رياضي ملون للأطفال", 60.0),
                    ItemSeed("ألعاب خارجية", "مسدس نيرف بلاستر", "مسدس نيرف مع ١٠ سهام", 320.0),
                    ItemSeed("ألعاب إلكترونية", "تابلت تعليمي للأطفال", "تابلت تعليمي عربي/إنجليزي", 550.0),
                    ItemSeed("ألعاب إلكترونية", "جيم باد ٤٠٠ لعبة", "جهاز ألعاب محمول ٤٠٠ لعبة كلاسيكية", 280.0),
                    ItemSeed("ألعاب إلكترونية", "روبوت ذكي ريموت", "روبوت ذكي بريموت كنترول وأصوات", 420.0),
                ),
            )

            // Customers per vendor type
            val vendorCustomers = mapOf(
                "RESTAURANT" to listOf(
                    CustomerSeed("أحمد محمود", "+20101000001", 25, 3500.0, 350),
                    CustomerSeed("فاطمة علي", "+20101000002", 18, 2200.0, 220),
                    CustomerSeed("محمد حسن", "+20101000003", 12, 1800.0, 180),
                    CustomerSeed("نور الدين", "+20101000004", 8, 950.0, 95),
                ),
                "PHARMACY" to listOf(
                    CustomerSeed("سارة أحمد", "+20102000001", 30, 4500.0, 450),
                    CustomerSeed("عمرو خالد", "+20102000002", 22, 3200.0, 320),
                    CustomerSeed("هدى محمد", "+20102000003", 15, 2100.0, 210),
                    CustomerSeed("يوسف إبراهيم", "+20102000004", 10, 1500.0, 150),
                    CustomerSeed("منى عبدالله", "+20102000005", 5, 800.0, 80),
                ),
                "CAFE" to listOf(
                    CustomerSeed("كريم وائل", "+20103000001", 40, 2800.0, 280),
                    CustomerSeed("ريم سامي", "+20103000002", 35, 2400.0, 240),
                    CustomerSeed("عمر طارق", "+20103000003", 20, 1500.0, 150),
                ),
                "BAKERY" to listOf(
                    CustomerSeed("حسين علي", "+20104000001", 50, 1200.0, 0),
                    CustomerSeed("آية محمد", "+20104000002", 30, 800.0, 0),
                ),
                "SUPERMARKET" to listOf(
                    CustomerSeed("خالد عبدالرحمن", "+20105000001", 60, 8500.0, 850),
                    CustomerSeed("نادية حسين", "+20105000002", 45, 6200.0, 620),
                    CustomerSeed("إسلام فتحي", "+20105000003", 35, 4800.0, 480),
                    CustomerSeed("سمية أحمد", "+20105000004", 20, 3000.0, 300),
                ),
                "RETAIL" to listOf(
                    CustomerSeed("ماما نورهان", "+20106000001", 15, 4200.0, 420),
                    CustomerSeed("بابا أحمد", "+20106000002", 10, 2800.0, 280),
                    CustomerSeed("تيتة فاطمة", "+20106000003", 8, 1950.0, 195),
                    CustomerSeed("سارة وأولادها", "+20106000004", 20, 5500.0, 550),
                    CustomerSeed("محمد حسام", "+20106000005", 5, 1200.0, 120),
                ),
            )

            // Stock per vendor type
            val vendorStock = mapOf(
                "RESTAURANT" to listOf(
                    StockSeed("لحم بقري", 50.0, 10.0, 280.0, "KILOGRAM"),
                    StockSeed("صدور فراخ", 30.0, 8.0, 120.0, "KILOGRAM"),
                    StockSeed("أرز بسمتي", 100.0, 20.0, 45.0, "KILOGRAM"),
                    StockSeed("طحينة", 15.0, 5.0, 60.0, "KILOGRAM"),
                    StockSeed("زيت زيتون", 20.0, 5.0, 120.0, "LITER"),
                ),
                "PHARMACY" to listOf(
                    StockSeed("بانادول أقراص", 200.0, 50.0, 22.0, "PACK"),
                    StockSeed("أوجمنتين ١ جم", 80.0, 20.0, 85.0, "PACK"),
                    StockSeed("فيتامين سي فوار", 150.0, 30.0, 40.0, "PACK"),
                    StockSeed("كمامات طبية", 100.0, 20.0, 30.0, "PACK"),
                    StockSeed("قفازات لاتكس", 50.0, 10.0, 45.0, "PACK"),
                    StockSeed("بيبانثين كريم", 60.0, 15.0, 65.0, "PIECE"),
                ),
                "CAFE" to listOf(
                    StockSeed("بن إسبريسو", 25.0, 5.0, 350.0, "KILOGRAM"),
                    StockSeed("حليب طازج", 50.0, 15.0, 25.0, "LITER"),
                    StockSeed("شوكولاتة", 10.0, 3.0, 180.0, "KILOGRAM"),
                    StockSeed("كراميل صوص", 8.0, 2.0, 120.0, "LITER"),
                    StockSeed("كريمة خفق", 12.0, 4.0, 80.0, "LITER"),
                ),
                "BAKERY" to listOf(
                    StockSeed("دقيق أبيض", 200.0, 50.0, 18.0, "KILOGRAM"),
                    StockSeed("سكر", 80.0, 20.0, 22.0, "KILOGRAM"),
                    StockSeed("زبدة", 30.0, 10.0, 130.0, "KILOGRAM"),
                    StockSeed("خميرة فورية", 20.0, 5.0, 45.0, "KILOGRAM"),
                    StockSeed("بيض", 500.0, 100.0, 3.0, "PIECE"),
                ),
                "SUPERMARKET" to listOf(
                    StockSeed("حليب كامل ١ لتر", 300.0, 80.0, 20.0, "PIECE"),
                    StockSeed("مياه معدنية", 500.0, 100.0, 3.0, "PIECE"),
                    StockSeed("أرز ١ كجم", 200.0, 50.0, 25.0, "PIECE"),
                    StockSeed("زيت طبخ ١ لتر", 150.0, 40.0, 55.0, "PIECE"),
                    StockSeed("سكر ١ كجم", 100.0, 30.0, 20.0, "PIECE"),
                ),
                "RETAIL" to listOf(
                    StockSeed("عروسة باربي كلاسيك", 25.0, 5.0, 280.0, "PIECE"),
                    StockSeed("دبدوب كبير ٨٠ سم", 15.0, 3.0, 200.0, "PIECE"),
                    StockSeed("شخصية سبايدرمان أكشن", 20.0, 5.0, 170.0, "PIECE"),
                    StockSeed("ليجو كلاسيك ٥٠٠ قطعة", 12.0, 3.0, 400.0, "PIECE"),
                    StockSeed("صلصال ألوان ١٢ لون", 40.0, 10.0, 50.0, "PIECE"),
                    StockSeed("سيارة ريموت كنترول", 18.0, 5.0, 230.0, "PIECE"),
                    StockSeed("كورة قدم مقاس ٤", 30.0, 8.0, 90.0, "PIECE"),
                    StockSeed("تابلت تعليمي للأطفال", 10.0, 3.0, 350.0, "PIECE"),
                    StockSeed("مسدس نيرف بلاستر", 22.0, 5.0, 195.0, "PIECE"),
                    StockSeed("روبوت ذكي ريموت", 8.0, 2.0, 260.0, "PIECE"),
                ),
            )

            // Workers per vendor type
            val vendorWorkers = mapOf(
                "RESTAURANT" to listOf(
                    WorkerSeed("WRK-R01", "شيف أحمد", "+20111000001", "شيف رئيسي", "MONTHLY", 8000.0),
                    WorkerSeed("WRK-R02", "محمود السفرجي", "+20111000002", "سفرجي", "DAILY", 250.0),
                    WorkerSeed("WRK-R03", "علي الغسال", null, "عامل نظافة", "DAILY", 150.0),
                ),
                "PHARMACY" to listOf(
                    WorkerSeed("WRK-P01", "د. هالة سعيد", "+20112000001", "صيدلي", "MONTHLY", 10000.0),
                    WorkerSeed("WRK-P02", "أمل فاروق", "+20112000002", "صيدلي مساعد", "MONTHLY", 6000.0),
                ),
                "CAFE" to listOf(
                    WorkerSeed("WRK-C01", "باريستا كريم", "+20113000001", "باريستا", "MONTHLY", 6000.0),
                    WorkerSeed("WRK-C02", "باريستا نور", "+20113000002", "باريستا", "MONTHLY", 5500.0),
                    WorkerSeed("WRK-C03", "سامي الويتر", "+20113000003", "ويتر", "DAILY", 200.0),
                ),
                "BAKERY" to listOf(
                    WorkerSeed("WRK-B01", "الأسطى حسن", "+20114000001", "خباز رئيسي", "MONTHLY", 7000.0),
                    WorkerSeed("WRK-B02", "مساعد عادل", "+20114000002", "مساعد خباز", "DAILY", 200.0),
                ),
                "SUPERMARKET" to listOf(
                    WorkerSeed("WRK-S01", "ممدوح المحاسب", "+20115000001", "محاسب", "MONTHLY", 5500.0),
                    WorkerSeed("WRK-S02", "سعيد الرفوف", "+20115000002", "عامل رفوف", "DAILY", 180.0),
                    WorkerSeed("WRK-S03", "حسام المخزن", "+20115000003", "أمين مخزن", "MONTHLY", 5000.0),
                ),
                "RETAIL" to listOf(
                    WorkerSeed("WRK-T01", "مصطفى أمين المخزن", "+20116000001", "أمين مخزن", "MONTHLY", 5000.0),
                    WorkerSeed("WRK-T02", "رانيا المبيعات", "+20116000002", "مسؤولة مبيعات", "MONTHLY", 4500.0),
                    WorkerSeed("WRK-T03", "عمرو التغليف", "+20116000003", "عامل تغليف", "DAILY", 180.0),
                ),
            )

            // ===================================================================
            // Create each vendor with all its data
            // ===================================================================
            for ((idx, vendor) in vendors.withIndex()) {
                val bt = vendor.businessType

                // Create vendor
                val vendorId = VendorsTable.insertAndGetId {
                    it[name] = vendor.name
                    it[address] = vendor.address
                    it[contactPhone] = vendor.phone
                    it[walletPhone] = vendor.phone
                    it[businessType] = bt
                    it[enableTables] = vendor.enableTables
                    it[enableDineIn] = vendor.enableDineIn
                    it[enableDelivery] = vendor.enableDelivery
                    it[enableTakeaway] = true
                    it[enableInStore] = vendor.enableInStore
                    it[enablePickupLater] = vendor.enableInStore
                    it[taxEnabled] = vendor.taxEnabled
                    it[defaultTaxPercent] = vendor.defaultTaxPercent.toBigDecimal()
                    it[stockMode] = vendor.stockMode
                    it[loyaltyEnabled] = vendor.loyaltyEnabled
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                logger.info("Created vendor: ${vendor.name} (${bt})")

                // Create users: Manager, Cashier, Delivery
                val prefix = vendor.phonePrefix
                val userRoles = mutableListOf(
                    Triple("MANAGER", "مدير ${vendor.name}", "+20${prefix}0${prefix}0${prefix}001"),
                    Triple("CASHIER", "كاشير ${vendor.name}", "+20${prefix}0${prefix}0${prefix}002"),
                )
                if (vendor.enableDelivery) {
                    userRoles.add(Triple("DELIVERY", "سائق ${vendor.name}", "+20${prefix}0${prefix}0${prefix}003"))
                }
                userRoles.add(Triple("KITCHEN", "مطبخ ${vendor.name}", "+20${prefix}0${prefix}0${prefix}004"))

                val userIdMap = mutableMapOf<String, java.util.UUID>() // role -> userId
                userRoles.forEach { (role, userName, phone) ->
                    val userId = UsersTable.insertAndGetId {
                        it[UsersTable.vendorId] = vendorId.value
                        it[UsersTable.role] = role
                        it[UsersTable.name] = userName
                        it[UsersTable.phone] = phone
                        it[email] = "${role.lowercase()}.${bt.lowercase()}@test.com"
                        it[UsersTable.passwordHash] = passwordHash
                        it[active] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    userIdMap[role] = userId.value
                }

                // Create categories
                val catMap = mutableMapOf<String, java.util.UUID>()
                vendorCategories[bt]?.forEach { cat ->
                    val catId = CategoriesTable.insertAndGetId {
                        it[CategoriesTable.vendorId] = vendorId.value
                        it[CategoriesTable.name] = cat.catName
                        it[displayOrder] = cat.order
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    catMap[cat.catName] = catId.value
                }

                // Create items
                data class CreatedItem(val id: java.util.UUID, val name: String, val price: Double)
                val createdItems = mutableListOf<CreatedItem>()
                vendorItems[bt]?.forEach { item ->
                    val catId = catMap[item.category] ?: return@forEach
                    val itemId = ItemsTable.insertAndGetId {
                        it[ItemsTable.vendorId] = vendorId.value
                        it[categoryId] = catId
                        it[ItemsTable.name] = item.itemName
                        it[description] = item.desc
                        it[price] = item.itemPrice.toBigDecimal()
                        it[available] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    createdItems.add(CreatedItem(itemId.value, item.itemName, item.itemPrice))
                }

                // Create tables (only for dine-in vendors)
                val tableIds = mutableListOf<java.util.UUID>()
                if (vendor.enableTables) {
                    (1..6).forEach { num ->
                        val tId = TablesTable.insertAndGetId {
                            it[TablesTable.vendorId] = vendorId.value
                            it[number] = "T$num"
                            it[capacity] = when (num) { 1, 4 -> 2; 2, 5 -> 4; 3, 6 -> 6; else -> 4 }
                            it[status] = if (num <= 2) "OCCUPIED" else "AVAILABLE"
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        tableIds.add(tId.value)
                    }
                }

                // Create customers
                val customerIds = mutableListOf<java.util.UUID>()
                vendorCustomers[bt]?.forEach { cust ->
                    val custId = CustomersTable.insertAndGetId {
                        it[CustomersTable.vendorId] = vendorId.value
                        it[CustomersTable.name] = cust.custName
                        it[phone] = cust.custPhone
                        it[orderCount] = cust.orderCount
                        it[totalSpent] = cust.totalSpent.toBigDecimal()
                        it[pointsBalance] = cust.points
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    customerIds.add(custId.value)
                }

                // Create stock
                data class CreatedStock(val id: java.util.UUID, val name: String, val qty: Double, val unit: String)
                val createdStocks = mutableListOf<CreatedStock>()
                vendorStock[bt]?.forEach { stock ->
                    val sId = StockTable.insertAndGetId {
                        it[StockTable.vendorId] = vendorId.value
                        it[itemName] = stock.itemName
                        it[quantity] = stock.qty.toBigDecimal()
                        it[minQuantity] = stock.minQty.toBigDecimal()
                        it[costPrice] = stock.costPrice.toBigDecimal()
                        it[unit] = stock.unit
                        it[baseUnit] = stock.unit
                        it[isMenuItem] = false
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    createdStocks.add(CreatedStock(sId.value, stock.itemName, stock.qty, stock.unit))
                }

                // Create workers
                data class CreatedWorker(val id: java.util.UUID, val name: String, val role: String, val salaryType: String, val salaryAmount: Double)
                val createdWorkers = mutableListOf<CreatedWorker>()
                vendorWorkers[bt]?.forEach { worker ->
                    val wId = WorkersTable.insertAndGetId {
                        it[WorkersTable.vendorId] = vendorId.value
                        it[workerId] = worker.workerId
                        it[fullName] = worker.fullName
                        it[WorkersTable.phone] = worker.workerPhone
                        it[role] = worker.role
                        it[salaryType] = worker.salaryType
                        it[salaryAmount] = worker.salaryAmount.toBigDecimal()
                        it[active] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    createdWorkers.add(CreatedWorker(wId.value, worker.fullName, worker.role, worker.salaryType, worker.salaryAmount))
                }

                // ─── Create orders with various statuses ───────────────────
                val cashierId = userIdMap["CASHIER"]!!
                val deliveryId = userIdMap["DELIVERY"]
                val paymentMethods = listOf("CASH", "WALLET", "CARD")

                // Determine valid channels for this vendor
                val channels = mutableListOf<String>()
                if (vendor.enableDineIn) channels.add("DINE_IN")
                if (vendor.enableDelivery) channels.add("DELIVERY")
                channels.add("TAKEAWAY") // always enabled
                if (vendor.enableInStore) channels.add("IN_STORE")

                // Status per channel
                val statusesByChannel = mapOf(
                    "DINE_IN" to listOf("CREATED", "IN_PREPARATION", "READY", "SERVED", "COMPLETED", "CANCELED", "REFUNDED"),
                    "DELIVERY" to listOf("CREATED", "IN_PREPARATION", "READY", "ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED", "CANCELED", "DELIVERY_FAILED", "REFUNDED"),
                    "TAKEAWAY" to listOf("CREATED", "IN_PREPARATION", "READY", "PICKED_UP", "COMPLETED", "CANCELED", "REFUNDED"),
                    "IN_STORE" to listOf("CREATED", "COMPLETED", "CANCELED", "REFUNDED"),
                    "PICKUP_LATER" to listOf("CREATED", "IN_PREPARATION", "READY", "PICKED_UP", "COMPLETED", "CANCELED", "REFUNDED")
                )

                data class CreatedOrder(val id: java.util.UUID, val status: String, val channel: String, val total: Double, val time: kotlinx.datetime.Instant)
                val createdOrders = mutableListOf<CreatedOrder>()
                var orderCounter = 0
                for (channel in channels) {
                    val statuses = statusesByChannel[channel] ?: continue
                    for (status in statuses) {
                        // Pick 2-3 random items for each order
                        val orderItems = createdItems.shuffled().take(minOf(3, createdItems.size))
                        val itemsSubtotal = orderItems.sumOf { it.price }
                        val deliveryFee = if (channel == "DELIVERY") 15.0 else 0.0
                        val taxAmount = if (vendor.taxEnabled) itemsSubtotal * vendor.defaultTaxPercent / 100.0 else 0.0
                        val orderTotal = itemsSubtotal + deliveryFee + taxAmount

                        // Payment status based on order status
                        val paymentStatus = when (status) {
                            "COMPLETED" -> "PAID"
                            "REFUNDED" -> "REFUNDED"
                            "CANCELED" -> "PENDING"
                            else -> if (status in listOf("SERVED", "DELIVERED", "PICKED_UP", "OUT_FOR_DELIVERY", "ASSIGNED")) "PAID" else "PENDING"
                        }
                        val paymentTiming = if (status in listOf("CREATED", "IN_PREPARATION")) "PAY_LATER" else "PAY_NOW"
                        val pm = paymentMethods[orderCounter % paymentMethods.size]

                        // Table for dine-in orders
                        val tableUuid = if (channel == "DINE_IN" && tableIds.isNotEmpty()) tableIds[orderCounter % tableIds.size] else null
                        // Customer for some orders
                        val custUuid = if (customerIds.isNotEmpty()) customerIds[orderCounter % customerIds.size] else null

                        // Vary the creation time to spread orders across the last 7 days
                        val offsetHours = (orderCounter * 3L) + 1L
                        val orderTime = now.minus(kotlin.time.Duration.parse("${offsetHours}h"))

                        val orderId = OrdersTable.insertAndGetId {
                            it[OrdersTable.vendorId] = vendorId.value
                            it[OrdersTable.channel] = channel
                            it[OrdersTable.status] = status
                            if (tableUuid != null) it[tableId] = tableUuid
                            it[OrdersTable.cashierId] = cashierId
                            if (channel == "DELIVERY" && deliveryId != null && status in listOf("ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED", "DELIVERY_FAILED")) {
                                it[deliveryUserId] = deliveryId
                            }
                            if (custUuid != null) it[customerId] = custUuid
                            it[paymentMethod] = pm
                            it[OrdersTable.paymentStatus] = paymentStatus
                            it[OrdersTable.paymentTiming] = paymentTiming
                            if (paymentStatus == "PAID") it[paymentConfirmedAt] = orderTime
                            it[subtotal] = itemsSubtotal.toBigDecimal()
                            it[OrdersTable.deliveryFee] = deliveryFee.toBigDecimal()
                            it[tax] = taxAmount.toBigDecimal()
                            it[taxPercent] = vendor.defaultTaxPercent.toBigDecimal()
                            it[total] = orderTotal.toBigDecimal()
                            it[notes] = if (orderCounter % 3 == 0) "ملاحظات تجريبية" else null
                            it[createdAt] = orderTime
                            it[updatedAt] = orderTime
                            if (status == "REFUNDED") {
                                it[refundedAt] = orderTime
                                it[refundedBy] = cashierId
                                it[refundReason] = "طلب العميل"
                            }
                        }

                        createdOrders.add(CreatedOrder(orderId.value, status, channel, orderTotal, orderTime))

                        // Insert order items
                        orderItems.forEach { oi ->
                            OrderItemsTable.insertAndGetId {
                                it[OrderItemsTable.orderId] = orderId.value
                                it[itemId] = oi.id
                                it[itemNameSnapshot] = oi.name
                                it[itemPriceSnapshot] = oi.price.toBigDecimal()
                                it[quantity] = (orderCounter % 3) + 1
                                it[note] = if (orderCounter % 4 == 0) "بدون سكر" else null
                                it[createdAt] = orderTime
                            }
                        }
                        orderCounter++
                    }
                }

                // ─── Item Variants (sizes, extras) for food/drink vendors ─────
                val variantItems = createdItems.take(minOf(4, createdItems.size))
                val variantGroupNames = when (bt) {
                    "RESTAURANT" -> listOf("الحجم" to listOf("صغير" to 0.0, "وسط" to 10.0, "كبير" to 20.0), "إضافات" to listOf("جبنة إضافية" to 5.0, "صلصة حارة" to 3.0, "سلطة جانبية" to 8.0))
                    "CAFE" -> listOf("الحجم" to listOf("صغير" to 0.0, "وسط" to 5.0, "كبير" to 10.0), "الحليب" to listOf("حليب كامل" to 0.0, "حليب لوز" to 5.0, "حليب شوفان" to 7.0))
                    "BAKERY" -> listOf("الحجم" to listOf("قطعة" to 0.0, "نصف كيلو" to 20.0, "كيلو" to 40.0))
                    "PHARMACY" -> listOf("التركيز" to listOf("عادي" to 0.0, "مركز" to 15.0))
                    else -> listOf("الحجم" to listOf("صغير" to 0.0, "كبير" to 10.0))
                }
                variantItems.forEach { item ->
                    variantGroupNames.forEachIndexed { gIdx, (groupName, options) ->
                        val groupId = ItemVariantGroupsTable.insertAndGetId {
                            it[itemId] = item.id
                            it[name] = groupName
                            it[required] = gIdx == 0
                            it[displayOrder] = gIdx
                            it[createdAt] = now
                        }
                        options.forEachIndexed { oIdx, (optName, priceAdj) ->
                            ItemVariantOptionsTable.insertAndGetId {
                                it[ItemVariantOptionsTable.groupId] = groupId.value
                                it[name] = optName
                                it[priceAdjustment] = priceAdj.toBigDecimal()
                                it[isDefault] = oIdx == 0
                                it[displayOrder] = oIdx
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Customer Addresses ──────────────────────────────────────
                val addressLabels = listOf("المنزل" to "١٢ شارع الملك فيصل، الجيزة", "العمل" to "٤٥ شارع التحرير، وسط البلد", "آخر" to "٧ شارع المعز، الحسين")
                customerIds.forEachIndexed { cIdx, custId ->
                    val addrCount = if (cIdx == 0) 2 else 1
                    (0 until addrCount).forEach { aIdx ->
                        val (label, addr) = addressLabels[(cIdx + aIdx) % addressLabels.size]
                        CustomerAddressesTable.insertAndGetId {
                            it[customerId] = custId
                            it[CustomerAddressesTable.label] = label
                            it[address] = addr
                            it[geoLat] = 30.0 + (cIdx * 0.01)
                            it[geoLng] = 31.2 + (cIdx * 0.01)
                            it[isDefault] = aIdx == 0
                            it[createdAt] = now
                        }
                    }
                }

                // ─── Tax Places (for tax-enabled vendors) ────────────────────
                if (vendor.taxEnabled) {
                    listOf(
                        Triple("داخل المحل", vendor.defaultTaxPercent, true),
                        Triple("توصيل", vendor.defaultTaxPercent, false),
                        Triple("بدون ضريبة", 0.0, false)
                    ).forEachIndexed { idx, (placeName, taxPct, default) ->
                        TaxPlacesTable.insertAndGetId {
                            it[TaxPlacesTable.vendorId] = vendorId.value
                            it[name] = placeName
                            it[taxPercent] = taxPct.toBigDecimal()
                            it[isDefault] = default
                            it[displayOrder] = idx
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                }

                // ─── Offers & Offer Items ────────────────────────────────────
                val offerDefs = listOf(
                    Triple("عرض اليوم", "PERCENT", 15.0),
                    Triple("عرض خاص ٢٠٪", "PERCENT", 20.0),
                    Triple("خصم ثابت", "FIXED_PRICE", 10.0)
                )
                offerDefs.forEachIndexed { oIdx, (offerName, discType, discVal) ->
                    val offerId = OffersTable.insertAndGetId {
                        it[OffersTable.vendorId] = vendorId.value
                        it[name] = offerName
                        it[description] = "عرض تجريبي للعملاء"
                        it[discountType] = discType
                        it[discountValue] = discVal.toBigDecimal()
                        it[active] = oIdx < 2
                        it[displayOrder] = oIdx
                        it[promoCode] = if (oIdx == 1) "SAVE20" else null
                        it[maxUses] = if (oIdx == 1) 100 else null
                        it[usedCount] = if (oIdx == 1) 12 else 0
                        it[startsAt] = now.minus(kotlin.time.Duration.parse("7d")).toEpochMilliseconds()
                        it[expiresAt] = now.plus(kotlin.time.Duration.parse("30d")).toEpochMilliseconds()
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    // Attach 2-3 items per offer
                    createdItems.shuffled().take(minOf(3, createdItems.size)).forEachIndexed { iIdx, item ->
                        OfferItemsTable.insertAndGetId {
                            it[OfferItemsTable.offerId] = offerId.value
                            it[itemId] = item.id
                            it[quantity] = iIdx + 1
                            it[createdAt] = now
                        }
                    }
                }

                // ─── Points Transactions (for loyalty-enabled vendors) ───────
                if (vendor.loyaltyEnabled && customerIds.isNotEmpty()) {
                    val completedOrders = createdOrders.filter { it.status == "COMPLETED" }
                    completedOrders.take(minOf(6, completedOrders.size)).forEachIndexed { pIdx, order ->
                        val custId = customerIds[pIdx % customerIds.size]
                        val earnPts = (order.total / 10).toInt().coerceAtLeast(1)
                        PointsTransactionsTable.insertAndGetId {
                            it[customerId] = custId
                            it[PointsTransactionsTable.vendorId] = vendorId.value
                            it[orderId] = order.id
                            it[type] = "EARN"
                            it[points] = earnPts
                            it[description] = "نقاط مكتسبة من طلب"
                            it[createdAt] = order.time
                        }
                        if (pIdx % 3 == 0) {
                            PointsTransactionsTable.insertAndGetId {
                                it[customerId] = custId
                                it[PointsTransactionsTable.vendorId] = vendorId.value
                                it[orderId] = order.id
                                it[type] = "REDEEM"
                                it[points] = (earnPts / 2).coerceAtLeast(1)
                                it[description] = "نقاط مستبدلة"
                                it[createdAt] = order.time
                            }
                        }
                    }
                }

                // ─── Reservations (for table-enabled vendors) ────────────────
                if (vendor.enableTables && tableIds.isNotEmpty()) {
                    val resStatuses = listOf("PENDING", "CONFIRMED", "COMPLETED", "CANCELLED")
                    val resNames = listOf("أحمد محمد", "سارة علي", "محمد حسن", "ليلى إبراهيم", "خالد عبدالله")
                    resNames.forEachIndexed { rIdx, clientName ->
                        val dayOffset = rIdx - 2 // some past, some future
                        val resDate = now.plus(kotlin.time.Duration.parse("${dayOffset * 24}h"))
                        val dateStr = resDate.toString().take(10)
                        val hours = listOf("12:00", "14:30", "18:00", "19:30", "21:00")
                        ReservationsTable.insertAndGetId {
                            it[ReservationsTable.vendorId] = vendorId.value
                            it[tableId] = tableIds[rIdx % tableIds.size]
                            it[ReservationsTable.clientName] = clientName
                            it[clientPhone] = "+2010000${1000 + rIdx}"
                            it[reservationDate] = dateStr
                            it[reservationTime] = hours[rIdx % hours.size]
                            it[numberOfGuests] = (rIdx % 4) + 2
                            it[notes] = if (rIdx % 2 == 0) "حجز بجانب النافذة" else null
                            it[status] = resStatuses[rIdx % resStatuses.size]
                            if (rIdx < 2) it[orderId] = createdOrders.firstOrNull { o -> o.channel == "DINE_IN" }?.id
                            it[createdBy] = cashierId
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                }

                // ─── Announcements ───────────────────────────────────────────
                val managerId = userIdMap["MANAGER"]!!
                val announcements = listOf(
                    Triple("تحديث ساعات العمل", "تم تعديل ساعات العمل لتبدأ من ٨ صباحاً حتى ١١ مساءً بدءاً من الأسبوع القادم", "ALL"),
                    Triple("اجتماع الفريق", "يرجى حضور اجتماع الفريق يوم الخميس الساعة ٣ عصراً", "ALL"),
                    Triple("عرض جديد", "تم إضافة عرض جديد للعملاء - خصم ٢٠٪ على جميع الطلبات", "CASHIERS"),
                    Triple("تحديث نظام التوصيل", "يرجى التأكد من تحديث التطبيق لآخر إصدار", "DELIVERY")
                )
                announcements.forEachIndexed { aIdx, (title, message, target) ->
                    val annTime = now.minus(kotlin.time.Duration.parse("${(aIdx + 1) * 12}h"))
                    val annId = AnnouncementsTable.insertAndGetId {
                        it[AnnouncementsTable.vendorId] = vendorId.value
                        it[senderId] = managerId
                        it[targetType] = target
                        it[AnnouncementsTable.title] = title
                        it[AnnouncementsTable.message] = message
                        it[priority] = if (aIdx == 0) "URGENT" else "NORMAL"
                        it[createdAt] = annTime
                    }
                    // Mark some as read by cashier
                    if (aIdx < 2) {
                        AnnouncementReadsTable.insertAndGetId {
                            it[announcementId] = annId.value
                            it[userId] = cashierId
                            it[readAt] = annTime.plus(kotlin.time.Duration.parse("1h"))
                        }
                    }
                }

                // ─── Attendance (last 7 days for all workers) ────────────────
                createdWorkers.forEach { worker ->
                    (0..6).forEach { dayOffset ->
                        val attendDay = now.minus(kotlin.time.Duration.parse("${dayOffset * 24}h"))
                        val dateStr = attendDay.toString().take(10)
                        val checkInTime = attendDay.minus(kotlin.time.Duration.parse("${8 + dayOffset % 2}h"))
                        val workedMins = (8 * 60) + (dayOffset * 15) // 8+ hours
                        val checkOutTime = checkInTime.plus(kotlin.time.Duration.parse("${workedMins}m"))
                        val authMethods = listOf("PIN", "QR", "MANUAL")
                        val attendId = AttendanceTable.insertAndGetId {
                            it[AttendanceTable.vendorId] = vendorId.value
                            it[AttendanceTable.workerId] = worker.id
                            it[date] = dateStr
                            it[checkIn] = checkInTime
                            if (dayOffset > 0) { // today might still be checked in
                                it[checkOut] = checkOutTime
                                it[workedMinutes] = workedMins
                            }
                            it[recordedBy] = cashierId
                            it[authMethod] = authMethods[dayOffset % authMethods.size]
                            it[note] = if (dayOffset == 0) "وردية صباحية" else null
                            it[createdAt] = checkInTime
                            it[updatedAt] = checkOutTime
                        }
                        // Auth logs
                        AttendanceAuthLogsTable.insertAndGetId {
                            it[AttendanceAuthLogsTable.workerId] = worker.id
                            it[AttendanceAuthLogsTable.cashierId] = cashierId
                            it[authMethod] = authMethods[dayOffset % authMethods.size]
                            it[success] = true
                            it[createdAt] = checkInTime
                        }
                    }
                }

                // ─── Salary Payments (last 2 months) ─────────────────────────
                createdWorkers.forEach { worker ->
                    val periods = if (worker.salaryType == "MONTHLY") {
                        listOf("2026-02-01" to "2026-02-28", "2026-01-01" to "2026-01-31")
                    } else {
                        (1..7).map { d ->
                            val day = now.minus(kotlin.time.Duration.parse("${d * 24}h")).toString().take(10)
                            day to day
                        }
                    }
                    periods.forEachIndexed { pIdx, (start, end) ->
                        val workedDays = if (worker.salaryType == "MONTHLY") 26 else 1
                        val amount = if (worker.salaryType == "MONTHLY") worker.salaryAmount else worker.salaryAmount
                        val otHours = if (pIdx == 0) 2.0 else 0.0
                        val otAmount = otHours * (worker.salaryAmount / 8 / 26 * 1.5)
                        SalaryPaymentsTable.insertAndGetId {
                            it[SalaryPaymentsTable.vendorId] = vendorId.value
                            it[SalaryPaymentsTable.workerId] = worker.id
                            it[periodType] = if (worker.salaryType == "MONTHLY") "MONTH" else "DAY"
                            it[periodStart] = start
                            it[periodEnd] = end
                            it[SalaryPaymentsTable.workedDays] = workedDays
                            it[SalaryPaymentsTable.amount] = amount.toBigDecimal()
                            it[overtimeHours] = otHours.toBigDecimal()
                            it[overtimeAmount] = otAmount.toBigDecimal()
                            it[paid] = pIdx > 0
                            if (pIdx > 0) {
                                it[paidAt] = now.minus(kotlin.time.Duration.parse("${pIdx * 30 * 24}h"))
                                it[paidBy] = managerId
                            }
                            it[note] = if (pIdx == 0) "في انتظار الصرف" else null
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                }

                // ─── Overtime Entries ─────────────────────────────────────────
                createdWorkers.take(2).forEachIndexed { wIdx, worker ->
                    (1..3).forEach { otDay ->
                        val otDate = now.minus(kotlin.time.Duration.parse("${otDay * 24}h")).toString().take(10)
                        val hrs = 1.5 + (otDay * 0.5)
                        val rate = worker.salaryAmount / 8 / 26 * 1.5
                        OvertimeTable.insertAndGetId {
                            it[OvertimeTable.vendorId] = vendorId.value
                            it[OvertimeTable.workerId] = worker.id
                            it[date] = otDate
                            it[hours] = hrs.toBigDecimal()
                            it[ratePerHour] = rate.toBigDecimal()
                            it[amount] = (hrs * rate).toBigDecimal()
                            it[note] = "ساعات إضافية - وردية مسائية"
                            it[createdBy] = managerId
                            it[createdAt] = now
                        }
                    }
                }

                // ─── Stock Transactions ──────────────────────────────────────
                createdStocks.forEach { stock ->
                    val txTypes = listOf("ADD" to 20.0, "PURCHASE" to 50.0, "DEDUCT" to -5.0, "ADJUST" to 3.0)
                    var prevQty = 0.0
                    txTypes.forEachIndexed { tIdx, (txType, qty) ->
                        val absQty = kotlin.math.abs(qty)
                        StockTransactionsTable.insertAndGetId {
                            it[stockId] = stock.id
                            it[type] = txType
                            it[quantity] = absQty.toBigDecimal()
                            it[previousQuantity] = prevQty.toBigDecimal()
                            it[note] = when (txType) {
                                "ADD" -> "إضافة مخزون جديد"
                                "PURCHASE" -> "شراء من المورد"
                                "DEDUCT" -> "خصم - استخدام يومي"
                                "ADJUST" -> "تعديل جرد"
                                else -> null
                            }
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("${(tIdx + 1) * 24}h"))
                        }
                        prevQty += qty
                    }
                }

                // ─── Recipes & Ingredients (for food vendors) ────────────────
                if (bt in listOf("RESTAURANT", "CAFE", "BAKERY") && createdItems.size >= 2 && createdStocks.size >= 2) {
                    val recipeItems = createdItems.take(2)
                    recipeItems.forEachIndexed { rIdx, item ->
                        val recipeId = RecipesTable.insertAndGetId {
                            it[RecipesTable.vendorId] = vendorId.value
                            it[itemId] = item.id
                            it[name] = "وصفة ${item.name}"
                            it[description] = "طريقة تحضير ${item.name}"
                            it[yieldQuantity] = java.math.BigDecimal.ONE
                            it[yieldUnit] = "PIECE"
                            it[status] = "ACTIVE"
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        // Add 2 ingredients from stock
                        createdStocks.take(2).forEachIndexed { iIdx, stock ->
                            RecipeIngredientsTable.insertAndGetId {
                                it[RecipeIngredientsTable.recipeId] = recipeId.value
                                it[RecipeIngredientsTable.stockId] = stock.id
                                it[quantity] = (0.1 + iIdx * 0.05).toBigDecimal()
                                it[unit] = stock.unit
                                it[fixedQuantity] = iIdx == 0
                                it[displayOrder] = iIdx
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Worker Roles (table) ──────────────────────────────────────
                val workerRoleDefs = when (bt) {
                    "RESTAURANT" -> listOf("شيف رئيسي", "سفرجي", "عامل نظافة", "مساعد مطبخ")
                    "PHARMACY" -> listOf("صيدلي", "صيدلي مساعد", "أمين مخزن")
                    "CAFE" -> listOf("باريستا", "ويتر", "عامل نظافة")
                    "BAKERY" -> listOf("خباز رئيسي", "مساعد خباز", "عامل تغليف")
                    "SUPERMARKET" -> listOf("محاسب", "عامل رفوف", "أمين مخزن", "مسؤول خضار")
                    "RETAIL" -> listOf("أمين مخزن", "مسؤولة مبيعات", "عامل تغليف")
                    else -> listOf("موظف")
                }
                workerRoleDefs.forEach { roleName ->
                    WorkerRolesTable.insertAndGetId {
                        it[WorkerRolesTable.vendorId] = vendorId.value
                        it[name] = roleName
                        it[description] = "وظيفة $roleName في ${vendor.name}"
                        it[createdAt] = now
                    }
                }

                // ─── Order Payments ────────────────────────────────────────────
                val paidOrders = createdOrders.filter { it.status in listOf("COMPLETED", "SERVED", "DELIVERED", "PICKED_UP") }
                paidOrders.forEach { order ->
                    OrderPaymentsTable.insertAndGetId {
                        it[orderId] = order.id
                        it[OrderPaymentsTable.vendorId] = vendorId.value
                        it[paymentMethod] = listOf("CASH", "CARD", "WALLET")[paidOrders.indexOf(order) % 3]
                        it[amount] = order.total.toBigDecimal()
                        it[paidBy] = cashierId
                        it[createdAt] = order.time
                    }
                }

                // ─── Cash Drawer Sessions + Movements ──────────────────────────
                val yesterdayTime = now.minus(kotlin.time.Duration.parse("24h"))
                val closedSessionId = CashDrawerSessionsTable.insertAndGetId {
                    it[CashDrawerSessionsTable.vendorId] = vendorId.value
                    it[CashDrawerSessionsTable.cashierId] = cashierId
                    it[openedAt] = yesterdayTime.minus(kotlin.time.Duration.parse("10h"))
                    it[closedAt] = yesterdayTime
                    it[openingBalance] = java.math.BigDecimal("500.00")
                    it[closingBalance] = java.math.BigDecimal("2350.00")
                    it[expectedBalance] = java.math.BigDecimal("2400.00")
                    it[difference] = java.math.BigDecimal("-50.00")
                    it[status] = "CLOSED"
                    it[notes] = "وردية أمس - فرق بسيط في الجرد"
                    it[createdAt] = yesterdayTime.minus(kotlin.time.Duration.parse("10h"))
                }
                val openSessionId = CashDrawerSessionsTable.insertAndGetId {
                    it[CashDrawerSessionsTable.vendorId] = vendorId.value
                    it[CashDrawerSessionsTable.cashierId] = cashierId
                    it[openedAt] = now.minus(kotlin.time.Duration.parse("6h"))
                    it[openingBalance] = java.math.BigDecimal("500.00")
                    it[status] = "OPEN"
                    it[createdAt] = now.minus(kotlin.time.Duration.parse("6h"))
                }
                // Cash movements for closed session
                listOf(
                    Triple("CASH_IN", 500.0, "رصيد افتتاحي"),
                    Triple("SALE", 1200.0, "مبيعات نقدية"),
                    Triple("CASH_OUT", 200.0, "مصروفات يومية"),
                    Triple("SALE", 850.0, "مبيعات نقدية")
                ).forEachIndexed { mIdx, (mType, mAmount, mReason) ->
                    CashMovementsTable.insertAndGetId {
                        it[sessionId] = closedSessionId.value
                        it[CashMovementsTable.vendorId] = vendorId.value
                        it[type] = mType
                        it[amount] = mAmount.toBigDecimal()
                        it[reason] = mReason
                        it[createdBy] = cashierId
                        it[createdAt] = yesterdayTime.minus(kotlin.time.Duration.parse("${(4 - mIdx) * 2}h"))
                    }
                }
                // Cash movements for open session
                listOf(
                    Triple("CASH_IN", 500.0, "رصيد افتتاحي"),
                    Triple("SALE", 650.0, "مبيعات صباحية")
                ).forEachIndexed { mIdx, (mType, mAmount, mReason) ->
                    CashMovementsTable.insertAndGetId {
                        it[sessionId] = openSessionId.value
                        it[CashMovementsTable.vendorId] = vendorId.value
                        it[type] = mType
                        it[amount] = mAmount.toBigDecimal()
                        it[reason] = mReason
                        it[createdBy] = cashierId
                        it[createdAt] = now.minus(kotlin.time.Duration.parse("${(2 - mIdx) * 2}h"))
                    }
                }

                // ─── Suppliers ─────────────────────────────────────────────────
                val supplierDefs = when (bt) {
                    "RESTAURANT" -> listOf(Triple("مورد اللحوم الطازجة", "عبدالله حسن", "+20100100001"), Triple("مورد الخضار والفاكهة", "محمد السيد", "+20100100002"), Triple("شركة التوابل والبهارات", "أحمد فتحي", "+20100100003"))
                    "PHARMACY" -> listOf(Triple("شركة فارما إيجيبت", "د. هشام", "+20100200001"), Triple("مستودع الدواء المركزي", "محمود كامل", "+20100200002"), Triple("شركة المستلزمات الطبية", "سامي عادل", "+20100200003"))
                    "CAFE" -> listOf(Triple("محمصة القهوة الذهبية", "كريم وائل", "+20100300001"), Triple("مورد الألبان الطازجة", "حسن علي", "+20100300002"), Triple("مصنع الحلويات الفاخرة", "نورهان أحمد", "+20100300003"))
                    "BAKERY" -> listOf(Triple("مطاحن مصر العليا", "خالد إبراهيم", "+20100400001"), Triple("مورد الزبدة والسمن", "عمر طارق", "+20100400002"), Triple("مورد المكسرات", "سعيد محمد", "+20100400003"))
                    "SUPERMARKET" -> listOf(Triple("شركة المشروبات الوطنية", "ماجد عبدالله", "+20100500001"), Triple("مصنع الألبان الطبيعية", "فتحي حسين", "+20100500002"), Triple("شركة المنظفات العالمية", "رامي خالد", "+20100500003"))
                    "RETAIL" -> listOf(Triple("مصنع ألعاب النجمة", "عماد سالم", "+20100600001"), Triple("موزع ليجو المعتمد", "ياسر حسن", "+20100600002"), Triple("مستورد الألعاب الإلكترونية", "أمير فوزي", "+20100600003"))
                    else -> listOf(Triple("مورد عام", "محمد", "+20100000001"))
                }
                val supplierIds = mutableListOf<java.util.UUID>()
                supplierDefs.forEach { (sName, sContact, sPhone) ->
                    val sId = SuppliersTable.insertAndGetId {
                        it[SuppliersTable.vendorId] = vendorId.value
                        it[name] = sName
                        it[contactName] = sContact
                        it[phone] = sPhone
                        it[email] = "${sName.take(5).replace(" ", "")}@supplier.com"
                        it[address] = "المنطقة الصناعية، القاهرة"
                        it[notes] = "مورد معتمد - توصيل خلال ٢٤ ساعة"
                        it[active] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    supplierIds.add(sId.value)
                }

                // ─── Purchase Orders + Items ──────────────────────────────────
                if (supplierIds.isNotEmpty() && createdStocks.isNotEmpty()) {
                    val poStatuses = listOf("RECEIVED", "SUBMITTED")
                    poStatuses.forEachIndexed { poIdx, poStatus ->
                        val poId = PurchaseOrdersTable.insertAndGetId {
                            it[PurchaseOrdersTable.vendorId] = vendorId.value
                            it[supplierId] = supplierIds[poIdx % supplierIds.size]
                            it[orderNumber] = "PO-${bt.take(3)}-${poIdx + 1}"
                            it[status] = poStatus
                            it[notes] = if (poIdx == 0) "تم الاستلام بالكامل" else "في انتظار التوصيل"
                            it[subtotal] = java.math.BigDecimal("1500.00")
                            it[tax] = if (vendor.taxEnabled) java.math.BigDecimal("210.00") else java.math.BigDecimal.ZERO
                            it[total] = if (vendor.taxEnabled) java.math.BigDecimal("1710.00") else java.math.BigDecimal("1500.00")
                            if (poIdx == 1) it[expectedDeliveryDate] = kotlinx.datetime.LocalDate.parse(now.plus(kotlin.time.Duration.parse("3d")).toString().take(10))
                            if (poStatus == "RECEIVED") it[receivedAt] = now.minus(kotlin.time.Duration.parse("2d"))
                            it[createdBy] = managerId
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("${(poIdx + 1) * 3}d"))
                            it[updatedAt] = now
                        }
                        createdStocks.take(3).forEachIndexed { piIdx, stock ->
                            val reqQty = 50.0 + piIdx * 20
                            val unitCost = 25.0 + piIdx * 10
                            PurchaseOrderItemsTable.insertAndGetId {
                                it[purchaseOrderId] = poId.value
                                it[stockId] = stock.id
                                it[requestedQuantity] = reqQty.toBigDecimal()
                                it[receivedQuantity] = if (poStatus == "RECEIVED") reqQty.toBigDecimal() else java.math.BigDecimal.ZERO
                                it[PurchaseOrderItemsTable.unitCost] = unitCost.toBigDecimal()
                                it[totalCost] = (reqQty * unitCost).toBigDecimal()
                                it[unit] = stock.unit
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Product Returns + Return Items ────────────────────────────
                val completedOrders2 = createdOrders.filter { it.status == "COMPLETED" }
                if (completedOrders2.size >= 2) {
                    val returnStatuses = listOf("PENDING", "APPROVED", "REJECTED")
                    returnStatuses.take(minOf(returnStatuses.size, completedOrders2.size)).forEachIndexed { rIdx, retStatus ->
                        val retOrder = completedOrders2[rIdx]
                        val retAmount = retOrder.total * 0.5
                        val retId = ProductReturnsTable.insertAndGetId {
                            it[ProductReturnsTable.vendorId] = vendorId.value
                            it[orderId] = retOrder.id
                            if (customerIds.isNotEmpty()) it[customerId] = customerIds[rIdx % customerIds.size]
                            it[returnType] = if (rIdx == 1) "EXCHANGE" else "RETURN"
                            it[status] = retStatus
                            it[reason] = when (rIdx) { 0 -> "المنتج غير مطابق للمواصفات"; 1 -> "العميل يريد استبدال المنتج"; else -> "تم الإلغاء من العميل" }
                            it[refundAmount] = retAmount.toBigDecimal()
                            it[refundMethod] = if (rIdx == 0) "CASH" else "CREDIT"
                            if (retStatus != "PENDING") {
                                it[processedBy] = managerId
                                it[processedAt] = now.minus(kotlin.time.Duration.parse("${rIdx}d"))
                            }
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("${rIdx + 1}d"))
                        }
                        // Return items - find order items for this order
                        val orderItemRows = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq retOrder.id }.toList()
                        orderItemRows.take(1).forEach { oiRow ->
                            ReturnItemsTable.insertAndGetId {
                                it[returnId] = retId.value
                                it[orderItemId] = oiRow[OrderItemsTable.id].value
                                it[itemId] = oiRow[OrderItemsTable.itemId]
                                it[quantity] = 1
                                it[ReturnItemsTable.reason] = if (rIdx == 2) "CHANGED_MIND" else "DEFECTIVE"
                                it[condition] = if (rIdx == 2) "GOOD" else "DAMAGED"
                                it[restockable] = rIdx != 2
                                it[refundAmount] = oiRow[OrderItemsTable.itemPriceSnapshot]
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Prescriptions + Items (PHARMACY only) ────────────────────
                if (bt == "PHARMACY" && createdItems.size >= 4) {
                    val rxStatuses = listOf("PENDING", "DISPENSED", "CANCELLED")
                    val rxDoctors = listOf("د. أحمد محمود", "د. سارة عبدالله", "د. خالد حسن")
                    val rxPatients = listOf("محمد أحمد" to 35, "فاطمة علي" to 28, "إبراهيم حسن" to 55)
                    rxStatuses.forEachIndexed { rxIdx, rxStatus ->
                        val (patientName, patientAge) = rxPatients[rxIdx]
                        val rxId = PrescriptionsTable.insertAndGetId {
                            it[PrescriptionsTable.vendorId] = vendorId.value
                            if (customerIds.isNotEmpty()) it[customerId] = customerIds[rxIdx % customerIds.size]
                            if (rxStatus == "DISPENSED" && completedOrders2.isNotEmpty()) it[orderId] = completedOrders2[rxIdx % completedOrders2.size].id
                            it[doctorName] = rxDoctors[rxIdx]
                            it[doctorPhone] = "+2010000${2000 + rxIdx}"
                            it[PrescriptionsTable.patientName] = patientName
                            it[patientPhone] = "+2010000${3000 + rxIdx}"
                            it[PrescriptionsTable.patientAge] = patientAge
                            it[diagnosis] = when (rxIdx) { 0 -> "التهاب حاد في الحلق"; 1 -> "نقص فيتامين د"; else -> "ارتفاع ضغط الدم" }
                            it[notes] = "روشتة تجريبية"
                            it[status] = rxStatus
                            if (rxStatus == "DISPENSED") {
                                it[dispensedAt] = now.minus(kotlin.time.Duration.parse("1d"))
                                it[dispensedBy] = cashierId
                            }
                            it[createdBy] = cashierId
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("${rxIdx + 1}d"))
                            it[updatedAt] = now
                        }
                        // Prescription items
                        createdItems.take(3).forEachIndexed { piIdx, item ->
                            PrescriptionItemsTable.insertAndGetId {
                                it[prescriptionId] = rxId.value
                                it[itemId] = item.id
                                it[quantity] = piIdx + 1
                                it[dosage] = when (piIdx) { 0 -> "٥٠٠ مجم"; 1 -> "١٠٠٠ مجم"; else -> "قرص واحد" }
                                it[frequency] = when (piIdx) { 0 -> "٣ مرات يومياً"; 1 -> "مرة يومياً"; else -> "كل ٨ ساعات" }
                                it[duration] = when (piIdx) { 0 -> "٧ أيام"; 1 -> "٣٠ يوم"; else -> "١٤ يوم" }
                                it[instructions] = if (piIdx == 0) "بعد الأكل" else null
                                it[dispensedQuantity] = if (rxStatus == "DISPENSED") piIdx + 1 else 0
                                it[PrescriptionItemsTable.status] = if (rxStatus == "DISPENSED") "DISPENSED" else "PENDING"
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Drug Interactions (PHARMACY only) ─────────────────────────
                if (bt == "PHARMACY" && createdItems.size >= 4) {
                    val interactions = listOf(
                        Triple(0, 1, Triple("MODERATE", "تفاعل متوسط - يجب المراقبة", "ممنوع الاستخدام المتزامن بدون إشراف طبي")),
                        Triple(0, 3, Triple("MILD", "تفاعل خفيف - تقليل الجرعة", "يمكن الاستخدام مع مراقبة")),
                        Triple(1, 2, Triple("SEVERE", "تفاعل شديد - ممنوع الاستخدام المتزامن", "يجب استبدال أحد الدواءين")),
                        Triple(2, 3, Triple("CONTRAINDICATED", "ممنوع تماماً", "يجب عدم صرف الدواءين معاً")),
                    )
                    interactions.forEach { (idxA, idxB, details) ->
                        val (severity, desc, recommendation) = details
                        DrugInteractionsTable.insertAndGetId {
                            it[DrugInteractionsTable.vendorId] = vendorId.value
                            it[itemIdA] = createdItems[idxA].id
                            it[itemIdB] = createdItems[idxB].id
                            it[DrugInteractionsTable.severity] = severity
                            it[description] = desc
                            it[descriptionAr] = desc
                            it[DrugInteractionsTable.recommendation] = recommendation
                            it[active] = true
                            it[createdAt] = now
                        }
                    }
                }

                // ─── Customer Credits + Transactions ──────────────────────────
                if (customerIds.size >= 2) {
                    customerIds.take(3).forEachIndexed { ccIdx, custId ->
                        val balance = 150.0 + ccIdx * 100
                        val credId = CustomerCreditsTable.insertAndGetId {
                            it[CustomerCreditsTable.vendorId] = vendorId.value
                            it[CustomerCreditsTable.customerId] = custId
                            it[CustomerCreditsTable.balance] = balance.toBigDecimal()
                            it[creditLimit] = java.math.BigDecimal("1000.00")
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        // Credit transactions
                        val chargeAmount = 300.0 + ccIdx * 50
                        CreditTransactionsTable.insertAndGetId {
                            it[creditId] = credId.value
                            it[CreditTransactionsTable.vendorId] = vendorId.value
                            it[type] = "CHARGE"
                            it[amount] = chargeAmount.toBigDecimal()
                            it[previousBalance] = java.math.BigDecimal.ZERO
                            it[newBalance] = chargeAmount.toBigDecimal()
                            it[note] = "شراء بالأجل"
                            it[createdBy] = cashierId
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("5d"))
                        }
                        val payAmount = chargeAmount - balance
                        CreditTransactionsTable.insertAndGetId {
                            it[creditId] = credId.value
                            it[CreditTransactionsTable.vendorId] = vendorId.value
                            it[type] = "PAYMENT"
                            it[amount] = payAmount.toBigDecimal()
                            it[previousBalance] = chargeAmount.toBigDecimal()
                            it[newBalance] = balance.toBigDecimal()
                            it[note] = "سداد جزئي"
                            it[createdBy] = cashierId
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("2d"))
                        }
                    }
                }

                // ─── Scheduled Orders + Items ─────────────────────────────────
                if (createdItems.size >= 3) {
                    val schedDefs = listOf(
                        Triple("SCHEDULED", "PICKUP_LATER", "طلب أسبوعي متكرر"),
                        Triple("CONFIRMED", "DELIVERY", "طلب محجوز مسبقاً"),
                        Triple("CANCELLED", "PICKUP_LATER", "طلب ملغي")
                    )
                    schedDefs.forEachIndexed { sIdx, (sStatus, sChannel, sNote) ->
                        val schedItems = createdItems.shuffled().take(3)
                        val schedSubtotal = schedItems.sumOf { it.price }
                        val schedId = ScheduledOrdersTable.insertAndGetId {
                            it[ScheduledOrdersTable.vendorId] = vendorId.value
                            if (customerIds.isNotEmpty()) it[customerId] = customerIds[sIdx % customerIds.size]
                            it[clientName] = if (sIdx == 1) "عميل جديد" else null
                            it[clientPhone] = if (sIdx == 1) "+20100009999" else null
                            it[channel] = sChannel
                            it[scheduledFor] = now.plus(kotlin.time.Duration.parse("${(sIdx + 1) * 24}h"))
                            it[status] = sStatus
                            it[notes] = sNote
                            it[paymentMethod] = "CASH"
                            it[paymentStatus] = if (sStatus == "CONFIRMED") "PAID" else "PENDING"
                            it[subtotal] = schedSubtotal.toBigDecimal()
                            it[total] = schedSubtotal.toBigDecimal()
                            it[createdBy] = cashierId
                            it[createdAt] = now.minus(kotlin.time.Duration.parse("${sIdx + 1}d"))
                            it[updatedAt] = now
                        }
                        schedItems.forEach { sItem ->
                            ScheduledOrderItemsTable.insertAndGetId {
                                it[scheduledOrderId] = schedId.value
                                it[itemId] = sItem.id
                                it[ScheduledOrderItemsTable.itemName] = sItem.name
                                it[itemPrice] = sItem.price.toBigDecimal()
                                it[quantity] = 1
                                it[createdAt] = now
                            }
                        }
                    }
                }

                // ─── Notifications ─────────────────────────────────────────────
                val notifDefs = listOf(
                    Triple("ORDER_NEW", "طلب جديد", "تم استلام طلب جديد رقم #${orderCounter}"),
                    Triple("ORDER_STATUS", "تحديث حالة الطلب", "تم تغيير حالة الطلب إلى جاري التحضير"),
                    Triple("LOW_STOCK", "تنبيه مخزون منخفض", "المخزون منخفض لبعض المنتجات - يرجى إعادة الطلب"),
                    Triple("SYSTEM", "تحديث النظام", "تم تحديث النظام بنجاح - إصدار ١.١.٠"),
                    Triple("ANNOUNCEMENT", "إعلان جديد", "تم نشر إعلان جديد من الإدارة")
                )
                notifDefs.forEachIndexed { nIdx, (nType, nTitle, nBody) ->
                    val nTime = now.minus(kotlin.time.Duration.parse("${(nIdx + 1) * 6}h"))
                    NotificationsTable.insertAndGetId {
                        it[NotificationsTable.vendorId] = vendorId.value
                        it[userId] = if (nIdx < 3) cashierId else null
                        it[type] = nType
                        it[title] = nTitle
                        it[body] = nBody
                        it[channel] = "IN_APP"
                        it[priority] = if (nType == "LOW_STOCK") "HIGH" else "NORMAL"
                        it[read] = nIdx > 2
                        if (nIdx > 2) it[readAt] = nTime.plus(kotlin.time.Duration.parse("1h"))
                        it[createdAt] = nTime
                    }
                }

                // ─── Stock Batches ─────────────────────────────────────────────
                createdStocks.forEachIndexed { bIdx, stock ->
                    val batchCount = if (bt == "PHARMACY") 2 else 1
                    (0 until batchCount).forEach { bNum ->
                        val batchQty = stock.qty / batchCount
                        val daysUntilExpiry = if (bt == "PHARMACY") 180 + bNum * 90 else 365
                        StockBatchesTable.insertAndGetId {
                            it[stockId] = stock.id
                            it[StockBatchesTable.vendorId] = vendorId.value
                            it[batchNumber] = "LOT-${bt.take(3)}-${bIdx + 1}-${bNum + 1}"
                            it[quantity] = batchQty.toBigDecimal()
                            it[initialQuantity] = (batchQty * 1.5).toBigDecimal()
                            it[costPrice] = java.math.BigDecimal("50.00")
                            it[expiryDate] = kotlinx.datetime.LocalDate.parse(now.plus(kotlin.time.Duration.parse("${daysUntilExpiry * 24}h")).toString().take(10))
                            it[receivedAt] = now.minus(kotlin.time.Duration.parse("${(bNum + 1) * 30 * 24}h"))
                            it[status] = "ACTIVE"
                            it[createdAt] = now
                        }
                    }
                }

                // ─── Activity Logs (for recent orders) ───────────────────────
                createdOrders.take(minOf(10, createdOrders.size)).forEach { order ->
                    val actions = when (order.status) {
                        "CREATED" -> listOf("ORDER_CREATED")
                        "IN_PREPARATION" -> listOf("ORDER_CREATED", "STATUS_CHANGED")
                        "COMPLETED" -> listOf("ORDER_CREATED", "STATUS_CHANGED", "PAYMENT_CONFIRMED", "ORDER_COMPLETED")
                        "CANCELED" -> listOf("ORDER_CREATED", "ORDER_CANCELED")
                        "REFUNDED" -> listOf("ORDER_CREATED", "ORDER_COMPLETED", "ORDER_REFUNDED")
                        else -> listOf("ORDER_CREATED", "STATUS_CHANGED")
                    }
                    actions.forEachIndexed { aIdx, action ->
                        ActivityLogsTable.insertAndGetId {
                            it[ActivityLogsTable.orderId] = order.id
                            it[userId] = cashierId
                            it[ActivityLogsTable.action] = action
                            it[payload] = """{"status":"${order.status}","channel":"${order.channel}"}"""
                            it[createdAt] = order.time.plus(kotlin.time.Duration.parse("${aIdx}m"))
                        }
                    }
                }

                logger.info("Vendor ${vendor.name}: ${vendorCategories[bt]?.size ?: 0} categories, ${vendorItems[bt]?.size ?: 0} items, ${vendorCustomers[bt]?.size ?: 0} customers, ${vendorStock[bt]?.size ?: 0} stock items, ${vendorWorkers[bt]?.size ?: 0} workers, $orderCounter orders")
            }

            logger.info("=== Test data seeded successfully! ===")
            logger.info("All accounts use password: password123")
            logger.info("1. مطعم الشام (RESTAURANT)       → Manager: +2010101001  Cashier: +2010101002  Delivery: +2010101003  Kitchen: +2010101004")
            logger.info("2. صيدلية الشفاء (PHARMACY)       → Manager: +2020202001  Cashier: +2020202002  Delivery: +2020202003  Kitchen: +2020202004")
            logger.info("3. كافيه لافندر (CAFE)            → Manager: +2030303001  Cashier: +2030303002  Delivery: +2030303003  Kitchen: +2030303004")
            logger.info("4. مخبز السنابل (BAKERY)          → Manager: +2040404001  Cashier: +2040404002  Delivery: +2040404003  Kitchen: +2040404004")
            logger.info("5. سوبر ماركت الخير (SUPERMARKET)  → Manager: +2050505001  Cashier: +2050505002  Delivery: +2050505003  Kitchen: +2050505004")
            logger.info("6. محل لعب أطفال توي لاند (RETAIL)  → Manager: +2060606001  Cashier: +2060606002  Delivery: +2060606003  Kitchen: +2060606004")
        }
    }
}
