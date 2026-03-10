package net.marllex.waselak.backend.plugins

import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.json.*

/**
 * RouteTrace provides step-by-step request tracing for API routes.
 *
 * Usage in route handlers:
 * ```
 * post("/login") {
 *     val trace = call.routeTrace()
 *     trace.step("Login started")
 *     trace.step("Finding user by phone", mapOf("phone" to request.phone))
 *     // ... do work ...
 *     trace.step("User found", mapOf("userId" to user.id, "role" to user.role))
 *     trace.step("Login completed")
 * }
 * ```
 *
 * Traces are automatically captured by the RequestLogging plugin and persisted
 * to the `trace_log` column in the request_logs table.
 */

val routeTraceKey = AttributeKey<RouteTrace>("routeTrace")

/**
 * Get or create the RouteTrace for this call.
 * Call this at the start of each route handler to begin tracing.
 */
fun ApplicationCall.routeTrace(): RouteTrace {
    return attributes.getOrNull(routeTraceKey) ?: RouteTrace().also {
        attributes.put(routeTraceKey, it)
    }
}

class RouteTrace {
    private val steps = mutableListOf<TraceStep>()
    private val startTime = System.currentTimeMillis()

    data class TraceStep(
        val step: Int,
        val message: String,
        val data: Map<String, String?>? = null,
        val elapsedMs: Long
    )

    /**
     * Record a trace step with an optional data map.
     * @param message Human-readable description of this step
     * @param data Optional key-value pairs of relevant variables
     */
    fun step(message: String, data: Map<String, String?>? = null) {
        val elapsed = System.currentTimeMillis() - startTime
        steps.add(
            TraceStep(
                step = steps.size + 1,
                message = message,
                data = data?.filterValues { it != null },
                elapsedMs = elapsed
            )
        )
    }

    /**
     * Convenience: record a step with a single key-value pair.
     */
    fun step(message: String, key: String, value: String?) {
        step(message, if (value != null) mapOf(key to value) else null)
    }

    /**
     * Serialize all steps to a JSON string for storage.
     * Returns null if no steps were recorded.
     */
    fun toJson(): String? {
        if (steps.isEmpty()) return null
        return buildJsonArray {
            steps.forEach { s ->
                addJsonObject {
                    put("step", s.step)
                    put("message", s.message)
                    s.data?.let { d ->
                        if (d.isNotEmpty()) {
                            putJsonObject("data") {
                                d.forEach { (k, v) -> put(k, v) }
                            }
                        }
                    }
                    put("elapsed_ms", s.elapsedMs)
                }
            }
        }.toString()
    }

    /**
     * Get the number of steps recorded.
     */
    fun stepCount(): Int = steps.size
}
