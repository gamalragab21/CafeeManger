package net.marllex.waselak.backend.domain.model

/**
 * Default feature/channel configuration per business type.
 * Used when creating a new vendor to auto-configure channels, tax, and stock settings.
 *
 * Mirrors [net.marllex.waselak.core.model.DomainFeatures] on the client side.
 */
data class DomainDefaults(
    val enableTables: Boolean,
    val enableKds: Boolean,
    val enableDineIn: Boolean,
    val enableDelivery: Boolean,
    val enableTakeaway: Boolean,
    val enableInStore: Boolean,
    val enablePickupLater: Boolean,
    val taxEnabled: Boolean,
    val defaultTaxPercent: Double,
    val stockMode: String,
    val enableDigitalMenu: Boolean = true,
    val enableRecipe: Boolean = true,
    val enableInstallments: Boolean = false,
) {
    companion object {
        /**
         * Returns sensible defaults for the given [businessType].
         * Request-level overrides still take priority (see AdminApiRoutes).
         */
        fun forType(businessType: String): DomainDefaults = when (businessType.uppercase()) {
            "RESTAURANT" -> DomainDefaults(
                enableTables = true,
                enableKds = true,
                enableDineIn = true,
                enableDelivery = true,
                enableTakeaway = true,
                enableInStore = false,
                enablePickupLater = false,
                taxEnabled = false,
                defaultTaxPercent = 0.0,
                stockMode = "WARN",
            )
            "CAFE" -> DomainDefaults(
                enableTables = true,
                enableKds = true,
                enableDineIn = true,
                enableDelivery = true,
                enableTakeaway = true,
                enableInStore = false,
                enablePickupLater = false,
                taxEnabled = false,
                defaultTaxPercent = 0.0,
                stockMode = "WARN",
            )
            "PHARMACY" -> DomainDefaults(
                enableTables = false,
                enableKds = false,
                enableDineIn = false,
                enableDelivery = true,
                enableTakeaway = false,
                enableInStore = true,
                enablePickupLater = true,
                taxEnabled = true,
                defaultTaxPercent = 14.0,
                stockMode = "ENFORCE",
                enableDigitalMenu = false,
                enableRecipe = false,
                enableInstallments = false,
            )
            "BAKERY" -> DomainDefaults(
                enableTables = false,
                enableKds = true,
                enableDineIn = false,
                enableDelivery = true,
                enableTakeaway = true,
                enableInStore = true,
                enablePickupLater = true,
                taxEnabled = false,
                defaultTaxPercent = 0.0,
                stockMode = "WARN",
            )
            "SUPERMARKET" -> DomainDefaults(
                enableTables = false,
                enableKds = false,
                enableDineIn = false,
                enableDelivery = true,
                enableTakeaway = false,
                enableInStore = true,
                enablePickupLater = true,
                taxEnabled = true,
                defaultTaxPercent = 14.0,
                stockMode = "ENFORCE",
            )
            "GROCERY" -> DomainDefaults(
                enableTables = false,
                enableKds = false,
                enableDineIn = false,
                enableDelivery = true,
                enableTakeaway = false,
                enableInStore = true,
                enablePickupLater = true,
                taxEnabled = true,
                defaultTaxPercent = 14.0,
                stockMode = "ENFORCE",
            )
            "RETAIL" -> DomainDefaults(
                enableTables = false,
                enableKds = false,
                enableDineIn = false,
                enableDelivery = false,
                enableTakeaway = false,
                enableInStore = true,
                enablePickupLater = true,
                taxEnabled = true,
                defaultTaxPercent = 14.0,
                stockMode = "ENFORCE",
                enableDigitalMenu = false,
                enableRecipe = false,
                enableInstallments = true,
            )
            "JUICE_BAR" -> DomainDefaults(
                enableTables = true,
                enableKds = true,
                enableDineIn = true,
                enableDelivery = true,
                enableTakeaway = true,
                enableInStore = false,
                enablePickupLater = false,
                taxEnabled = false,
                defaultTaxPercent = 0.0,
                stockMode = "WARN",
            )
            else -> forType("RESTAURANT") // fallback
        }
    }
}
