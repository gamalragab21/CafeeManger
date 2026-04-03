package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

// ─── Custom Exceptions ───────────────────────────────────────────
class PlanLimitExceededException(message: String) : RuntimeException(message)
class FeatureNotAvailableException(message: String) : RuntimeException(message)
class AccountSuspendedException(message: String) : RuntimeException(message)

// ─── Data classes for plan info ──────────────────────────────────
data class VendorPlanLimits(
    val planName: String,
    val planDisplayName: String,
    val priceEgp: Int,
    val maxManagers: Int,       // -1 = unlimited
    val maxCashiers: Int,
    val maxDelivery: Int,
    val maxOrdersPerMonth: Int,
    val maxMenuItems: Int,
    val maxBranches: Int,
    val stockManagement: Boolean,
    val workerAttendance: Boolean,
    val deliveryModule: Boolean,
    val overtime: Boolean,
    val salaries: Boolean,
    val customerManagement: Boolean,
    val tableManagement: Boolean,
    val digitalReceipt: Boolean,
    val workerQrcode: Boolean,
    val loyaltyPoints: Boolean,
    val manualDiscount: Boolean,
    val offersManagement: Boolean,
    val cashDrawer: Boolean,
    val splitPayment: Boolean,
    val customerCredit: Boolean,
    val installments: Boolean,
    val suppliers: Boolean,
    val returns: Boolean,
    val prescriptions: Boolean,
    val drugInteractions: Boolean,
    val scheduledOrders: Boolean,
    val kds: Boolean,
    val notifications: Boolean,
    val analytics: String,      // NONE, FULL
    val digitalMenu: String,    // NONE, FULL
    // Override values from vendor subscription (nullable = use plan default)
    val overrideMaxManagers: Int? = null,
    val overrideMaxCashiers: Int? = null,
    val overrideMaxDelivery: Int? = null,
    val overrideMaxOrders: Int? = null,
    val overrideMaxItems: Int? = null,
) {
    fun effectiveMaxManagers() = overrideMaxManagers ?: maxManagers
    fun effectiveMaxCashiers() = overrideMaxCashiers ?: maxCashiers
    fun effectiveMaxDelivery() = overrideMaxDelivery ?: maxDelivery
    fun effectiveMaxOrders() = overrideMaxOrders ?: maxOrdersPerMonth
    fun effectiveMaxItems() = overrideMaxItems ?: maxMenuItems

    fun isUnlimitedManagers() = effectiveMaxManagers() == -1
    fun isUnlimitedCashiers() = effectiveMaxCashiers() == -1
    fun isUnlimitedDelivery() = effectiveMaxDelivery() == -1
    fun isUnlimitedOrders() = effectiveMaxOrders() == -1
    fun isUnlimitedItems() = effectiveMaxItems() == -1
}

class PlanService {

    private val logger = LoggerFactory.getLogger("PlanService")

