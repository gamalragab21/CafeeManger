package net.marllex.waselak.core.network.datasource

import io.ktor.client.statement.readBytes
import net.marllex.waselak.core.network.WaselakApiClient

class WorkerNetworkDataSource(
    private val api: WaselakApiClient
) {
    suspend fun getWorkerQrCode(workerId: String): ByteArray {
        val response = api.getWorkerQrCode(workerId)
        return response.readBytes()
    }
}
