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

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpTargets(this)

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
