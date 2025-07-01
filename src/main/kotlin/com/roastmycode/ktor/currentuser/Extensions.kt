package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.withContext


suspend fun <T> withUserContext(userContext: UserContext, block: suspend () -> T): T {
    CurrentUser.set(userContext)
    return try {
        withContext(CurrentUser.asContextElement()) {
            block()
        }
    } finally {
        CurrentUser.set(null)
    }
}

/**
 * Check if the current user owns a resource
 */
fun UserContext.owns(resourceOwnerId: Int): Boolean = userId == resourceOwnerId

/**
 * Check if user can access a tenant's resource
 */
fun UserContext.canAccessTenant(resourceTenantId: Int): Boolean =
    tenantId == resourceTenantId || hasRole("SUPER_ADMIN")

/**
 * Require a specific role or throw
 */
fun UserContext.requireRole(role: String, message: String = "Missing required role: $role") {
    if (!hasRole(role)) {
        throw ForbiddenException(message)
    }
}

/**
 * Require any of the specified roles or throw
 */
fun UserContext.requireAnyRole(vararg roles: String, message: String = "Missing required roles: ${roles.joinToString()}") {
    if (!hasAnyRole(*roles)) {
        throw ForbiddenException(message)
    }
}

/**
 * Require ownership of a resource or throw
 */
fun UserContext.requireOwnership(resourceOwnerId: Int, message: String = "You don't own this resource") {
    if (!owns(resourceOwnerId)) {
        throw ForbiddenException(message)
    }
}