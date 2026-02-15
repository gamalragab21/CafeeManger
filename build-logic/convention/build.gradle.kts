import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "net.marllex.waselak.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.sqldelight.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "waselak.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpComposeLibrary") {
            id = "waselak.kmp.compose.library"
            implementationClass = "KmpComposeLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "waselak.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpApplication") {
            id = "waselak.kmp.application"
            implementationClass = "KmpApplicationConventionPlugin"
        }
        register("koin") {
            id = "waselak.koin"
            implementationClass = "KoinConventionPlugin"
        }
        register("sqldelight") {
            id = "waselak.sqldelight"
            implementationClass = "SqlDelightConventionPlugin"
        }
    }
}
