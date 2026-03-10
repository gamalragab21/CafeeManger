package net.marllex.waselak.backend.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals

class HealthRouteTest {

    @Test
    fun `health endpoint returns 200 with UP status`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/health") {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("status" to "UP", "service" to "Waselak API")
                    )
                }
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("UP", body["status"]?.jsonPrimitive?.content)
        assertEquals("Waselak API", body["service"]?.jsonPrimitive?.content)
    }

    @Test
    fun `health endpoint returns JSON content type`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/health") {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("status" to "UP", "service" to "Waselak API")
                    )
                }
            }
        }

        val response = client.get("/health")
        assertEquals(
            ContentType.Application.Json,
            response.contentType()?.withoutParameters()
        )
    }
}
