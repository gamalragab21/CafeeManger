package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Drug Interaction domain models.
 * Tracks known interactions between medicines for pharmacy vendors.
 */

@Serializable
data class DrugInteraction(
    val id: String,
    val vendorId: String,
    val itemIdA: String,
    val itemNameA: String? = null,
    val itemIdB: String,
    val itemNameB: String? = null,
    val severity: String = "MODERATE",  // MILD, MODERATE, SEVERE, CONTRAINDICATED
    val description: String,
    val descriptionAr: String? = null,
    val recommendation: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
) {
    val isMild: Boolean get() = severity == "MILD"
    val isModerate: Boolean get() = severity == "MODERATE"
    val isSevere: Boolean get() = severity == "SEVERE"
    val isContraindicated: Boolean get() = severity == "CONTRAINDICATED"
    val isDangerous: Boolean get() = severity in listOf("SEVERE", "CONTRAINDICATED")
}

@Serializable
data class InteractionCheckResult(
    val hasInteractions: Boolean,
    val interactions: List<DrugInteraction> = emptyList(),
) {
    val hasDangerousInteractions: Boolean get() = interactions.any { it.isDangerous }
    val dangerousCount: Int get() = interactions.count { it.isDangerous }
}

enum class DrugInteractionSeverity {
    MILD, MODERATE, SEVERE, CONTRAINDICATED;

    companion object {
        fun fromString(value: String): DrugInteractionSeverity =
            entries.firstOrNull { it.name == value.uppercase() } ?: MODERATE
    }
}
