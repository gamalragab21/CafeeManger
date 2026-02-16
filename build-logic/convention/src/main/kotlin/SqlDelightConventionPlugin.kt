import net.marllex.waselak.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SqlDelightConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("app.cash.sqldelight")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(libs.findLibrary("sqldelight-runtime").get())
                    implementation(libs.findLibrary("sqldelight-coroutines").get())
                }

                sourceSets.androidMain.dependencies {
                    implementation(libs.findLibrary("sqldelight-android-driver").get())
                }

                sourceSets.findByName("iosMain")?.dependencies {
                    implementation(libs.findLibrary("sqldelight-native-driver").get())
                }

                sourceSets.findByName("desktopMain")?.dependencies {
                    implementation(libs.findLibrary("sqldelight-sqlite-driver").get())
                }
            }
        }
    }
}
