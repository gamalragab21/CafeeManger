plugins {
    alias(libs.plugins.cafeemanger.android.library)
    alias(libs.plugins.cafeemanger.android.library.compose)
}

android {
    namespace = "net.marllex.cafeemanger.core.ui"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)

    debugApi(libs.androidx.compose.ui.tooling)
}
