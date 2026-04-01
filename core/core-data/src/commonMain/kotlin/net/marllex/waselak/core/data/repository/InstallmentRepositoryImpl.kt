package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.InstallmentAnalytics
import net.marllex.waselak.core.model.InstallmentPayment
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateInstallmentPlanRequest
import net.marllex.waselak.core.network.dto.RecordInstallmentPaymentRequest
import net.marllex.waselak.core.network.dto.UpdateInstallmentStatusRequest
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.common.logging.AppLogger

class InstallmentRepositoryImpl(
    private val apiClient: WaselakApiClient,
) : InstallmentRepository {

    private companion object { private const val TAG = "InstallmentRepo" }

    override suspend fun createPlan(
        customerId: String,
        totalAmount: Double,
        numInstallments: Int,
        orderId: String?,
        downPayment: Double,
        lateFeePercent: Double,
        startDate: Long?,
    ): Result<InstallmentPlan> = runCatching {
        AppLogger.d(TAG, "createPlan: customer=$customerId, total=$totalAmount, months=$numInstallments")
        apiClient.createInstallmentPlan(
            CreateInstallmentPlanRequest(
                customerId = customerId,
                orderId = orderId,
                totalAmount = totalAmount,
                downPayment = downPayment,
                numInstallments = numInstallments,
                lateFeePercent = lateFeePercent,
                startDate = startDate,
            )
        ).toDomain()
    }

    override suspend fun getPlans(status: String?): Result<List<InstallmentPlan>> = runCatching {
        AppLogger.d(TAG, "getPlans: status=$status")
        apiClient.getInstallmentPlans(status).map { it.toDomain() }
    }

    override suspend fun getPlan(planId: String): Result<InstallmentPlan> = runCatching {
        AppLogger.d(TAG, "getPlan: id=$planId")
        apiClient.getInstallmentPlan(planId).toDomain()
    }

    override suspend fun getCustomerPlans(customerId: String): Result<List<InstallmentPlan>> = runCatching {
        AppLogger.d(TAG, "getCustomerPlans: customer=$customerId")
        apiClient.getCustomerInstallments(customerId).map { it.toDomain() }
    }

    override suspend fun recordPayment(planId: String, amount: Double, note: String?): Result<InstallmentPayment> = runCatching {
        AppLogger.d(TAG, "recordPayment: plan=$planId, amount=$amount")
        apiClient.recordInstallmentPayment(planId, RecordInstallmentPaymentRequest(amount, note)).toDomain()
    }

    override suspend fun updatePlanStatus(planId: String, status: String): Result<InstallmentPlan> = runCatching {
        AppLogger.d(TAG, "updatePlanStatus: plan=$planId, status=$status")
        apiClient.updateInstallmentStatus(planId, UpdateInstallmentStatusRequest(status)).toDomain()
    }

    override suspend fun applyLateFee(planId: String): Result<InstallmentPlan> = runCatching {
        AppLogger.d(TAG, "applyLateFee: plan=$planId")
        apiClient.applyInstallmentLateFee(planId).toDomain()
    }

    override suspend fun getAnalytics(from: Long?, to: Long?): Result<InstallmentAnalytics> = runCatching {
        AppLogger.d(TAG, "getAnalytics: from=$from, to=$to")
        apiClient.getInstallmentAnalytics(from, to).toDomain()
    }
}
