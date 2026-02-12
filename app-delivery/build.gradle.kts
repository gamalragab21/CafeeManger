plugins {
    alias(libs.plugins.cafeemanger.android.application)
    alias(libs.plugins.cafeemanger.android.application.compose)
    alias(libs.plugins.cafeemanger.android.hilt)
}

android {
    namespace = "net.marllex.cafeemanger.delivery"

    defaultConfig {
        applicationId = "net.marllex.cafeemanger.delivery"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))

        // Google Maps API Key - replace with your actual key or set via local.properties
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") as? String ?: ""
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
    implementation(project(":feature:feature-delivery-orders"))
    implementation(project(":feature:feature-delivery-navigation"))
    implementation(project(":feature:feature-delivery-status"))
    implementation(project(":feature:feature-manager-staff"))
    implementation("androidx.print:print:1.1.0")

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    // Maps
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
