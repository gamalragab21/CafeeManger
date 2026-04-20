import com.android.build.gradle.LibraryExtension
import net.marllex.waselak.configureAndroidLibrary
import net.marllex.waselak.configureComposeStability
import net.marllex.waselak.configureKmpTargets
import net.marllex.waselak.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
                apply("com.android.library")
            }

            configureComposeStability()

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpTargets(this)

                val composeDeps = ComposePlugin.Dependencies(project)
                sourceSets.commonMain.dependencies {
                    // Compose
                    implementation(composeDeps.runtime)
                    implementation(composeDeps.foundation)
                    implementation(composeDeps.material3)
                    implementation(composeDeps.materialIconsExtended)
                    implementation(composeDeps.ui)
                    implementation(composeDeps.components.resources)

                    // Core modules
                    implementation(project(":core:core-ui"))
                    implementation(project(":core:core-model"))
                    implementation(project(":core:core-domain"))
                    implementation(project(":core:core-common"))

                    // Navigation
                    implementation(libs.findLibrary("navigation-compose").get())

                    // ViewModel
                    implementation(libs.findLibrary("lifecycle-viewmodel-compose").get())
                    implementation(libs.findLibrary("lifecycle-runtime-compose").get())
                    implementation(libs.findLibrary("lifecycle-viewmodel").get())

                    // Koin
                    implementation(libs.findLibrary("koin-core").get())
                    implementation(libs.findLibrary("koin-compose").get())
                    implementation(libs.findLibrary("koin-compose-viewmodel").get())

                    // Coroutines
                    implementation(libs.findLibrary("kotlinx-coroutines-core").get())

                    // DateTime
                    implementation(libs.findLibrary("kotlinx-datetime").get())

                    // Logging
                    implementation(libs.findLibrary("kermit").get())
                }

                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                    implementation(libs.findLibrary("turbine").get())
                }
            }

            extensions.configure<LibraryExtension> {
                configureAndroidLibrary(this)
            }
        }
    }
}
