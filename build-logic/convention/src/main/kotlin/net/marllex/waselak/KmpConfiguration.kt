package net.marllex.waselak

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureKmpTargets(
    extension: KotlinMultiplatformExtension,
) {
    extension.apply {
        applyDefaultHierarchyTemplate()

        androidTarget {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_17
            }
        }

        jvm("desktop")

        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = project.name
                isStatic = true
            }
        }
    }
}

internal fun Project.configureAndroidLibrary(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig {
            minSdk = 28
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
