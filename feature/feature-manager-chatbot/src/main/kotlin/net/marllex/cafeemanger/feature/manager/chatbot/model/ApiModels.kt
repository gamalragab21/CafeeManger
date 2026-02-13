package net.marllex.cafeemanger.feature.manager.chatbot.model

/**
 * Request model for sending a query to the chatbot API
 */
data class QueryRequest(
    val query: String,
    val context: ConversationContextDto? = null,
    val language: String = "en"
)

/**
 * Response model from the chatbot API
 */
data class QueryResponse(
    val answer: String,
    val data: ResponseDataDto? = null,
    val visualFormat: String = "text",
    val suggestions: List<String> = emptyList(),
    val context: ConversationContextDto,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DTO for conversation context
 */
data class ConversationContextDto(
    val conversationId: String,
    val lastIntent: String? = null,
    val lastEntities: Map<String, String> = emptyMap(),
    val lastTimestamp: Long = System.currentTimeMillis()
)

/**
 * DTO for response data
 */
data class ResponseDataDto(
    val type: String,
    val values: Map<String, String>
)

/**
 * Response model for quick suggestions
 */
data class SuggestionsResponse(
    val suggestions: List<QuickSuggestionDto>
)

/**
 * DTO for quick suggestion
 */
data class QuickSuggestionDto(
    val text: String,
    val category: String,
    val icon: String
)
