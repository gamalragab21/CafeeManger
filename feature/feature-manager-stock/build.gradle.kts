plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.stock"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.stock.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
        }
    }
}
