package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Offers
import net.marllex.waselak.core.database.Offer_items

class OfferDao(private val db: WaselakDatabase) {
    private val offerQueries get() = db.offerQueries
    private val offerItemQueries get() = db.offerItemQueries

    fun getOffers(vendorId: String): Flow<List<Offers>> =
        offerQueries.getOffers(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getActiveOffers(vendorId: String, now: Long): Flow<List<Offers>> =
        offerQueries.getActiveOffers(vendorId, now, now).asFlow().mapToList(Dispatchers.Default)

    fun getOfferById(id: String): Flow<Offers?> =
        offerQueries.getOfferById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    fun getOfferItems(offerId: String): List<Offer_items> =
        offerItemQueries.getOfferItems(offerId).executeAsList()

    suspend fun insertOffer(offer: Offers) {
        offerQueries.insertOffer(
            id = offer.id,
            vendor_id = offer.vendor_id,
            name = offer.name,
            description = offer.description,
            image_url = offer.image_url,
            discount_type = offer.discount_type,
            discount_value = offer.discount_value,
            active = offer.active,
            expires_at = offer.expires_at,
            promo_code = offer.promo_code,
            max_uses = offer.max_uses,
            used_count = offer.used_count,
            starts_at = offer.starts_at,
            display_order = offer.display_order,
            created_at = offer.created_at,
            updated_at = offer.updated_at,
        )
    }

    suspend fun insertOfferItems(items: List<Offer_items>) {
        db.transaction {
            items.forEach { item ->
                offerItemQueries.insertOfferItem(
                    id = item.id,
                    offer_id = item.offer_id,
                    item_id = item.item_id,
                    item_name = item.item_name,
                    item_price = item.item_price,
                    quantity = item.quantity,
                )
            }
        }
    }

    suspend fun insertOfferWithItems(offer: Offers, items: List<Offer_items>) {
        db.transaction {
            offerQueries.insertOffer(
                id = offer.id,
                vendor_id = offer.vendor_id,
                name = offer.name,
                description = offer.description,
                image_url = offer.image_url,
                discount_type = offer.discount_type,
                discount_value = offer.discount_value,
                active = offer.active,
                expires_at = offer.expires_at,
                promo_code = offer.promo_code,
                max_uses = offer.max_uses,
                used_count = offer.used_count,
                starts_at = offer.starts_at,
                display_order = offer.display_order,
                created_at = offer.created_at,
                updated_at = offer.updated_at,
            )
            // Clear old items first (for updates)
            offerItemQueries.deleteOfferItems(offer.id)
            items.forEach { item ->
                offerItemQueries.insertOfferItem(
                    id = item.id,
                    offer_id = item.offer_id,
                    item_id = item.item_id,
                    item_name = item.item_name,
                    item_price = item.item_price,
                    quantity = item.quantity,
                )
            }
        }
    }

    suspend fun deleteOffer(id: String) {
        db.transaction {
            offerItemQueries.deleteOfferItems(id)
            offerQueries.deleteOffer(id)
        }
    }

    suspend fun deleteAllOffers(vendorId: String) {
        db.transaction {
            offerItemQueries.deleteAllOfferItems()
            offerQueries.deleteAllOffers(vendorId)
        }
    }
}
