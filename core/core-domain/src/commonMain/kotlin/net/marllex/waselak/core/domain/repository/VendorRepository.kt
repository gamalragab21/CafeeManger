package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Vendor

interface VendorRepository {
    fun getMyVendor(): Flow<Vendor?>
    suspend fun refreshVendor(): Result<Vendor>
    suspend fun updateVendor(
        name: String? = null, logoUrl: String? = null, address: String? = null,
        contactPhone: String? = null, walletPhone: String? = null,
        enableTables: Boolean? = null, enableDineIn: Boolean? = null,
        enableDelivery: Boolean? = null,
        biometricRequired: Boolean? = null,
        enableOfflineMode: Boolean? = null,
        loyaltyEnabled: Boolean? = null,
        pointsEarnRate: Double? = null,
        pointsRedeemRate: Double? = null,
        minPointsRedeem: Int? = null,
        maxManualDiscountPercent: Double? = null,
        manualDiscountRequiresPin: Boolean? = null,
        // Tax settings — surfaced through the dashboard so managers can
        // toggle tax on/off and set the percent. Used by the backend at
        // order-creation time (DatabaseConfig.kt:1191) and by receipts
        // to decide whether the tax row is shown (ReceiptModel.kt).
        taxEnabled: Boolean? = null,
        defaultTaxPercent: Double? = null,
    ): Result<Vendor>
}
