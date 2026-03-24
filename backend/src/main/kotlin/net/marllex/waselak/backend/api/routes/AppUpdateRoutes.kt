package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.data.database.AppReleasesTable
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val ADMIN_PASSWORD = System.getenv("ADMIN_PASSWORD") ?: "admin123"

private suspend fun RoutingContext.requireAdmin(): Boolean {
    val password = call.request.header("X-Admin-Password")
    if (password != ADMIN_PASSWORD) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid admin password"))
        return false
    }
    return true
}

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class AppReleaseDto(
    val id: String,
    val version_name: String,
    val version_code: Int,
    val update_status: String,       // OPTIONAL, MANDATORY
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int = 1,
    val drive_folder_id: String? = null,
    val is_active: Boolean = true,
    val released_at: Long,
    val created_at: Long,
)

@Serializable
data class CreateReleaseDto(
    val version_name: String,
    val version_code: Int,
    val update_status: String = "OPTIONAL",  // OPTIONAL or MANDATORY
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int = 1,
    val drive_folder_id: String? = null,
)

@Serializable
data class UpdateReleaseDto(
    val update_status: String? = null,
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int? = null,
    val drive_folder_id: String? = null,
    val is_active: Boolean? = null,
)

@Serializable
data class CheckUpdateResponse(
    val has_update: Boolean,
    val latest_version: String,
    val latest_version_code: Int,
    val current_version: String,
    val current_version_code: Int,
    val update_status: String,       // UP_TO_DATE, OPTIONAL, MANDATORY
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val download_url: String? = null,
    val released_at: Long? = null,
)

// ─── Google Drive URL Builder ───────────────────────────────────

private fun buildDownloadUrl(
    driveFolderId: String?,
    parentFolderId: String,
    versionName: String,
    appName: String,
    platform: String,
): String? {
    // The files on Google Drive follow this naming:
    // {app}-v{version}-{DDMMYY}-release-{platform}.{ext}
    // Stored in: parentFolder/v{version}/release/
    // We return a Google Drive folder link — user can find their file there
    val folderId = driveFolderId ?: return null
    return "https://drive.google.com/drive/folders/$folderId"
}

// ─── Routes ─────────────────────────────────────────────────────

