package com.roastmycode.ktor.currentuser

import com.auth0.jwt.interfaces.Payload
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Base class for property mapping implementations
 */
internal sealed class PropertyMapping {
    abstract val propertyName: String
    abstract fun extract(payloadMap: Map<String, Any?>): Any?
}

/**
 * Simple property mapping for the DSL
 */
internal class SimplePropertyMapping(
    override val propertyName: String,
    val claimPath: String,
    val targetType: kotlin.reflect.KClass<*>
) : PropertyMapping() {
    override fun extract(payloadMap: Map<String, Any?>): Any? {
        // Security: Validate claim path to prevent injection attacks
        validateClaimPath(claimPath)
        
        val parts = claimPath.split(".")
        var current: Any? = payloadMap
        
        // Security: Limit traversal depth to prevent DoS
        if (parts.size > 10) {
            throw UserContextExtractionException("Claim path too deep: ${parts.size} levels")
        }
        
        for (part in parts) {
            // Security: Validate each path component
            if (part.isEmpty() || part.length > 100) {
                throw UserContextExtractionException("Invalid claim path component: '$part'")
            }
            if (!part.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*$")) && !part.startsWith("https://")) {
                throw UserContextExtractionException("Invalid claim path format: '$part'")
            }
            
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }
        
        return convertToType(current, targetType)
    }
    
    private fun validateClaimPath(path: String) {
        if (path.isEmpty() || path.length > 500) {
            throw UserContextExtractionException("Invalid claim path length: ${path.length}")
        }
        // Prevent path traversal attempts
        if (path.contains("..") || path.contains("//")) {
            throw UserContextExtractionException("Path traversal attempt detected: $path")
        }
    }
    
    private fun convertToType(value: Any?, targetType: kotlin.reflect.KClass<*>): Any? {
        if (value == null) return null
        
        return try {
            when (targetType) {
                String::class -> {
                    val stringValue = value.toString()
                    // Security: Validate string length to prevent DoS attacks
                    if (stringValue.length > 1000) {
                        throw IllegalArgumentException("String claim too long: ${stringValue.length} characters")
                    }
                    stringValue
                }
                Int::class -> when (value) {
                    is Number -> {
                        val intValue = value.toInt()
                        // Security: Validate reasonable integer ranges
                        if (intValue < 0 || intValue > Int.MAX_VALUE) {
                            throw IllegalArgumentException("Integer value out of valid range: $intValue")
                        }
                        intValue
                    }
                    is String -> {
                        val intValue = value.toIntOrNull() 
                            ?: throw NumberFormatException("Cannot convert '$value' to integer")
                        if (intValue < 0) {
                            throw IllegalArgumentException("Negative integer values not allowed: $intValue")
                        }
                        intValue
                    }
                    else -> throw IllegalArgumentException("Cannot convert ${value::class.simpleName} to Int")
                }
                Boolean::class -> when (value) {
                    is Boolean -> value
                    is String -> when (value.lowercase()) {
                        "true", "1", "yes" -> true
                        "false", "0", "no" -> false
                        else -> throw IllegalArgumentException("Cannot convert '$value' to boolean")
                    }
                    is Number -> value.toInt() != 0
                    else -> throw IllegalArgumentException("Cannot convert ${value::class.simpleName} to Boolean")
                }
                List::class -> when (value) {
                    is List<*> -> {
                        // Security: Validate list size to prevent DoS attacks
                        if (value.size > 100) {
                            throw IllegalArgumentException("List claim too large: ${value.size} items")
                        }
                        value
                    }
                    is Array<*> -> {
                        if (value.size > 100) {
                            throw IllegalArgumentException("Array claim too large: ${value.size} items")
                        }
                        value.toList()
                    }
                    else -> listOf(value)
                }
                else -> value
            }
        } catch (e: Exception) {
            // SECURITY: Do not silently fail type conversions as this can lead to authorization bypasses
            throw UserContextExtractionException("Failed to convert claim value '$value' to ${targetType.simpleName}: ${e.message}", e)
        }
    }
}

/**
 * Serializable property mapping for complex objects
 */
internal class SerializablePropertyMapping(
    override val propertyName: String,
    val claimPath: String,
    val targetType: kotlin.reflect.KClass<*>
) : PropertyMapping() {
    override fun extract(payloadMap: Map<String, Any?>): Any? {
        // Security: Validate claim path to prevent injection attacks (same as SimplePropertyMapping)
        validateClaimPath(claimPath)
        
        val parts = claimPath.split(".")
        var current: Any? = payloadMap
        
        // Security: Limit traversal depth to prevent DoS
        if (parts.size > 10) {
            throw UserContextExtractionException("Claim path too deep: ${parts.size} levels")
        }
        
        for (part in parts) {
            // Security: Validate each path component
            if (part.isEmpty() || part.length > 100) {
                throw UserContextExtractionException("Invalid claim path component: '$part'")
            }
            if (!part.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*$")) && !part.startsWith("https://")) {
                throw UserContextExtractionException("Invalid claim path format: '$part'")
            }
            
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }
        
        if (current == null) return null
        
        // For serializable objects, we return the raw data
        // The user will need to handle deserialization in their code
        return when (current) {
            is Map<*, *> -> current
            is String -> current
            is List<*> -> current
            else -> current
        }
    }
    
    private fun validateClaimPath(path: String) {
        if (path.isEmpty() || path.length > 500) {
            throw UserContextExtractionException("Invalid claim path length: ${path.length}")
        }
        // Prevent path traversal attempts
        if (path.contains("..") || path.contains("//")) {
            throw UserContextExtractionException("Path traversal attempt detected: $path")
        }
    }
}

