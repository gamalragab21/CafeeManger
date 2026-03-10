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

// ── Convenience tasks for building all apps ─────────────────────────────────

val appModules = listOf("app-manager", "app-cashier", "app-delivery")

tasks.register("assembleAllDebug") {
    group = "build"
    description = "Assemble debug APKs for all apps (manager, cashier, delivery)"
    dependsOn(appModules.map { ":$it:assembleDebug" })
}

tasks.register("assembleAllRelease") {
    group = "build"
    description = "Assemble release APKs for all apps (manager, cashier, delivery)"
    dependsOn(appModules.map { ":$it:assembleRelease" })
}

tasks.register("packageAllDesktopDmg") {
    group = "build"
    description = "Package macOS DMG installers for all desktop apps"
    dependsOn(appModules.map { ":$it:packageDmg" })
}

tasks.register("packageAllDesktopMsi") {
    group = "build"
    description = "Package Windows MSI installers for all desktop apps"
    dependsOn(appModules.map { ":$it:packageMsi" })
}

tasks.register("packageAllDesktopDeb") {
    group = "build"
    description = "Package Linux DEB packages for all desktop apps"
    dependsOn(appModules.map { ":$it:packageDeb" })
}
