package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.OffersTable
import net.marllex.waselak.backend.data.database.OfferItemsTable
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class OfferItemDto(
    val id: String,
    val offer_id: String,
    val item_id: String,
    val item_name: String,
    val item_price: Double,
    val quantity: Int,
    val created_at: Long? = null,
)

@Serializable
data class OfferDto(
    val id: String,
    val vendor_id: String,
    val name: String,
    val description: String? = null,
    val image_url: String? = null,
    val discount_type: String,
    val discount_value: Double,
    val active: Boolean,
    val expires_at: Long? = null,
    val promo_code: String? = null,
    val max_uses: Int? = null,
    val used_count: Int = 0,
    val starts_at: Long? = null,
    val display_order: Int,
    val items: List<OfferItemDto> = emptyList(),
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateOfferItemDto(
    val item_id: String,
    val quantity: Int = 1,
)

@Serializable
data class CreateOfferDto(
    val name: String,
    val description: String? = null,
    val image_url: String? = null,
    val discount_type: String, // FIXED_PRICE or PERCENT
    val discount_value: Double,
    val active: Boolean = true,
    val expires_at: Long? = null,
    val promo_code: String? = null,
    val max_uses: Int? = null,
    val starts_at: Long? = null,
    val display_order: Int = 0,
    val items: List<CreateOfferItemDto> = emptyList(),
)

@Serializable
data class UpdateOfferDto(
    val name: String? = null,
    val description: String? = null,
    val image_url: String? = null,
    val discount_type: String? = null,
    val discount_value: Double? = null,
    val active: Boolean? = null,
    val expires_at: Long? = null,
    val promo_code: String? = null,
    val max_uses: Int? = null,
    val starts_at: Long? = null,
    val display_order: Int? = null,
    val items: List<CreateOfferItemDto>? = null,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.offerRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/offers") {

        // GET /api/v1/offers?active=true
        get {
            val trace = call.routeTrace()
            trace.step("List offers started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")
            val activeFilter = call.parameters["active"]?.toBooleanStrictOrNull()
            trace.step("Querying offers", mapOf("vendorId" to principal.vendorId, "activeFilter" to (activeFilter?.toString() ?: "null")))

            val now = Clock.System.now().toEpochMilliseconds()

            val offers = transaction {
                var query = OffersTable.selectAll()
                    .where { OffersTable.vendorId eq UUID.fromString(principal.vendorId) }

                // Filter by active status
                if (activeFilter == true) {
                    query = query.andWhere { OffersTable.active eq true }
                    // Filter out expired offers
                    query = query.andWhere {
                        (OffersTable.expiresAt.isNull()) or (OffersTable.expiresAt greater now)
                    }
                    // Filter out offers that haven't started yet
                    query = query.andWhere {
                        (OffersTable.startsAt.isNull()) or (OffersTable.startsAt lessEq now)
                    }
                    // Filter out offers that exceeded their usage limit
                    query = query.andWhere {
                        (OffersTable.maxUses.isNull()) or (OffersTable.usedCount less OffersTable.maxUses!!)
                    }
                } else if (activeFilter == false) {
                    query = query.andWhere { OffersTable.active eq false }
                }

                val offerRows = query
                    .orderBy(OffersTable.displayOrder)
                    .orderBy(OffersTable.createdAt, SortOrder.DESC)
                    .toList()

                offerRows.map { row ->
                    val offerId = row[OffersTable.id].value
                    val items = loadOfferItems(offerId)
                    row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")
                }
            }
            trace.step("Offers retrieved", mapOf("count" to offers.size.toString()))
            trace.step("List offers completed")
            call.respond(HttpStatusCode.OK, offers)
        }

        // GET /api/v1/offers/{id}
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get offer by ID started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Querying offer", mapOf("offerId" to id))

            val offer = transaction {
                val row = OffersTable.selectAll()
                    .where {
                        (OffersTable.id eq UUID.fromString(id)) and
                        (OffersTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Offer not found")

                val items = loadOfferItems(row[OffersTable.id].value)
                row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")
            }
            trace.step("Offer found", mapOf("offerId" to offer.id, "name" to offer.name))
            trace.step("Get offer by ID completed")
            call.respond(HttpStatusCode.OK, offer)
        }

        // POST /api/v1/offers
        post {
            val trace = call.routeTrace()
            trace.step("Create offer started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")
            val request = call.receive<CreateOfferDto>()

            require(request.name.isNotBlank()) { "Offer name is required" }
            require(request.discount_type in listOf("FIXED_PRICE", "PERCENT")) {
                "discount_type must be FIXED_PRICE or PERCENT"
            }
            require(request.discount_value > 0) { "discount_value must be positive" }
            if (request.discount_type == "PERCENT") {
                require(request.discount_value <= 100) { "Percentage discount cannot exceed 100" }
            }
            require(request.items.isNotEmpty()) { "Offer must have at least one item" }
            trace.step("Creating offer", mapOf(
                "name" to request.name,
                "discountType" to request.discount_type,
                "discountValue" to request.discount_value.toString(),
                "itemsCount" to request.items.size.toString()
            ))

            val offer = transaction {
                val vendorUuid = UUID.fromString(principal.vendorId)

                // Validate all item IDs exist and belong to vendor
                request.items.forEach { offerItem ->
                    val exists = ItemsTable.selectAll().where {
                        (ItemsTable.id eq UUID.fromString(offerItem.item_id)) and
                        (ItemsTable.vendorId eq vendorUuid)
                    }.count() > 0
                    require(exists) { "Item ${offerItem.item_id} not found or doesn't belong to vendor" }
                }

                // Insert offer
                val offerId = OffersTable.insertAndGetId {
                    it[vendorId] = vendorUuid
                    it[name] = request.name
                    it[description] = request.description
                    it[imageUrl] = request.image_url
                    it[discountType] = request.discount_type
                    it[discountValue] = request.discount_value.toBigDecimal()
                    it[active] = request.active
                    it[expiresAt] = request.expires_at
                    it[promoCode] = request.promo_code
                    it[maxUses] = request.max_uses
                    it[startsAt] = request.starts_at
                    it[displayOrder] = request.display_order
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }

                // Insert offer items
                request.items.forEach { offerItem ->
                    OfferItemsTable.insertAndGetId {
                        it[OfferItemsTable.offerId] = offerId.value
                        it[itemId] = UUID.fromString(offerItem.item_id)
                        it[quantity] = offerItem.quantity
                        it[createdAt] = Clock.System.now()
                    }
                }

                // Return created offer
                val row = OffersTable.selectAll()
                    .where { OffersTable.id eq offerId }
                    .first()
                val items = loadOfferItems(offerId.value)
                row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")
            }
            trace.step("Offer created", mapOf("offerId" to offer.id, "name" to offer.name, "itemsCount" to offer.items.size.toString()))
            trace.step("Create offer completed")
            call.respond(HttpStatusCode.Created, offer)
        }

        // PUT /api/v1/offers/{id}
        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update offer started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateOfferDto>()
            trace.step("Updating offer", mapOf(
                "offerId" to id,
                "name" to (request.name ?: "unchanged"),
                "discountType" to (request.discount_type ?: "unchanged"),
                "itemsProvided" to (request.items != null).toString()
            ))

            request.discount_type?.let { dt ->
                require(dt in listOf("FIXED_PRICE", "PERCENT")) {
                    "discount_type must be FIXED_PRICE or PERCENT"
                }
            }
            request.discount_value?.let { dv ->
                require(dv > 0) { "discount_value must be positive" }
                if (request.discount_type == "PERCENT" || (request.discount_type == null)) {
                    // If type is PERCENT (or unchanged but was PERCENT), validate
                }
            }

            val (offer, oldImageToDelete) = transaction {
                val vendorUuid = UUID.fromString(principal.vendorId)
                val offerUuid = UUID.fromString(id)

                // Verify offer exists and belongs to vendor
                val existing = OffersTable.selectAll().where {
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }.firstOrNull() ?: throw NoSuchElementException("Offer not found")

                val oldImageUrl = existing[OffersTable.imageUrl]

                // Update offer fields
                OffersTable.update({
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.description?.let { stmt[description] = it }
                    request.image_url?.let { stmt[imageUrl] = it }
                    request.discount_type?.let { stmt[discountType] = it }
                    request.discount_value?.let { stmt[discountValue] = it.toBigDecimal() }
                    request.active?.let { stmt[active] = it }
                    request.expires_at?.let { stmt[expiresAt] = it }
                    request.promo_code?.let { stmt[promoCode] = it }
                    request.max_uses?.let { stmt[maxUses] = it }
                    request.starts_at?.let { stmt[startsAt] = it }
                    request.display_order?.let { stmt[displayOrder] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                // Replace items if provided
                if (request.items != null) {
                    require(request.items.isNotEmpty()) { "Offer must have at least one item" }

                    // Validate all new item IDs
                    request.items.forEach { offerItem ->
                        val exists = ItemsTable.selectAll().where {
                            (ItemsTable.id eq UUID.fromString(offerItem.item_id)) and
                            (ItemsTable.vendorId eq vendorUuid)
                        }.count() > 0
                        require(exists) { "Item ${offerItem.item_id} not found or doesn't belong to vendor" }
                    }

                    // Delete old items
                    OfferItemsTable.deleteWhere { OfferItemsTable.offerId eq offerUuid }

                    // Insert new items
                    request.items.forEach { offerItem ->
                        OfferItemsTable.insertAndGetId {
                            it[OfferItemsTable.offerId] = offerUuid
                            it[itemId] = UUID.fromString(offerItem.item_id)
                            it[quantity] = offerItem.quantity
                            it[createdAt] = Clock.System.now()
                        }
                    }
                }

                // Return updated offer + old image to delete if changed
                val row = OffersTable.selectAll()
                    .where { OffersTable.id eq offerUuid }
                    .first()
                val items = loadOfferItems(offerUuid)
                val offerDto = row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")

                // Determine if old image needs cleanup (image changed)
                val imageToDelete = if (request.image_url != null && oldImageUrl != null && oldImageUrl != request.image_url) {
                    oldImageUrl
                } else null

                Pair(offerDto, imageToDelete)
            }
            // Delete old image file if image was changed
            if (oldImageToDelete != null) {
                trace.step("Deleting old offer image", mapOf("oldImageUrl" to oldImageToDelete))
                deleteUploadedFile(oldImageToDelete)
            }
            trace.step("Offer updated", mapOf("offerId" to offer.id, "name" to offer.name, "itemsCount" to offer.items.size.toString()))
            trace.step("Update offer completed")
            call.respond(HttpStatusCode.OK, offer)
        }

        // DELETE /api/v1/offers/{id}
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete offer started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Deleting offer", mapOf("offerId" to id, "vendorId" to principal.vendorId))

            val imageUrl = transaction {
                val offerUuid = UUID.fromString(id)
                val vendorUuid = UUID.fromString(principal.vendorId)

                val existing = OffersTable.selectAll().where {
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }.firstOrNull() ?: throw NoSuchElementException("Offer not found")

                val imgUrl = existing[OffersTable.imageUrl]

                // Offer items cascade-deleted via FK
                OffersTable.deleteWhere {
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }
                imgUrl
            }
            // Delete uploaded image file if present
            if (imageUrl != null) {
                trace.step("Deleting offer image", mapOf("imageUrl" to imageUrl))
                deleteUploadedFile(imageUrl)
            }
            trace.step("Offer deleted", mapOf("offerId" to id))
            trace.step("Delete offer completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // POST /api/v1/offers/apply-promo
        post("/apply-promo") {
            val trace = call.routeTrace()
            trace.step("Apply promo code started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")

            @Serializable
            data class ApplyPromoDto(val code: String)

            val request = call.receive<ApplyPromoDto>()
            require(request.code.isNotBlank()) { "Promo code is required" }
            val vendorUUID = UUID.fromString(principal.vendorId)
            val now = Clock.System.now().toEpochMilliseconds()
            trace.step("Promo code received", mapOf("code" to request.code))

            val offer = transaction {
                val row = OffersTable.selectAll()
                    .where {
                        (OffersTable.vendorId eq vendorUUID) and
                        (OffersTable.promoCode eq request.code.uppercase()) and
                        (OffersTable.active eq true)
                    }
                    .andWhere {
                        (OffersTable.expiresAt.isNull()) or (OffersTable.expiresAt greater now)
                    }
                    .andWhere {
                        (OffersTable.startsAt.isNull()) or (OffersTable.startsAt lessEq now)
                    }
                    .andWhere {
                        (OffersTable.maxUses.isNull()) or (OffersTable.usedCount less OffersTable.maxUses!!)
                    }
                    .firstOrNull()
                    ?: throw NoSuchElementException("Invalid or expired promo code")

                val items = loadOfferItems(row[OffersTable.id].value)
                row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")
            }
            trace.step("Promo code applied", mapOf("offerId" to offer.id, "name" to offer.name))
            trace.step("Apply promo code completed")
            call.respond(HttpStatusCode.OK, offer)
        }

        // PATCH /api/v1/offers/{id}/toggle
        patch("/{id}/toggle") {
            val trace = call.routeTrace()
            trace.step("Toggle offer started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "OFFERS")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Toggling offer active status", mapOf("offerId" to id))

            val offer = transaction {
                val offerUuid = UUID.fromString(id)
                val vendorUuid = UUID.fromString(principal.vendorId)

                val existing = OffersTable.selectAll().where {
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }.firstOrNull() ?: throw NoSuchElementException("Offer not found")

                val currentActive = existing[OffersTable.active]

                OffersTable.update({
                    (OffersTable.id eq offerUuid) and
                    (OffersTable.vendorId eq vendorUuid)
                }) {
                    it[active] = !currentActive
                    it[updatedAt] = Clock.System.now()
                }

                val row = OffersTable.selectAll()
                    .where { OffersTable.id eq offerUuid }
                    .first()
                val items = loadOfferItems(offerUuid)
                row.toOfferDto(items, call.request.header("Host") ?: "localhost:8080", call.request.header("X-Forwarded-Proto") ?: "http")
            }
            trace.step("Offer toggled", mapOf("offerId" to offer.id, "active" to offer.active.toString()))
            trace.step("Toggle offer completed")
            call.respond(HttpStatusCode.OK, offer)
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun loadOfferItems(offerId: UUID): List<OfferItemDto> {
    return OfferItemsTable
        .join(ItemsTable, JoinType.LEFT, OfferItemsTable.itemId, ItemsTable.id)
        .selectAll()
        .where { OfferItemsTable.offerId eq offerId }
        .map { row ->
            OfferItemDto(
                id = row[OfferItemsTable.id].toString(),
                offer_id = row[OfferItemsTable.offerId].toString(),
                item_id = row[OfferItemsTable.itemId].toString(),
                item_name = row[ItemsTable.name],
                item_price = row[ItemsTable.price].toDouble(),
                quantity = row[OfferItemsTable.quantity],
                created_at = row[OfferItemsTable.createdAt].toEpochMilliseconds(),
            )
        }
}

private fun ResultRow.toOfferDto(items: List<OfferItemDto>, host: String = "localhost:8080", scheme: String = "http") = OfferDto(
    id = this[OffersTable.id].toString(),
    vendor_id = this[OffersTable.vendorId].toString(),
    name = this[OffersTable.name],
    description = this[OffersTable.description],
    image_url = rewriteUploadUrl(this[OffersTable.imageUrl], host, scheme),
    discount_type = this[OffersTable.discountType],
    discount_value = this[OffersTable.discountValue].toDouble(),
    active = this[OffersTable.active],
    expires_at = this[OffersTable.expiresAt],
    promo_code = this[OffersTable.promoCode],
    max_uses = this[OffersTable.maxUses],
    used_count = this[OffersTable.usedCount],
    starts_at = this[OffersTable.startsAt],
    display_order = this[OffersTable.displayOrder],
    items = items,
    created_at = this[OffersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[OffersTable.updatedAt].toEpochMilliseconds(),
)
