plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.delivery.navigation"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.delivery.navigation.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
        }
    }
}
