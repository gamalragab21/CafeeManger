package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.domain.service.ChatbotService
import net.marllex.waselak.backend.plugins.routeTrace
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
            val trace = call.routeTrace()
            trace.step("Chatbot query started")
            val principal = requireRole("MANAGER")

            val request = call.receive<ChatbotQueryRequest>()
            val vendorId = UUID.fromString(principal.vendorId)
            trace.step("Query received", mapOf(
                "question" to request.query,
                "language" to request.language,
                "vendorId" to vendorId.toString(),
                "hasContext" to (request.context != null).toString()
            ))

            try {
                // Process the query
                trace.step("Processing query with chatbot service")
                val serviceResponse = chatbotService.processQuery(
                    query = request.query,
                    vendorId = vendorId,
                    language = request.language
                )
                trace.step("Query processed", mapOf(
                    "answerLength" to serviceResponse.answer.length.toString(),
                    "visualFormat" to serviceResponse.visualFormat,
                    "suggestionsCount" to serviceResponse.suggestions.size.toString(),
                    "hasData" to (serviceResponse.data != null).toString()
                ))

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

                trace.step("Chatbot query completed", mapOf("conversationId" to conversationId))
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                trace.step("Chatbot query failed", mapOf("error" to (e.message ?: "unknown")))
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to process query"))
                )
            }
        }
        
        // GET /api/v1/chatbot/suggestions - Get quick suggestions
        get("/suggestions") {
            val trace = call.routeTrace()
            trace.step("Get suggestions started")
            val principal = requireRole("MANAGER")

            val language = call.request.queryParameters["language"] ?: "en"
            trace.step("Fetching suggestions", mapOf("language" to language))

            try {
                val suggestions = chatbotService.getQuickSuggestions(language)
                trace.step("Suggestions fetched", mapOf("count" to suggestions.size.toString()))

                val response = ChatbotSuggestionsResponse(
                    suggestions = suggestions.map { suggestion ->
                        ChatbotSuggestionDto(
                            text = suggestion.text,
                            category = suggestion.category,
                            icon = suggestion.icon
                        )
                    }
                )

                trace.step("Get suggestions completed")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                trace.step("Get suggestions failed", mapOf("error" to (e.message ?: "unknown")))
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to get suggestions"))
                )
            }
        }
    }
}
