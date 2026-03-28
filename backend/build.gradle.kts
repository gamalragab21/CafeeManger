plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
    application
}

group = "net.marllex.waselak"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
application {
    mainClass.set("net.marllex.waselak.backend.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// Load env vars from env/backend-debug.env when running locally
tasks.named<JavaExec>("run") {
    val envFile = rootProject.file("env/backend-debug.env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                environment(key.trim(), value.trim())
            }
    }
}

val ktor_version = "3.0.3"
val koin_version = "4.0.1"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers:$ktor_version")
    implementation("io.ktor:ktor-server-double-receive:$ktor_version")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.56.0")

    // PostgreSQL Driver
    implementation("org.postgresql:postgresql:42.7.4")

    // Connection Pooling
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Koin DI for Ktor 3.x
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // Kotlin Reflect (required by Koin)
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")

    // Password Hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // PDF Generation
    implementation("com.itextpdf:itext7-core:7.2.5")

    // Excel Generation
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Sentry
    implementation("io.sentry:sentry:7.3.0")
    implementation("io.sentry:sentry-logback:7.3.0")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.1.0")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("waselak-backend")
        imageTag.set("latest")
    }
}