/**
 * Configurable JWT extractor that uses the provided configuration
 */
class ConfigurableJwtExtractor(
    private val config: UserContextExtractionConfiguration
) : UserContextExtractor {

    override fun extract(principal: Principal): UserContext {
        if (principal !is JWTPrincipal) {
            throw UserContextExtractionException("Principal must be JWTPrincipal for JWT extraction")
        }

        val payload = principal.payload
        val payloadMap = payloadToMap(payload)

        // Extract core properties using new DSL
        val userId = try {
            val userIdStr = extractUserIdNew(payloadMap) ?: throw UserContextExtractionException("No userId claim found in JWT")
            val userIdInt = userIdStr.toString().toIntOrNull() ?: throw UserContextExtractionException("userId claim is not a valid integer")
            // Security: Validate userId is positive
            if (userIdInt <= 0) {
                throw UserContextExtractionException("userId must be positive: $userIdInt")
            }
            userIdInt
        } catch (e: NumberFormatException) {
            throw UserContextExtractionException("userId claim cannot be converted to integer: ${e.message}", e)
        }
        
        val email = extractEmailNew(payloadMap)?.let { emailValue ->
            // Security: Validate email format and length
            val emailStr = emailValue.toString()
            if (emailStr.length > 254) { // RFC 5321 limit
                throw UserContextExtractionException("Email address too long: ${emailStr.length} characters")
            }
            if (!emailStr.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                throw UserContextExtractionException("Invalid email format: $emailStr")
            }
            emailStr
        } ?: throw UserContextExtractionException("No email claim found in JWT") 
        
        val tenantId = try {
            extractTenantIdNew(payloadMap) ?: config.defaultTenantId ?: throw UserContextExtractionException("No tenantId claim found in JWT and no default configured")
        } catch (e: NumberFormatException) {
            throw UserContextExtractionException("tenantId claim cannot be converted to integer: ${e.message}", e)
        }
        
        val roles = extractRolesNew(payloadMap) ?: emptySet()

        // Extract additional properties using object mappings
        val additionalProperties = mutableMapOf<String, Any?>()
        
        // Handle object mappings (inline object definitions)
        config.objectMappings.forEach { (objectName, objectMapping) ->
            try {
                val objectProps = mutableMapOf<String, Any?>()
                objectMapping.properties.forEach { (propName, propMapping) ->
                    try {
                        propMapping.extract(payloadMap)?.let { value ->
                            objectProps[propName] = value
                            additionalProperties[propName] = value  // Also add to root level
                        }
                    } catch (e: Exception) {
                        // Log property extraction error but continue with other properties
                    }
                }
                if (objectProps.isNotEmpty()) {
                    additionalProperties[objectName] = objectProps
                }
            } catch (e: Exception) {
                // Log object mapping error but continue with other objects
            }
        }
        
        // Handle custom properties (serializable objects)
        config.customProperties.forEach { mapping ->
            try {
                mapping.extract(payloadMap)?.let { value ->
                    additionalProperties[mapping.propertyName] = value
                }
            } catch (e: Exception) {
                // Log serializable property extraction error but continue
            }
        }

        return UserContext(
            userId = userId,
            tenantId = tenantId,
            email = email,
            roles = roles,
            properties = additionalProperties
        )
    }

    private fun payloadToMap(payload: Payload): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        // Convert all claims to a searchable map structure
        payload.claims.forEach { (key, claim) ->
            result[key] = when {
                claim.isNull -> null
                claim.asString() != null -> claim.asString()
                claim.asInt() != null -> claim.asInt()
                claim.asLong() != null -> claim.asLong()
                claim.asBoolean() != null -> claim.asBoolean()
                claim.asDouble() != null -> claim.asDouble()
                claim.asList(Any::class.java) != null -> claim.asList(Any::class.java)
                claim.asMap() != null -> claim.asMap()
                else -> null
            }
        }
        
        return result
    }
    
    // DSL extraction methods
    private fun extractUserIdNew(payloadMap: Map<String, Any?>): String? {
        return config.userIdMapping?.extract(payloadMap) as? String
    }
    
    private fun extractEmailNew(payloadMap: Map<String, Any?>): String? {
        return config.emailMapping?.extract(payloadMap) as? String
    }
    
    private fun extractTenantIdNew(payloadMap: Map<String, Any?>): Int? {
        return config.tenantIdMapping?.extract(payloadMap) as? Int
    }
    
    private fun extractRolesNew(payloadMap: Map<String, Any?>): Set<String>? {
        val roles = config.rolesMapping?.extract(payloadMap)
        return when (roles) {
            is List<*> -> {
                val stringRoles = roles.filterIsInstance<String>()
                // Security: Validate role names and count
                if (stringRoles.size > 50) {
                    throw UserContextExtractionException("Too many roles: ${stringRoles.size}")
                }
                stringRoles.forEach { role ->
                    if (role.length > 100) {
                        throw UserContextExtractionException("Role name too long: $role")
                    }
                    if (!role.matches(Regex("^[A-Z_][A-Z0-9_]*$"))) {
                        throw UserContextExtractionException("Invalid role format: $role")
                    }
                }
                stringRoles.toSet()
            }
            is String -> {
                if (roles.length > 100) {
                    throw UserContextExtractionException("Role name too long: $roles")
                }
                if (!roles.matches(Regex("^[A-Z_][A-Z0-9_]*$"))) {
                    throw UserContextExtractionException("Invalid role format: $roles")
                }
                setOf(roles)
            }
            else -> null
        }
    }
}