package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.data.database.LeadsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateLeadResponse(
    val ok: Boolean,
    val id: String,
)

@Serializable
data class CreateLeadRequest(
    val business_name: String,
    val business_phone: String,
    val contact_name: String,
    val contact_phone: String,
    val notes: String? = null,
    val source: String? = null,
)

@Serializable
data class LeadResponse(
    val id: String,
    val business_name: String,
    val business_phone: String,
    val contact_name: String,
    val contact_phone: String,
    val notes: String?,
    val source: String,
    val status: String,
    val created_at: Long,
)

/**
 * Routes that the public landing page hits. Auth-free by design — these
 * endpoints are reachable from any visitor (no JWT, no HMAC) and live
 * outside the normal vendor-scoped tree. The HMAC plugin already
 * skip-lists `/api/v1/public/`-prefixed paths.
 *
 * - POST /api/v1/public/leads — accept a lead-capture form submission
 *   from the landing page. Light validation, IP + UA recorded for
 *   triage. Returns 201 + lead id on success, 400 on missing fields,
 *   422 on a phone that doesn't pass the (very loose) digit-count
 *   sanity check. We don't enforce uniqueness — duplicate inquiries
 *   from the same business are normal and the sales team de-dupes.
 */
fun Route.leadsPublicRoutes() {
    route("/api/v1/public/leads") {
        post {
            val body = try {
                call.receive<CreateLeadRequest>()
            } catch (e: Throwable) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON: ${e.message}"))
                return@post
            }

            // ── Validation ────────────────────────────────────────
            val biz = body.business_name.trim()
            val bizPhone = body.business_phone.trim()
            val contact = body.contact_name.trim()
            val contactPh = body.contact_phone.trim()
            if (biz.isBlank() || bizPhone.isBlank() || contact.isBlank() || contactPh.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "business_name, business_phone, contact_name, contact_phone are required"),
                )
                return@post
            }
            // Loose phone sanity — at least 7 digits, no upper bound to
            // accommodate country codes. The form is mainly a lead-funnel,
            // not authoritative contact data.
            val phoneDigits = bizPhone.count { it.isDigit() }
            val contactDigits = contactPh.count { it.isDigit() }
            if (phoneDigits < 7 || contactDigits < 7) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    mapOf("error" to "Phone numbers must contain at least 7 digits"),
                )
                return@post
            }
            if (biz.length > 255 || contact.length > 255) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    mapOf("error" to "Names must be <= 255 chars"),
                )
                return@post
            }

            // ── Insert ────────────────────────────────────────────
            val ip = call.request.origin.remoteHost
            val ua = call.request.header("User-Agent")
            val now = Clock.System.now()
            val id = UUID.randomUUID()
            transaction {
                LeadsTable.insert {
                    it[LeadsTable.id] = id
                    it[businessName] = biz
                    it[businessPhone] = bizPhone
                    it[contactName] = contact
                    it[contactPhone] = contactPh
                    it[notes] = body.notes?.takeIf { n -> n.isNotBlank() }
                    it[channel] = body.source?.takeIf { s -> s.isNotBlank() } ?: "landing"
                    it[ipAddress] = ip
                    it[userAgent] = ua
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            call.respond(
                HttpStatusCode.Created,
                CreateLeadResponse(ok = true, id = id.toString()),
            )
        }
    }
}

/**
 * Authenticated admin/CMS routes for triaging leads. Mounted inside the
 * JWT block so only authenticated admins can read the leads list.
 *
 * - GET  /api/v1/admin/leads             — paginated list, newest first
 * - PUT  /api/v1/admin/leads/{id}/status — update status (NEW → CONTACTED → CONVERTED / REJECTED)
 */
fun Route.leadsAdminRoutes() {
    route("/api/v1/admin/leads") {
        get {
            val limit = (call.parameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 500)
            val offset = (call.parameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
            val statusFilter = call.parameters["status"]?.takeIf { it.isNotBlank() }
            val leads = transaction {
                val rows = LeadsTable.selectAll()
                    .let { q -> if (statusFilter != null) q.where { LeadsTable.status eq statusFilter } else q }
                    .orderBy(LeadsTable.createdAt, SortOrder.DESC)
                    .limit(limit).offset(offset)
                rows.map { row ->
                    LeadResponse(
                        id = row[LeadsTable.id].value.toString(),
                        business_name = row[LeadsTable.businessName],
                        business_phone = row[LeadsTable.businessPhone],
                        contact_name = row[LeadsTable.contactName],
                        contact_phone = row[LeadsTable.contactPhone],
                        notes = row[LeadsTable.notes],
                        source = row[LeadsTable.channel],
                        status = row[LeadsTable.status],
                        created_at = row[LeadsTable.createdAt].toEpochMilliseconds(),
                    )
                }
            }
            call.respond(HttpStatusCode.OK, leads)
        }
    }
}
