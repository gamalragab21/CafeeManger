package net.marllex.waselak.core.data.offline

import io.ktor.client.HttpClient
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

class ConnectivityChecker(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        scope.launch { monitorLoop() }
    }

    private suspend fun monitorLoop() {
        while (true) {
            _isOnline.value = pingServer()
            delay(PING_INTERVAL_MS)
        }
    }

    suspend fun checkNow(): Boolean {
        val result = pingServer()
        _isOnline.value = result
        return result
    }

    private suspend fun pingServer(): Boolean = try {
        val response = client.get("${baseUrl}health")
        response.status.isSuccess()
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val PING_INTERVAL_MS = 15_000L
    }
}
