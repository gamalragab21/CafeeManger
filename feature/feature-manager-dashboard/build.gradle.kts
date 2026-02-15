plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.dashboard"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.dashboard.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
    }
}
