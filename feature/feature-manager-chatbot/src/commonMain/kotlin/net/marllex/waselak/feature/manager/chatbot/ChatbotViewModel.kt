package net.marllex.waselak.feature.manager.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.feature.manager.chatbot.data.ChatbotRepository
import net.marllex.waselak.feature.manager.chatbot.model.ChatMessage
import net.marllex.waselak.feature.manager.chatbot.model.ConversationContext
import net.marllex.waselak.feature.manager.chatbot.model.QuickSuggestion
import net.marllex.waselak.feature.manager.chatbot.model.ResponseData
import net.marllex.waselak.feature.manager.chatbot.model.ResponseType
import net.marllex.waselak.feature.manager.chatbot.model.SuggestionCategory
import net.marllex.waselak.feature.manager.chatbot.model.VisualFormat
import net.marllex.waselak.core.common.logging.AppLogger

class ChatbotViewModel(
    private val repository: ChatbotRepository
) : ViewModel() {
    private companion object { private const val TAG = "Chatbot" }


    data class UiState(
        val messages: List<ChatMessage> = emptyList(),
        val suggestions: List<QuickSuggestion> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val conversationContext: ConversationContext? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadQuickSuggestions()
        loadConversationHistory()
    }

    fun sendMessage(text: String, language: String = "en") {
        AppLogger.d(TAG, "sendMessage called")
        if (text.isBlank()) return

        viewModelScope.launch {
            // Add user message
            addMessage(ChatMessage.User(text = text))

            // Show loading
            val loadingMessage = ChatMessage.Loading()
            addMessage(loadingMessage)
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Send query to backend via repository
                val result = repository.sendQuery(
                    query = text,
                    context = _uiState.value.conversationContext,
                    language = language
                )

                result.fold(
                    onSuccess = { response ->
                        // Remove loading message
                        removeMessage(loadingMessage.id)

                        // Parse response data
                        val responseData = response.data?.let { dataDto ->
                            ResponseData(
                                type = ResponseType.valueOf(dataDto.type.uppercase()),
                                values = dataDto.values.mapValues { it.value as Any }
                            )
                        }

                        // Add bot response
                        addMessage(
                            ChatMessage.Bot(
                                text = response.answer,
                                data = responseData,
                                suggestions = response.suggestions,
                                visualFormat = VisualFormat.valueOf(
                                    response.visualFormat.uppercase()
                                )
                            )
                        )

                        // Update context
                        val newContext = ConversationContext(
                            conversationId = response.context.conversationId,
                            lastIntent = response.context.lastIntent,
                            lastEntities = response.context.lastEntities,
                            lastTimestamp = response.context.lastTimestamp
                        )

                        _uiState.update {
                            it.copy(
                                conversationContext = newContext,
                                isLoading = false,
                                error = null
                            )
                        }

                        // Save conversation
                        saveConversation()
                    },
                    onFailure = { exception ->
                        // Remove loading message
                        removeMessage(loadingMessage.id)

                        // Add error message
                        val errorMessage = when {
                            exception.message?.contains("timeout", ignoreCase = true) == true ->
                                "Request timed out. Please try again."
                            exception.message?.contains("network", ignoreCase = true) == true ->
                                "Network error. Please check your connection."
                            else -> exception.message ?: "Unknown error occurred"
                        }

                        addMessage(ChatMessage.Error(message = errorMessage))

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = errorMessage
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                // Remove loading message
                removeMessage(loadingMessage.id)

                // Add error message
                addMessage(
                    ChatMessage.Error(
                        message = e.message ?: "Unknown error occurred"
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    conversationContext = null
                )
            }
            repository.clearConversation()
        }
    }

    private fun loadQuickSuggestions() {
        viewModelScope.launch {
            val result = repository.getQuickSuggestions()
            result.fold(
                onSuccess = { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions) }
                },
                onFailure = {
                    // Use default suggestions on error
                    _uiState.update {
                        it.copy(suggestions = getDefaultSuggestions())
                    }
                }
            )
        }
    }

    private fun loadConversationHistory() {
        viewModelScope.launch {
            val result = repository.loadConversation()
            result.fold(
                onSuccess = { history ->
                    _uiState.update {
                        it.copy(
                            messages = history.messages,
                            conversationContext = history.context
                        )
                    }
                },
                onFailure = {
                    // Ignore errors, start with empty conversation
                }
            )
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }

    private fun removeMessage(messageId: String) {
        _uiState.update {
            it.copy(messages = it.messages.filter { msg -> msg.id != messageId })
        }
    }

    private fun saveConversation() {
        viewModelScope.launch {
            repository.saveConversation(
                messages = _uiState.value.messages,
                context = _uiState.value.conversationContext
            )
        }
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
