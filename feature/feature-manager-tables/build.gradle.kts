plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.tables"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.tables.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
        }
    }
}
