plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.manager.staff"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(project(":core:core-network"))
}
