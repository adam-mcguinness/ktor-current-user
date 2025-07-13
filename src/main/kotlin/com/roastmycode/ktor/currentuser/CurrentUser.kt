package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.asContextElement


object CurrentUser {
    private val contextThreadLocal = ThreadLocal<UserContext>()

    /**
     * Get the current user context
     * @throws IllegalStateException if no user context is available
     */
    val context: UserContext
        get() {
            val ctx = contextOrNull
            if (ctx == null) {
                println("[CurrentUser] ERROR: Attempted to access context but it's NULL!")
                throw IllegalStateException("No user context available. Ensure the request is authenticated.")
            }
            println("[CurrentUser] Context accessed: User(id=${ctx.userId}, email=${ctx.email})")
            return ctx
        }

    /**
     * Get the current user context or null if not authenticated
     */
    val contextOrNull: UserContext?
        get() {
            val ctx = getCurrentContext()
            println("[CurrentUser] contextOrNull accessed: ${ctx?.let { "User(id=${it.userId}, email=${it.email})" } ?: "NULL"}")
            return ctx
        }

    /**
     * Check if a user context exists
     */
    val isAuthenticated: Boolean
        get() {
            val isAuth = contextOrNull != null
            println("[CurrentUser] isAuthenticated check: $isAuth")
            return isAuth
        }

    // Convenience properties
    val id: String get() {
        println("[CurrentUser] id property accessed")
        return context.userId
    }
    val tenantId: Int? get() {
        println("[CurrentUser] tenantId property accessed")
        return context.tenantId
    }
    val email: String? get() {
        println("[CurrentUser] email property accessed")
        return context.email
    }
    val roles: Set<String> get() {
        println("[CurrentUser] roles property accessed")
        return context.roles
    }
    val organizationId: Int? get() = context.organizationId
    val branchId: Int? get() = context.branchId
    val departmentId: String? get() = context.departmentId
    val permissions: Set<String>? get() = context.permissions

    // Convenience methods
    fun hasRole(role: String): Boolean {
        println("[CurrentUser] hasRole('$role') called")
        val result = context.hasRole(role)
        println("[CurrentUser] hasRole('$role') = $result")
        return result
    }
    fun hasAnyRole(vararg roles: String) = context.hasAnyRole(*roles)
    fun hasAllRoles(vararg roles: String) = context.hasAllRoles(*roles)
    fun hasPermission(permission: String) = context.hasPermission(permission)
    fun hasAnyPermission(vararg permissions: String) = context.hasAnyPermission(*permissions)
    fun hasAllPermissions(vararg permissions: String) = context.hasAllPermissions(*permissions)
    
    // Authorization convenience methods (delegating to extension functions)
    fun owns(resourceOwnerId: Int): Boolean {
        println("[CurrentUser] owns($resourceOwnerId) called")
        val result = context.owns(resourceOwnerId)
        println("[CurrentUser] owns($resourceOwnerId) = $result")
        return result
    }
    fun owns(resourceOwnerId: String): Boolean {
        println("[CurrentUser] owns($resourceOwnerId) called")
        val result = context.owns(resourceOwnerId)
        println("[CurrentUser] owns($resourceOwnerId) = $result")
        return result
    }
    fun canAccessTenant(resourceTenantId: Int) = context.canAccessTenant(resourceTenantId)
    fun requireRole(role: String, message: String = "Missing required role: $role") {
        println("[CurrentUser] requireRole('$role') called")
        context.requireRole(role, message)
        println("[CurrentUser] requireRole('$role') passed")
    }
    fun requireAnyRole(vararg roles: String, message: String = "Missing required roles: ${roles.joinToString()}") = context.requireAnyRole(*roles, message = message)
    fun requireOwnership(resourceOwnerId: Int, message: String = "You don't own this resource") = context.requireOwnership(resourceOwnerId, message)
    fun requireOwnership(resourceOwnerId: String, message: String = "You don't own this resource") = context.requireOwnership(resourceOwnerId, message)

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
        println("[CurrentUser] Setting user context: ${context?.let { "User(id=${it.userId}, email=${it.email})" } ?: "NULL"}")
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
        val ctx = contextThreadLocal.get()
        // Don't log here to avoid infinite recursion with contextOrNull
        return ctx
    }
}