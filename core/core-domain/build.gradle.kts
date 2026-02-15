plugins {
    alias(libs.plugins.waselak.kmp.library)
}

android {
    namespace = "net.marllex.waselak.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-model"))
            implementation(project(":core:core-common"))
            api(project(":core:core-network"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
