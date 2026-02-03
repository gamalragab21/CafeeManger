plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
