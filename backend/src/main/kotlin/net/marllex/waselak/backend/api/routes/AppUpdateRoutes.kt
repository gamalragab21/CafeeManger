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
    val download_filename: String? = null,
    val released_at: Long? = null,
)

// ─── Google Drive URL Builder ───────────────────────────────────

// Parent folder ID from env (set in GitHub Secrets / gradle.properties)
private val GOOGLE_DRIVE_PARENT_FOLDER = System.getenv("GOOGLE_DRIVE_FOLDER_ID") ?: ""

/**
 * Auto-builds the download info based on naming convention:
 * File: {app}-v{version}-{DDMMYY}-release-{platform}.{ext}
 * Folder: parentFolder/v{version}/release/
 *
 * Returns the Google Drive folder URL for the version's release folder.
 * The user can find their specific file there.
 */
private fun buildDownloadInfo(
    versionName: String,
    releasedAt: kotlinx.datetime.Instant,
    appName: String,
    platform: String,
): Pair<String?, String> {
    // Build the expected filename
    val instant = java.time.Instant.ofEpochMilli(releasedAt.toEpochMilliseconds())
    val localDate = instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate()
    val ddmmyy = "${localDate.dayOfMonth.toString().padStart(2, '0')}${localDate.monthValue.toString().padStart(2, '0')}${(localDate.year % 100).toString().padStart(2, '0')}"

    val ext = when (platform.lowercase()) {
        "android" -> "apk"
        "windows" -> "msi"
        "macos" -> "dmg"
        "linux" -> "deb"
        else -> "apk"
    }

    val fileName = "$appName-v$versionName-$ddmmyy-release-$platform.$ext"

    // Build the folder URL (users browse to find their file)
    val folderUrl = if (GOOGLE_DRIVE_PARENT_FOLDER.isNotBlank()) {
        "https://drive.google.com/drive/folders/$GOOGLE_DRIVE_PARENT_FOLDER"
    } else null

    return folderUrl to fileName
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
                    val releasedAt = latestRelease[AppReleasesTable.releasedAt]

                    // Determine update status
                    val updateStatus = if (currentVersionCode < minVersionCode) {
                        "MANDATORY"  // Below minimum → forced update
                    } else {
                        latestRelease[AppReleasesTable.updateStatus]  // OPTIONAL or MANDATORY
                    }

                    // Auto-build download URL and filename from naming convention
                    val (downloadUrl, downloadFilename) = buildDownloadInfo(
                        versionName = latestVersionName,
                        releasedAt = releasedAt,
                        appName = appName,
                        platform = platform,
                    )

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
                        download_filename = downloadFilename,
                        released_at = releasedAt.toEpochMilliseconds(),
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
