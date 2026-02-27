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

actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                _isOnline.value = checkConnectivity()
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    private fun checkConnectivity(): Boolean {
        // Use TCP socket to DNS port — InetAddress.isReachable() uses ICMP
        // which requires root privileges on macOS and often returns false.
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
