plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.cashier"

    defaultConfig {
        applicationId = "net.marllex.waselak.cashier"
        // versionCode and versionName are set centrally via gradle.properties → KmpApplicationConventionPlugin

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))
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
        mainClass = "net.marllex.waselak.cashier.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Waselak Cashier"
            packageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            vendor = "Marllex"
            description = "Waselak Restaurant Cashier"

            macOS {
                bundleID = "net.marllex.waselak.cashier"
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                val ico = project.file("src/desktopMain/resources/icon.ico")
                if (ico.exists()) iconFile.set(ico)
                menuGroup = "Waselak"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "b2c3d4e5-f6a7-8901-bcde-f12345678901"
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
            implementation(project(":feature:feature-manager-orders"))
            implementation(project(":feature:feature-cashier-pos"))
            implementation(project(":feature:feature-cashier-payment"))
            implementation(project(":feature:feature-cashier-receipt"))
            implementation(project(":feature:feature-cashier-attendance"))
            implementation(project(":feature:feature-manager-tables"))
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
