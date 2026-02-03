plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.hilt)
    alias(libs.plugins.cafeemanger.android.room)
}

android {
    namespace = "net.marllex.cafeemanger.core.database"
}

dependencies {
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))

    implementation(libs.kotlinx.coroutines.android)
}
