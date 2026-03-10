plugins {
    alias(libs.plugins.waselak.kmp.feature)
}

android {
    namespace = "net.marllex.waselak.feature.manager.orders"
}

compose.resources {
    packageOfResClass = "net.marllex.waselak.feature.manager.orders.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-data"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.mockk)
            }
        }
    }
}
