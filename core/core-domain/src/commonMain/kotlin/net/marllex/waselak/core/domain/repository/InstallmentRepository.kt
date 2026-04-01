package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.InstallmentAnalytics
import net.marllex.waselak.core.model.InstallmentPayment
import net.marllex.waselak.core.model.InstallmentPlan

interface InstallmentRepository {
    suspend fun createPlan(
        customerId: String,
        totalAmount: Double,
        numInstallments: Int,
        orderId: String? = null,
        downPayment: Double = 0.0,
        lateFeePercent: Double = 0.0,
        startDate: Long? = null,
    ): Result<InstallmentPlan>

    suspend fun getPlans(status: String? = null): Result<List<InstallmentPlan>>
    suspend fun getPlan(planId: String): Result<InstallmentPlan>
    suspend fun getCustomerPlans(customerId: String): Result<List<InstallmentPlan>>

    suspend fun recordPayment(planId: String, amount: Double, note: String? = null): Result<InstallmentPayment>
    suspend fun updatePlanStatus(planId: String, status: String): Result<InstallmentPlan>
    suspend fun applyLateFee(planId: String): Result<InstallmentPlan>

    suspend fun getAnalytics(from: Long? = null, to: Long? = null): Result<InstallmentAnalytics>
}
