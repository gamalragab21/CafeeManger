plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.core.domain"
}

dependencies {
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))

    implementation(libs.kotlinx.coroutines.android)
}
