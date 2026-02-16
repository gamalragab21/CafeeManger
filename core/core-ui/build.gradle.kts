plugins {
    alias(libs.plugins.waselak.kmp.compose.library)
}

android {
    namespace = "net.marllex.waselak.core.ui"
}

compose.resources {
    publicResClass = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-model"))
            implementation(libs.kotlinx.datetime)
            api(libs.coil.compose)
            api(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.print)
            implementation(libs.zxing.core)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.zxing.core)
        }
    }
}
