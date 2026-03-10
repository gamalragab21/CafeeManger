package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.database.dao.OfferDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.OfferRepository
import net.marllex.waselak.core.model.Offer
import net.marllex.waselak.core.model.OfferItem
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateOfferItemRequest
import net.marllex.waselak.core.network.dto.CreateOfferRequest
import net.marllex.waselak.core.network.dto.UpdateOfferRequest
import net.marllex.waselak.core.network.mapper.toDomain

class OfferRepositoryImpl(
    private val api: WaselakApiClient,
    private val offerDao: OfferDao,
    private val authRepository: AuthRepository,
) : OfferRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getOffers(): Flow<List<Offer>> {
        AppLogger.d("OfferRepo", "Reading all offers from local DB")
        return offerDao.getOffers(vendorId).map { list ->
            list.map { offer ->
                val items = offerDao.getOfferItems(offer.id).map { it.toDomain() }
                offer.toDomain(items)
            }
        }
    }

    override fun getActiveOffers(): Flow<List<Offer>> {
        val now = Clock.System.now().toEpochMilliseconds()
        AppLogger.d("OfferRepo", "Reading active offers from local DB")
        return offerDao.getActiveOffers(vendorId, now).map { list ->
            list.map { offer ->
                val items = offerDao.getOfferItems(offer.id).map { it.toDomain() }
                offer.toDomain(items)
            }
        }
    }

    override suspend fun refreshOffers(): Result<List<Offer>> = runCatching {
        AppLogger.d("OfferRepo", "Refreshing offers from API")
        val response = api.getOffers()
        val offers = response.map { it.toDomain() }
        AppLogger.i("OfferRepo", "Fetched ${offers.size} offers from API")

        // Clear and re-insert all offers with items
        offerDao.deleteAllOffers(vendorId)
        offers.forEach { offer ->
            offerDao.insertOfferWithItems(
                offer = offer.toDbEntity(),
                items = offer.items.map { it.toDbEntity() }
            )
        }

        AppLogger.i("OfferRepo", "Offers refresh complete: ${offers.size} offers saved")
        offers
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to refresh offers", e)
    }

    override suspend fun createOffer(
        name: String,
        description: String?,
        imageUrl: String?,
        discountType: String,
        discountValue: Double,
        active: Boolean,
        expiresAt: Long?,
        displayOrder: Int,
        items: List<Pair<String, Int>>,
        promoCode: String?,
        maxUses: Int?,
        startsAt: Long?,
    ): Result<Offer> = runCatching {
        AppLogger.d("OfferRepo", "Creating offer: name=$name, items=${items.size}")
        val response = api.createOffer(CreateOfferRequest(
            name = name,
            description = description,
            imageUrl = imageUrl,
            discountType = discountType,
            discountValue = discountValue,
            active = active,
            expiresAt = expiresAt,
            displayOrder = displayOrder,
            items = items.map { (itemId, qty) -> CreateOfferItemRequest(itemId = itemId, quantity = qty) },
            promoCode = promoCode,
            maxUses = maxUses,
            startsAt = startsAt,
        ))
        val offer = response.toDomain()
        offerDao.insertOfferWithItems(
            offer = offer.toDbEntity(),
            items = offer.items.map { it.toDbEntity() }
        )
        AppLogger.i("OfferRepo", "Offer created: id=${offer.id}, name=${offer.name}")
        offer
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to create offer: name=$name", e)
    }

    override suspend fun updateOffer(
        id: String,
        name: String?,
        description: String?,
        imageUrl: String?,
        discountType: String?,
        discountValue: Double?,
        active: Boolean?,
        expiresAt: Long?,
        displayOrder: Int?,
        items: List<Pair<String, Int>>?,
        promoCode: String?,
        maxUses: Int?,
        startsAt: Long?,
    ): Result<Offer> = runCatching {
        AppLogger.d("OfferRepo", "Updating offer: id=$id")
        val response = api.updateOffer(id, UpdateOfferRequest(
            name = name,
            description = description,
            imageUrl = imageUrl,
            discountType = discountType,
            discountValue = discountValue,
            active = active,
            expiresAt = expiresAt,
            displayOrder = displayOrder,
            items = items?.map { (itemId, qty) -> CreateOfferItemRequest(itemId = itemId, quantity = qty) },
            promoCode = promoCode,
            maxUses = maxUses,
            startsAt = startsAt,
        ))
        val offer = response.toDomain()
        offerDao.insertOfferWithItems(
            offer = offer.toDbEntity(),
            items = offer.items.map { it.toDbEntity() }
        )
        AppLogger.i("OfferRepo", "Offer updated: id=${offer.id}, name=${offer.name}")
        offer
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to update offer: id=$id", e)
    }

    override suspend fun deleteOffer(id: String): Result<Unit> = runCatching {
        AppLogger.d("OfferRepo", "Deleting offer: id=$id")
        api.deleteOffer(id)
        offerDao.deleteOffer(id)
        AppLogger.i("OfferRepo", "Offer deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to delete offer: id=$id", e)
    }

    override suspend fun toggleOffer(id: String): Result<Offer> = runCatching {
        AppLogger.d("OfferRepo", "Toggling offer: id=$id")
        val response = api.toggleOffer(id)
        val offer = response.toDomain()
        offerDao.insertOfferWithItems(
            offer = offer.toDbEntity(),
            items = offer.items.map { it.toDbEntity() }
        )
        AppLogger.i("OfferRepo", "Offer toggled: id=${offer.id}, active=${offer.active}")
        offer
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to toggle offer: id=$id", e)
    }

    override suspend fun uploadImage(imageBytes: ByteArray, fileName: String): Result<String> = runCatching {
        AppLogger.d("OfferRepo", "Uploading offer image: fileName=$fileName")
        val response = api.uploadImage(imageBytes, fileName)
        AppLogger.i("OfferRepo", "Offer image uploaded: url=${response.url}")
        response.url
    }.onFailure { e ->
        AppLogger.e("OfferRepo", "Failed to upload offer image", e)
    }
}
