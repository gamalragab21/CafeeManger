plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.cashier.pos"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(project(":core:core-auth"))
}
