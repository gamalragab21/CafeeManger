plugins {
    alias(libs.plugins.waselak.kmp.library)
    alias(libs.plugins.waselak.koin)
    alias(libs.plugins.waselak.sqldelight)
}

android {
    namespace = "net.marllex.waselak.core.database"
}

sqldelight {
    databases {
        create("WaselakDatabase") {
            packageName.set("net.marllex.waselak.core.database")
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-model"))
            implementation(project(":core:core-common"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
