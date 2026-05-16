package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.writeByteArray
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.marllex.waselak.backend.data.database.AppReleasesTable
import net.marllex.waselak.backend.data.database.AppSettingsTable
import net.marllex.waselak.backend.domain.service.GithubReleaseService
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

private val ADMIN_PASSWORD = System.getenv("ADMIN_PASSWORD") ?: "admin123"

/**
 * Short-lived shared secret the GitHub Action sends in its X-Push-Token
 * header when calling /release-published. Loaded once at startup —
 * setting the env var requires a backend restart (which is fine because
 * rotating this token is a security-event-only activity).
 */
private val BACKEND_PUSH_TOKEN: String? = System.getenv("BACKEND_PUSH_TOKEN")

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

/**
 * Map a (app, platform) request to the expected release asset filename
 * produced by ci.yml. Naming convention (matches existing pipeline):
 *
 *     {app}-v{version}-{DDMMYY}-release-{platform}.{ext}
 *
 * Examples:
 *     cashier-v1.11.0-160526-release-android.apk
 *     manager-v1.11.0-160526-release-macos.dmg
 *
 * Returns null when platform → ext mapping is unknown.
 */
private fun expectedAssetFilename(
    appName: String,
    platform: String,
    versionName: String,
    ddmmyy: String,
): String? {
    val plat = when (platform.lowercase()) {
        "desktop" -> "windows"
        else -> platform.lowercase()
    }
    val ext = when (plat) {
        "android" -> "apk"
        "windows" -> "msi"
        "macos", "mac" -> "dmg"
        "linux" -> "deb"
        "ios" -> "ipa"
        else -> return null
    }
    return "$appName-v$versionName-$ddmmyy-release-$plat.$ext"
}

/**
 * Build the JSON response served from a GitHub-backed snapshot.
 * Picks the right asset by filename match; if the asset doesn't exist
 * (e.g. iOS isn't published) we still return `has_update` so the app
 * can prompt — just without a working download URL.
 */
private fun buildGithubResponse(
    release: net.marllex.waselak.backend.domain.service.GithubReleaseService.ReleaseSnapshot,
    appName: String,
    platform: String,
    currentVersion: String,
    currentVersionCode: Int,
    variant: String,
): CheckUpdateResponse {
    // Settings (social links etc.) still come from the DB — they're
    // independent of the release source.
    val social = transaction {
        val s = AppSettingsTable.selectAll().firstOrNull()
        listOf(
            s?.get(AppSettingsTable.facebookUrl),
            s?.get(AppSettingsTable.landingPageUrl),
            s?.get(AppSettingsTable.instagramUrl),
            s?.get(AppSettingsTable.whatsappNumber),
        )
    }
    val (fbUrl, lpUrl, igUrl, waNum) = listOf(social[0], social[1], social[2], social[3])

    // Comparison logic:
    //   • Same versionName → up-to-date, regardless of how the backend
    //     computed the version_code. The release notes don't always carry
    //     an explicit VERSION_CODE=N marker, so the backend falls back to
    //     mapping `major*10000+minor*100+patch` → for "1.11.0" that's
    //     11100, while the actual app build is 36. Without the name guard,
    //     an up-to-date client would forever see "update available".
    //   • Different versionName → trust the numeric ordering. This still
    //     works for the normal monotonic upgrade path.
    val hasUpdate = when {
        currentVersion == release.versionName -> false
        else -> release.versionCode > currentVersionCode
    }
    if (!hasUpdate) {
        return CheckUpdateResponse(
            has_update = false,
            latest_version = release.versionName,
            latest_version_code = release.versionCode,
            current_version = currentVersion,
            current_version_code = currentVersionCode,
            update_status = "UP_TO_DATE",
            facebook_url = fbUrl,
            landing_page_url = lpUrl,
            instagram_url = igUrl,
            whatsapp_number = waNum,
        )
    }

    // Find the asset whose name matches our (app, platform) tuple. The
    // CI bakes the build date (DDMMYY) into every filename; we search
    // every asset rather than predict the date because the release
    // commit might span midnight UTC.
    val plat = when (platform.lowercase()) {
        "desktop" -> "windows"
        else -> platform.lowercase()
    }
    val ext = when (plat) {
        "android" -> "apk"
        "windows" -> "msi"
        "macos", "mac" -> "dmg"
        "linux" -> "deb"
        "ios" -> "ipa"
        else -> "apk"
    }
    // Match the right variant (debug / release) for this client. Debug
    // builds have a different signing keystore than release; if a debug
    // client tried to install a release APK Android would reject it for
    // signature mismatch (and vice versa). Default to release for any
    // unknown / missing value so old clients that don't send the param
    // keep working.
    val variantSegment = when (variant.lowercase()) {
        "debug" -> "debug"
        else -> "release"
    }
    val asset = release.assets.firstOrNull { a ->
        // Format: {app}-v{version}-{DDMMYY}-{variant}-{plat}.{ext}
        a.name.startsWith("$appName-v${release.versionName}-")
            && a.name.endsWith("-$variantSegment-$plat.$ext")
    }

    return CheckUpdateResponse(
        has_update = true,
        latest_version = release.versionName,
        latest_version_code = release.versionCode,
        current_version = currentVersion,
        current_version_code = currentVersionCode,
        update_status = "OPTIONAL",
        release_notes = release.releaseNotes,
        release_notes_ar = release.releaseNotesAr,
        download_url = asset?.let { "api/v1/app/download?filename=${it.name}&version=${release.versionName}" },
        download_filename = asset?.name,
        released_at = release.publishedAt,
        facebook_url = fbUrl,
        landing_page_url = lpUrl,
        instagram_url = igUrl,
        whatsapp_number = waNum,
    )
}

