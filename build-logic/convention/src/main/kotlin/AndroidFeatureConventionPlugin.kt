import com.android.build.gradle.LibraryExtension
import net.marllex.cafeemanger.configureAndroidCompose
import net.marllex.cafeemanger.configureKotlinAndroid
import net.marllex.cafeemanger.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("cafeemanger.android.hilt")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                configureAndroidCompose(this)
                defaultConfig.targetSdk = 35
            }

            dependencies {
                add("implementation", project(":core:core-ui"))
                add("implementation", project(":core:core-model"))
                add("implementation", project(":core:core-domain"))
                add("implementation", project(":core:core-common"))

                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
            }
        }
    }
}
