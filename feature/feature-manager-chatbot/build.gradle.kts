plugins {
    alias(libs.plugins.waselak.kmp.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.feature.manager.chatbot"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.chatbot.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(project(":core:core-network"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}
