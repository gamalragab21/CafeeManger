package net.marllex.waselak.core.model

/**
 * Configuration for vendor business types.
 * Maps a business type to display labels and icons used throughout the app.
 */
data class VendorTypeConfig(
    val type: String,
    val icon: String,
    val displayNameAr: String,
    val displayNameEn: String,
    val orderLabelAr: String,
    val orderLabelEn: String,
    val ordersLabelAr: String,
    val ordersLabelEn: String,
    val itemLabelAr: String,
    val itemLabelEn: String,
)

object VendorTypeConfigs {
    private val configs = mapOf(
        "RESTAURANT" to VendorTypeConfig(
            type = "RESTAURANT",
            icon = "🍽️",
            displayNameAr = "مطعم",
            displayNameEn = "Restaurant",
            orderLabelAr = "طلب",
            orderLabelEn = "Order",
            ordersLabelAr = "طلبات",
            ordersLabelEn = "Orders",
            itemLabelAr = "صنف",
            itemLabelEn = "Item",
        ),
        "CAFE" to VendorTypeConfig(
            type = "CAFE",
            icon = "☕",
            displayNameAr = "كافيه",
            displayNameEn = "Café",
            orderLabelAr = "طلب",
            orderLabelEn = "Order",
            ordersLabelAr = "طلبات",
            ordersLabelEn = "Orders",
            itemLabelAr = "صنف",
            itemLabelEn = "Item",
        ),
        "PHARMACY" to VendorTypeConfig(
            type = "PHARMACY",
            icon = "💊",
            displayNameAr = "صيدلية",
            displayNameEn = "Pharmacy",
            orderLabelAr = "روشتة",
            orderLabelEn = "Prescription",
            ordersLabelAr = "روشتات",
            ordersLabelEn = "Prescriptions",
            itemLabelAr = "دواء",
            itemLabelEn = "Medicine",
        ),
        "BAKERY" to VendorTypeConfig(
            type = "BAKERY",
            icon = "🥖",
            displayNameAr = "مخبز",
            displayNameEn = "Bakery",
            orderLabelAr = "طلب",
            orderLabelEn = "Order",
            ordersLabelAr = "طلبات",
            ordersLabelEn = "Orders",
            itemLabelAr = "صنف",
            itemLabelEn = "Item",
        ),
        "SUPERMARKET" to VendorTypeConfig(
            type = "SUPERMARKET",
            icon = "🛒",
            displayNameAr = "سوبر ماركت",
            displayNameEn = "Supermarket",
            orderLabelAr = "فاتورة",
            orderLabelEn = "Invoice",
            ordersLabelAr = "فواتير",
            ordersLabelEn = "Invoices",
            itemLabelAr = "منتج",
            itemLabelEn = "Product",
        ),
        "GROCERY" to VendorTypeConfig(
            type = "GROCERY",
            icon = "🛒",
            displayNameAr = "بقالة",
            displayNameEn = "Grocery",
            orderLabelAr = "فاتورة",
            orderLabelEn = "Invoice",
            ordersLabelAr = "فواتير",
            ordersLabelEn = "Invoices",
            itemLabelAr = "منتج",
            itemLabelEn = "Product",
        ),
        "RETAIL" to VendorTypeConfig(
            type = "RETAIL",
            icon = "🏪",
            displayNameAr = "محل",
            displayNameEn = "Retail",
            orderLabelAr = "فاتورة",
            orderLabelEn = "Invoice",
            ordersLabelAr = "فواتير",
            ordersLabelEn = "Invoices",
            itemLabelAr = "منتج",
            itemLabelEn = "Product",
        ),
        "JUICE_BAR" to VendorTypeConfig(
            type = "JUICE_BAR",
            icon = "🧃",
            displayNameAr = "عصائر",
            displayNameEn = "Juice Bar",
            orderLabelAr = "طلب",
            orderLabelEn = "Order",
            ordersLabelAr = "طلبات",
            ordersLabelEn = "Orders",
            itemLabelAr = "صنف",
            itemLabelEn = "Item",
        ),
    )

    fun forType(businessType: String): VendorTypeConfig =
        configs[businessType] ?: configs["RESTAURANT"]!!

    fun iconForType(businessType: String): String =
        configs[businessType]?.icon ?: "🍽️"

    val allTypes: List<VendorTypeConfig> get() = configs.values.toList()
}
