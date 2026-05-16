package net.marllex.waselak.core.data.offline

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connectivity probe with two signals:
 *
 *   1. **Backend reachable** — `GET ${baseUrl}health` (always tried).
 *   2. **Public-internet reachable** — `GET` against any of a small set
 *      of well-known public endpoints (only tried when the backend
 *      probe just failed, so we don't waste bandwidth on the happy
 *      path).
 *
 * A user is considered "online" if **either** probe succeeds. That
 * matches what the offline dialog actually needs to know: "does the
 * user have a path to the internet (so they can fix the backend
 * outage) or are they really offline (so we should let them work
 * offline and queue writes)?" Without the public-net check we'd flash
 * the offline dialog any time our backend goes down for maintenance,
 * even though the user has a perfectly good Wi-Fi connection.
 *
 * **Hysteresis** — to avoid flickering the "offline" dialog on a single
 * slow request, we require [FAILURES_BEFORE_OFFLINE] consecutive
 * failures (on BOTH probes) before flipping to offline. Recovery is
 * instant: the first successful ping flips back to online.
 *
 * **Per-request timeout** — 5 s. Slower than that and we treat it as
 * offline. Avoids hanging the StateFlow on a single dead TCP connect.
 */
class ConnectivityChecker(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    /** Set when the backend is up. */
    private val _backendReachable = MutableStateFlow(true)
    val backendReachable: StateFlow<Boolean> = _backendReachable.asStateFlow()

    /** Set when ANY public host responds — i.e. the user has internet. */
    private val _publicNetReachable = MutableStateFlow(true)
    val publicNetReachable: StateFlow<Boolean> = _publicNetReachable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Bias toward "online" at startup — the dialog should never flash
    // before we've actually had a chance to probe the backend.
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Running count of consecutive failed probes; reset to 0 on success. */
    private var consecutiveFailures = 0

    init {
        scope.launch { monitorLoop() }
    }

    private suspend fun monitorLoop() {
        // Give the app a few seconds to settle before the first probe,
        // so we don't false-alarm during cold-start network init.
        delay(WARMUP_DELAY_MS)
        while (true) {
            updateState(probeOnce())
            delay(PING_INTERVAL_MS)
        }
    }

    /**
     * Force a probe right now (e.g. when the user taps "Retry" on the
     * offline dialog). Returns the latest reachability state.
     */
    suspend fun checkNow(): Boolean {
        updateState(probeOnce())
        return _isOnline.value
    }

    /**
     * One full probe cycle. Tries the backend first; only if that fails
     * do we fall back to a public-internet probe. Returns true if EITHER
     * succeeded — the user is considered online so long as they have
     * SOMETHING to talk to (backend down ≠ user offline).
     */
    private suspend fun probeOnce(): Boolean {
        val backendOk = pingServer()
        _backendReachable.value = backendOk
        if (backendOk) {
            // Happy path — backend works, public net is implicitly fine.
            _publicNetReachable.value = true
            return true
        }
        // Backend down. Differentiate "user offline" from "backend
        // down" by probing a public host. If ANY public host answers,
        // the user is online and we should NOT show the dreaded
        // offline-mode dialog — the user can still navigate to a
        // support page, retry, etc.
        val publicOk = pingPublicInternet()
        _publicNetReachable.value = publicOk
        return publicOk
    }

    private fun updateState(reachable: Boolean) {
        if (reachable) {
            // Successful probe — recover immediately.
            consecutiveFailures = 0
            _isOnline.value = true
        } else {
            consecutiveFailures += 1
            if (consecutiveFailures >= FAILURES_BEFORE_OFFLINE) {
                _isOnline.value = false
            }
            // Otherwise keep the previous value — we're in the
            // "grace period" between a single failure and committing
            // to the offline state.
        }
    }

    private suspend fun pingServer(): Boolean = try {
        // Defensive `/` handling — older env files had a trailing slash on
        // BASE_URL (http://host:8081/) so "${baseUrl}health" produced a
        // correct URL by accident. The new HTTPS env files don't have the
        // trailing slash, which would produce "...waselak.onlinehealth".
        // Normalise both forms here.
        val healthUrl = baseUrl.trimEnd('/') + "/health"
        val response = client.get(healthUrl) {
            timeout {
                requestTimeoutMillis = PROBE_TIMEOUT_MS
                connectTimeoutMillis = PROBE_TIMEOUT_MS
                socketTimeoutMillis = PROBE_TIMEOUT_MS
            }
        }
        response.status.isSuccess()
    } catch (_: Exception) {
        false
    }

    /**
     * Hit any of [PUBLIC_PROBE_URLS] — first success wins. We use
     * lightweight "are you alive?" endpoints (204 No Content where
     * possible) so the byte cost stays tiny. Listed in approximate
     * order of reliability + reachability worldwide.
     */
    private suspend fun pingPublicInternet(): Boolean {
        for (url in PUBLIC_PROBE_URLS) {
            try {
                val response = client.get(url) {
                    timeout {
                        requestTimeoutMillis = PROBE_TIMEOUT_MS
                        connectTimeoutMillis = PROBE_TIMEOUT_MS
                        socketTimeoutMillis = PROBE_TIMEOUT_MS
                    }
                }
                // Accept any 2xx / 3xx — many of these endpoints
                // deliberately return 204 or 301 to keep payload tiny.
                if (response.status.value in 200..399) return true
            } catch (_: Exception) {
                // Try the next one.
            }
        }
        return false
    }

    companion object {
        /** How often (ms) the monitor loop probes the backend. */
        private const val PING_INTERVAL_MS = 15_000L

        /** Per-probe HTTP/socket timeout. */
        private const val PROBE_TIMEOUT_MS = 5_000L

        /** Delay before the first probe runs after construction. */
        private const val WARMUP_DELAY_MS = 2_000L

        /**
         * Number of consecutive probe failures required before
         * declaring the backend offline. With a 15-s probe interval
         * that's a ~30-s "are you sure?" window, which has proven to
         * filter out transient network glitches (e.g. brief
         * Wi-Fi → cellular handoffs) without making the offline
         * dialog feel sluggish on real outages.
         */
        private const val FAILURES_BEFORE_OFFLINE = 2

        /**
         * Endpoints we hit to verify the user has *any* internet path,
         * in priority order. We deliberately pick "captive-portal-
         * detection" URLs from Google/Apple/Microsoft because:
         *   1. They're hosted on global CDNs, so latency is good
         *      almost everywhere.
         *   2. They return tiny responses (often 204 No Content), so
         *      the bandwidth cost of being conservative is negligible.
         *   3. They're well-known enough that captive-portal Wi-Fi
         *      hotspots, ISPs, and corporate proxies all let them
         *      through — they're the same probes the operating system
         *      uses for its own connectivity indicator.
         */
        private val PUBLIC_PROBE_URLS = listOf(
            "https://www.google.com/generate_204",
            "https://www.gstatic.com/generate_204",
            "https://captive.apple.com/hotspot-detect.html",
            "https://www.cloudflare.com/cdn-cgi/trace",
        )
    }
}
