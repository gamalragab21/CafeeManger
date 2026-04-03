plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.kds"

    defaultConfig {
        applicationId = "net.marllex.waselak.kds"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "ar"))
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.marllex.waselak.kds.MainKt"

        jvmArgs += listOf(
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-exports", "jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-reads", "jdk.unsupported=ALL-UNNAMED",
        )

        nativeDistributions {

            modules("jdk.unsupported", "java.sql", "java.naming", "java.management", "java.instrument", "java.scripting", "java.compiler", "java.logging", "java.xml", "java.desktop", "java.security.jgss", "java.security.sasl", "java.datatransfer", "java.prefs", "java.net.http", "jdk.crypto.ec", "jdk.crypto.cryptoki", "java.security.jgss", "jdk.localedata", "jdk.accessibility")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            val isDebugDesktop = (project.findProperty("BUILD_ENV") as? String)?.lowercase() == "debug"
            packageName = if (isDebugDesktop) "Waselak KDS Debug" else "Waselak KDS"
            packageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            vendor = "Marllex"
            description = if (isDebugDesktop) "Waselak KDS Debug" else "Waselak Kitchen Display System"

            macOS {
                bundleID = "net.marllex.waselak.kds"
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
                dockName = if (isDebugDesktop) "Waselak KDS Debug" else "Waselak KDS"
                dmgPackageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
                dmgPackageBuildVersion = project.findProperty("APP_VERSION_CODE") as? String ?: "1"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSHighResolutionCapable</key>
                        <true/>
                    """
                }
            }
            windows {
                val ico = project.file("src/desktopMain/resources/icon.ico")
                if (ico.exists()) iconFile.set(ico)
                menuGroup = "Waselak"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = if (isDebugDesktop) "d4e5f6a7-b8c9-0123-defa-234567890124" else "d4e5f6a7-b8c9-0123-defa-234567890123"
                msiPackageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            }
            linux {
                val png = project.file("src/desktopMain/resources/icon.png")
                if (png.exists()) iconFile.set(png)
                packageName = "waselak-kds"
                debMaintainer = "gamalragab217@gmail.com"
                menuGroup = "Waselak"
                appCategory = "Office"
                shortcut = true
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core modules
            implementation(project(":core:core-common"))
            implementation(project(":core:core-model"))
            implementation(project(":core:core-ui"))
            implementation(project(":core:core-network"))
            implementation(project(":core:core-database"))
            implementation(project(":core:core-domain"))
            implementation(project(":core:core-data"))
            implementation(project(":core:core-auth"))

            // Feature modules
            implementation(project(":feature:feature-auth"))

            // Navigation & Lifecycle
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Koin Compose
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            val targetOs = project.findProperty("targetOs")?.toString() ?: "current"
            implementation(
                when (targetOs) {
                    "windows" -> compose.desktop.windows_x64
                    "linux" -> compose.desktop.linux_x64
                    "macos-x64" -> compose.desktop.macos_x64
                    "macos-arm64" -> compose.desktop.macos_arm64
                    else -> compose.desktop.currentOs
                }
            )
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
