package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.TaxPlaceRepository
import net.marllex.waselak.core.model.TaxPlace
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateTaxPlaceRequest
import net.marllex.waselak.core.network.dto.UpdateTaxPlaceRequest

class TaxPlaceRepositoryImpl constructor(
    private val api: WaselakApiClient,
) : TaxPlaceRepository {

    override suspend fun getTaxPlaces(): Result<List<TaxPlace>> = runCatching {
        AppLogger.d(TAG, "Fetching tax places")
        api.getTaxPlaces().map { it.toDomain() }
    }

    override suspend fun createTaxPlace(name: String, taxPercent: Double, isDefault: Boolean, displayOrder: Int): Result<TaxPlace> =
        runCatching {
            AppLogger.d(TAG, "Creating tax place: name=$name, taxPercent=$taxPercent")
            api.createTaxPlace(CreateTaxPlaceRequest(name, taxPercent, isDefault, displayOrder)).toDomain()
        }

    override suspend fun updateTaxPlace(id: String, name: String?, taxPercent: Double?, isDefault: Boolean?, displayOrder: Int?): Result<TaxPlace> =
        runCatching {
            AppLogger.d(TAG, "Updating tax place: id=$id")
            api.updateTaxPlace(id, UpdateTaxPlaceRequest(name, taxPercent, isDefault, displayOrder)).toDomain()
        }

    override suspend fun deleteTaxPlace(id: String): Result<Unit> =
        runCatching {
            AppLogger.d(TAG, "Deleting tax place: id=$id")
            api.deleteTaxPlace(id)
        }

    private fun net.marllex.waselak.core.network.dto.TaxPlaceResponse.toDomain() = TaxPlace(
        id = id,
        vendorId = vendorId,
        name = name,
        taxPercent = taxPercent,
        isDefault = isDefault,
        displayOrder = displayOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private companion object {
        const val TAG = "TaxPlaceRepo"
    }
}
