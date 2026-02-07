package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.Vendor

interface VendorRepository {
    fun getMyVendor(): Flow<Vendor?>
    suspend fun refreshVendor(): Result<Vendor>
    suspend fun updateVendor(
        name: String? = null, logoUrl: String? = null, address: String? = null,
        contactPhone: String? = null, walletPhone: String? = null,
        enableTables: Boolean? = null, enableDineIn: Boolean? = null,
        enableDelivery: Boolean? = null,
    ): Result<Vendor>
}
