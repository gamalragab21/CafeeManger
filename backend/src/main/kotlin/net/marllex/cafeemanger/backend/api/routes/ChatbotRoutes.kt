package net.marllex.cafeemanger.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.marllex.cafeemanger.backend.api.middleware.requireRole
import net.marllex.cafeemanger.backend.domain.service.ChatbotService
import java.util.UUID

@Serializable
data class ChatbotQueryRequest(
    val query: String,
    val context: ChatbotContextDto? = null,
    val language: String = "en"
)

@Serializable
data class ChatbotContextDto(
    val conversation_id: String,
    val last_intent: String? = null,
    val last_entities: Map<String, String> = emptyMap(),
    val last_timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatbotQueryResponse(
    val answer: String,
    val data: ChatbotResponseDataDto? = null,
    val visual_format: String = "text",
    val suggestions: List<String> = emptyList(),
    val context: ChatbotContextDto,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatbotResponseDataDto(
    val type: String,
    val values: Map<String, String>
)

@Serializable
data class ChatbotSuggestionsResponse(
    val suggestions: List<ChatbotSuggestionDto>
)

@Serializable
data class ChatbotSuggestionDto(
    val text: String,
    val category: String,
    val icon: String
)

fun Route.chatbotRoutes() {
    val chatbotService = ChatbotService()
    
    route("/api/v1/chatbot") {
        
        // POST /api/v1/chatbot/query - Process a chatbot query
        post("/query") {
            val principal = requireRole("MANAGER")
            
            val request = call.receive<ChatbotQueryRequest>()
            val vendorId = UUID.fromString(principal.vendorId)
            
            try {
                // Process the query
                val serviceResponse = chatbotService.processQuery(
                    query = request.query,
                    vendorId = vendorId,
                    language = request.language
                )
                
                // Generate or reuse conversation ID
                val conversationId = request.context?.conversation_id 
                    ?: UUID.randomUUID().toString()
                
                // Build response
                val response = ChatbotQueryResponse(
                    answer = serviceResponse.answer,
                    data = serviceResponse.data?.let { data ->
                        ChatbotResponseDataDto(
                            type = data["type"] as? String ?: "TEXT",
                            values = (data["values"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                                ?.mapValues { it.value.toString() } ?: emptyMap()
                        )
                    },
                    visual_format = serviceResponse.visualFormat,
                    suggestions = serviceResponse.suggestions,
                    context = ChatbotContextDto(
                        conversation_id = conversationId,
                        last_intent = null, // TODO: Extract from service
                        last_entities = emptyMap(), // TODO: Extract from service
                        last_timestamp = System.currentTimeMillis()
                    ),
                    timestamp = System.currentTimeMillis()
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to process query"))
                )
            }
        }
        
        // GET /api/v1/chatbot/suggestions - Get quick suggestions
        get("/suggestions") {
            val principal = requireRole("MANAGER")
            
            val language = call.request.queryParameters["language"] ?: "en"
            
            try {
                val suggestions = chatbotService.getQuickSuggestions(language)
                
                val response = ChatbotSuggestionsResponse(
                    suggestions = suggestions.map { suggestion ->
                        ChatbotSuggestionDto(
                            text = suggestion.text,
                            category = suggestion.category,
                            icon = suggestion.icon
                        )
                    }
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to get suggestions"))
                )
            }
        }
    }
}
