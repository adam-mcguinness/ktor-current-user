package com.roastmycode.ktor.currentuser

/**
 * DSL property types for configuration
 */
data class StringProperty(val claimPath: String)
data class IntProperty(val claimPath: String)
data class LongProperty(val claimPath: String)
data class BooleanProperty(val claimPath: String)
data class ListProperty<T>(val claimPath: String, val elementType: kotlin.reflect.KClass<*>)
data class SerializableProperty<T>(val claimPath: String, val type: kotlin.reflect.KClass<*>)

/**
 * Object mapping for nested structures
 */
@ConsistentCopyVisibility
data class ObjectMapping internal constructor(
    val claimPath: String,
    internal val properties: Map<String, PropertyMapping>
)

/**
 * Builder for object mappings
 */
class ObjectMappingBuilder(private val claimPath: String) {
    private val properties = mutableMapOf<String, PropertyMapping>()

    var email: StringProperty? = null
        set(value) {
            field = value
            if (value != null) {
                properties["email"] = SimplePropertyMapping("email", "${claimPath}.${value.claimPath}", String::class)
            }
        }

    var scope: StringProperty? = null
        set(value) {
            field = value
            if (value != null) {
                properties["scope"] = SimplePropertyMapping("scope", "${claimPath}.${value.claimPath}", String::class)
            }
        }

    var permissions: ListProperty<String>? = null
        set(value) {
            field = value
            if (value != null) {
                properties["permissions"] = SimplePropertyMapping("permissions", "${claimPath}.${value.claimPath}", List::class)
            }
        }

    var branchId: IntProperty? = null
        set(value) {
            field = value
            if (value != null) {
                properties["branchId"] = SimplePropertyMapping("branchId", "${claimPath}.${value.claimPath}", Int::class)
            }
        }

    var department: StringProperty? = null
        set(value) {
            field = value
            if (value != null) {
                properties["department"] = SimplePropertyMapping("department", "${claimPath}.${value.claimPath}", String::class)
            }
        }

    // Generic property setter for custom properties
    fun setProperty(name: String, property: StringProperty) {
        properties[name] = SimplePropertyMapping(name, "${claimPath}.${property.claimPath}", String::class)
    }

    fun setProperty(name: String, property: IntProperty) {
        properties[name] = SimplePropertyMapping(name, "${claimPath}.${property.claimPath}", Int::class)
    }

    fun setProperty(name: String, property: LongProperty) {
        properties[name] = SimplePropertyMapping(name, "${claimPath}.${property.claimPath}", Long::class)
    }

    fun setProperty(name: String, property: BooleanProperty) {
        properties[name] = SimplePropertyMapping(name, "${claimPath}.${property.claimPath}", Boolean::class)
    }

    fun <T> setProperty(name: String, property: ListProperty<T>) {
        properties[name] = SimplePropertyMapping(name, "${claimPath}.${property.claimPath}", List::class)
    }

    fun build(): ObjectMapping = ObjectMapping(claimPath, properties.toMap())
}

/**
 * Configuration for UserContext extraction
 */
class UserContextExtractionConfiguration {
    // Core property mappings with defaults for standard JWT claims
    internal var userIdMapping: PropertyMapping? = SimplePropertyMapping("userId", "sub", String::class)
    internal var emailMapping: PropertyMapping? = SimplePropertyMapping("email", "email", String::class)
    internal var rolesMapping: PropertyMapping? = SimplePropertyMapping("roles", "roles", List::class)
    
    // Object mappings for nested structures
    internal val objectMappings = mutableMapOf<String, ObjectMapping>()

    // Type-safe property assignment methods
    fun string(claimPath: String): StringProperty = StringProperty(claimPath)
    fun int(claimPath: String): IntProperty = IntProperty(claimPath)
    fun long(claimPath: String): LongProperty = LongProperty(claimPath)
    fun boolean(claimPath: String): BooleanProperty = BooleanProperty(claimPath)
    inline fun <reified T> list(claimPath: String): ListProperty<T> = ListProperty(claimPath, T::class)
    inline fun <reified T> serializable(claimPath: String): SerializableProperty<T> = SerializableProperty(claimPath, T::class)
    
    fun `object`(claimPath: String, block: ObjectMappingBuilder.() -> Unit): ObjectMapping {
        val builder = ObjectMappingBuilder(claimPath)
        builder.block()
        val mapping = builder.build()
        objectMappings[claimPath] = mapping
        return mapping
    }

    // Core property assignments
    var userId: StringProperty? = null
        set(value) {
            field = value
            userIdMapping = value?.let { SimplePropertyMapping("userId", it.claimPath, String::class) }
        }

    var email: StringProperty? = null
        set(value) {
            field = value
            emailMapping = value?.let { SimplePropertyMapping("email", it.claimPath, String::class) }
        }

    var roles: ListProperty<String>? = null
        set(value) {
            field = value
            rolesMapping = value?.let { SimplePropertyMapping("roles", it.claimPath, List::class) }
        }

    // Object property assignments - can be either ObjectMapping or SerializableProperty
    private var _userMetadata: Any? = null
    var userMetadata: Any?
        get() = _userMetadata
        set(value) {
            _userMetadata = value
            when (value) {
                is ObjectMapping -> objectMappings["userMetadata"] = value
                is SerializableProperty<*> -> {
                    val mapping = SerializablePropertyMapping("userMetadata", value.claimPath)
                    customProperties.add(mapping)
                }
            }
        }

    private var _appMetadata: Any? = null
    var appMetadata: Any?
        get() = _appMetadata
        set(value) {
            _appMetadata = value
            when (value) {
                is ObjectMapping -> objectMappings["appMetadata"] = value
                is SerializableProperty<*> -> {
                    val mapping = SerializablePropertyMapping("appMetadata", value.claimPath)
                    customProperties.add(mapping)
                }
            }
        }

    // Additional custom properties
    internal val customProperties = mutableListOf<PropertyMapping>()
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