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
        }
        // qr-kit:3.1.3 ships an iosArm64 klib but Kotlin/Native 2.1.0's
        // KLIB resolver fails to load it (path-format mismatch with what
        // Gradle 8.11.1's metadata expects). Plus nothing in this module
        // actually imports the library yet — keeping it around for the
        // future Android-side QR scanner UI but not pulling it into iOS
        // builds where it would just block the link step.
        androidMain.dependencies {
            implementation("network.chaintech:qr-kit:3.1.3")
        }
    }
}
