package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.VendorDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.UpdateVendorRequest
import net.marllex.waselak.core.network.mapper.toDomain

class VendorRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val vendorDao: VendorDao,
    private val authRepository: AuthRepository,
) : VendorRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getMyVendor(): Flow<Vendor?> =
        vendorDao.getVendorById(vendorId).map { it?.toDomain() }

    override suspend fun refreshVendor(): Result<Vendor> = runCatching {
        val response = api.getMyVendor()
        val vendor = response.toDomain()
        vendorDao.insertVendor(vendor.toDbEntity())
        vendor
    }

    override suspend fun updateVendor(
        name: String?, logoUrl: String?, address: String?,
        contactPhone: String?, walletPhone: String?,
        enableTables: Boolean?, enableDineIn: Boolean?,
        enableDelivery: Boolean?,
        offlineModeEnabled: Boolean?, biometricRequired: Boolean?,
    ): Result<Vendor> = runCatching {
        val response = api.updateMyVendor(
            UpdateVendorRequest(
                name = name, logoUrl = logoUrl, address = address,
                contactPhone = contactPhone, walletPhone = walletPhone,
                enableTables = enableTables, enableDineIn = enableDineIn,
                enableDelivery = enableDelivery,
                offlineModeEnabled = offlineModeEnabled,
                biometricRequired = biometricRequired,
            )
        )
        val vendor = response.toDomain()
        vendorDao.insertVendor(vendor.toDbEntity())
        vendor
    }
}
