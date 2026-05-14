package net.marllex.waselak.core.network.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Desktop OS-level connectivity probe.
 *
 * Java doesn't expose Apple's `nw_path_monitor` or Android's
 * `ConnectivityManager.NetworkCallback`, so we poll with a TCP connect
 * to a small list of well-known public hosts.
 *
 * Strategy:
 *   1. Try each [PROBE_HOSTS] entry until one succeeds — if any single
 *      one connects, the device has SOMETHING reachable and is "online".
 *   2. Hysteresis: require [FAILURES_BEFORE_OFFLINE] consecutive
 *      total-failure cycles before flipping to offline, so a transient
 *      DNS hiccup or a packet-drop doesn't trip the dialog.
 *   3. Recovery is instant — the first probe that connects flips back.
 *
 * The single-host check (8.8.8.8 only) the previous version used was
 * fragile on corporate / hotel Wi-Fi that blocks Google DNS.
 *
 * Note: this is the platform-OS signal only — the actual app uses
 * [OfflineModeManager.isOnline] which also unions with the backend
 * health probe.
 */
actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var consecutiveFailures = 0

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Warm up before first probe so we don't false-alarm at
            // cold-start before networking has settled.
            delay(WARMUP_DELAY_MS)
            while (true) {
                update(anyProbeSucceeds())
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    private fun update(reachable: Boolean) {
        if (reachable) {
            consecutiveFailures = 0
            _isOnline.value = true
        } else {
            consecutiveFailures += 1
            if (consecutiveFailures >= FAILURES_BEFORE_OFFLINE) {
                _isOnline.value = false
            }
        }
    }

    /**
     * Try each candidate in [PROBE_HOSTS] sequentially; return true on
     * the first successful TCP connect. We stop as soon as one works —
     * no need to verify every host, the goal is "is *anything*
     * reachable on the open internet?".
     */
    private fun anyProbeSucceeds(): Boolean {
        for ((host, port) in PROBE_HOSTS) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                    return true
                }
            } catch (_: Exception) {
                // Try the next host in the list.
            }
        }
        return false
    }

    companion object {
        /**
         * Hosts probed for OS-level connectivity. Two DNS resolvers
         * from different operators (Google + Cloudflare) plus
         * Cloudflare's "captive portal" check at 1.1.1.1:80 — picked
         * so a network that blocks port 53 still has a chance to
         * register as online via the HTTP probe.
         */
        private val PROBE_HOSTS = listOf(
            "8.8.8.8" to 53,    // Google DNS
            "1.1.1.1" to 53,    // Cloudflare DNS
            "1.1.1.1" to 80,    // Cloudflare HTTP (fallback for DNS-blocked nets)
        )

        private const val PROBE_TIMEOUT_MS = 5_000
        private const val PROBE_INTERVAL_MS = 15_000L
        private const val WARMUP_DELAY_MS = 2_000L
        private const val FAILURES_BEFORE_OFFLINE = 2
    }
}
