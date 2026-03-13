package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.DrugInteractionRepository
import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.model.InteractionCheckResult
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CheckInteractionsRequest
import net.marllex.waselak.core.network.dto.CreateDrugInteractionRequest
import net.marllex.waselak.core.network.mapper.toDomain

class DrugInteractionRepositoryImpl(
    private val api: WaselakApiClient,
) : DrugInteractionRepository {

    override suspend fun getInteractions(): Result<List<DrugInteraction>> = runCatching {
        api.getDrugInteractions().map { it.toDomain() }
    }

    override suspend fun createInteraction(request: CreateDrugInteractionRequest): Result<DrugInteraction> = runCatching {
        api.createDrugInteraction(request).toDomain()
    }

    override suspend fun checkInteractions(itemIds: List<String>): Result<InteractionCheckResult> = runCatching {
        api.checkDrugInteractions(CheckInteractionsRequest(itemIds)).toDomain()
    }

    override suspend fun deleteInteraction(id: String): Result<Unit> = runCatching {
        api.deleteDrugInteraction(id)
    }

    override suspend fun toggleInteraction(id: String): Result<DrugInteraction> = runCatching {
        api.toggleDrugInteraction(id).toDomain()
    }
}
