plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.analytics"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.analytics.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
            implementation(libs.vico.multiplatform)
            implementation(libs.vico.multiplatform.m3)
        }
    }
}
