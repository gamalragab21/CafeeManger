plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
//    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.2"
    application
}

group = "net.marllex.cafeemanger"
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
    mainClass.set("net.marllex.cafeemanger.backend.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-netty:3.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-server-auth:3.0.2")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.2")
    implementation("io.ktor:ktor-server-cors:3.0.2")
    implementation("io.ktor:ktor-server-status-pages:3.0.2")
    implementation("io.ktor:ktor-server-call-logging:3.0.2")
    implementation("io.ktor:ktor-server-default-headers:3.0.2")



    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
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

    // Koin DI
    val koin_version = "3.5.6"
     // Koin for Ktor 3.x
    implementation("io.insert-koin:koin-ktor:${koin_version}")
    implementation("io.insert-koin:koin-logger-slf4j:${koin_version}")


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

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.0.2")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("cafeemanger-backend")
        imageTag.set("latest")
    }
}
