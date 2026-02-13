package net.marllex.cafeemanger.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Bad Request", cause.message ?: "Invalid request")
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("Conflict", cause.message ?: "Invalid state")
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Not Found", cause.message ?: "Resource not found")
            )
        }
        exception<SecurityException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Forbidden", cause.message ?: "Insufficient permissions")
            )
        }
        exception<Throwable> { call, cause ->
            // Log the full stack trace for debugging
            call.application.log.error("Unhandled error: ${cause.message}", cause)
            cause.printStackTrace()

            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    "Internal Server Error",

                    cause.message ?: "An unexpected error occurred"
                )
            )
        }
    }
}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
