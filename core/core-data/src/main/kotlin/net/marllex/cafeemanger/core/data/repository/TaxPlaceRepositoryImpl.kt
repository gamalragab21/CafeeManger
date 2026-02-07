package net.marllex.cafeemanger.core.data.repository

import net.marllex.cafeemanger.core.domain.repository.TaxPlaceRepository
import net.marllex.cafeemanger.core.model.TaxPlace
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.CreateTaxPlaceRequest
import net.marllex.cafeemanger.core.network.dto.UpdateTaxPlaceRequest
import javax.inject.Inject

class TaxPlaceRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
) : TaxPlaceRepository {

    override suspend fun getTaxPlaces(): Result<List<TaxPlace>> = runCatching {
        api.getTaxPlaces().map { it.toDomain() }
    }

    override suspend fun createTaxPlace(name: String, taxPercent: Double, isDefault: Boolean, displayOrder: Int): Result<TaxPlace> =
        runCatching {
            api.createTaxPlace(CreateTaxPlaceRequest(name, taxPercent, isDefault, displayOrder)).toDomain()
        }

    override suspend fun updateTaxPlace(id: String, name: String?, taxPercent: Double?, isDefault: Boolean?, displayOrder: Int?): Result<TaxPlace> =
        runCatching {
            api.updateTaxPlace(id, UpdateTaxPlaceRequest(name, taxPercent, isDefault, displayOrder)).toDomain()
        }

    override suspend fun deleteTaxPlace(id: String): Result<Unit> =
        runCatching { api.deleteTaxPlace(id) }

    private fun net.marllex.cafeemanger.core.network.dto.TaxPlaceResponse.toDomain() = TaxPlace(
        id = id,
        vendorId = vendorId,
        name = name,
        taxPercent = taxPercent,
        isDefault = isDefault,
        displayOrder = displayOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
