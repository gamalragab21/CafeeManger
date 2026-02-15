package net.marllex.waselak.feature.manager.chatbot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.ChatbotContextDto
import net.marllex.waselak.core.network.dto.ChatbotQueryRequest
import net.marllex.waselak.core.network.dto.ChatbotQueryResponse
import net.marllex.waselak.feature.manager.chatbot.model.ChatMessage
import net.marllex.waselak.feature.manager.chatbot.model.ConversationContext
import net.marllex.waselak.feature.manager.chatbot.model.ConversationHistory
import net.marllex.waselak.feature.manager.chatbot.model.QuickSuggestion
import net.marllex.waselak.feature.manager.chatbot.model.SuggestionCategory

class ChatbotRepositoryImpl(
    private val apiClient: WaselakApiClient
) : ChatbotRepository {

    private val mutex = Mutex()

    // In-memory conversation storage
    private var savedMessages: List<ChatMessage> = emptyList()
    private var savedContext: ConversationContext? = null

    // In-memory suggestions cache
    private var cachedSuggestions: List<QuickSuggestion>? = null
    private var cacheTimestamp: Long = 0L
    private val cacheExpiryMs = 5 * 60 * 1000L // 5 minutes

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

            val response = apiClient.sendChatbotQuery(request)
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
                val response = apiClient.getChatbotSuggestions()
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
            mutex.withLock {
                savedMessages = messages
                savedContext = context
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadConversation(): Result<ConversationHistory> =
        withContext(Dispatchers.IO) {
            try {
                val (messages, context) = mutex.withLock {
                    savedMessages to savedContext
                }
                Result.success(ConversationHistory(messages, context))
            } catch (e: Exception) {
                Result.success(ConversationHistory())
            }
        }

    override suspend fun clearConversation(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                savedMessages = emptyList()
                savedContext = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCachedSuggestions(): List<QuickSuggestion>? {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - cacheTimestamp > cacheExpiryMs) {
            return null
        }
        return cachedSuggestions
    }

    private fun cacheSuggestions(suggestions: List<QuickSuggestion>) {
        cachedSuggestions = suggestions
        cacheTimestamp = Clock.System.now().toEpochMilliseconds()
    }

    private fun getDefaultSuggestions(): List<QuickSuggestion> {
        return listOf(
            QuickSuggestion("What are today's total sales?", SuggestionCategory.SALES, "cash"),
            QuickSuggestion("Who is absent today?", SuggestionCategory.STAFF, "people"),
            QuickSuggestion("Top selling items this week", SuggestionCategory.ANALYTICS, "star"),
            QuickSuggestion("How many orders today?", SuggestionCategory.ORDERS, "box")
        )
    }
}
