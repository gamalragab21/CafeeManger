package net.marllex.waselak.backend.config

import io.ktor.server.config.ApplicationConfig

class HmacConfig(config: ApplicationConfig) {
    private val hmacConfig = config.config("hmac")

    val secret: String = hmacConfig.property("secret").getString()
    val timestampToleranceMs: Long = hmacConfig.propertyOrNull("timestampToleranceMs")
        ?.getString()?.toLong() ?: 300_000L // 5 minutes default
}
