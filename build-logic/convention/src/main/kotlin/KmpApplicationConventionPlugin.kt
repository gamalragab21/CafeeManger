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

            // Generate BuildConfig with BASE_URL and HMAC_SECRET from gradle.properties
            val baseUrl = project.findProperty("BASE_URL") as? String ?: "https://api.waselak.net/"
            val hmacSecret = project.findProperty("HMAC_SECRET") as? String ?: ""
            val buildConfigDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")

            val generateBuildConfig = tasks.register("generateBuildConfig") {
                val outputDir = buildConfigDir
                outputs.dir(outputDir)
                inputs.property("baseUrl", baseUrl)
                inputs.property("hmacSecret", hmacSecret)

                doLast {
                    val dir = outputDir.get().asFile.resolve("net/marllex/waselak/config")
                    dir.mkdirs()
                    dir.resolve("BuildConfig.kt").writeText(
                        """
                        |package net.marllex.waselak.config
                        |
                        |object BuildConfig {
                        |    const val BASE_URL: String = "$baseUrl"
                        |    const val HMAC_SECRET: String = "$hmacSecret"
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
                compileSdk = 36

                defaultConfig {
                    minSdk = 28
                    targetSdk = 36
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
