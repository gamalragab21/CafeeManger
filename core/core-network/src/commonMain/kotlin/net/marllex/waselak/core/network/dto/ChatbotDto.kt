package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request DTOs ────────────────────────────────────────────────

@Serializable
data class ChatbotQueryRequest(
    @SerialName("query") val query: String,
    @SerialName("context") val context: ChatbotContextDto? = null,
    @SerialName("language") val language: String = "en"
)

@Serializable
data class ChatbotContextDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("last_intent") val lastIntent: String? = null,
    @SerialName("last_entities") val lastEntities: Map<String, String> = emptyMap(),
    @SerialName("last_timestamp") val lastTimestamp: Long = System.currentTimeMillis()
)

// ─── Response DTOs ───────────────────────────────────────────────

@Serializable
data class ChatbotQueryResponse(
    @SerialName("answer") val answer: String,
    @SerialName("data") val data: ChatbotResponseDataDto? = null,
    @SerialName("visual_format") val visualFormat: String = "text",
    @SerialName("suggestions") val suggestions: List<String> = emptyList(),
    @SerialName("context") val context: ChatbotContextDto,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatbotResponseDataDto(
    @SerialName("type") val type: String,
    @SerialName("values") val values: Map<String, String>
)

@Serializable
data class ChatbotSuggestionsResponse(
    @SerialName("suggestions") val suggestions: List<ChatbotSuggestionDto>
)

@Serializable
data class ChatbotSuggestionDto(
    @SerialName("text") val text: String,
    @SerialName("category") val category: String,
    @SerialName("icon") val icon: String
)
