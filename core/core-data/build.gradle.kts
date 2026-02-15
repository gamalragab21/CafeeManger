plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-domain"))
            implementation(project(":core:core-common"))
            implementation(project(":core:core-network"))
            implementation(project(":core:core-database"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
        }
    }
}
