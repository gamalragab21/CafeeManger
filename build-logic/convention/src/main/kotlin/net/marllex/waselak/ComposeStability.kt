package net.marllex.waselak

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Hands the Compose compiler the project-wide stability config so it treats our
 * @Serializable data classes (core-model) as stable without having to annotate
 * them or pull a Compose runtime dependency into non-Compose modules.
 *
 * Must be called *after* `org.jetbrains.kotlin.plugin.compose` is applied, since
 * the `composeCompiler` extension is registered by that plugin.
 */
fun Project.configureComposeStability() {
    val stabilityFile = rootProject.layout.projectDirectory.file("compose_stability.conf")
    if (!stabilityFile.asFile.exists()) return

    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(stabilityFile)
        }
    }
}
