plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.manager"

    defaultConfig {
        applicationId = "net.marllex.waselak.manager"
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
                "proguard-rules.pro"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.marllex.waselak.manager.MainKt"
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
            implementation(project(":feature:feature-manager-dashboard"))
            implementation(project(":feature:feature-manager-categories"))
            implementation(project(":feature:feature-manager-items"))
            implementation(project(":feature:feature-manager-tables"))
            implementation(project(":feature:feature-manager-users"))
            implementation(project(":feature:feature-manager-staff"))
            implementation(project(":feature:feature-manager-analytics"))
            implementation(project(":feature:feature-manager-orders"))
            implementation(project(":feature:feature-manager-stock"))
            implementation(project(":feature:feature-manager-chatbot"))

            // Navigation & Lifecycle
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)

            // Koin Compose
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)

            // QR code generation (Android-only)
            implementation(libs.zxing.core)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
