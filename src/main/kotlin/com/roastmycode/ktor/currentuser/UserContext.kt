package com.roastmycode.ktor.currentuser

import kotlin.reflect.KProperty

/**
 * Data class representing the authenticated user context with dynamic properties.
 * 
 * @property userId The unique identifier of the user
 * @property tenantId The tenant identifier for multi-tenant applications
 * @property email The user's email address
 * @property roles Set of roles assigned to the user
 * @property organizationId The organization identifier (optional)
 * @property branchId The branch identifier for multi-branch systems (optional)
 * @property departmentId The department identifier (optional)
 * @property permissions Set of granular permissions (optional)
 * @property properties Map of additional custom properties
 */
data class UserContext(
    val userId: String,
    val tenantId: Int? = null,
    val email: String? = null,
    val roles: Set<String> = emptySet(),
    val organizationId: Int? = null,
    val branchId: Int? = null,
    val departmentId: String? = null,
    val permissions: Set<String>? = null,
    val properties: Map<String, Any?> = emptyMap()
) {
    /**
     * Check if the user has a specific role
     */
    fun hasRole(role: String): Boolean {
        val result = roles.contains(role)
        println("[UserContext] hasRole('$role'): roles=$roles, result=$result")
        return result
    }

    /**
     * Check if the user has any of the specified roles
     */
    fun hasAnyRole(vararg roles: String): Boolean {
        val result = roles.any { this.roles.contains(it) }
        println("[UserContext] hasAnyRole(${roles.joinToString()}): userRoles=$this.roles, result=$result")
        return result
    }

    /**
     * Check if the user has all of the specified roles
     */
    fun hasAllRoles(vararg roles: String): Boolean {
        val result = roles.all { this.roles.contains(it) }
        println("[UserContext] hasAllRoles(${roles.joinToString()}): userRoles=$this.roles, result=$result")
        return result
    }

    /**
     * Check if the user has a specific permission
     */
    fun hasPermission(permission: String): Boolean {
        val result = permissions?.contains(permission) ?: false
        println("[UserContext] hasPermission('$permission'): permissions=$permissions, result=$result")
        return result
    }

    /**
     * Check if the user has any of the specified permissions
     */
    fun hasAnyPermission(vararg permissions: String): Boolean = 
        this.permissions?.any { permissions.contains(it) } ?: false

    /**
     * Check if the user has all of the specified permissions
     */
    fun hasAllPermissions(vararg permissions: String): Boolean = 
        this.permissions?.let { perms -> permissions.all { perms.contains(it) } } ?: false

    /**
     * Get a property value by key with type safety
     */
    inline fun <reified T> get(key: String): T? {
        val value = properties[key] as? T
        println("[UserContext] get('$key'): value=$value, type=${T::class.simpleName}")
        return value
    }

    /**
     * Get a property value or throw if not found
     */
    inline fun <reified T> require(key: String): T {
        val value = get<T>(key)
        if (value == null) {
            println("[UserContext] ERROR: Required property '$key' not found!")
            throw IllegalStateException("Required property '$key' not found in user context")
        }
        println("[UserContext] require('$key'): value=$value")
        return value
    }

    /**
     * Property delegate for accessing custom properties
     */
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T? {
        @Suppress("UNCHECKED_CAST")
        return properties[property.name] as? T
    }
}

/**
 * Interface for extracting UserContext from authentication principals
 */
interface UserContextExtractor {
    /**
     * Extract user context from an authentication principal
     * @param principal The authentication principal (e.g., JWTPrincipal)
     * @return UserContext containing user information
     * @throws UserContextExtractionException if extraction fails
     */
    fun extract(principal: Any): UserContext
}