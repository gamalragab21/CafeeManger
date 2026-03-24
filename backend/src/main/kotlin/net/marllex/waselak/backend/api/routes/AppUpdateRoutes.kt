package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.marllex.waselak.backend.data.database.AppReleasesTable
import net.marllex.waselak.backend.data.database.AppSettingsTable
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
    val released_date: String? = null,   // DDMMYY
    val is_active: Boolean = true,
    val released_at: Long,
    val created_at: Long,
)

@Serializable
data class CreateReleaseDto(
    val version_name: String,
    val version_code: Int,
    val update_status: String = "OPTIONAL",
    val release_notes: String? = null,
    val release_notes_ar: String? = null,
    val min_version_code: Int = 1,
    val drive_folder_id: String? = null,
    val released_date: String? = null,  // DDMMYY format — if null, uses today
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
    // Social links (global app settings)
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

// ─── Dropbox URL Builder ───────────────────────────────────────

private val DROPBOX_ACCESS_TOKEN = System.getenv("DROPBOX_ACCESS_TOKEN") ?: ""

/**
 * Auto-builds the download info based on naming convention:
 * File: {app}-v{version}-{DDMMYY}-release-{platform}.{ext}
 * Dropbox path: /waselak-builds/v{version}/release/{filename}
 *
 * Returns the backend proxy download URL and filename.
 */
private fun buildDownloadInfo(
    versionName: String,
    releasedDate: String?,
    releasedAt: kotlinx.datetime.Instant,
    appName: String,
    platform: String,
): Pair<String?, String> {
    val ddmmyy = if (!releasedDate.isNullOrBlank()) {
        releasedDate
    } else {
        val instant = java.time.Instant.ofEpochMilli(releasedAt.toEpochMilliseconds())
        val localDate = instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate()
        "${localDate.dayOfMonth.toString().padStart(2, '0')}${localDate.monthValue.toString().padStart(2, '0')}${(localDate.year % 100).toString().padStart(2, '0')}"
    }

    val resolvedPlatform = when (platform.lowercase()) {
        "desktop" -> "windows"
        else -> platform.lowercase()
    }

    val ext = when (resolvedPlatform) {
        "android" -> "apk"
        "windows" -> "msi"
        "macos", "mac" -> "dmg"
        "linux" -> "deb"
        "ios" -> "ipa"
        else -> "apk"
    }

    val fileName = "$appName-v$versionName-$ddmmyy-release-$resolvedPlatform.$ext"
    val downloadUrl = "api/v1/app/download?filename=$fileName&version=$versionName"

    return downloadUrl to fileName
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



            val response = transaction {
                // Load global social links
                val settings = AppSettingsTable.selectAll().firstOrNull()
                val fbUrl = settings?.get(AppSettingsTable.facebookUrl)
                val lpUrl = settings?.get(AppSettingsTable.landingPageUrl)
                val igUrl = settings?.get(AppSettingsTable.instagramUrl)
                val waNum = settings?.get(AppSettingsTable.whatsappNumber)

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
                        facebook_url = fbUrl,
                        landing_page_url = lpUrl,
                        instagram_url = igUrl,
                        whatsapp_number = waNum,
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

                    val releasedDate = latestRelease[AppReleasesTable.releasedDate]

                    // Auto-build download URL and filename from naming convention
                    val (downloadUrl, downloadFilename) = buildDownloadInfo(
                        versionName = latestVersionName,
                        releasedDate = releasedDate,
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
                        facebook_url = fbUrl,
                        landing_page_url = lpUrl,
                        instagram_url = igUrl,
                        whatsapp_number = waNum,
                    )
                }
            }

            trace.step("Update check completed", mapOf(
                "hasUpdate" to response.has_update.toString(),
                "updateStatus" to response.update_status
            ))
            call.respond(HttpStatusCode.OK, response)
        }

        // ── Download proxy: fetches file from Dropbox and streams to client ──
        get("/download") {
            val trace = call.routeTrace()
            trace.step("Download proxy started")
            val filename = call.parameters["filename"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "filename parameter required"))
            val version = call.parameters["version"] ?: ""

            if (DROPBOX_ACCESS_TOKEN.isBlank()) {
                return@get call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Dropbox credentials not configured"))
            }

            try {
                // Build Dropbox path
                val dropboxPath = "/waselak-builds/v$version/release/$filename"
                trace.step("Downloading from Dropbox", mapOf("path" to dropboxPath))

                // Use Dropbox download API
                val downloadUrl = java.net.URL("https://content.dropboxapi.com/2/files/download")
                val conn = downloadUrl.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $DROPBOX_ACCESS_TOKEN")
                conn.setRequestProperty("Dropbox-API-Arg", """{"path": "$dropboxPath"}""")
                conn.doInput = true

                if (conn.responseCode != 200) {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    trace.step("Dropbox download failed", mapOf("code" to conn.responseCode.toString(), "error" to errorBody))
                    return@get call.respond(HttpStatusCode.NotFound,
                        mapOf("error" to "File not found on Dropbox", "filename" to filename, "path" to dropboxPath))
                }

                val fileSize = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L

                // Stream the file to the client
                val ext = filename.substringAfterLast('.', "bin")
                val contentType = when (ext) {
                    "apk" -> "application/vnd.android.package-archive"
                    "msi" -> "application/x-msi"
                    "dmg" -> "application/x-apple-diskimage"
                    "deb" -> "application/vnd.debian.binary-package"
                    "ipa" -> "application/octet-stream"
                    else -> "application/octet-stream"
                }

                call.response.header("Content-Disposition", "attachment; filename=\"$filename\"")
                if (fileSize > 0) {
                    call.response.header("Content-Length", fileSize.toString())
                }

                call.respondOutputStream(
                    contentType = io.ktor.http.ContentType.parse(contentType),
                    status = HttpStatusCode.OK,
                ) {
                    conn.inputStream.use { input ->
                        input.copyTo(this, bufferSize = 8192)
                    }
                }

                trace.step("Download proxy completed", mapOf("filename" to filename, "size" to fileSize.toString()))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to "Download failed: ${e.message}"))
            }
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

