plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.auth"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.auth.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-auth"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.biometric)
        }
    }
}
