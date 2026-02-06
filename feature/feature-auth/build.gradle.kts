plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.auth"
}

dependencies {
    implementation(project(":core:core-auth"))
}
