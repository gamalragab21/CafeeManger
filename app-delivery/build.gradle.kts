plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.delivery"

    defaultConfig {
        applicationId = "net.marllex.waselak.delivery"
        // versionCode and versionName are set centrally via gradle.properties → KmpApplicationConventionPlugin

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))

        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") as? String ?: ""
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.marllex.waselak.delivery.MainKt"

        jvmArgs += listOf(
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-exports", "jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-reads", "jdk.unsupported=ALL-UNNAMED",
        )

        nativeDistributions {
            modules("jdk.unsupported")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Waselak Delivery"
            packageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            vendor = "Marllex"
            description = "Waselak Restaurant Delivery"

            macOS {
                bundleID = "net.marllex.waselak.delivery"
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                val ico = project.file("src/desktopMain/resources/icon.ico")
                if (ico.exists()) iconFile.set(ico)
                menuGroup = "Waselak"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "c3d4e5f6-a7b8-9012-cdef-123456789012"
            }
            linux {
                val png = project.file("src/desktopMain/resources/icon.png")
                if (png.exists()) iconFile.set(png)
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core modules
            implementation(project(":core:core-common"))
            implementation(project(":core:core-model"))
            implementation(project(":core:core-ui"))
            implementation(project(":core:core-network"))
            implementation(project(":core:core-database"))
            implementation(project(":core:core-domain"))
            implementation(project(":core:core-data"))
            implementation(project(":core:core-auth"))

            // Feature modules
            implementation(project(":feature:feature-auth"))
            implementation(project(":feature:feature-delivery-orders"))
            implementation(project(":feature:feature-delivery-navigation"))
            implementation(project(":feature:feature-delivery-status"))
            implementation(project(":feature:feature-manager-staff"))

            // Navigation & Lifecycle
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Koin Compose
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)

            // Maps (Android-only)
            implementation(libs.play.services.maps)
            implementation(libs.play.services.location)
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
        }
    }
}
