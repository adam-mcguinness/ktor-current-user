package com.roastmycode.ktor.currentuser

import kotlin.reflect.KProperty

/**
 * Data class representing the authenticated user context with dynamic properties.
 */
data class UserContext(
    val userId: Int,
    val email: String,
    val roles: Set<String> = emptySet(),
    val properties: Map<String, Any?> = emptyMap()
) {
    /**
     * Check if the user has a specific role
     */
    fun hasRole(role: String): Boolean = roles.contains(role)

    /**
     * Check if the user has any of the specified roles
     */
    fun hasAnyRole(vararg roles: String): Boolean = roles.any { this.roles.contains(it) }

    /**
     * Check if the user has all of the specified roles
     */
    fun hasAllRoles(vararg roles: String): Boolean = roles.all { this.roles.contains(it) }

    /**
     * Get a property value by key with type safety
     */
    inline fun <reified T> get(key: String): T? = properties[key] as? T

    /**
     * Get a property value or throw if not found
     */
    inline fun <reified T> require(key: String): T = get<T>(key)
        ?: throw IllegalStateException("Required property '$key' not found in user context")

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