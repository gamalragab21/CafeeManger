plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.admin"

    defaultConfig {
        applicationId = "net.marllex.waselak.admin"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.marllex.waselak.admin.MainKt"
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core UI only (admin has its own network layer)
            implementation(project(":core:core-common"))
            implementation(project(":core:core-model"))
            implementation(project(":core:core-ui"))

            // Navigation & Lifecycle
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Ktor Client for admin API
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)


            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Logging
            implementation(libs.kermit)

            // Serialization
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            val targetOs = project.findProperty("targetOs")?.toString() ?: "current"
            implementation(
                when (targetOs) {
                    "windows" -> compose.desktop.windows_x64
                    "linux" -> compose.desktop.linux_x64
                    "macos-x64" -> compose.desktop.macos_x64
                    "macos-arm64" -> compose.desktop.macos_arm64
                    else -> compose.desktop.currentOs
                }
            )
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
    }
}
