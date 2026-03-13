package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.PrescriptionRepository
import net.marllex.waselak.core.model.Prescription
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreatePrescriptionRequest
import net.marllex.waselak.core.network.dto.DispensePrescriptionRequest
import net.marllex.waselak.core.network.mapper.toDomain

class PrescriptionRepositoryImpl(
    private val api: WaselakApiClient,
) : PrescriptionRepository {

    override suspend fun getPrescriptions(status: String?, customerId: String?, limit: Int, offset: Int): Result<List<Prescription>> = runCatching {
        api.getPrescriptions(status, customerId, limit, offset).map { it.toDomain() }
    }

    override suspend fun getPrescription(id: String): Result<Prescription> = runCatching {
        api.getPrescription(id).toDomain()
    }

    override suspend fun createPrescription(request: CreatePrescriptionRequest): Result<Prescription> = runCatching {
        api.createPrescription(request).toDomain()
    }

    override suspend fun dispensePrescription(id: String, request: DispensePrescriptionRequest): Result<Prescription> = runCatching {
        api.dispensePrescription(id, request).toDomain()
    }

    override suspend fun cancelPrescription(id: String): Result<Prescription> = runCatching {
        api.cancelPrescription(id).toDomain()
    }
}
