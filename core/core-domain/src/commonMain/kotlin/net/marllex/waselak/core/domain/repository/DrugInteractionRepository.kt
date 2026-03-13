package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.DrugInteraction
import net.marllex.waselak.core.model.InteractionCheckResult
import net.marllex.waselak.core.network.dto.CreateDrugInteractionRequest

interface DrugInteractionRepository {
    suspend fun getInteractions(): Result<List<DrugInteraction>>
    suspend fun createInteraction(request: CreateDrugInteractionRequest): Result<DrugInteraction>
    suspend fun checkInteractions(itemIds: List<String>): Result<InteractionCheckResult>
    suspend fun deleteInteraction(id: String): Result<Unit>
    suspend fun toggleInteraction(id: String): Result<DrugInteraction>
}
