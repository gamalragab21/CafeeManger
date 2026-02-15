package net.marllex.waselak.feature.manager.chatbot.model

import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sealed class representing different types of chat messages
 */
sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    /**
     * Message sent by the user
     */
    data class User @OptIn(ExperimentalUuidApi::class) constructor(
        override val id: String = Uuid.random().toString(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        val text: String
    ) : ChatMessage()

    /**
     * Message sent by the bot
     */
    data class Bot @OptIn(ExperimentalUuidApi::class) constructor(
        override val id: String = Uuid.random().toString(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        val text: String,
        val data: ResponseData? = null,
        val suggestions: List<String> = emptyList(),
        val visualFormat: VisualFormat = VisualFormat.TEXT
    ) : ChatMessage()

    /**
     * Error message
     */
    data class Error @OptIn(ExperimentalUuidApi::class) constructor(
        override val id: String = Uuid.random().toString(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        val message: String
    ) : ChatMessage()

    /**
     * Loading indicator
     */
    data class Loading @OptIn(ExperimentalUuidApi::class) constructor(
        override val id: String = Uuid.random().toString(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : ChatMessage()
}

/**
 * Response data structure containing structured information
 */
data class ResponseData(
    val type: ResponseType,
    val values: Map<String, Any>
)

/**
 * Types of responses the bot can provide
 */
enum class ResponseType {
    SALES_SUMMARY,
    STAFF_LIST,
    ANALYTICS_CHART,
    COMPARISON,
    LIST
}

/**
 * Visual format for displaying data
 */
enum class VisualFormat {
    TEXT,
    TABLE,
    LIST,
    COMPARISON
}

/**
 * Conversation context for maintaining state
 */
data class ConversationContext(
    val conversationId: String,
    val lastIntent: String? = null,
    val lastEntities: Map<String, String> = emptyMap(),
    val lastTimestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Quick suggestion for common queries
 */
data class QuickSuggestion(
    val text: String,
    val category: SuggestionCategory,
    val icon: String
)

/**
 * Categories for quick suggestions
 */
enum class SuggestionCategory {
    SALES,
    STAFF,
    ANALYTICS,
    ORDERS
}

/**
 * Conversation history for persistence
 */
data class ConversationHistory(
    val messages: List<ChatMessage> = emptyList(),
    val context: ConversationContext? = null
)
