package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class DrugInteractionDto(
    val id: String,
    val vendor_id: String,
    val item_id_a: String,
    val item_name_a: String? = null,
    val item_id_b: String,
    val item_name_b: String? = null,
    val severity: String,          // MILD, MODERATE, SEVERE, CONTRAINDICATED
    val description: String,
    val description_ar: String? = null,
    val recommendation: String? = null,
    val active: Boolean = true,
    val created_at: Long,
)

@Serializable
data class CreateDrugInteractionDto(
    val item_id_a: String,
    val item_id_b: String,
    val severity: String = "MODERATE",
    val description: String,
    val description_ar: String? = null,
    val recommendation: String? = null,
)

@Serializable
data class CheckInteractionsDto(
    val item_ids: List<String>,
)

@Serializable
data class InteractionCheckResult(
    val has_interactions: Boolean,
    val interactions: List<DrugInteractionDto>,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.drugInteractionRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/drug-interactions") {

        // GET all drug interactions for vendor
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "DRUG_INTERACTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val severity = call.parameters["severity"]
            val itemId = call.parameters["item_id"]

            val interactions = transaction {
                var query = DrugInteractionsTable.selectAll().where {
                    DrugInteractionsTable.vendorId eq vendorUUID
                }
                severity?.let { s ->
                    query = query.andWhere { DrugInteractionsTable.severity eq s.uppercase() }
                }
                itemId?.let { iid ->
                    val itemUUID = UUID.fromString(iid)
                    query = query.andWhere {
                        (DrugInteractionsTable.itemIdA eq itemUUID) or
                        (DrugInteractionsTable.itemIdB eq itemUUID)
                    }
                }

                query.orderBy(DrugInteractionsTable.createdAt, SortOrder.DESC)
                    .map { it.toDrugInteractionDto() }
            }
            call.respond(HttpStatusCode.OK, interactions)
        }

        // POST create a drug interaction rule
        post {
            val trace = call.routeTrace()
            trace.step("Create drug interaction rule")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "DRUG_INTERACTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<CreateDrugInteractionDto>()

            require(request.severity in listOf("MILD", "MODERATE", "SEVERE", "CONTRAINDICATED")) {
                "Invalid severity: ${request.severity}"
            }
            require(request.item_id_a != request.item_id_b) { "Cannot create interaction between same drug" }
            require(request.description.isNotBlank()) { "Description is required" }

            val interaction = transaction {
                val now = Clock.System.now()
                val itemAUUID = UUID.fromString(request.item_id_a)
                val itemBUUID = UUID.fromString(request.item_id_b)

                // Check if interaction already exists (in either direction)
                val existing = DrugInteractionsTable.selectAll().where {
                    (DrugInteractionsTable.vendorId eq vendorUUID) and
                    (
                        ((DrugInteractionsTable.itemIdA eq itemAUUID) and (DrugInteractionsTable.itemIdB eq itemBUUID)) or
                        ((DrugInteractionsTable.itemIdA eq itemBUUID) and (DrugInteractionsTable.itemIdB eq itemAUUID))
                    )
                }.firstOrNull()

                if (existing != null) {
                    throw IllegalStateException("Drug interaction already exists between these items")
                }

                val id = DrugInteractionsTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[itemIdA] = itemAUUID
                    it[itemIdB] = itemBUUID
                    it[severity] = request.severity
                    it[description] = request.description
                    it[descriptionAr] = request.description_ar
                    it[recommendation] = request.recommendation
                    it[active] = true
                    it[createdAt] = now
                }

                val itemNameA = ItemsTable.selectAll().where { ItemsTable.id eq itemAUUID }
                    .firstOrNull()?.get(ItemsTable.name)
                val itemNameB = ItemsTable.selectAll().where { ItemsTable.id eq itemBUUID }
                    .firstOrNull()?.get(ItemsTable.name)

                DrugInteractionDto(
                    id = id.toString(),
                    vendor_id = vendorUUID.toString(),
                    item_id_a = request.item_id_a,
                    item_name_a = itemNameA,
                    item_id_b = request.item_id_b,
                    item_name_b = itemNameB,
                    severity = request.severity,
                    description = request.description,
                    description_ar = request.description_ar,
                    recommendation = request.recommendation,
                    active = true,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Drug interaction created", mapOf("id" to interaction.id))
            call.respond(HttpStatusCode.Created, interaction)
        }

        // POST check interactions between a list of medicines
        post("/check") {
            val trace = call.routeTrace()
            trace.step("Check drug interactions")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "DRUG_INTERACTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val request = call.receive<CheckInteractionsDto>()

            require(request.item_ids.size >= 2) { "At least 2 items required to check interactions" }

            val result = transaction {
                val itemUUIDs = request.item_ids.map { UUID.fromString(it) }
                val foundInteractions = mutableListOf<DrugInteractionDto>()

                for (i in itemUUIDs.indices) {
                    for (j in i + 1 until itemUUIDs.size) {
                        val interaction = DrugInteractionsTable.selectAll().where {
                            (DrugInteractionsTable.vendorId eq vendorUUID) and
                            (DrugInteractionsTable.active eq true) and
                            (
                                ((DrugInteractionsTable.itemIdA eq itemUUIDs[i]) and (DrugInteractionsTable.itemIdB eq itemUUIDs[j])) or
                                ((DrugInteractionsTable.itemIdA eq itemUUIDs[j]) and (DrugInteractionsTable.itemIdB eq itemUUIDs[i]))
                            )
                        }.firstOrNull()

                        if (interaction != null) {
                            foundInteractions.add(interaction.toDrugInteractionDto())
                        }
                    }
                }

                InteractionCheckResult(
                    has_interactions = foundInteractions.isNotEmpty(),
                    interactions = foundInteractions,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }

        // DELETE a drug interaction rule
        delete("/{id}") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "DRUG_INTERACTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val idUUID = UUID.fromString(id)
                val count = DrugInteractionsTable.deleteWhere {
                    (DrugInteractionsTable.id eq idUUID) and
                    (DrugInteractionsTable.vendorId eq vendorUUID)
                }
                if (count == 0) throw NoSuchElementException("Drug interaction not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("deleted" to id))
        }

        // PATCH toggle active status
        patch("/{id}/toggle") {
            val trace = call.routeTrace()
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "DRUG_INTERACTIONS")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val updated = transaction {
                val idUUID = UUID.fromString(id)
                val row = DrugInteractionsTable.selectAll().where {
                    (DrugInteractionsTable.id eq idUUID) and
                    (DrugInteractionsTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Drug interaction not found")

                val newActive = !row[DrugInteractionsTable.active]
                DrugInteractionsTable.update({ DrugInteractionsTable.id eq idUUID }) {
                    it[active] = newActive
                }
                mapOf("id" to id, "active" to newActive)
            }
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toDrugInteractionDto(): DrugInteractionDto {
    val itemIdA = this[DrugInteractionsTable.itemIdA]
    val itemIdB = this[DrugInteractionsTable.itemIdB]
    val nameA = ItemsTable.selectAll().where { ItemsTable.id eq itemIdA }
        .firstOrNull()?.get(ItemsTable.name)
    val nameB = ItemsTable.selectAll().where { ItemsTable.id eq itemIdB }
        .firstOrNull()?.get(ItemsTable.name)

    return DrugInteractionDto(
        id = this[DrugInteractionsTable.id].toString(),
        vendor_id = this[DrugInteractionsTable.vendorId].toString(),
        item_id_a = itemIdA.toString(),
        item_name_a = nameA,
        item_id_b = itemIdB.toString(),
        item_name_b = nameB,
        severity = this[DrugInteractionsTable.severity],
        description = this[DrugInteractionsTable.description],
        description_ar = this[DrugInteractionsTable.descriptionAr],
        recommendation = this[DrugInteractionsTable.recommendation],
        active = this[DrugInteractionsTable.active],
        created_at = this[DrugInteractionsTable.createdAt].toEpochMilliseconds(),
    )
}
