package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.asContextElement


object CurrentUser {
    private val contextThreadLocal = ThreadLocal<UserContext>()

    /**
     * Get the current user context
     * @throws IllegalStateException if no user context is available
     */
    val context: UserContext
        get() = contextOrNull
            ?: throw IllegalStateException("No user context available. Ensure the request is authenticated.")

    /**
     * Get the current user context or null if not authenticated
     */
    val contextOrNull: UserContext?
        get() = getCurrentContext()

    /**
     * Check if a user context exists
     */
    val isAuthenticated: Boolean
        get() = contextOrNull != null

    // Convenience properties
    val id: Int get() = context.userId
    val tenantId: Int get() = context.tenantId
    val email: String get() = context.email
    val roles: Set<String> get() = context.roles

    // Convenience methods
    fun hasRole(role: String) = context.hasRole(role)
    fun hasAnyRole(vararg roles: String) = context.hasAnyRole(*roles)
    fun hasAllRoles(vararg roles: String) = context.hasAllRoles(*roles)
    
    // Authorization convenience methods (delegating to extension functions)
    fun owns(resourceOwnerId: Int) = context.owns(resourceOwnerId)
    fun canAccessTenant(resourceTenantId: Int) = context.canAccessTenant(resourceTenantId)
    fun requireRole(role: String, message: String = "Missing required role: $role") = context.requireRole(role, message)
    fun requireAnyRole(vararg roles: String, message: String = "Missing required roles: ${roles.joinToString()}") = context.requireAnyRole(*roles, message = message)
    fun requireOwnership(resourceOwnerId: Int, message: String = "You don't own this resource") = context.requireOwnership(resourceOwnerId, message)

    /**
     * Execute a block with the current user context.
     * Useful for avoiding multiple ThreadLocal lookups.
     * Performance: Caches context to avoid repeated ThreadLocal access
     */
    inline fun <T> use(block: (UserContext) -> T): T {
        val cachedContext = contextOrNull ?: throw IllegalStateException("No user context available. Ensure the request is authenticated.")
        return block(cachedContext)
    }

    /**
     * Execute a block with the current user context or return null
     */
    inline fun <T> useOrNull(block: (UserContext) -> T): T? {
        return contextOrNull?.let(block)
    }

    /**
     * Execute a block if authenticated, otherwise execute the else block
     */
    inline fun <T> ifAuthenticated(
        block: (UserContext) -> T,
        elseBlock: () -> T
    ): T {
        return contextOrNull?.let(block) ?: elseBlock()
    }

    // Internal methods for plugin use
    internal fun set(context: UserContext?) {
        if (context != null) {
            contextThreadLocal.set(context)
        } else {
            contextThreadLocal.remove()
        }
    }

    internal fun asContextElement() = contextThreadLocal.asContextElement()
    
    /**
     * Get current context from ThreadLocal
     * ThreadLocal + asContextElement() ensures proper propagation across coroutines
     */
    private fun getCurrentContext(): UserContext? {
        return contextThreadLocal.get()
    }
}