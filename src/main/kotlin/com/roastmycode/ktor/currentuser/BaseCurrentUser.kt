package com.roastmycode.ktor.currentuser

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Base class for user-defined CurrentUser objects.
 * Provides property delegates for easy access to extracted JWT claims.
 */
abstract class BaseCurrentUser {
    internal val contextHolder = UserContextHolder()
    
    /**
     * Get the current user context
     */
    val context: UserContext? 
        get() = contextHolder.get()
    
    /**
     * Set the current user context (internal use)
     */
    internal fun set(context: UserContext?) {
        contextHolder.set(context)
    }
    
    /**
     * Get as coroutine context element
     */
    fun asContextElement() = contextHolder.asContextElement()
    
    /**
     * Check if user is authenticated
     */
    val isAuthenticated: Boolean
        get() = context != null
    
    /**
     * Get user ID (for backward compatibility)
     */
    val id: Int
        get() = context?.userId ?: throw UnauthorizedException("User not authenticated")
    
    /**
     * Get user email (for backward compatibility)
     */
    val email: String
        get() = context?.email ?: throw UnauthorizedException("User not authenticated")
    
    /**
     * Get user roles (for backward compatibility)
     */
    val roles: Set<String>
        get() = context?.roles ?: emptySet()
    
    /**
     * Property delegate for String properties
     */
    protected fun string(): ReadOnlyProperty<Any?, String> = PropertyDelegate()
    
    /**
     * Property delegate for Int properties
     */
    protected fun int(): ReadOnlyProperty<Any?, Int> = PropertyDelegate()
    
    /**
     * Property delegate for Long properties
     */
    protected fun long(): ReadOnlyProperty<Any?, Long> = PropertyDelegate()
    
    /**
     * Property delegate for Boolean properties
     */
    protected fun boolean(): ReadOnlyProperty<Any?, Boolean> = PropertyDelegate()
    
    /**
     * Property delegate for typed properties (serializable objects)
     */
    protected fun <T> typed(): ReadOnlyProperty<Any?, T> = PropertyDelegate()
    
    /**
     * Property delegate for nullable String properties
     */
    protected fun stringOrNull(): ReadOnlyProperty<Any?, String?> = NullablePropertyDelegate()
    
    /**
     * Property delegate for nullable Int properties
     */
    protected fun intOrNull(): ReadOnlyProperty<Any?, Int?> = NullablePropertyDelegate()
    
    /**
     * Property delegate for nullable Long properties
     */
    protected fun longOrNull(): ReadOnlyProperty<Any?, Long?> = NullablePropertyDelegate()
    
    /**
     * Property delegate for nullable Boolean properties
     */
    protected fun booleanOrNull(): ReadOnlyProperty<Any?, Boolean?> = NullablePropertyDelegate()
    
    /**
     * Property delegate for nullable typed properties
     */
    protected fun <T> typedOrNull(): ReadOnlyProperty<Any?, T?> = NullablePropertyDelegate()
    
    /**
     * Check if user has a specific role
     */
    fun hasRole(role: String): Boolean = context?.hasRole(role) ?: false
    
    /**
     * Check if user has any of the specified roles
     */
    fun hasAnyRole(vararg roles: String): Boolean = context?.hasAnyRole(*roles) ?: false
    
    /**
     * Check if user has all of the specified roles
     */
    fun hasAllRoles(vararg roles: String): Boolean = context?.hasAllRoles(*roles) ?: false
    
    /**
     * Get a property value by name
     */
    fun <T> getProperty(name: String): T? = context?.getProperty(name)
    
    /**
     * Property delegate implementation
     */
    private inner class PropertyDelegate<T> : ReadOnlyProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            val value = context?.properties?.get(property.name)
                ?: throw UnauthorizedException("User not authenticated or property '${property.name}' not found")
            
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
    }
    
    /**
     * Nullable property delegate implementation
     */
    private inner class NullablePropertyDelegate<T> : ReadOnlyProperty<Any?, T?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            @Suppress("UNCHECKED_CAST")
            return context?.properties?.get(property.name) as? T
        }
    }
}