    // ─── Get vendor's current plan limits ─────────────────────────
    fun getVendorPlanLimits(vendorId: UUID): VendorPlanLimits {
        return transaction {
            val subscription = VendorSubscriptionsTable.selectAll()
                .where { (VendorSubscriptionsTable.vendorId eq vendorId) and (VendorSubscriptionsTable.status eq "ACTIVE") }
                .firstOrNull()
                ?: throw NoSuchElementException("No active subscription found for vendor $vendorId")

            val planId = subscription[VendorSubscriptionsTable.planId]
            val plan = SubscriptionPlansTable.selectAll()
                .where { SubscriptionPlansTable.id eq planId }
                .firstOrNull()
                ?: throw NoSuchElementException("Subscription plan not found")

            VendorPlanLimits(
                planName = plan[SubscriptionPlansTable.name],
                planDisplayName = plan[SubscriptionPlansTable.displayName],
                priceEgp = plan[SubscriptionPlansTable.priceEgp],
                maxManagers = plan[SubscriptionPlansTable.maxManagers],
                maxCashiers = plan[SubscriptionPlansTable.maxCashiers],
                maxDelivery = plan[SubscriptionPlansTable.maxDelivery],
                maxOrdersPerMonth = plan[SubscriptionPlansTable.maxOrdersPerMonth],
                maxMenuItems = plan[SubscriptionPlansTable.maxMenuItems],
                maxBranches = plan[SubscriptionPlansTable.maxBranches],
                stockManagement = plan[SubscriptionPlansTable.stockManagement],
                workerAttendance = plan[SubscriptionPlansTable.workerAttendance],
                deliveryModule = plan[SubscriptionPlansTable.deliveryModule],
                overtime = plan[SubscriptionPlansTable.overtime],
                salaries = plan[SubscriptionPlansTable.salaries],
                customerManagement = plan[SubscriptionPlansTable.customerManagement],
                tableManagement = plan[SubscriptionPlansTable.tableManagement],
                digitalReceipt = plan[SubscriptionPlansTable.digitalReceipt],
                workerQrcode = plan[SubscriptionPlansTable.workerQrcode],
                loyaltyPoints = plan[SubscriptionPlansTable.loyaltyPoints],
                manualDiscount = plan[SubscriptionPlansTable.manualDiscount],
                offersManagement = plan[SubscriptionPlansTable.offersManagement],
                cashDrawer = plan[SubscriptionPlansTable.cashDrawer],
                splitPayment = plan[SubscriptionPlansTable.splitPayment],
                customerCredit = plan[SubscriptionPlansTable.customerCredit],
                installments = plan[SubscriptionPlansTable.installments],
                suppliers = plan[SubscriptionPlansTable.suppliers],
                returns = plan[SubscriptionPlansTable.returns],
                prescriptions = plan[SubscriptionPlansTable.prescriptions],
                drugInteractions = plan[SubscriptionPlansTable.drugInteractions],
                scheduledOrders = plan[SubscriptionPlansTable.scheduledOrders],
                kds = plan[SubscriptionPlansTable.kds],
                notifications = plan[SubscriptionPlansTable.notifications],
                analytics = plan[SubscriptionPlansTable.analytics],
                digitalMenu = plan[SubscriptionPlansTable.digitalMenu],
                overrideMaxManagers = subscription[VendorSubscriptionsTable.overrideMaxManagers],
                overrideMaxCashiers = subscription[VendorSubscriptionsTable.overrideMaxCashiers],
                overrideMaxDelivery = subscription[VendorSubscriptionsTable.overrideMaxDelivery],
                overrideMaxOrders = subscription[VendorSubscriptionsTable.overrideMaxOrders],
                overrideMaxItems = subscription[VendorSubscriptionsTable.overrideMaxItems],
            )
        }
    }

