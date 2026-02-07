plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.manager.stock"
}

dependencies {
    implementation(project(":core:core-data"))
}
