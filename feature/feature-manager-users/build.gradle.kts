plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.manager.users"
}

dependencies {
    implementation(project(":core:core-data"))
}
