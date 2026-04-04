package net.marllex.waselak.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SentryPlugin")

fun Application.configureSentry() {
    val dsn = environment.config.propertyOrNull("sentry.dsn")?.getString()
        ?: System.getenv("SENTRY_DSN") ?: ""
    if (dsn.isBlank()) {
        logger.warn("Sentry DSN not configured, Sentry is disabled")
        return
    }

    val sentryEnv = environment.config.propertyOrNull("sentry.environment")?.getString()
        ?: System.getenv("SENTRY_ENV") ?: "development"

    Sentry.init { options ->
        options.dsn = dsn
        options.environment = sentryEnv
        options.tracesSampleRate = 1.0
        options.setTag("app", "backend")
        options.setTag("platform", "server")
        options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
            event
        }
    }

    logger.info("Sentry initialized (env=$sentryEnv)")

    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()
        try {
            proceed()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val status = call.response.status()?.value ?: 0

            // Log every request as a breadcrumb
            val breadcrumb = Breadcrumb().apply {
                type = "http"
                category = "http.request"
                message = "$method $path -> $status (${duration}ms)"
                level = when {
                    status in 200..399 -> SentryLevel.INFO
                    status in 400..499 -> SentryLevel.INFO // 4xx = expected business logic
                    else -> SentryLevel.ERROR // 5xx = real server errors
                }
                setData("method", method)
                setData("path", path)
                setData("status_code", status.toString())
                setData("duration_ms", duration.toString())
            }
            Sentry.addBreadcrumb(breadcrumb)

            // Only capture real server errors (500+) as Sentry events
            // 4xx responses are expected business logic:
            //   400 = bad request, 401 = expired token, 403 = feature not available,
            //   404 = not found, 409 = conflict (duplicate)
            if (status in 500..599) {
                Sentry.withScope { scope ->
                    scope.setTag("http.method", method)
                    scope.setTag("http.path", path)
                    scope.setTag("http.status_code", status.toString())
                    scope.setExtra("duration_ms", duration.toString())
                    scope.setExtra("query_string", call.request.queryString())
                    scope.setExtra("user_agent", call.request.userAgent() ?: "unknown")

                    val event = SentryEvent().apply {
                        this.message = Message().apply {
                            this.message = "HTTP $status: $method $path"
                        }
                        level = SentryLevel.ERROR
                    }
                    Sentry.captureEvent(event)
                }
            }
        }
    }
}
