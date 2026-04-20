package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond

/**
 * Small weak-ETag helper used by list endpoints that the Android app syncs on a regular
 * tick (items, categories). The goal is to let the client send `If-None-Match: <etag>`
 * and receive `304 Not Modified` (no body) when the data hasn't changed, so we skip
 * serialization + network transfer on the hot path.
 *
 * We derive the ETag from `(maxUpdatedAtEpochMs, rowCount)` of the filtered query:
 *  - A row insert/update bumps `maxUpdatedAt`.
 *  - A pure delete doesn't touch `maxUpdatedAt`, but it does change `rowCount`.
 *  - A delete+insert within the same tick changes `rowCount` (count delta), so still ok.
 *
 * Mark as weak (`W/"…"`) because this signals semantic equivalence, not byte-equality
 * of the representation — serialization order could in principle differ.
 */
internal object ETagSupport {

    /** Format a weak ETag from (latestUpdatedAt epoch-ms, rowCount). */
    fun weakEtag(latestUpdatedAtEpochMs: Long, rowCount: Long): String =
        """W/"v-$latestUpdatedAtEpochMs-$rowCount""""

    /**
     * If the client's `If-None-Match` matches `currentEtag`, respond `304 Not Modified`
     * (still sending the ETag header so proxies can cache) and return `true`. Callers
     * should short-circuit further work in that case.
     *
     * Otherwise set the `ETag` response header and return `false` — the caller should
     * then produce and respond with the full body as usual.
     */
    suspend fun respondNotModifiedIfMatches(call: ApplicationCall, currentEtag: String): Boolean {
        val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
        if (ifNoneMatch != null && ifNoneMatchContains(ifNoneMatch, currentEtag)) {
            call.response.header(HttpHeaders.ETag, currentEtag)
            call.respond(HttpStatusCode.NotModified)
            return true
        }
        call.response.header(HttpHeaders.ETag, currentEtag)
        return false
    }

    /**
     * `If-None-Match` can be a list: `W/"a", W/"b", "c"`. Split on comma and compare
     * trimmed values. Also treat `*` as a universal match, per RFC 9110 §13.1.2.
     */
    private fun ifNoneMatchContains(header: String, etag: String): Boolean {
        if (header.trim() == "*") return true
        return header.split(',').any { it.trim() == etag }
    }
}
