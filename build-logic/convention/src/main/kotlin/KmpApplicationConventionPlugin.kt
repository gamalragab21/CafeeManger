import com.android.build.api.dsl.ApplicationExtension
import net.marllex.waselak.configureKmpTargets
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

class KmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
                apply("com.android.application")
            }

            // ── Determine build environment (debug or release) ──────────────
            val buildEnv = resolveBuildEnv()
            val isDebug = buildEnv == "debug"

            logger.lifecycle("┌─────────────────────────────────────────────")
            logger.lifecycle("│ Build Environment: $buildEnv")
            logger.lifecycle("│ IS_DEBUG: $isDebug")

            // ── Load environment properties ─────────────────────────────────
            val envProps = loadEnvProperties(buildEnv)
            val baseUrl = envProps.getProperty("BASE_URL")
                ?: project.findProperty("BASE_URL") as? String
                ?: "https://api.waselak.net"
            val hmacSecret = envProps.getProperty("HMAC_SECRET")
                ?: project.findProperty("HMAC_SECRET") as? String
                ?: ""
            val sentryDsn = envProps.getProperty("SENTRY_DSN")
                ?: project.findProperty("SENTRY_DSN") as? String
                ?: ""

            // ── Load centralized version ──────────────────────────────────
            val appVersionName = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            val appVersionCode = (project.findProperty("APP_VERSION_CODE") as? String)?.toIntOrNull() ?: 1

            logger.lifecycle("│ BASE_URL: $baseUrl")
            logger.lifecycle("│ VERSION: $appVersionName ($appVersionCode)")
            logger.lifecycle("└─────────────────────────────────────────────")

            // ── Generate BuildConfig ────────────────────────────────────────
            val buildConfigDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")

            val generateBuildConfig = tasks.register("generateBuildConfig") {
                val outputDir = buildConfigDir
                outputs.dir(outputDir)
                inputs.property("baseUrl", baseUrl)
                inputs.property("hmacSecret", hmacSecret)
                inputs.property("sentryDsn", sentryDsn)
                inputs.property("isDebug", isDebug)
                inputs.property("appVersionName", appVersionName)
                inputs.property("appVersionCode", appVersionCode)

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
                        |    const val SENTRY_DSN: String = "$sentryDsn"
                        |    const val IS_DEBUG: Boolean = $isDebug
                        |    const val VERSION_NAME: String = "$appVersionName"
                        |    const val VERSION_CODE: Int = $appVersionCode
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
                    versionCode = appVersionCode
                    versionName = appVersionName
                }

                // ── Release signing config ──────────────────────────────────
                // Priority:
                //   1. keystore/keystore.properties (local development)
                //   2. KEYSTORE_BASE64 env var (CI — auto-decode + hardcoded creds)
                val keystorePropsFile = rootProject.file("keystore/keystore.properties")
                val keystoreBase64 = System.getenv("KEYSTORE_BASE64")

                val signingReady = when {
                    // ── Local dev: read from properties file ──
                    keystorePropsFile.exists() -> {
                        val keystoreProps = Properties()
                        keystorePropsFile.inputStream().use { keystoreProps.load(it) }

                        signingConfigs {
                            create("release") {
                                storeFile = rootProject.file(keystoreProps.getProperty("STORE_FILE"))
                                storePassword = keystoreProps.getProperty("STORE_PASSWORD")
                                keyAlias = keystoreProps.getProperty("KEY_ALIAS")
                                keyPassword = keystoreProps.getProperty("KEY_PASSWORD")
                            }
                        }
                        true
                    }
                    // ── CI: decode base64 keystore from env var ──
                    !keystoreBase64.isNullOrBlank() -> {
                        val keystoreDir = rootProject.file("keystore")
                        if (!keystoreDir.exists()) keystoreDir.mkdirs()
                        val keystoreFile = keystoreDir.resolve("waselak-release.jks")
                        keystoreFile.writeBytes(
                            java.util.Base64.getDecoder().decode(keystoreBase64)
                        )
                        logger.lifecycle("Decoded KEYSTORE_BASE64 → ${keystoreFile.absolutePath} (${keystoreFile.length()} bytes)")

                        signingConfigs {
                            create("release") {
                                storeFile = keystoreFile
                                storePassword = "waselak2024"
                                keyAlias = "waselak"
                                keyPassword = "waselak2024"
                            }
                        }
                        true
                    }
                    else -> {
                        logger.warn("keystore/keystore.properties not found and KEYSTORE_BASE64 not set — release APKs will not be signed")
                        false
                    }
                }

                if (signingReady) {
                    buildTypes {
                        getByName("release") {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }

    /**
     * Determines the build environment:
     * 1. Explicit `-PBUILD_ENV=release` takes highest priority
     * 2. Auto-detect from Gradle task names (Release, packageDmg, packageMsi, packageDeb)
     * 3. Default to "debug"
     */
    private fun Project.resolveBuildEnv(): String {
        // 1. Explicit override via project property
        val explicit = project.findProperty("BUILD_ENV") as? String
        if (explicit != null) return explicit.lowercase()

        // 2. Auto-detect from task names
        val taskNames = gradle.startParameter.taskNames
        val releaseIndicators = listOf(
            "Release", "release",
            "packageDmg", "packageMsi", "packageDeb", "packageRpm",
            "packageUberJarForCurrentOS",
        )
        val isRelease = taskNames.any { task ->
            releaseIndicators.any { indicator -> task.contains(indicator) }
        }
        return if (isRelease) "release" else "debug"
    }

    /**
     * Loads properties from env/{buildEnv}.properties.
     * Falls back to an empty Properties if the file doesn't exist.
     */
    private fun Project.loadEnvProperties(buildEnv: String): Properties {
        val props = Properties()
        val envFile = rootProject.file("env/$buildEnv.properties")
        if (envFile.exists()) {
            envFile.inputStream().use { props.load(it) }
        } else {
            logger.warn("⚠️  env/$buildEnv.properties not found — using fallback defaults")
        }
        return props
    }
}
