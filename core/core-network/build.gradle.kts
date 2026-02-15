plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.waselak.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.core.network"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-model"))
            implementation(project(":core:core-common"))

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
    }
}
