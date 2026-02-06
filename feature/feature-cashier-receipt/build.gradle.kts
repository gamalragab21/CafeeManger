plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.cashier.receipt"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
}
