package net.marllex.cafeemanger.core.network.datasource

import net.marllex.cafeemanger.core.network.CafeeMangerApi
import javax.inject.Inject

class WorkerNetworkDataSource @Inject constructor(
    private val api: CafeeMangerApi
) {
    suspend fun getWorkerQrCode(workerId: String): ByteArray {
        val response = api.getWorkerQrCode(workerId)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!.byteStream().readBytes()
        } else {
            throw Exception("Failed to download QR code: ${response.code()}")
        }
    }
}
