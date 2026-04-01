package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Defines which features are available for a specific business type.
 *
 * Used by the UI layer to show/hide screens, tabs, buttons, and workflows
 * based on the vendor's business type. Also used by the backend to
 * auto-configure vendor defaults when creating a new vendor.
 *
 * Example:
 * ```
 * val features = DomainFeatures.forType("PHARMACY")
 * if (features.hasBatchExpiry) { /* show expiry alerts */ }
 * ```
 */
@Serializable
data class DomainFeatures(
    // ── Order Channels ──────────────────────────────────────────────
    /** Dine-in tables (floor plan, table assignment) */
    val hasTables: Boolean = false,
    /** Dine-in ordering (eat in the store) */
    val hasDineIn: Boolean = false,
    /** Delivery ordering (driver delivers to customer) */
    val hasDelivery: Boolean = true,
    /** Takeaway ordering (customer picks up) */
    val hasTakeaway: Boolean = true,
    /** In-store purchase (walk-in, retail-style) */
    val hasInStore: Boolean = false,
    /** Pickup later (order now, collect later) */
    val hasPickupLater: Boolean = false,

    // ── Inventory & Stock ───────────────────────────────────────────
    /** Track stock batches with expiry dates (FIFO deduction) */
    val hasBatchExpiry: Boolean = false,
    /** Barcode scanning for POS and stock receiving */
    val hasBarcode: Boolean = false,
    /** Recipe / Bill of Materials (ingredients per item) */
    val hasRecipes: Boolean = false,
    /** Default stock mode: NONE, WARN, or ENFORCE */
    val defaultStockMode: String = "NONE",

    // ── Kitchen & Prep ──────────────────────────────────────────────
    /** Kitchen Display System (order queue for kitchen staff) */
    val hasKDS: Boolean = false,
    /** Course firing (fire appetizers → mains → desserts) */
    val hasCourseFiring: Boolean = false,
    /** Pre-orders / scheduled orders (order for a future date/time) */
    val hasPreOrders: Boolean = false,
    /** Production planning (daily batch quantities) */
    val hasProductionPlanning: Boolean = false,

    // ── Payment & Billing ───────────────────────────────────────────
    /** Split payments / bill splitting */
    val hasSplitPayments: Boolean = false,
    /** Cash drawer management (open/close shift, cash count) */
    val hasCashDrawer: Boolean = true,
    /** Returns and exchanges */
    val hasReturns: Boolean = false,
    /** Customer credit accounts (buy now, pay later — store-level) */
    val hasCustomerCredit: Boolean = false,
    /** Installment payment plans (pay in monthly installments) */
    val hasInstallments: Boolean = false,

    // ── Domain-Specific ─────────────────────────────────────────────
    /** Prescription management (doctor, patient, dosage, refills) */
    val hasPrescriptions: Boolean = false,
    /** Drug interaction warnings */
    val hasDrugInteractions: Boolean = false,
    /** Shisha/hookah management (flavors, coal timer) */
    val hasShisha: Boolean = false,
    /** Custom product orders (cake decoration, custom prints, etc.) */
    val hasCustomOrders: Boolean = false,

    // ── CRM & Loyalty ───────────────────────────────────────────────
    /** Customer loyalty points */
    val hasLoyalty: Boolean = true,
    /** Offers and promo codes */
    val hasOffers: Boolean = true,

    // ── Tax & Compliance ────────────────────────────────────────────
    /** Tax enabled by default */
    val defaultTaxEnabled: Boolean = false,
    /** Default tax percentage */
    val defaultTaxPercent: Double = 0.0,

    // ── Supplier & Purchasing ───────────────────────────────────────
    /** Supplier management and purchase orders */
    val hasSuppliers: Boolean = false,
) {
    companion object {
        /**
         * Returns the default [DomainFeatures] for a given business type.
         * These defaults are used when creating a new vendor and to
         * drive UI visibility across all apps.
         */
        /**
         * Creates features from a Vendor object.
         * Rule: Vendor toggle is the source of truth.
         * Business type defaults are just initial values — vendor can override.
         */
        fun forVendor(vendor: Vendor): DomainFeatures {
            val base = forType(vendor.businessType)
            return base.copy(
                hasSplitPayments = vendor.enableSplitPayment,
                hasCashDrawer = vendor.enableCashDrawer,
                hasReturns = vendor.enableReturns,
                hasCustomerCredit = vendor.enableCustomerCredit,
                hasInstallments = vendor.enableInstallments,
                hasPreOrders = vendor.enableScheduledOrders,
                hasSuppliers = vendor.enableSuppliers,
                hasPrescriptions = vendor.enablePrescriptions,
                hasDrugInteractions = vendor.enableDrugInteractions,
            )
        }

        fun forType(businessType: String): DomainFeatures = when (businessType.uppercase()) {
            "RESTAURANT" -> restaurant()
            "CAFE" -> cafe()
            "PHARMACY" -> pharmacy()
            "BAKERY" -> bakery()
            "SUPERMARKET" -> supermarket()
            "GROCERY" -> grocery()
            "RETAIL" -> retail()
            "JUICE_BAR" -> juiceBar()
            else -> restaurant() // fallback
        }

        // ── Per-type configurations ──────────────────────────────────

        private fun restaurant() = DomainFeatures(
            hasTables = true,
            hasDineIn = true,
            hasDelivery = true,
            hasTakeaway = true,
            hasInStore = false,
            hasPickupLater = false,
            hasBatchExpiry = false,
            hasBarcode = false,
            hasRecipes = true,
            defaultStockMode = "WARN",
            hasKDS = true,
            hasCourseFiring = true,
            hasPreOrders = true,
            hasProductionPlanning = false,
            hasSplitPayments = true,
            hasCashDrawer = true,
            hasReturns = false,
            hasCustomerCredit = false,
            hasInstallments = false,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = false,
            defaultTaxPercent = 0.0,
            hasSuppliers = true,
        )

        private fun cafe() = DomainFeatures(
            hasTables = true,
            hasDineIn = true,
            hasDelivery = true,
            hasTakeaway = true,
            hasInStore = false,
            hasPickupLater = false,
            hasBatchExpiry = false,
            hasBarcode = false,
            hasRecipes = true,
            defaultStockMode = "WARN",
            hasKDS = true,
            hasCourseFiring = false,
            hasPreOrders = false,
            hasProductionPlanning = false,
            hasSplitPayments = true,
            hasCashDrawer = true,
            hasReturns = false,
            hasCustomerCredit = false,
            hasInstallments = false,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = true,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = false,
            defaultTaxPercent = 0.0,
            hasSuppliers = true,
        )

        private fun pharmacy() = DomainFeatures(
            hasTables = false,
            hasDineIn = false,
            hasDelivery = true,
            hasTakeaway = false,
            hasInStore = true,
            hasPickupLater = true,
            hasBatchExpiry = true,
            hasBarcode = true,
            hasRecipes = false,
            defaultStockMode = "ENFORCE",
            hasKDS = false,
            hasCourseFiring = false,
            hasPreOrders = true,
            hasProductionPlanning = false,
            hasSplitPayments = false,
            hasCashDrawer = true,
            hasReturns = true,
            hasCustomerCredit = true,
            hasInstallments = false,
            hasPrescriptions = true,
            hasDrugInteractions = true,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = true,
            defaultTaxPercent = 14.0,
            hasSuppliers = true,
        )

        private fun bakery() = DomainFeatures(
            hasTables = false,
            hasDineIn = false,
            hasDelivery = true,
            hasTakeaway = true,
            hasInStore = true,
            hasPickupLater = true,
            hasBatchExpiry = true,
            hasBarcode = false,
            hasRecipes = true,
            defaultStockMode = "WARN",
            hasKDS = true,
            hasCourseFiring = false,
            hasPreOrders = true,
            hasProductionPlanning = true,
            hasSplitPayments = false,
            hasCashDrawer = true,
            hasReturns = false,
            hasCustomerCredit = false,
            hasInstallments = false,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = true,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = false,
            defaultTaxPercent = 0.0,
            hasSuppliers = true,
        )

        private fun supermarket() = DomainFeatures(
            hasTables = false,
            hasDineIn = false,
            hasDelivery = true,
            hasTakeaway = false,
            hasInStore = true,
            hasPickupLater = true,
            hasBatchExpiry = true,
            hasBarcode = true,
            hasRecipes = false,
            defaultStockMode = "ENFORCE",
            hasKDS = false,
            hasCourseFiring = false,
            hasPreOrders = false,
            hasProductionPlanning = false,
            hasSplitPayments = true,
            hasCashDrawer = true,
            hasReturns = true,
            hasCustomerCredit = true,
            hasInstallments = true,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = true,
            defaultTaxPercent = 14.0,
            hasSuppliers = true,
        )

        private fun grocery() = DomainFeatures(
            hasTables = false,
            hasDineIn = false,
            hasDelivery = true,
            hasTakeaway = false,
            hasInStore = true,
            hasPickupLater = true,
            hasBatchExpiry = true,
            hasBarcode = true,
            hasRecipes = false,
            defaultStockMode = "ENFORCE",
            hasKDS = false,
            hasCourseFiring = false,
            hasPreOrders = false,
            hasProductionPlanning = false,
            hasSplitPayments = false,
            hasCashDrawer = true,
            hasReturns = true,
            hasCustomerCredit = true,
            hasInstallments = true,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = true,
            defaultTaxPercent = 14.0,
            hasSuppliers = true,
        )

        private fun retail() = DomainFeatures(
            hasTables = false,
            hasDineIn = false,
            hasDelivery = false,
            hasTakeaway = false,
            hasInStore = true,
            hasPickupLater = true,
            hasBatchExpiry = false,
            hasBarcode = true,
            hasRecipes = false,
            defaultStockMode = "ENFORCE",
            hasKDS = false,
            hasCourseFiring = false,
            hasPreOrders = false,
            hasProductionPlanning = false,
            hasSplitPayments = true,
            hasCashDrawer = true,
            hasReturns = true,
            hasCustomerCredit = true,
            hasInstallments = true,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = true,
            defaultTaxPercent = 14.0,
            hasSuppliers = true,
        )

        private fun juiceBar() = DomainFeatures(
            hasTables = true,
            hasDineIn = true,
            hasDelivery = true,
            hasTakeaway = true,
            hasInStore = false,
            hasPickupLater = false,
            hasBatchExpiry = true,
            hasBarcode = false,
            hasRecipes = true,
            defaultStockMode = "WARN",
            hasKDS = true,
            hasCourseFiring = false,
            hasPreOrders = false,
            hasProductionPlanning = false,
            hasSplitPayments = false,
            hasCashDrawer = true,
            hasReturns = false,
            hasCustomerCredit = false,
            hasInstallments = false,
            hasPrescriptions = false,
            hasDrugInteractions = false,
            hasShisha = false,
            hasCustomOrders = false,
            hasLoyalty = true,
            hasOffers = true,
            defaultTaxEnabled = false,
            defaultTaxPercent = 0.0,
            hasSuppliers = true,
        )
    }
}
