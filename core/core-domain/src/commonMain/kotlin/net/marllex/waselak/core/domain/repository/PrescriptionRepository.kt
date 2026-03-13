package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.Prescription
import net.marllex.waselak.core.network.dto.CreatePrescriptionRequest
import net.marllex.waselak.core.network.dto.DispensePrescriptionRequest

interface PrescriptionRepository {
    suspend fun getPrescriptions(status: String? = null, customerId: String? = null, limit: Int = 50, offset: Int = 0): Result<List<Prescription>>
    suspend fun getPrescription(id: String): Result<Prescription>
    suspend fun createPrescription(request: CreatePrescriptionRequest): Result<Prescription>
    suspend fun dispensePrescription(id: String, request: DispensePrescriptionRequest): Result<Prescription>
    suspend fun cancelPrescription(id: String): Result<Prescription>
}
