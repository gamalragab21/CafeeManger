plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.manager.dashboard"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.coil.compose)
}
