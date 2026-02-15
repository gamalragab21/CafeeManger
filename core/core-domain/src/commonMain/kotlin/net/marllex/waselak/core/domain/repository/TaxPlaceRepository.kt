package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.TaxPlace

interface TaxPlaceRepository {
    suspend fun getTaxPlaces(): Result<List<TaxPlace>>
    suspend fun createTaxPlace(name: String, taxPercent: Double, isDefault: Boolean, displayOrder: Int): Result<TaxPlace>
    suspend fun updateTaxPlace(id: String, name: String?, taxPercent: Double?, isDefault: Boolean?, displayOrder: Int?): Result<TaxPlace>
    suspend fun deleteTaxPlace(id: String): Result<Unit>
}
