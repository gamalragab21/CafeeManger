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

// ── Backend ─────────────────────────────────────────────────────────────────

tasks.register("buildBackendJar") {
    group = "build"
    description = "Build backend fat JAR"
    dependsOn(":backend:buildFatJar")
    doLast {
        val jarDir = file("build/deploy")
        jarDir.mkdirs()
        val sourceJar = fileTree("backend/build/libs").matching { include("*-all.jar") }.singleFile
        sourceJar.copyTo(file("build/deploy/waselak-backend.jar"), overwrite = true)
        println("✅ Backend JAR: build/deploy/waselak-backend.jar (${sourceJar.length() / 1024 / 1024}MB)")
    }
}

// ── Deploy: Build + Upload to Dropbox ───────────────────────────────────────

// Deploy ALL (Android + Desktop) to Dropbox
tasks.register<Exec>("deployAllDebug") {
    group = "deploy"
    description = "Build all debug artifacts (Android + Desktop) and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "debug", "all")
}

tasks.register<Exec>("deployAllRelease") {
    group = "deploy"
    description = "Build all release artifacts (Android + Desktop) and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "release", "all")
}

// Deploy Android only to Dropbox
tasks.register<Exec>("deployAndroidDebug") {
    group = "deploy"
    description = "Build Android debug APKs and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "debug", "android")
}

tasks.register<Exec>("deployAndroidRelease") {
    group = "deploy"
    description = "Build Android release APKs and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "release", "android")
}

// Deploy Desktop only to Dropbox
tasks.register<Exec>("deployDesktopDebug") {
    group = "deploy"
    description = "Build Desktop debug packages and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "debug", "desktop")
}

tasks.register<Exec>("deployDesktopRelease") {
    group = "deploy"
    description = "Build Desktop release packages and upload to Dropbox"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "release", "desktop")
}

// Upload pre-built artifacts to Dropbox (skip build)
tasks.register<Exec>("uploadToDropbox") {
    group = "deploy"
    description = "Upload pre-built artifacts to Dropbox (skip build)"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-to-dropbox.sh", "upload-only", "release")
}

// ── VPS Backend Deploy ──────────────────────────────────────────────────────

tasks.register<Exec>("deployBackendToVPS") {
    group = "deploy"
    description = "Build backend JAR and deploy to VPS"
    workingDir = rootDir
    commandLine("bash", "scripts/deploy-backend-vps.sh")
}

tasks.register<Exec>("vpsStatus") {
    group = "deploy"
    description = "Check VPS backend health and current version"
    workingDir = rootDir
    commandLine("bash", "-c", """
        echo "=== VPS Release Backend (8080) ==="
        curl -s -m 5 http://187.124.47.222:8080/health 2>/dev/null || echo "❌ Not reachable"
        echo ""
        echo "=== VPS Debug Backend (8081) ==="
        curl -s -m 5 http://187.124.47.222:8081/health 2>/dev/null || echo "❌ Not reachable"
        echo ""
        echo "=== Current Version ==="
        ssh -i ~/.ssh/id_ed25519_hostinger -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@187.124.47.222 "cat /opt/waselak/CURRENT_VERSION 2>/dev/null || echo 'Unknown'" 2>/dev/null || echo "SSH not available"
    """.trimIndent())
}

tasks.register<Exec>("vpsRestart") {
    group = "deploy"
    description = "Restart VPS backend services"
    workingDir = rootDir
    commandLine("bash", "-c", """
        echo "Restarting VPS services..."
        ssh -i ~/.ssh/id_ed25519_hostinger -o StrictHostKeyChecking=no -o ConnectTimeout=10 root@187.124.47.222 "systemctl restart waselak-release waselak-debug && sleep 10 && curl -s http://localhost:8080/health && echo '' && curl -s http://localhost:8081/health" 2>&1
        echo ""
        echo "✅ VPS services restarted"
    """.trimIndent())
}

// ── Deploy Everything ───────────────────────────────────────────────────────

tasks.register("deployAll") {
    group = "deploy"
    description = "Build everything (Android + Desktop + Backend) and deploy to Dropbox + VPS"
    dependsOn("buildAllRelease", "buildBackendJar")
    finalizedBy("uploadToDropbox", "deployBackendToVPS")
}
