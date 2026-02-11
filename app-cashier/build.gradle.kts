plugins {
    alias(libs.plugins.cafeemanger.android.application)
    alias(libs.plugins.cafeemanger.android.application.compose)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.cashier"

    defaultConfig {
        applicationId = "net.marllex.cafeemanger.cashier"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-auth"))

    // Feature modules
    implementation(project(":feature:feature-auth"))
    implementation(project(":feature:feature-manager-orders"))
    implementation(project(":feature:feature-cashier-pos"))
    implementation(project(":feature:feature-cashier-payment"))
    implementation(project(":feature:feature-cashier-receipt"))
    implementation(project(":feature:feature-cashier-attendance"))
    implementation(project(":feature:feature-manager-tables"))
    implementation(project(":feature:feature-manager-staff"))

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
