plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.cashier.attendance"
}

dependencies {
    implementation(project(":core:core-data"))
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
}
