package net.marllex.waselak.feature.manager.chatbot.data

import net.marllex.waselak.feature.manager.chatbot.model.ConversationContext
import net.marllex.waselak.feature.manager.chatbot.model.ConversationHistory
import net.marllex.waselak.feature.manager.chatbot.model.QuickSuggestion
import net.marllex.waselak.feature.manager.chatbot.model.ChatMessage
import net.marllex.waselak.core.network.dto.ChatbotQueryResponse

/**
 * Repository interface for chatbot operations
 */
interface ChatbotRepository {
    /**
     * Send a query to the chatbot API
     */
    suspend fun sendQuery(
        query: String,
        context: ConversationContext?,
        language: String = "en"
    ): Result<ChatbotQueryResponse>
    
    /**
     * Get quick suggestions from the API
     */
    suspend fun getQuickSuggestions(): Result<List<QuickSuggestion>>
    
    /**
     * Save conversation history locally
     */
    suspend fun saveConversation(
        messages: List<ChatMessage>,
        context: ConversationContext?
    ): Result<Unit>
    
    /**
     * Load conversation history from local storage
     */
    suspend fun loadConversation(): Result<ConversationHistory>
    
    /**
     * Clear conversation history
     */
    suspend fun clearConversation(): Result<Unit>
}
