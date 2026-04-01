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
                installments = true, // Available on all plans
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
    fun checkFeature(vendorId: UUID, feature: String) {
        val limits = getVendorPlanLimits(vendorId)
        when (feature) {
            "STOCK" -> if (!limits.stockManagement) throw FeatureNotAvailableException(
                "Stock management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "WORKER", "ATTENDANCE" -> if (!limits.workerAttendance) throw FeatureNotAvailableException(
                "Worker & attendance management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "OVERTIME" -> if (!limits.overtime) throw FeatureNotAvailableException(
                "Overtime management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "SALARY" -> if (!limits.salaries) throw FeatureNotAvailableException(
                "Salary management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "CUSTOMER" -> if (!limits.customerManagement) throw FeatureNotAvailableException(
                "Customer management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "DELIVERY" -> if (!limits.deliveryModule) throw FeatureNotAvailableException(
                "Delivery module is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "ANALYTICS" -> if (limits.analytics == "NONE") throw FeatureNotAvailableException(
                "Analytics & dashboard is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "EXPORT" -> if (limits.analytics == "NONE") throw FeatureNotAvailableException(
                "Data export is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "DIGITAL_MENU" -> if (limits.digitalMenu == "NONE") throw FeatureNotAvailableException(
                "Digital menu is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "TABLE" -> if (!limits.tableManagement) throw FeatureNotAvailableException(
                "Table management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "DIGITAL_RECEIPT" -> if (!limits.digitalReceipt) throw FeatureNotAvailableException(
                "Digital receipt is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "WORKER_QRCODE" -> if (!limits.workerQrcode) throw FeatureNotAvailableException(
                "Worker QR code / ID card is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "LOYALTY" -> if (!limits.loyaltyPoints) throw FeatureNotAvailableException(
                "Loyalty points is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "MANUAL_DISCOUNT" -> if (!limits.manualDiscount) throw FeatureNotAvailableException(
                "Manual discounts is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "OFFERS" -> if (!limits.offersManagement) throw FeatureNotAvailableException(
                "Offers management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "CASH_DRAWER" -> if (!limits.cashDrawer) throw FeatureNotAvailableException(
                "Cash drawer management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "SPLIT_PAYMENT" -> if (!limits.splitPayment) throw FeatureNotAvailableException(
                "Split payment is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "CUSTOMER_CREDIT" -> if (!limits.customerCredit) throw FeatureNotAvailableException(
                "Customer credit is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "INSTALLMENTS" -> if (!limits.installments) throw FeatureNotAvailableException(
                "Installment plans are not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "SUPPLIERS" -> if (!limits.suppliers) throw FeatureNotAvailableException(
                "Supplier management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "RETURNS" -> if (!limits.returns) throw FeatureNotAvailableException(
                "Returns management is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "PRESCRIPTIONS" -> if (!limits.prescriptions) throw FeatureNotAvailableException(
                "Prescriptions is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "DRUG_INTERACTIONS" -> if (!limits.drugInteractions) throw FeatureNotAvailableException(
                "Drug interactions is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "SCHEDULED_ORDERS" -> if (!limits.scheduledOrders) throw FeatureNotAvailableException(
                "Scheduled orders is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
            "KDS" -> if (!limits.kds) throw FeatureNotAvailableException(
                "Kitchen display system is not available on the ${limits.planDisplayName} plan. Please upgrade."
            )
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
            logger.info("Vendor $vendorId assigned to plan $planName")
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
