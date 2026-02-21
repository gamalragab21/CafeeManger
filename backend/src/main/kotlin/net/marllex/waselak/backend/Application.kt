package net.marllex.waselak.backend

import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.netty.*
import net.marllex.waselak.backend.config.DatabaseConfig
import net.marllex.waselak.backend.di.appModule
import net.marllex.waselak.backend.plugins.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Initialize Koin DI
    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single<ApplicationConfig> { environment.config }
            },
            appModule
        )
    }

    // Initialize database
    DatabaseConfig.init(environment.config, false)

    // Configure plugins
    configureSerialization()
    configureAuthentication()
    configureCORS()
    configureHmacVerification()
    configureStatusPages()
    configureCallLogging()
    configureRouting()
}
