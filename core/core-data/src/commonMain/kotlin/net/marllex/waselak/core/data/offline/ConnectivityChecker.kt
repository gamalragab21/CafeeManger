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
 * Backend-reachable connectivity check.
 *
 * Pings `${baseUrl}health` periodically and exposes whether the backend
 * is reachable as a [StateFlow]. This is the **single source of truth**
 * for "is the app online" decisions — it directly measures what we
 * actually care about (can we talk to our backend?) instead of the
 * OS-level "do I have a network interface?" signal, which is noisy on
 * captive-portal Wi-Fi, networks that block Google DNS probes, or
 * Wi-Fi that drops to LAN-only mid-session.
 *
 * **Hysteresis** — to avoid flickering the "offline" dialog on a single
 * slow request, we require [FAILURES_BEFORE_OFFLINE] consecutive
 * failures before flipping to offline. Recovery is instant: the first
 * successful ping flips back to online.
 *
 * **Per-request timeout** — 5 s. Slower than that and we treat it as
 * offline. Avoids hanging the StateFlow on a single dead TCP connect.
 */
class ConnectivityChecker(
    private val baseUrl: String,
    private val client: HttpClient,
) {
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
            updateState(pingServer())
            delay(PING_INTERVAL_MS)
        }
    }

    /**
     * Force a probe right now (e.g. when the user taps "Retry" on the
     * offline dialog). Returns the latest reachability state.
     */
    suspend fun checkNow(): Boolean {
        updateState(pingServer())
        return _isOnline.value
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
        val response = client.get("${baseUrl}health") {
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
    }
}
