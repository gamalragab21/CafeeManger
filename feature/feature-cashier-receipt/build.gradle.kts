plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.cashier.receipt"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation("androidx.print:print:1.1.0")
}