fun Route.appUpdateRoutes() {
    // ── Public endpoint: check for updates (no auth required) ─────
    route("/api/v1/app") {
        get("/check-update") {
            val trace = call.routeTrace()
            trace.step("Check update started")

            val appName = call.parameters["app"] ?: "manager"
            val currentVersion = call.parameters["version"] ?: "1.0.0"
            val currentVersionCode = call.parameters["version_code"]?.toIntOrNull() ?: 1
            val platform = call.parameters["platform"] ?: "android"

            val parentFolderId = System.getenv("GOOGLE_DRIVE_FOLDER_ID") ?: ""

            val response = transaction {
                // Find the latest active release
                val latestRelease = AppReleasesTable.selectAll()
                    .where { AppReleasesTable.isActive eq true }
                    .orderBy(AppReleasesTable.versionCode, SortOrder.DESC)
                    .firstOrNull()

                if (latestRelease == null || latestRelease[AppReleasesTable.versionCode] <= currentVersionCode) {
                    // No update available
                    CheckUpdateResponse(
                        has_update = false,
                        latest_version = currentVersion,
                        latest_version_code = currentVersionCode,
                        current_version = currentVersion,
                        current_version_code = currentVersionCode,
                        update_status = "UP_TO_DATE",
                    )
                } else {
                    val latestVersionName = latestRelease[AppReleasesTable.versionName]
                    val latestVersionCode = latestRelease[AppReleasesTable.versionCode]
                    val minVersionCode = latestRelease[AppReleasesTable.minVersionCode]
                    val driveFolderId = latestRelease[AppReleasesTable.driveFolderId]

                    // Determine update status
                    val updateStatus = if (currentVersionCode < minVersionCode) {
                        "MANDATORY"  // Below minimum → forced update
                    } else {
                        latestRelease[AppReleasesTable.updateStatus]  // OPTIONAL or MANDATORY
                    }

                    val downloadUrl = buildDownloadUrl(driveFolderId, parentFolderId, latestVersionName, appName, platform)

                    CheckUpdateResponse(
                        has_update = true,
                        latest_version = latestVersionName,
                        latest_version_code = latestVersionCode,
                        current_version = currentVersion,
                        current_version_code = currentVersionCode,
                        update_status = updateStatus,
                        release_notes = latestRelease[AppReleasesTable.releaseNotes],
                        release_notes_ar = latestRelease[AppReleasesTable.releaseNotesAr],
                        download_url = downloadUrl,
                        released_at = latestRelease[AppReleasesTable.releasedAt].toEpochMilliseconds(),
                    )
                }
            }

            trace.step("Update check completed", mapOf(
                "hasUpdate" to response.has_update.toString(),
                "updateStatus" to response.update_status
            ))
            call.respond(HttpStatusCode.OK, response)
        }
    }

    // ── Admin endpoints: manage releases ──────────────────────────
    route("/api/v1/admin/releases") {

        // GET all releases
        get {
            val trace = call.routeTrace()
            if (!requireAdmin()) return@get

            val releases = transaction {
                AppReleasesTable.selectAll()
                    .orderBy(AppReleasesTable.versionCode, SortOrder.DESC)
                    .map { it.toReleaseDto() }
            }
            call.respond(HttpStatusCode.OK, releases)
        }

        // POST create a new release
        post {
            val trace = call.routeTrace()
            if (!requireAdmin()) return@post
            val request = call.receive<CreateReleaseDto>()

            require(request.version_name.isNotBlank()) { "Version name is required" }
            require(request.version_code > 0) { "Version code must be positive" }
            require(request.update_status in listOf("OPTIONAL", "MANDATORY")) {
                "Update status must be OPTIONAL or MANDATORY"
            }

            val release = transaction {
                val now = Clock.System.now()

                val id = AppReleasesTable.insertAndGetId {
                    it[versionName] = request.version_name
                    it[versionCode] = request.version_code
                    it[updateStatus] = request.update_status
                    it[releaseNotes] = request.release_notes
                    it[releaseNotesAr] = request.release_notes_ar
                    it[minVersionCode] = request.min_version_code
                    it[driveFolderId] = request.drive_folder_id
                    it[isActive] = true
                    it[releasedAt] = now
                    it[createdAt] = now
                }

                AppReleasesTable.selectAll()
                    .where { AppReleasesTable.id eq id }
                    .first()
                    .toReleaseDto()
            }

            call.respond(HttpStatusCode.Created, release)
        }

        // PUT update a release
        put("/{id}") {
            val trace = call.routeTrace()
            if (!requireAdmin()) return@put
            val releaseId = call.parameters["id"] ?: throw IllegalArgumentException("Release ID required")
            val request = call.receive<UpdateReleaseDto>()

            val updated = transaction {
                val uuid = UUID.fromString(releaseId)

                AppReleasesTable.update({ AppReleasesTable.id eq uuid }) {
                    request.update_status?.let { v -> it[updateStatus] = v }
                    request.release_notes?.let { v -> it[releaseNotes] = v }
                    request.release_notes_ar?.let { v -> it[releaseNotesAr] = v }
                    request.min_version_code?.let { v -> it[minVersionCode] = v }
                    request.drive_folder_id?.let { v -> it[driveFolderId] = v }
                    request.is_active?.let { v -> it[isActive] = v }
                }

                AppReleasesTable.selectAll()
                    .where { AppReleasesTable.id eq uuid }
                    .firstOrNull()
                    ?.toReleaseDto()
            }

            if (updated != null) {
                call.respond(HttpStatusCode.OK, updated)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Release not found"))
            }
        }

        // DELETE a release
        delete("/{id}") {
            if (!requireAdmin()) return@delete
            val releaseId = call.parameters["id"] ?: throw IllegalArgumentException("Release ID required")

            transaction {
                AppReleasesTable.deleteWhere { AppReleasesTable.id eq UUID.fromString(releaseId) }
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }
    }
}

// ─── Mapper ─────────────────────────────────────────────────────

private fun ResultRow.toReleaseDto() = AppReleaseDto(
    id = this[AppReleasesTable.id].toString(),
    version_name = this[AppReleasesTable.versionName],
    version_code = this[AppReleasesTable.versionCode],
    update_status = this[AppReleasesTable.updateStatus],
    release_notes = this[AppReleasesTable.releaseNotes],
    release_notes_ar = this[AppReleasesTable.releaseNotesAr],
    min_version_code = this[AppReleasesTable.minVersionCode],
    drive_folder_id = this[AppReleasesTable.driveFolderId],
    is_active = this[AppReleasesTable.isActive],
    released_at = this[AppReleasesTable.releasedAt].toEpochMilliseconds(),
    created_at = this[AppReleasesTable.createdAt].toEpochMilliseconds(),
)
