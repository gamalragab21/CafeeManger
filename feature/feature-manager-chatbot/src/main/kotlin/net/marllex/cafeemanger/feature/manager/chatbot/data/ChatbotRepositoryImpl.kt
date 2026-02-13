package net.marllex.cafeemanger.feature.manager.chatbot.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.ChatbotContextDto
import net.marllex.cafeemanger.core.network.dto.ChatbotQueryRequest
import net.marllex.cafeemanger.core.network.dto.ChatbotQueryResponse
import net.marllex.cafeemanger.feature.manager.chatbot.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatbotRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val sharedPreferences: SharedPreferences
) : ChatbotRepository {
    
    companion object {
        private const val KEY_CONVERSATION_HISTORY = "chatbot_conversation_history"
        private const val KEY_CONVERSATION_CONTEXT = "chatbot_conversation_context"
        private const val CACHE_KEY_SUGGESTIONS = "chatbot_suggestions_cache"
        private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    override suspend fun sendQuery(
        query: String,
        context: ConversationContext?,
        language: String
    ): Result<ChatbotQueryResponse> = withContext(Dispatchers.IO) {
        try {
            val contextDto = context?.let {
                ChatbotContextDto(
                    conversationId = it.conversationId,
                    lastIntent = it.lastIntent,
                    lastEntities = it.lastEntities,
                    lastTimestamp = it.lastTimestamp
                )
            }
            
            val request = ChatbotQueryRequest(
                query = query,
                context = contextDto,
                language = language
            )
            
            val response = api.sendChatbotQuery(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getQuickSuggestions(): Result<List<QuickSuggestion>> = 
        withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cached = getCachedSuggestions()
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                
                // Fetch from API
                val response = api.getChatbotSuggestions()
                val suggestions = response.suggestions.map { dto ->
                    QuickSuggestion(
                        text = dto.text,
                        category = SuggestionCategory.valueOf(dto.category.uppercase()),
                        icon = dto.icon
                    )
                }
                
                // Cache the suggestions
                cacheSuggestions(suggestions)
                
                Result.success(suggestions)
            } catch (e: Exception) {
                // Return default suggestions on error
                Result.success(getDefaultSuggestions())
            }
        }
    
    override suspend fun saveConversation(
        messages: List<ChatMessage>,
        context: ConversationContext?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save messages count only (simplified)
            sharedPreferences.edit()
                .putInt(KEY_CONVERSATION_HISTORY, messages.size)
                .apply()
            
            // Save context
            context?.let {
                sharedPreferences.edit()
                    .putString("${KEY_CONVERSATION_CONTEXT}_id", it.conversationId)
                    .putString("${KEY_CONVERSATION_CONTEXT}_intent", it.lastIntent ?: "")
                    .putLong("${KEY_CONVERSATION_CONTEXT}_timestamp", it.lastTimestamp)
                    .apply()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun loadConversation(): Result<ConversationHistory> = 
        withContext(Dispatchers.IO) {
            try {
                val conversationId = sharedPreferences.getString("${KEY_CONVERSATION_CONTEXT}_id", null)
                val lastIntent = sharedPreferences.getString("${KEY_CONVERSATION_CONTEXT}_intent", null)
                val lastTimestamp = sharedPreferences.getLong("${KEY_CONVERSATION_CONTEXT}_timestamp", System.currentTimeMillis())
                
                val context = conversationId?.let {
                    ConversationContext(
                        conversationId = it,
                        lastIntent = lastIntent?.takeIf { it.isNotEmpty() },
                        lastTimestamp = lastTimestamp
                    )
                }
                
                Result.success(ConversationHistory(emptyList(), context))
            } catch (e: Exception) {
                Result.success(ConversationHistory())
            }
        }
    
    override suspend fun clearConversation(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit()
                .remove(KEY_CONVERSATION_HISTORY)
                .remove("${KEY_CONVERSATION_CONTEXT}_id")
                .remove("${KEY_CONVERSATION_CONTEXT}_intent")
                .remove("${KEY_CONVERSATION_CONTEXT}_timestamp")
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ─── Private Helper Methods ──────────────────────────────────
    
    private fun getCachedSuggestions(): List<QuickSuggestion>? {
        val timestamp = sharedPreferences.getLong("${CACHE_KEY_SUGGESTIONS}_timestamp", 0)
        
        // Check if cache is expired
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS) {
            return null
        }
        
        return try {
            val count = sharedPreferences.getInt("${CACHE_KEY_SUGGESTIONS}_count", 0)
            if (count == 0) return null
            
            (0 until count).mapNotNull { index ->
                val text = sharedPreferences.getString("${CACHE_KEY_SUGGESTIONS}_${index}_text", null) ?: return@mapNotNull null
                val category = sharedPreferences.getString("${CACHE_KEY_SUGGESTIONS}_${index}_category", null) ?: return@mapNotNull null
                val icon = sharedPreferences.getString("${CACHE_KEY_SUGGESTIONS}_${index}_icon", null) ?: return@mapNotNull null
                
                QuickSuggestion(
                    text = text,
                    category = SuggestionCategory.valueOf(category),
                    icon = icon
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun cacheSuggestions(suggestions: List<QuickSuggestion>) {
        try {
            val editor = sharedPreferences.edit()
            editor.putInt("${CACHE_KEY_SUGGESTIONS}_count", suggestions.size)
            editor.putLong("${CACHE_KEY_SUGGESTIONS}_timestamp", System.currentTimeMillis())
            
            suggestions.forEachIndexed { index, suggestion ->
                editor.putString("${CACHE_KEY_SUGGESTIONS}_${index}_text", suggestion.text)
                editor.putString("${CACHE_KEY_SUGGESTIONS}_${index}_category", suggestion.category.name)
                editor.putString("${CACHE_KEY_SUGGESTIONS}_${index}_icon", suggestion.icon)
            }
            
            editor.apply()
        } catch (e: Exception) {
            // Ignore cache errors
        }
    }
    
    private fun getDefaultSuggestions(): List<QuickSuggestion> {
        return listOf(
            QuickSuggestion("What are today's total sales?", SuggestionCategory.SALES, "💰"),
            QuickSuggestion("Who is absent today?", SuggestionCategory.STAFF, "👥"),
            QuickSuggestion("Top selling items this week", SuggestionCategory.ANALYTICS, "⭐"),
            QuickSuggestion("How many orders today?", SuggestionCategory.ORDERS, "📦")
        )
    }
}
