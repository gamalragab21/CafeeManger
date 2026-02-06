package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.Vendor

interface VendorRepository {
    fun getMyVendor(): Flow<Vendor?>
    suspend fun refreshVendor(): Result<Vendor>
    suspend fun updateVendor(
        name: String?, logoUrl: String?, address: String?,
        contactPhone: String?, walletPhone: String?
    ): Result<Vendor>
}
