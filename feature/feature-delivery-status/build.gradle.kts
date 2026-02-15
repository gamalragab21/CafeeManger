plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.delivery.status"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.delivery.status.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(project(":core:core-auth"))
        }
    }
}
