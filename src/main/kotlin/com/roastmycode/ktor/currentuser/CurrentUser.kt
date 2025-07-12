package com.roastmycode.ktor.currentuser

/**
 * Default CurrentUser implementation for backward compatibility.
 * Provides access to standard user properties (id, email, roles).
 * 
 * For custom properties, create your own CurrentUser object extending BaseCurrentUser.
 */
object CurrentUser : BaseCurrentUser() {
    // Authorization convenience methods (delegating to extension functions)
    fun owns(resourceOwnerId: Int) = context?.owns(resourceOwnerId) ?: false
    
    fun requireRole(role: String, message: String = "Missing required role: $role") {
        context?.requireRole(role, message) ?: throw UnauthorizedException("User not authenticated")
    }
    
    fun requireAnyRole(vararg roles: String, message: String = "Missing required roles: ${roles.joinToString()}") {
        context?.requireAnyRole(*roles, message = message) ?: throw UnauthorizedException("User not authenticated")
    }
    
    fun requireOwnership(resourceOwnerId: Int, message: String = "You don't own this resource") {
        context?.requireOwnership(resourceOwnerId, message) ?: throw UnauthorizedException("User not authenticated")
    }

    /**
     * Execute a block with the current user context.
     * Useful for avoiding multiple ThreadLocal lookups.
     */
    inline fun <T> use(block: (UserContext) -> T): T {
        val cachedContext = context ?: throw UnauthorizedException("User not authenticated")
        return block(cachedContext)
    }

    /**
     * Execute a block with the current user context or return null
     */
    inline fun <T> useOrNull(block: (UserContext) -> T): T? {
        return context?.let(block)
    }

    /**
     * Execute a block if authenticated, otherwise execute the else block
     */
    inline fun <T> ifAuthenticated(
        block: (UserContext) -> T,
        elseBlock: () -> T
    ): T {
        return context?.let(block) ?: elseBlock()
    }
}