// ─── Routes ─────────────────────────────────────────────────────

/**
 * No-auth routes that the GitHub Action calls (X-Push-Token is the
 * auth mechanism). Registered OUTSIDE the JWT auth block in
 * Routing.kt so CI can hit them without minting a user JWT.
 */
fun Route.appUpdatePublicRoutes() {
    val github by KoinJavaComponent.inject<GithubReleaseService>(GithubReleaseService::class.java)

    post("/api/v1/app/release-published") {
        val token = call.request.header("X-Push-Token")
        if (BACKEND_PUSH_TOKEN.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "BACKEND_PUSH_TOKEN not configured"))
            return@post
        }
        if (token != BACKEND_PUSH_TOKEN) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid push token"))
            return@post
        }
        val snapshot = github.refresh()
        if (snapshot != null) {
            call.respond(
                HttpStatusCode.OK,
                ReleasePublishedResponse(
                    ok = true,
                    tag = snapshot.tagName,
                    version_code = snapshot.versionCode,
                    assets = snapshot.assets.size,
                ),
            )
        } else {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Failed to fetch release from GitHub"))
        }
    }
}

@Serializable
private data class ReleasePublishedResponse(
    val ok: Boolean,
    val tag: String,
    val version_code: Int,
    val assets: Int,
)

