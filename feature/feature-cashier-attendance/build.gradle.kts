plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.cashier.attendance"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.cashier.attendance.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
        }
        androidMain.dependencies {
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.accompanist.permissions)
        }
    }
}
