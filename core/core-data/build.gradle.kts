plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.core.data"
}

dependencies {
    api(project(":core:core-domain"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))

    implementation(libs.kotlinx.coroutines.android)
}
