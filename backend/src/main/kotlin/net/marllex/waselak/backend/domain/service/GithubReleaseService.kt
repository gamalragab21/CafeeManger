package net.marllex.waselak.backend.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Source of truth for "what's the latest published Waselak release?".
 *
 * Wraps the GitHub Releases REST API:
 *   GET /repos/{owner}/{repo}/releases/latest
 *
 * Three trigger paths populate this service's cache, satisfying the
 * hybrid push+poll+pull design we agreed on:
 *
 *   • PUSH  — the GitHub Action's final step calls
 *             POST /api/v1/app/release-published, which calls
 *             [refresh] here. Latency: ~seconds after the release goes
 *             live on GitHub.
 *
 *   • POLL  — once a day, [periodicPoll] calls [refresh]. Catches
 *             releases that missed the push (VPS reboot during CI run,
 *             flaky network on GitHub's side, etc.).
 *
 *   • PULL  — when /check-update is hit and the cache is older than
 *             [CACHE_TTL_MS], we call [refresh] inline so the response
 *             still returns fresh data. With a 5-min TTL we burn at
 *             most 288 GitHub API calls/day under heavy traffic —
 *             well inside the 5000 req/h authenticated quota.
 *
 * Asset URLs returned for private repos require the same `Authorization:
 * Bearer $GITHUB_PAT` header to actually download. [downloadAsset] is
 * the helper for that — used by the `/api/v1/app/download` proxy route.
 */
class GithubReleaseService(
    private val repo: String,
    private val token: String?,
) {
    private val log = LoggerFactory.getLogger(GithubReleaseService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    // Using plain HttpURLConnection so we don't pull a Ktor client
    // engine into the backend's runtime classpath (the project only
    // ships server-side Ktor bits; adding a client engine would bloat
    // the JAR for the rare case of a backend → backend HTTP call).

    @Serializable
    data class ReleaseAsset(
        val name: String,
        val apiUrl: String,           // https://api.github.com/.../releases/assets/<id>
        val sizeBytes: Long,
    )

    @Serializable
    data class ReleaseSnapshot(
        val tagName: String,          // "v1.11.0"
        val versionName: String,      // "1.11.0"  (tag without leading 'v')
        val versionCode: Int,         // parsed from body, or inferred from tag suffix
        val releaseNotes: String,
        val releaseNotesAr: String?,  // optional Arabic block (parsed below)
        val htmlUrl: String,          // browser link to the release page
        val publishedAt: Long,        // epoch ms
        val assets: List<ReleaseAsset>,
    )

    // Cache state. Protected by [mutex] so concurrent /check-update
    // requests don't all race to refresh at the same moment.
    @Volatile
    private var cache: ReleaseSnapshot? = null
    @Volatile
    private var cachedAt: Long = 0L
    private val mutex = Mutex()

    init {
        scope.launch { periodicPoll() }
    }

    /** Latest known release. Triggers a refresh if cache is stale or empty. */
    suspend fun latestRelease(): ReleaseSnapshot? {
        val now = System.currentTimeMillis()
        val current = cache
        if (current != null && (now - cachedAt) < CACHE_TTL_MS) return current
        return refresh()
    }

    /**
     * Force a fresh fetch from GitHub and update the cache. Called by
     * the `/release-published` push endpoint after CI uploads assets,
     * by the periodic poller, and by [latestRelease] on cache miss.
     */
    suspend fun refresh(): ReleaseSnapshot? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL("$API_BASE/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() }
                    log.warn("GitHub releases fetch failed: HTTP $code for $repo — ${err?.take(200)}")
                    return@withContext cache
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parse(body)
                cache = parsed
                cachedAt = System.currentTimeMillis()
                log.info("Refreshed GitHub release cache: ${parsed.tagName} (code=${parsed.versionCode}, ${parsed.assets.size} assets)")
                parsed
            } catch (e: Throwable) {
                log.warn("GitHub releases fetch errored: ${e.message}")
                cache
            }
        }
    }

    /**
     * Stream an asset's bytes through the caller's [OutputStream].
     * GitHub's private-repo asset URLs require:
     *   1. GET /releases/assets/<id> with Authorization + Accept: application/octet-stream
     *   2. GitHub responds with a 302 redirect to a signed CDN URL —
     *      the CDN URL has the auth baked into a query param so we
     *      MUST strip our Authorization header on the second request
     *      to avoid a 400 ("two-credential-paths" rejection).
     *
     * Returns the byte count + content type, or null on failure.
     */
    suspend fun downloadAsset(assetApiUrl: String): AssetResponse? = withContext(Dispatchers.IO) {
        try {
            // Step 1: ask GitHub for the redirect. We need to disable
            // auto-redirect so we can strip our Authorization header
            // on the second hop — GitHub's signed CDN URL embeds its
            // own auth in a query parameter and rejects requests that
            // ALSO carry a bearer token ("two-credential-paths" error).
            val first = (URL(assetApiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val firstCode = first.responseCode
            if (firstCode !in 300..399) {
                log.warn("Asset metadata fetch returned HTTP $firstCode (expected 302 redirect)")
                return@withContext null
            }
            val location = first.getHeaderField("Location") ?: return@withContext null
            first.disconnect()

            // Step 2: download the actual bytes from the CDN — NO
            // Authorization header.
            val cdn = (URL(location).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/octet-stream")
            }
            val cdnCode = cdn.responseCode
            if (cdnCode !in 200..299) {
                log.warn("Asset CDN fetch failed: HTTP $cdnCode")
                cdn.disconnect()
                return@withContext null
            }
            val contentType = cdn.contentType ?: "application/octet-stream"
            val buffer = ByteArrayOutputStream()
            cdn.inputStream.use { it.copyTo(buffer) }
            cdn.disconnect()
            AssetResponse(bytes = buffer.toByteArray(), contentType = contentType)
        } catch (e: Throwable) {
            log.warn("downloadAsset failed: ${e.message}")
            null
        }
    }

    data class AssetResponse(val bytes: ByteArray, val contentType: String)

    // ── Internal ──────────────────────────────────────────────────────

    private fun parse(rawJson: String): ReleaseSnapshot {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("missing tag_name")
        val versionName = tagName.removePrefix("v")
        val body = root["body"]?.jsonPrimitive?.contentOrNull ?: ""
        val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: ""

        // versionCode discovery — first try a "build N" / "VERSION_CODE=N" / "(N)" pattern
        // in the body; fall back to the patch component of the tag.
        val versionCodeFromBody = Regex("""(?i)(?:build|version[_ ]?code)[^\d]*?(\d+)""")
            .find(body)?.groupValues?.get(1)?.toIntOrNull()
        val versionCodeFromTag = Regex("""\((\d+)\)""").find(tagName)?.groupValues?.get(1)?.toIntOrNull()
        val versionCode = versionCodeFromBody ?: versionCodeFromTag ?: tagToVersionCode(versionName)

        // Arabic block — caller can extract a "## العربية" / "Arabic notes:" section if present
        val arBlockMatch = Regex("""(?ms)(?:^|\n)\s*(?:#+\s*)?(?:العربية|Arabic[^:\n]*[:])\s*\n(.+?)(?=\n\s*(?:#+|\Z))""")
            .find(body)
        val releaseNotesAr = arBlockMatch?.groupValues?.get(1)?.trim()

        val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull?.let {
            // ISO-8601 → epoch ms
            try {
                java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()
            } catch (_: Throwable) { 0L }
        } ?: 0L

        val assets = root["assets"]?.jsonArray?.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            ReleaseAsset(name = name, apiUrl = url, sizeBytes = size)
        }.orEmpty()

        return ReleaseSnapshot(
            tagName = tagName,
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = body,
            releaseNotesAr = releaseNotesAr,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            assets = assets,
        )
    }

    /**
     * Fallback when no explicit build number is in the body: turn
     * "1.11.0" into 11100 (major*10000 + minor*100 + patch). Stable
     * monotonic mapping for our typical SemVer triples.
     */
    private fun tagToVersionCode(versionName: String): Int {
        val parts = versionName.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }

    private suspend fun periodicPoll() {
        // First poll fires immediately on backend boot so the cache is
        // warm before the first user request. After that we run the
        // long [POLL_INTERVAL_MS] daily fallback.
        delay(15_000L)
        refresh()
        while (true) {
            delay(POLL_INTERVAL_MS)
            log.info("Daily release poll fired")
            refresh()
        }
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
        /** Inline-refresh threshold for /check-update PULL path (5 min). */
        private const val CACHE_TTL_MS = 5L * 60_000L
        /** Daily fallback poll. */
        private const val POLL_INTERVAL_MS = 24L * 60L * 60_000L
    }
}
