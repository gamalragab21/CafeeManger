plugins {
    alias(libs.plugins.cafeemanger.android.feature)
}

android {
    namespace = "net.marllex.cafeemanger.feature.manager.chatbot"
}

dependencies {
    implementation(project(":core:core-data"))
    
    // Security - Encrypted SharedPreferences
    implementation(libs.security.crypto)
}
