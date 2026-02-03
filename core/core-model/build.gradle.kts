plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.cafeemanger.core.model"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
