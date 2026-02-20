plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.staff"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.staff.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(project(":core:core-network"))
            implementation("network.chaintech:qr-kit:3.1.3")
        }
    }
}
