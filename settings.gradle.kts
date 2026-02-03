pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CafeeManger"

// Legacy app module (will be removed after migration)
include(":app")

// Application modules
include(":app-manager")
include(":app-cashier")
include(":app-delivery")

// Core modules
include(":core:core-common")
include(":core:core-model")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-database")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-auth")

// Feature modules - Shared
include(":feature:feature-auth")
include(":feature:feature-orders")
include(":feature:feature-menu")
include(":feature:feature-profile")

// Feature modules - Manager App
include(":feature:feature-manager-dashboard")
include(":feature:feature-manager-categories")
include(":feature:feature-manager-items")
include(":feature:feature-manager-tables")
include(":feature:feature-manager-users")
include(":feature:feature-manager-analytics")
include(":feature:feature-manager-orders")

// Feature modules - Cashier App
include(":feature:feature-cashier-pos")
include(":feature:feature-cashier-payment")
include(":feature:feature-cashier-receipt")

// Feature modules - Delivery App
include(":feature:feature-delivery-orders")
include(":feature:feature-delivery-navigation")
include(":feature:feature-delivery-status")
