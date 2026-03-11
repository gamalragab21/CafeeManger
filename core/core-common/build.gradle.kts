plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            api(libs.sentry.kmp)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            api(libs.sentry.kmp)
        }
        iosMain.dependencies {
            api(libs.sentry.kmp)
        }
    }
}
