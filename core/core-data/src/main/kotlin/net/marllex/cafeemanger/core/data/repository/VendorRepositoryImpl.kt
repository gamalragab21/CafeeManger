package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.VendorDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.Vendor
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.UpdateVendorRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class VendorRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val vendorDao: VendorDao,
    private val authRepository: AuthRepository,
) : VendorRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getMyVendor(): Flow<Vendor?> =
        vendorDao.getVendorById(vendorId).map { it?.toDomain() }

    override suspend fun refreshVendor(): Result<Vendor> = runCatching {
        val response = api.getMyVendor()
        val vendor = response.toDomain()
        vendorDao.insertVendor(vendor.toEntity())
        vendor
    }

    override suspend fun updateVendor(
        name: String?, logoUrl: String?, address: String?,
        contactPhone: String?, walletPhone: String?,
        enableTables: Boolean?, enableDineIn: Boolean?,
        enableDelivery: Boolean?,
    ): Result<Vendor> = runCatching {
        val response = api.updateMyVendor(
            UpdateVendorRequest(
                name = name, logoUrl = logoUrl, address = address,
                contactPhone = contactPhone, walletPhone = walletPhone,
                enableTables = enableTables, enableDineIn = enableDineIn,
                enableDelivery = enableDelivery,
            )
        )
        val vendor = response.toDomain()
        vendorDao.insertVendor(vendor.toEntity())
        vendor
    }
}
