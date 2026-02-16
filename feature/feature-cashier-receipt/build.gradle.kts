plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.cashier.receipt"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.cashier.receipt.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(project(":core:core-network"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation("network.chaintech:qr-kit:3.1.3")
        }
        androidMain.dependencies {
            implementation(libs.androidx.print)
        }
    }
}
