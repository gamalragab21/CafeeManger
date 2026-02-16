import com.android.build.gradle.LibraryExtension
import net.marllex.waselak.configureAndroidLibrary
import net.marllex.waselak.configureKmpTargets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpTargets(this)
            }

            extensions.configure<LibraryExtension> {
                configureAndroidLibrary(this)
            }
        }
    }
}