    // ─── Check if user can be created ─────────────────────────────
    fun checkUserCreation(vendorId: UUID, role: String) {
        val limits = getVendorPlanLimits(vendorId)

        transaction {
            when (role) {
                "MANAGER" -> {
                    if (!limits.isUnlimitedManagers()) {
                        val currentCount = UsersTable.selectAll()
                            .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "MANAGER") and (UsersTable.active eq true) }
                            .count().toInt()
                        if (currentCount >= limits.effectiveMaxManagers()) {
                            throw PlanLimitExceededException(
                                "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxManagers()} manager(s). " +
                                "Current: $currentCount. Please upgrade your plan to add more managers."
                            )
                        }
                    }
                }
                "CASHIER" -> {
                    if (!limits.isUnlimitedCashiers()) {
                        val currentCount = UsersTable.selectAll()
                            .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "CASHIER") and (UsersTable.active eq true) }
                            .count().toInt()
                        if (currentCount >= limits.effectiveMaxCashiers()) {
                            throw PlanLimitExceededException(
                                "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxCashiers()} cashier(s). " +
                                "Current: $currentCount. Please upgrade your plan to add more cashiers."
                            )
                        }
                    }
                }
                "DELIVERY" -> {
                    if (!limits.isUnlimitedDelivery()) {
                        val currentCount = UsersTable.selectAll()
                            .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "DELIVERY") and (UsersTable.active eq true) }
                            .count().toInt()
                        if (currentCount >= limits.effectiveMaxDelivery()) {
                            throw PlanLimitExceededException(
                                "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxDelivery()} delivery user(s). " +
                                "Current: $currentCount. Please upgrade your plan to add more delivery users."
                            )
                        }
                    }
                }
                // KITCHEN users share the cashier limit (they work in the same venue context)
                "KITCHEN" -> {
                    if (!limits.isUnlimitedCashiers()) {
                        val currentCount = UsersTable.selectAll()
                            .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "KITCHEN") and (UsersTable.active eq true) }
                            .count().toInt()
                        if (currentCount >= limits.effectiveMaxCashiers()) {
                            throw PlanLimitExceededException(
                                "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxCashiers()} kitchen user(s). " +
                                "Current: $currentCount. Please upgrade your plan to add more kitchen users."
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Check if menu item can be created ─────────────────────────
    fun checkItemCreation(vendorId: UUID) {
        val limits = getVendorPlanLimits(vendorId)

        if (!limits.isUnlimitedItems()) {
            transaction {
                val currentCount = ItemsTable.selectAll()
                    .where { ItemsTable.vendorId eq vendorId }
                    .count().toInt()
                if (currentCount >= limits.effectiveMaxItems()) {
                    throw PlanLimitExceededException(
                        "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxItems()} menu items. " +
                        "Current: $currentCount. Please upgrade your plan to add more items."
                    )
                }
            }
        }
    }

    // ─── Check if order can be created (monthly limit) ─────────────
    fun checkOrderCreation(vendorId: UUID) {
        val limits = getVendorPlanLimits(vendorId)

        if (!limits.isUnlimitedOrders()) {
            transaction {
                // Count orders in the current calendar month
                val now = Clock.System.now()
                val monthStart = getStartOfMonth(now)

                val currentMonthOrders = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorId) and
                        (OrdersTable.createdAt greaterEq monthStart)
                    }
                    .count().toInt()

                if (currentMonthOrders >= limits.effectiveMaxOrders()) {
                    throw PlanLimitExceededException(
                        "Your ${limits.planDisplayName} plan allows a maximum of ${limits.effectiveMaxOrders()} orders per month. " +
                        "Current month usage: $currentMonthOrders. Please upgrade your plan for more orders."
                    )
                }
            }
        }
    }

    // ─── Check delivery channel availability ──────────────────────
    fun checkDeliveryChannel(vendorId: UUID) {
        val limits = getVendorPlanLimits(vendorId)
        if (!limits.deliveryModule) {
            throw FeatureNotAvailableException(
                "Delivery module is not available on the ${limits.planDisplayName} plan. Please upgrade to use delivery features."
            )
        }
    }

    // ─── Feature gate checks ──────────────────────────────────────
    // Admin toggle is the SOURCE OF TRUTH. If admin enabled a feature
    // for a vendor, it works regardless of the plan.
    // Plan check only applies when admin hasn't explicitly enabled it.
    /**
     * Check if a feature is enabled for this vendor.
     * All features are controlled by vendor-level toggles (admin configuration).
     * No plan-based restriction — admin can enable/disable anything per merchant.
     */
    fun checkFeature(vendorId: UUID, feature: String) {
        val vendor = transaction {
            VendorsTable.selectAll().where { VendorsTable.id eq vendorId }.firstOrNull()
        } ?: throw FeatureNotAvailableException("Vendor not found")

        val enabled = when (feature) {
            "STOCK" -> vendor[VendorsTable.enableStock]
            "WORKER", "ATTENDANCE" -> vendor[VendorsTable.enableAttendance]
            "OVERTIME" -> vendor[VendorsTable.enableOvertime]
            "SALARY" -> vendor[VendorsTable.enableSalary]
            "CUSTOMER" -> vendor[VendorsTable.enableCustomers]
            "DELIVERY" -> vendor[VendorsTable.enableDelivery]
            "ANALYTICS" -> vendor[VendorsTable.enableAnalytics]
            "EXPORT" -> vendor[VendorsTable.enableExport]
            "DIGITAL_MENU" -> vendor[VendorsTable.enableDigitalMenu]
            "TABLE" -> vendor[VendorsTable.enableTables]
            "DIGITAL_RECEIPT" -> vendor[VendorsTable.enableDigitalReceipt]
            "WORKER_QRCODE" -> vendor[VendorsTable.enableWorkerQrcode]
            "LOYALTY" -> vendor[VendorsTable.enableLoyalty]
            "MANUAL_DISCOUNT" -> vendor[VendorsTable.enableManualDiscount]
            "OFFERS" -> vendor[VendorsTable.enableOffers]
            "CASH_DRAWER" -> vendor[VendorsTable.enableCashDrawer]
            "SPLIT_PAYMENT" -> vendor[VendorsTable.enableSplitPayment]
            "CUSTOMER_CREDIT" -> vendor[VendorsTable.enableCustomerCredit]
            "INSTALLMENTS" -> vendor[VendorsTable.enableInstallments]
            "SUPPLIERS" -> vendor[VendorsTable.enableSuppliers]
            "RETURNS" -> vendor[VendorsTable.enableReturns]
            "PRESCRIPTIONS" -> vendor[VendorsTable.enablePrescriptions]
            "DRUG_INTERACTIONS" -> vendor[VendorsTable.enableDrugInteractions]
            "SCHEDULED_ORDERS" -> vendor[VendorsTable.enableScheduledOrders]
            "KDS" -> vendor[VendorsTable.enableKds]
            "RECIPE" -> vendor[VendorsTable.enableRecipe]
            "ANNOUNCEMENTS" -> vendor[VendorsTable.enableAnnouncements]
            else -> true // Unknown features are allowed by default
        }

        if (!enabled) {
            throw FeatureNotAvailableException("This feature is not enabled for your store. Contact admin to enable it.")
        }
    }

    // ─── Admin: assign plan to vendor ─────────────────────────────
    fun assignPlanToVendor(vendorId: UUID, planName: String, notes: String? = null) {
        transaction {
            val plan = SubscriptionPlansTable.selectAll()
                .where { SubscriptionPlansTable.name eq planName.uppercase() }
                .firstOrNull()
                ?: throw NoSuchElementException("Plan '$planName' not found")

            val planId = plan[SubscriptionPlansTable.id].value

            // Upsert: update if exists, insert if not
            val existing = VendorSubscriptionsTable.selectAll()
                .where { VendorSubscriptionsTable.vendorId eq vendorId }
                .firstOrNull()

            if (existing != null) {
                VendorSubscriptionsTable.update({ VendorSubscriptionsTable.vendorId eq vendorId }) {
                    it[VendorSubscriptionsTable.planId] = planId
                    it[status] = "ACTIVE"
                    it[startedAt] = Clock.System.now()
                    it[VendorSubscriptionsTable.notes] = notes
                    it[updatedAt] = Clock.System.now()
                }
            } else {
                VendorSubscriptionsTable.insertAndGetId {
                    it[VendorSubscriptionsTable.vendorId] = vendorId
                    it[VendorSubscriptionsTable.planId] = planId
                    it[status] = "ACTIVE"
                    it[startedAt] = Clock.System.now()
                    it[VendorSubscriptionsTable.notes] = notes
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            }
            // Apply plan feature defaults to vendor toggles
            applyPlanDefaults(vendorId, plan)

            logger.info("Vendor $vendorId assigned to plan $planName — feature defaults applied")
        }
    }

    /**
     * Apply plan feature flags as defaults to vendor enable_* columns.
     * Only sets flags that the plan defines — admin can override afterward.
     */
    private fun applyPlanDefaults(vendorId: UUID, plan: org.jetbrains.exposed.sql.ResultRow) {
        transaction {
            VendorsTable.update({ VendorsTable.id eq vendorId }) {
                it[enableStock] = plan[SubscriptionPlansTable.stockManagement]
                it[enableAttendance] = plan[SubscriptionPlansTable.workerAttendance]
                it[enableDelivery] = plan[SubscriptionPlansTable.deliveryModule]
                it[enableAnalytics] = plan[SubscriptionPlansTable.analytics] != "NONE"
                it[enableExport] = plan[SubscriptionPlansTable.analytics] != "NONE"
                it[enableDigitalMenu] = plan[SubscriptionPlansTable.digitalMenu] != "NONE"
                it[enableOvertime] = plan[SubscriptionPlansTable.overtime]
                it[enableSalary] = plan[SubscriptionPlansTable.salaries]
                it[enableCustomers] = plan[SubscriptionPlansTable.customerManagement]
                it[enableTables] = plan[SubscriptionPlansTable.tableManagement]
                it[enableDigitalReceipt] = plan[SubscriptionPlansTable.digitalReceipt]
                it[enableWorkerQrcode] = plan[SubscriptionPlansTable.workerQrcode]
                it[enableLoyalty] = plan[SubscriptionPlansTable.loyaltyPoints]
                it[enableManualDiscount] = plan[SubscriptionPlansTable.manualDiscount]
                it[enableOffers] = plan[SubscriptionPlansTable.offersManagement]
                it[enableCashDrawer] = plan[SubscriptionPlansTable.cashDrawer]
                it[enableSplitPayment] = plan[SubscriptionPlansTable.splitPayment]
                it[enableCustomerCredit] = plan[SubscriptionPlansTable.customerCredit]
                it[enableInstallments] = plan[SubscriptionPlansTable.installments]
                it[enableSuppliers] = plan[SubscriptionPlansTable.suppliers]
                it[enableReturns] = plan[SubscriptionPlansTable.returns]
                it[enablePrescriptions] = plan[SubscriptionPlansTable.prescriptions]
                it[enableDrugInteractions] = plan[SubscriptionPlansTable.drugInteractions]
                it[enableScheduledOrders] = plan[SubscriptionPlansTable.scheduledOrders]
                it[enableKds] = plan[SubscriptionPlansTable.kds]
                it[enableAnnouncements] = plan[SubscriptionPlansTable.notifications]
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    /**
     * Reset vendor feature flags to their plan defaults.
     * Admin can call this to "restore" a vendor's features to what the plan provides.
     */
    fun resetVendorToplanDefaults(vendorId: UUID) {
        transaction {
            val sub = VendorSubscriptionsTable.selectAll()
                .where { VendorSubscriptionsTable.vendorId eq vendorId }
                .firstOrNull() ?: throw NoSuchElementException("Vendor has no subscription")

            val plan = SubscriptionPlansTable.selectAll()
                .where { SubscriptionPlansTable.id eq sub[VendorSubscriptionsTable.planId] }
                .first()

            applyPlanDefaults(vendorId, plan)
        }
    }

    // ─── Admin: update plan overrides for a vendor ────────────────
    fun updateVendorOverrides(
        vendorId: UUID,
        overrideMaxManagers: Int? = null,
        overrideMaxCashiers: Int? = null,
        overrideMaxDelivery: Int? = null,
        overrideMaxOrders: Int? = null,
        overrideMaxItems: Int? = null,
    ) {
        transaction {
            val updated = VendorSubscriptionsTable.update({ VendorSubscriptionsTable.vendorId eq vendorId }) {
                it[VendorSubscriptionsTable.overrideMaxManagers] = overrideMaxManagers
                it[VendorSubscriptionsTable.overrideMaxCashiers] = overrideMaxCashiers
                it[VendorSubscriptionsTable.overrideMaxDelivery] = overrideMaxDelivery
                it[VendorSubscriptionsTable.overrideMaxOrders] = overrideMaxOrders
                it[VendorSubscriptionsTable.overrideMaxItems] = overrideMaxItems
                it[updatedAt] = Clock.System.now()
            }
            if (updated == 0) throw NoSuchElementException("No subscription found for vendor $vendorId")
        }
    }

    // ─── Get vendor usage stats ───────────────────────────────────
    fun getVendorUsage(vendorId: UUID): Map<String, Any> {
        return transaction {
            val now = Clock.System.now()
            val monthStart = getStartOfMonth(now)

            val managers = UsersTable.selectAll()
                .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "MANAGER") and (UsersTable.active eq true) }
                .count().toInt()
            val cashiers = UsersTable.selectAll()
                .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "CASHIER") and (UsersTable.active eq true) }
                .count().toInt()
            val delivery = UsersTable.selectAll()
                .where { (UsersTable.vendorId eq vendorId) and (UsersTable.role eq "DELIVERY") and (UsersTable.active eq true) }
                .count().toInt()
            val menuItems = ItemsTable.selectAll()
                .where { ItemsTable.vendorId eq vendorId }
                .count().toInt()
            val monthlyOrders = OrdersTable.selectAll()
                .where { (OrdersTable.vendorId eq vendorId) and (OrdersTable.createdAt greaterEq monthStart) }
                .count().toInt()

            mapOf(
                "managers" to managers,
                "cashiers" to cashiers,
                "delivery" to delivery,
                "menuItems" to menuItems,
                "monthlyOrders" to monthlyOrders,
            )
        }
    }

    // ─── List all plans ───────────────────────────────────────────
    fun listActivePlans(): List<ResultRow> {
        return transaction {
            SubscriptionPlansTable.selectAll()
                .where { SubscriptionPlansTable.active eq true }
                .orderBy(SubscriptionPlansTable.displayOrder)
                .toList()
        }
    }

    // ─── Helper: start of current month ───────────────────────────
    private fun getStartOfMonth(instant: Instant): Instant {
        val dateTime = instant.toString().substring(0, 7) // "YYYY-MM"
        return Instant.parse("${dateTime}-01T00:00:00Z")
    }
}
