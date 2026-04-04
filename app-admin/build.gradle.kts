plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.admin"

    defaultConfig {
        applicationId = "net.marllex.waselak.admin"
        // versionCode and versionName are set centrally via gradle.properties → KmpApplicationConventionPlugin

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            val isDebugDesktop = (project.findProperty("BUILD_ENV") as? String)?.lowercase() == "debug"
            packageName = if (isDebugDesktop) "Waselak Admin Debug" else "Waselak Admin"
            packageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            vendor = "Marllex"
            description = if (isDebugDesktop) "Waselak Admin Debug" else "Waselak Admin Panel"

            macOS {
                bundleID = "net.marllex.waselak.admin"
                val icns = project.file("src/desktopMain/resources/icon.icns")
                if (icns.exists()) iconFile.set(icns)
                dockName = if (isDebugDesktop) "Waselak Admin Debug" else "Waselak Admin"
            }
            windows {
                val ico = project.file("src/desktopMain/resources/icon.ico")
                if (ico.exists()) iconFile.set(ico)
                menuGroup = "Waselak"
                shortcut = true
                perUserInstall = true
            }
            linux {
                val png = project.file("src/desktopMain/resources/icon.png")
                if (png.exists()) iconFile.set(png)
                packageName = "waselak-admin"
                menuGroup = "Waselak"
            }
        }
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
