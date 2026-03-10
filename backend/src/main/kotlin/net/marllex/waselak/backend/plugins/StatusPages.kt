package net.marllex.waselak.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.domain.service.AccountSuspendedException
import net.marllex.waselak.backend.domain.service.FeatureNotAvailableException
import net.marllex.waselak.backend.domain.service.PlanLimitExceededException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AccountSuspendedException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("ACCOUNT_SUSPENDED", cause.message ?: "Your account has been suspended")
            )
        }
        exception<PlanLimitExceededException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("PLAN_LIMIT_EXCEEDED", cause.message ?: "You have reached your plan's limit")
            )
        }
        exception<FeatureNotAvailableException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("FEATURE_NOT_AVAILABLE", cause.message ?: "This feature is not available on your current plan")
            )
        }
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
