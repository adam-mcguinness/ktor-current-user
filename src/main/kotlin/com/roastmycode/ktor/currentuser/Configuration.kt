package com.roastmycode.ktor.currentuser

/**
 * DSL property types for configuration
 */
data class StringProperty(val claimPath: String)
data class IntProperty(val claimPath: String)
data class BooleanProperty(val claimPath: String)
data class ListProperty<T>(val claimPath: String, val elementType: kotlin.reflect.KClass<*>)


/**
 * Configuration for UserContext extraction
 */
class UserContextExtractionConfiguration {
    // Core property mappings with defaults for standard Auth0 access token claims
    internal var userIdMapping: PropertyMapping? = SimplePropertyMapping("userId", "sub", String::class)
    internal var emailMapping: PropertyMapping? = null  // Not in standard Auth0 access tokens
    internal var tenantIdMapping: PropertyMapping? = null
    internal var rolesMapping: PropertyMapping? = null  // Auth0 uses "permissions" not "roles"
    
    // Additional common property mappings
    internal var organizationIdMapping: PropertyMapping? = null
    internal var branchIdMapping: PropertyMapping? = null
    internal var departmentIdMapping: PropertyMapping? = null
    internal var permissionsMapping: PropertyMapping? = null

    /**
     * Default tenant ID if not found in claims
     */
    var defaultTenantId: Int? = null

    // Type-safe property assignment methods
    fun string(claimPath: String): StringProperty = StringProperty(claimPath)
    fun int(claimPath: String): IntProperty = IntProperty(claimPath)
    fun boolean(claimPath: String): BooleanProperty = BooleanProperty(claimPath)
    inline fun <reified T> list(claimPath: String): ListProperty<T> = ListProperty(claimPath, T::class)

    // Core property assignments
    var userId: Any? = null
        set(value) {
            field = value
            userIdMapping = when (value) {
                is StringProperty -> SimplePropertyMapping("userId", value.claimPath, String::class)
                is IntProperty -> SimplePropertyMapping("userId", value.claimPath, Int::class)
                null -> null
                else -> throw IllegalArgumentException("userId must be string() or int()")
            }
        }

    var email: StringProperty? = null
        set(value) {
            field = value
            emailMapping = value?.let { SimplePropertyMapping("email", it.claimPath, String::class) }
        }

    var tenantId: IntProperty? = null
        set(value) {
            field = value
            tenantIdMapping = value?.let { SimplePropertyMapping("tenantId", it.claimPath, Int::class) }
        }

    var roles: ListProperty<String>? = null
        set(value) {
            field = value
            rolesMapping = value?.let { SimplePropertyMapping("roles", it.claimPath, List::class) }
        }

    var organizationId: IntProperty? = null
        set(value) {
            field = value
            organizationIdMapping = value?.let { SimplePropertyMapping("organizationId", it.claimPath, Int::class) }
        }

    var branchId: IntProperty? = null
        set(value) {
            field = value
            branchIdMapping = value?.let { SimplePropertyMapping("branchId", it.claimPath, Int::class) }
        }

    var departmentId: StringProperty? = null
        set(value) {
            field = value
            departmentIdMapping = value?.let { SimplePropertyMapping("departmentId", it.claimPath, String::class) }
        }

    var permissions: ListProperty<String>? = null
        set(value) {
            field = value
            permissionsMapping = value?.let { SimplePropertyMapping("permissions", it.claimPath, List::class) }
        }


    
    // Map for custom claim definitions
    internal val customClaims = mutableMapOf<String, PropertyMapping>()
    
    /**
     * Define a custom claim that will be available in UserContext.properties
     * @param name The name to use in UserContext.properties
     * @param property The property definition (string, int, boolean, list, etc.)
     */
    fun customClaim(name: String, property: StringProperty) {
        customClaims[name] = SimplePropertyMapping(name, property.claimPath, String::class)
    }
    
    fun customClaim(name: String, property: IntProperty) {
        customClaims[name] = SimplePropertyMapping(name, property.claimPath, Int::class)
    }
    
    fun customClaim(name: String, property: BooleanProperty) {
        customClaims[name] = SimplePropertyMapping(name, property.claimPath, Boolean::class)
    }
    
    fun <T> customClaim(name: String, property: ListProperty<T>) {
        customClaims[name] = SimplePropertyMapping(name, property.claimPath, List::class)
    }
}

/**
 * Configuration for the UserContext plugin
 */
class UserContextConfiguration {
    /**
     * Configuration for extraction
     */
    val extraction = UserContextExtractionConfiguration()

    /**
     * Custom extractor (overrides extraction configuration)
     */
    var extractor: UserContextExtractor? = null

    /**
     * Whether to throw an exception when accessing context without authentication
     */
    var requireAuthentication: Boolean = false

    /**
     * Callback invoked when user context is successfully extracted
     */
    var onContextExtracted: ((UserContext) -> Unit)? = null

    /**
     * Callback invoked when context extraction fails
     */
    var onExtractionError: ((Throwable) -> Unit)? = null

    /**
     * Configure extraction using DSL
     */
    fun extraction(block: UserContextExtractionConfiguration.() -> Unit) {
        extraction.block()
    }
}