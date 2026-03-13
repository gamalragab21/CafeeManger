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

tasks.register<Exec>("deployAllDebug") {
    group = "deploy"
    description = "Build all debug artifacts and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "debug")
}

tasks.register<Exec>("deployAllRelease") {
    group = "deploy"
    description = "Build all release artifacts and upload to Google Drive"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-drive.sh", "release")
}
