plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.core.auth"
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))

    implementation(libs.security.crypto)
    implementation(libs.jwt.decode)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
