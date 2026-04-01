plugins {
    alias(libs.plugins.waselak.kmp.application)
    alias(libs.plugins.waselak.koin)
}

android {
    namespace = "net.marllex.waselak.cashier"

    defaultConfig {
        applicationId = "net.marllex.waselak.cashier"
        // versionCode and versionName are set centrally via gradle.properties → KmpApplicationConventionPlugin

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
        mainClass = "net.marllex.waselak.cashier.MainKt"

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
            includeAllModules = true
            modules("jdk.unsupported", "java.sql", "java.naming", "java.management", "java.instrument", "java.scripting", "java.compiler", "java.logging", "java.xml", "java.desktop", "java.security.jgss", "java.security.sasl", "java.datatransfer", "java.prefs", "java.net.http")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            val isDebugDesktop = (project.findProperty("BUILD_ENV") as? String)?.lowercase() == "debug"
            packageName = if (isDebugDesktop) "Waselak Cashier Debug" else "Waselak Cashier"
            packageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            vendor = "Marllex"
            description = if (isDebugDesktop) "Waselak Cashier (Debug)" else "Waselak Restaurant Cashier"

            macOS {
                bundleID = "net.marllex.waselak.cashier"
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
                dockName = "Waselak Cashier"
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
                upgradeUuid = if (isDebugDesktop) "b2c3d4e5-f6a7-8901-bcde-f12345678902" else "b2c3d4e5-f6a7-8901-bcde-f12345678901"
                msiPackageVersion = project.findProperty("APP_VERSION_NAME") as? String ?: "1.0.0"
            }
            linux {
                val png = project.file("src/desktopMain/resources/icon.png")
                if (png.exists()) iconFile.set(png)
                packageName = "waselak-cashier"
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
            implementation(project(":feature:feature-manager-orders"))
            implementation(project(":feature:feature-cashier-pos"))
            implementation(project(":feature:feature-cashier-payment"))
            implementation(project(":feature:feature-cashier-receipt"))
            implementation(project(":feature:feature-cashier-attendance"))
            implementation(project(":feature:feature-manager-tables"))
            implementation(project(":feature:feature-manager-staff"))

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
