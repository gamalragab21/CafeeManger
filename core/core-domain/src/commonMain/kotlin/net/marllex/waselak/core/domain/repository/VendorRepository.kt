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
    ): Result<Vendor>
}
