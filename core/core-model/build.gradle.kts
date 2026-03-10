plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.waselak.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
