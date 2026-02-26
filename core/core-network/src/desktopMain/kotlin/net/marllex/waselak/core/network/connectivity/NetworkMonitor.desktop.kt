package net.marllex.waselak.core.network.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                _isOnline.value = try {
                    InetAddress.getByName("8.8.8.8").isReachable(3000)
                } catch (_: Exception) {
                    false
                }
                delay(10_000) // Check every 10 seconds
            }
        }
    }
}
