package net.marllex.cafeemanger.backend.api.middleware

import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import net.marllex.cafeemanger.backend.plugins.UserPrincipal

fun RoutingContext.requireRole(vararg allowedRoles: String): UserPrincipal {
    val principal = call.principal<UserPrincipal>()
        ?: throw SecurityException("Authentication required")

    if (principal.role !in allowedRoles) {
        throw SecurityException("Insufficient permissions. Required: ${allowedRoles.joinToString()}")
    }

    return principal
}

fun RoutingContext.currentUser(): UserPrincipal {
    return call.principal<UserPrincipal>()
        ?: throw SecurityException("Authentication required")
}
