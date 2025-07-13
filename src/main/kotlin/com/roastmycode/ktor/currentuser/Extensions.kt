package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.withContext


suspend fun <T> withUserContext(userContext: UserContext, block: suspend () -> T): T {
    println("[withUserContext] Setting up user context for block execution: User(id=${userContext.userId}, email=${userContext.email})")
    CurrentUser.set(userContext)
    return try {
        withContext(CurrentUser.asContextElement()) {
            println("[withUserContext] Executing block with user context")
            block()
        }
    } finally {
        println("[withUserContext] Cleaning up user context")
        CurrentUser.set(null)
    }
}

/**
 * Check if the current user owns a resource
 */
fun UserContext.owns(resourceOwnerId: Int): Boolean {
    val result = userId == resourceOwnerId.toString()
    println("[UserContext.ext] owns($resourceOwnerId): userId=$userId, result=$result")
    return result
}

/**
 * Check if the current user owns a resource (string version)
 */
fun UserContext.owns(resourceOwnerId: String): Boolean {
    val result = userId == resourceOwnerId
    println("[UserContext.ext] owns($resourceOwnerId): userId=$userId, result=$result")
    return result
}

/**
 * Check if user can access a tenant's resource
 */
fun UserContext.canAccessTenant(resourceTenantId: Int): Boolean {
    // If user has no tenantId, they can't access tenant-specific resources (unless SUPER_ADMIN)
    val result = (tenantId != null && tenantId == resourceTenantId) || hasRole("SUPER_ADMIN")
    println("[UserContext.ext] canAccessTenant($resourceTenantId): tenantId=$tenantId, result=$result")
    return result
}

/**
 * Require a specific role or throw
 */
fun UserContext.requireRole(role: String, message: String = "Missing required role: $role") {
    println("[UserContext.ext] requireRole('$role') called")
    if (!hasRole(role)) {
        println("[UserContext.ext] ERROR: User lacks required role '$role'! Throwing ForbiddenException")
        throw ForbiddenException(message)
    }
    println("[UserContext.ext] requireRole('$role') check passed")
}

/**
 * Require any of the specified roles or throw
 */
fun UserContext.requireAnyRole(vararg roles: String, message: String = "Missing required roles: ${roles.joinToString()}") {
    println("[UserContext.ext] requireAnyRole(${roles.joinToString()}) called")
    if (!hasAnyRole(*roles)) {
        println("[UserContext.ext] ERROR: User lacks any of required roles! Throwing ForbiddenException")
        throw ForbiddenException(message)
    }
    println("[UserContext.ext] requireAnyRole check passed")
}

/**
 * Require ownership of a resource or throw
 */
fun UserContext.requireOwnership(resourceOwnerId: Int, message: String = "You don't own this resource") {
    println("[UserContext.ext] requireOwnership($resourceOwnerId) called")
    if (!owns(resourceOwnerId)) {
        println("[UserContext.ext] ERROR: User doesn't own resource! userId=$userId, resourceOwnerId=$resourceOwnerId")
        throw ForbiddenException(message)
    }
    println("[UserContext.ext] requireOwnership($resourceOwnerId) check passed")
}

/**
 * Require ownership of a resource or throw (string version)
 */
fun UserContext.requireOwnership(resourceOwnerId: String, message: String = "You don't own this resource") {
    println("[UserContext.ext] requireOwnership($resourceOwnerId) called")
    if (!owns(resourceOwnerId)) {
        println("[UserContext.ext] ERROR: User doesn't own resource! userId=$userId, resourceOwnerId=$resourceOwnerId")
        throw ForbiddenException(message)
    }
    println("[UserContext.ext] requireOwnership($resourceOwnerId) check passed")
}