// Standalone settings file for the backend Ktor project.
// Without this, gradle walks up the filesystem and picks up the parent
// CafeeManger root settings.gradle.kts (Android multi-module). That pulls
// the Android plugin which we don't need here — and downloading it fails
// when the network blocks plugins.gradle.org. This file scopes the build
// to the backend directory so we never traverse into :app, :build-logic, etc.
//
// The Dockerfile copies only the backend/ folder so Docker builds were
// already standalone; this just gives local builds the same isolation.
// Match the name the project has when invoked via `:backend:buildFatJar` from
// the parent Waselak settings — keeps the output JAR named `backend-all.jar`,
// which is what scripts/deploy-backend-vps.sh and the Dockerfile both expect.
rootProject.name = "backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}
