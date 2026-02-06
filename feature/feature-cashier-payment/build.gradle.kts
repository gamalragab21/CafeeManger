plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.cashier.payment"
}

dependencies {
    implementation(project(":core:core-data"))
}