fun Route.appUpdateRoutes() {
    val github by KoinJavaComponent.inject<GithubReleaseService>(GithubReleaseService::class.java)

    // ── Public endpoint: check for updates (no auth required) ─────
    route("/api/v1/app") {
        get("/check-update") {
            val trace = call.routeTrace()
            trace.step("Check update started")

            val appName = call.parameters["app"] ?: "manager"
            val currentVersion = call.parameters["version"] ?: "1.0.0"
            val currentVersionCode = call.parameters["version_code"]?.toIntOrNull() ?: 1
            val platform = call.parameters["platform"] ?: "android"
            // variant=debug|release. Defaults to "release" since the older
            // installed apps that pre-date this change don't send it.
            val variant = call.parameters["variant"] ?: "release"

            // ── GitHub-backed PRIMARY path ─────────────────────────
            // Source of truth is GitHub Releases. If our cache has a
            // snapshot (push from CI, daily poll, or inline pull below
            // populated it) we serve from there. Falls through to the
            // legacy AppReleasesTable path only when GitHub is unreachable
            // (e.g. PAT expired, GitHub outage, brand-new VPS without a
            // refresh yet).
            val githubRelease = try {
                github.latestRelease()
            } catch (e: Throwable) {
                null
            }
            if (githubRelease != null) {
                val ghResponse = buildGithubResponse(
                    release = githubRelease,
                    appName = appName,
                    platform = platform,
                    currentVersion = currentVersion,
                    currentVersionCode = currentVersionCode,
                    variant = variant,
                )
                trace.step("Update check completed (github)", mapOf(
                    "hasUpdate" to ghResponse.has_update.toString(),
                    "tag" to githubRelease.tagName,
                ))
                call.respond(HttpStatusCode.OK, ghResponse)
                return@get
            }

            // ── Legacy DB-backed FALLBACK path ─────────────────────
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

        // ── Download proxy ─────────────────────────────────────────
        // First tries to match the requested filename against the
        // GitHub release cache (private-repo asset streamed through us
        // with the PAT). If nothing matches we fall back to the
        // legacy Dropbox path so the existing installed-cashiers
        // (which already point at this route) don't break during the
        // GitHub-source rollout.
        get("/download") {
            val trace = call.routeTrace()
            trace.step("Download proxy started")
            val filename = call.parameters["filename"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "filename parameter required"))
            val version = call.parameters["version"] ?: ""

            // ── GitHub-source PRIMARY path ─────────────────────────
            try {
                val release = github.latestRelease()
                val matching = release?.assets?.firstOrNull { it.name == filename }
                if (matching != null) {
                    trace.step("Streaming from GitHub", mapOf("asset" to filename))
                    val asset = github.openAssetStream(matching.apiUrl)
                    if (asset != null) {
                        val ext = filename.substringAfterLast('.', "bin")
                        val contentType = when (ext) {
                            "apk" -> "application/vnd.android.package-archive"
                            "msi" -> "application/x-msi"
                            "dmg" -> "application/x-apple-diskimage"
                            "deb" -> "application/vnd.debian.binary-package"
                            "ipa" -> "application/octet-stream"
                            else -> asset.contentType
                        }
                        call.response.header("Content-Disposition", "attachment; filename=\"$filename\"")
                        // Stream via an explicit OutgoingContent so we can
                        // surface the correct Content-Length to the client.
                        // respondOutputStream forces chunked encoding (no
                        // Content-Length) which leaves the download UI stuck
                        // at 0% — the client uses Content-Length to compute
                        // progress per chunk.
                        val resolvedContentType = io.ktor.http.ContentType.parse(contentType)
                        val knownLength = asset.contentLength.takeIf { it > 0 }
                        call.respond(object : io.ktor.http.content.OutgoingContent.WriteChannelContent() {
                            override val contentType = resolvedContentType
                            override val contentLength: Long? = knownLength
                            override val status = HttpStatusCode.OK
                            override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                                asset.use { rsrc ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = rsrc.stream.read(buffer)
                                        if (n <= 0) break
                                        channel.writeByteArray(buffer.copyOf(n))
                                    }
                                    channel.flushAndClose()
                                }
                            }
                        })
                        trace.step("GitHub download completed", mapOf(
                            "filename" to filename,
                            "size" to asset.contentLength.toString(),
                        ))
                        return@get
                    }
                }
            } catch (e: Throwable) {
                // Fall through to Dropbox.
                trace.step("GitHub path failed, falling back", mapOf("error" to (e.message ?: "")))
            }

            // ── Legacy Dropbox FALLBACK path ───────────────────────
            if (DROPBOX_ACCESS_TOKEN.isBlank()) {
                return@get call.respond(HttpStatusCode.NotFound,
                    mapOf("error" to "Asset not found on GitHub and Dropbox is not configured"))
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
