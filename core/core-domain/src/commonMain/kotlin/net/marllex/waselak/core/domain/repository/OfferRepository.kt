package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Offer

interface OfferRepository {
    fun getOffers(): Flow<List<Offer>>
    fun getActiveOffers(): Flow<List<Offer>>
    suspend fun refreshOffers(): Result<List<Offer>>
    suspend fun createOffer(
        name: String,
        description: String?,
        imageUrl: String?,
        discountType: String,
        discountValue: Double,
        active: Boolean,
        expiresAt: Long?,
        displayOrder: Int,
        items: List<Pair<String, Int>>, // itemId to quantity
        promoCode: String? = null,
        maxUses: Int? = null,
        startsAt: Long? = null,
    ): Result<Offer>
    suspend fun updateOffer(
        id: String,
        name: String?,
        description: String?,
        imageUrl: String?,
        discountType: String?,
        discountValue: Double?,
        active: Boolean?,
        expiresAt: Long?,
        displayOrder: Int?,
        items: List<Pair<String, Int>>?, // null = don't change items
        promoCode: String? = null,
        maxUses: Int? = null,
        startsAt: Long? = null,
    ): Result<Offer>
    suspend fun deleteOffer(id: String): Result<Unit>
    suspend fun toggleOffer(id: String): Result<Offer>
    suspend fun uploadImage(imageBytes: ByteArray, fileName: String): Result<String>
}
