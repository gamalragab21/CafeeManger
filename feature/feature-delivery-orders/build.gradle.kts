plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.delivery.orders"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.delivery.orders.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(project(":core:core-network"))
            implementation(project(":core:core-auth"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.zxing.core)
        }
    }
}
