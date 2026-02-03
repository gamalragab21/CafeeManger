plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.marllex.cafeemanger.core.network"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