// ─── App Settings DTOs ──────────────────────────────────────────

@Serializable
data class AppSettingsDto(
    val facebook_url: String? = null,
    val landing_page_url: String? = null,
    val instagram_url: String? = null,
    val whatsapp_number: String? = null,
)

// ─── CMS App Settings Routes (inside authenticate block) ────────

fun Route.cmsAppSettingsRoutes() {
    route("/api/v1/cms/settings") {
        // GET — read current settings
        get {
            val settings = transaction {
                AppSettingsTable.selectAll().firstOrNull()
            }
            if (settings == null) {
                call.respond(HttpStatusCode.OK, AppSettingsDto())
            } else {
                call.respond(HttpStatusCode.OK, AppSettingsDto(
                    facebook_url = settings[AppSettingsTable.facebookUrl],
                    landing_page_url = settings[AppSettingsTable.landingPageUrl],
                    instagram_url = settings[AppSettingsTable.instagramUrl],
                    whatsapp_number = settings[AppSettingsTable.whatsappNumber],
                ))
            }
        }

        // PUT — update settings (upsert)
        put {
            val request = call.receive<AppSettingsDto>()
            transaction {
                val existing = AppSettingsTable.selectAll().firstOrNull()
                if (existing == null) {
                    AppSettingsTable.insert {
                        it[facebookUrl] = request.facebook_url
                        it[landingPageUrl] = request.landing_page_url
                        it[instagramUrl] = request.instagram_url
                        it[whatsappNumber] = request.whatsapp_number
                        it[updatedAt] = Clock.System.now()
                    }
                } else {
                    AppSettingsTable.update({ AppSettingsTable.id eq existing[AppSettingsTable.id] }) {
                        request.facebook_url?.let { v -> it[facebookUrl] = v }
                        request.landing_page_url?.let { v -> it[landingPageUrl] = v }
                        request.instagram_url?.let { v -> it[instagramUrl] = v }
                        request.whatsapp_number?.let { v -> it[whatsappNumber] = v }
                        it[updatedAt] = Clock.System.now()
                    }
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "Settings updated"))
        }
    }
}
