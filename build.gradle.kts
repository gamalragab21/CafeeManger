// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ksp) apply false
}

// ── App module lists ────────────────────────────────────────────────────────

/** All apps that produce Android APKs */
val androidAppModules = listOf("app-manager", "app-cashier", "app-delivery", "app-kds", "app-admin")

/** Apps that produce desktop installers (DMG/MSI/DEB) — app-admin is Android-only */
val desktopAppModules = listOf("app-manager", "app-cashier", "app-delivery", "app-kds")

// ── Android-only tasks ──────────────────────────────────────────────────────

tasks.register("assembleAllDebug") {
    group = "build"
    description = "Assemble debug APKs for all Android apps"
    dependsOn(androidAppModules.map { ":$it:assembleDebug" })
}

tasks.register("assembleAllRelease") {
    group = "build"
    description = "Assemble release APKs for all Android apps"
    dependsOn(androidAppModules.map { ":$it:assembleRelease" })
}

// ── Desktop-only tasks ──────────────────────────────────────────────────────

tasks.register("packageAllDesktopDmg") {
    group = "build"
    description = "Package macOS DMG installers for all desktop apps"
    dependsOn(desktopAppModules.map { ":$it:packageDmg" })
}

tasks.register("packageAllDesktopMsi") {
    group = "build"
    description = "Package Windows MSI installers for all desktop apps"
    dependsOn(desktopAppModules.map { ":$it:packageMsi" })
}

tasks.register("packageAllDesktopDeb") {
    group = "build"
    description = "Package Linux DEB packages for all desktop apps"
    dependsOn(desktopAppModules.map { ":$it:packageDeb" })
}

// ── Desktop cross-platform JARs (can build from any OS) ──────────────────────
// Usage: ./gradlew packageAllDesktopJars -PtargetOs=windows
//        ./gradlew packageAllDesktopJars -PtargetOs=linux
//        ./gradlew packageAllDesktopJars  (defaults to current OS)

tasks.register("packageAllDesktopJars") {
    group = "build"
    description = "Package uber JARs for all desktop apps (use -PtargetOs=windows|linux|macos-x64|macos-arm64)"
    dependsOn(desktopAppModules.map { ":$it:packageUberJarForCurrentOS" })
}

// ── Combined: Debug (Android APKs + Desktop packages, debug env) ────────────

tasks.register("buildAllDebug") {
    group = "build"
    description = "Build ALL debug artifacts: Android debug APKs + Desktop debug packages (current OS)"
    dependsOn(androidAppModules.map { ":$it:assembleDebug" })
    dependsOn(desktopAppModules.map { ":$it:packageDistributionForCurrentOS" })
}

// ── Combined: Release (Android APKs + Desktop packages, release env) ────────

tasks.register("buildAllRelease") {
    group = "build"
    description = "Build ALL release artifacts: Android release APKs + Desktop release packages (current OS)"
    dependsOn(androidAppModules.map { ":$it:assembleRelease" })
    dependsOn(desktopAppModules.map { ":$it:packageReleaseDistributionForCurrentOS" })
}

// ── Deploy: Build + Upload to Google Drive ───────────────────────────────────

// Deploy ALL (Android + Desktop)
tasks.register<Exec>("deployAllDebug") {
    group = "deploy"
    description = "Build all debug artifacts (Android + Desktop) and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "debug", "all")
}

tasks.register<Exec>("deployAllRelease") {
    group = "deploy"
    description = "Build all release artifacts (Android + Desktop) and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "release", "all")
}

// Deploy Android only
tasks.register<Exec>("deployAndroidDebug") {
    group = "deploy"
    description = "Build Android debug APKs and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "debug", "android")
}

tasks.register<Exec>("deployAndroidRelease") {
    group = "deploy"
    description = "Build Android release APKs and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "release", "android")
}

// Deploy Desktop only
tasks.register<Exec>("deployDesktopDebug") {
    group = "deploy"
    description = "Build Desktop debug packages and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "debug", "desktop")
}

tasks.register<Exec>("deployDesktopRelease") {
    group = "deploy"
    description = "Build Desktop release packages and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "release", "desktop")
}
