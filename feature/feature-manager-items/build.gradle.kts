plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.items"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.items.generated.resources"
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
