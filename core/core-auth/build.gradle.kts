plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.waselak.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.core.auth"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-common"))
            implementation(project(":core:core-model"))
            implementation(project(":core:core-network"))
            implementation(project(":core:core-domain"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.datastore.core)
        }
    }
}
