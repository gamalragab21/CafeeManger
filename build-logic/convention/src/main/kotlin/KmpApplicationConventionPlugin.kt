import com.android.build.api.dsl.ApplicationExtension
import net.marllex.waselak.configureKmpTargets
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
                apply("com.android.application")
            }

            // Generate BuildConfig with BASE_URL from gradle.properties
            val baseUrl = project.findProperty("BASE_URL") as? String ?: "https://api.waselak.net/"
            val buildConfigDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")

            val generateBuildConfig = tasks.register("generateBuildConfig") {
                val outputDir = buildConfigDir
                outputs.dir(outputDir)
                inputs.property("baseUrl", baseUrl)

                doLast {
                    val dir = outputDir.get().asFile.resolve("net/marllex/waselak/config")
                    dir.mkdirs()
                    dir.resolve("BuildConfig.kt").writeText(
                        """
                        |package net.marllex.waselak.config
                        |
                        |object BuildConfig {
                        |    const val BASE_URL: String = "$baseUrl"
                        |}
                        """.trimMargin()
                    )
                }
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpTargets(this)

                sourceSets.commonMain {
                    kotlin.srcDir(generateBuildConfig.map { buildConfigDir.get() })
                }

                val composeDeps = ComposePlugin.Dependencies(project)
                sourceSets.commonMain.dependencies {
                    implementation(composeDeps.runtime)
                    implementation(composeDeps.foundation)
                    implementation(composeDeps.material3)
                    implementation(composeDeps.materialIconsExtended)
                    implementation(composeDeps.ui)
                    implementation(composeDeps.components.resources)
                }
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 28
                    targetSdk = 35
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
