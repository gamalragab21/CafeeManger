plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.delivery.orders"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(project(":core:core-auth"))
    implementation(libs.coil.compose)
    implementation("androidx.print:print:1.1.0")
    implementation(libs.zxing.core)
}
