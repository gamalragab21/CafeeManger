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

rootProject.name = "Waselak"

// Application modules
include(":app-manager")
include(":app-cashier")
include(":app-delivery")
include(":backend")

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

// Feature modules - Manager App
include(":feature:feature-manager-dashboard")
include(":feature:feature-manager-categories")
include(":feature:feature-manager-items")
include(":feature:feature-manager-tables")
include(":feature:feature-manager-users")
include(":feature:feature-manager-analytics")
include(":feature:feature-manager-orders")
include(":feature:feature-manager-stock")

// Feature modules - Manager App (Staff)
include(":feature:feature-manager-staff")

// Feature modules - Manager App (Chatbot)
include(":feature:feature-manager-chatbot")

// Feature modules - Cashier App
include(":feature:feature-cashier-pos")
include(":feature:feature-cashier-payment")
include(":feature:feature-cashier-receipt")
include(":feature:feature-cashier-attendance")

// Feature modules - Delivery App
include(":feature:feature-delivery-orders")
include(":feature:feature-delivery-navigation")
include(":feature:feature-delivery-status")
