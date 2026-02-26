package net.marllex.waselak.core.network.connectivity

import kotlinx.coroutines.flow.StateFlow

expect class NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}
