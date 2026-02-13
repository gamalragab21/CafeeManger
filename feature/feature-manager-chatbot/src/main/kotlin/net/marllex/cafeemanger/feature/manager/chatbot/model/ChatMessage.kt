package net.marllex.cafeemanger.feature.manager.chatbot.model

import java.util.UUID

/**
 * Sealed class representing different types of chat messages
 */
sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long
    
    /**
     * Message sent by the user
     */
    data class User(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String
    ) : ChatMessage()
    
    /**
     * Message sent by the bot
     */
    data class Bot(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val data: ResponseData? = null,
        val suggestions: List<String> = emptyList(),
        val visualFormat: VisualFormat = VisualFormat.TEXT
    ) : ChatMessage()
    
    /**
     * Error message
     */
    data class Error(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val message: String
    ) : ChatMessage()
    
    /**
     * Loading indicator
     */
    data class Loading(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis()
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
    val lastTimestamp: Long = System.currentTimeMillis()
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
