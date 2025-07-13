package com.roastmycode.ktor.currentuser

import com.auth0.jwt.interfaces.Payload
import io.ktor.server.auth.jwt.*

/**
 * Base class for property mapping implementations
 */
internal sealed class PropertyMapping {
    abstract val propertyName: String
    abstract fun extract(payloadMap: Map<String, Any?>): Any?
    
    /**
     * Common function to validate claim paths and prevent security issues
     */
    private fun validateClaimPath(path: String) {
        if (path.isEmpty() || path.length > 500) {
            throw UserContextExtractionException("Invalid claim path length: ${path.length}")
        }
        
        // Check if this is a URL-based claim (like Auth0 custom claims)
        val isUrlClaim = path.startsWith("http://") || path.startsWith("https://")
        
        if (isUrlClaim) {
            // For URL claims, just check for path traversal in the URL path part
            if (path.contains("../")) {
                throw UserContextExtractionException("Path traversal attempt detected: $path")
            }
        } else {
            // For regular claims, check for path traversal attempts
            if (path.contains("..") || path.contains("//")) {
                throw UserContextExtractionException("Path traversal attempt detected: $path")
            }
        }
    }
    
    /**
     * Common function to traverse claim paths in payload maps
     */
    protected fun traverseClaimPath(payloadMap: Map<String, Any?>, claimPath: String): Any? {
        validateClaimPath(claimPath)
        
        // Check if this is a URL-based claim (Auth0 custom claims)
        val isUrlClaim = claimPath.startsWith("http://") || claimPath.startsWith("https://")
        
        if (isUrlClaim) {
            // URL-based claims are direct keys in the payload, not nested
            return payloadMap[claimPath]
        }
        
        // For non-URL claims, support nested paths with dot notation
        val parts = claimPath.split(".")
        var current: Any? = payloadMap
        
        // Security: Limit traversal depth to prevent DoS
        if (parts.size > 10) {
            throw UserContextExtractionException("Claim path too deep: ${parts.size} levels")
        }
        
        for (part in parts) {
            // Security: Validate each path component
            if (part.isEmpty() || part.length > 200) {
                throw UserContextExtractionException("Invalid claim path component: '$part'")
            }
            // Validate claim name format
            if (!part.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*$"))) {
                throw UserContextExtractionException("Invalid claim path format: '$part'")
            }
            
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }
        
        return current
    }
}

/**
 * Simple property mapping for the DSL
 */
internal class SimplePropertyMapping(
    override val propertyName: String,
    private val claimPath: String,
    private val targetType: kotlin.reflect.KClass<*>
) : PropertyMapping() {
    override fun extract(payloadMap: Map<String, Any?>): Any? {
        val current = traverseClaimPath(payloadMap, claimPath)
        return convertToType(current, targetType)
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
                        if (intValue < 0) {
                            throw IllegalArgumentException("Negative integer values not allowed: $intValue")
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
 * Configurable JWT extractor that uses the provided configuration
 */
class ConfigurableJwtExtractor(
    private val config: UserContextExtractionConfiguration
) : UserContextExtractor {

    override fun extract(principal: Any): UserContext {
        val jwtPrincipal = principal as? JWTPrincipal
            ?: throw UserContextExtractionException("Principal must be JWTPrincipal for JWT extraction")

        val payload = jwtPrincipal.payload
        val payloadMap = payloadToMap(payload)

        // Extract core properties using new DSL
        val userId = extractUserIdNew(payloadMap) ?: throw UserContextExtractionException("No userId claim found in JWT")
        
        val email = extractEmailNew(payloadMap)?.let { emailValue ->
            // Security: Validate email format and length
            val emailStr = emailValue
            if (emailStr.length > 254) { // RFC 5321 limit
                throw UserContextExtractionException("Email address too long: ${emailStr.length} characters")
            }
            if (!emailStr.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                throw UserContextExtractionException("Invalid email format: $emailStr")
            }
            emailStr
        } // Email is optional - not in standard Auth0 access tokens 
        
        val tenantId = try {
            extractTenantIdNew(payloadMap) ?: config.defaultTenantId
        } catch (e: NumberFormatException) {
            throw UserContextExtractionException("tenantId claim cannot be converted to integer: ${e.message}", e)
        }
        
        val roles = extractRolesNew(payloadMap) ?: emptySet()
        
        // Extract additional common properties
        val organizationId = extractOrganizationIdNew(payloadMap)
        val branchId = extractBranchIdNew(payloadMap)
        val departmentId = extractDepartmentIdNew(payloadMap)
        val permissions = extractPermissionsNew(payloadMap)

        // Extract additional properties
        val additionalProperties = mutableMapOf<String, Any?>()
        
        
        // Handle custom claims defined via customClaim DSL
        config.customClaims.forEach { (name, mapping) ->
            try {
                mapping.extract(payloadMap)?.let { value ->
                    additionalProperties[name] = value
                }
            } catch (e: Exception) {
                // Log custom claim extraction error but continue
            }
        }

        return UserContext(
            userId = userId,
            tenantId = tenantId,
            email = email,
            roles = roles,
            organizationId = organizationId,
            branchId = branchId,
            departmentId = departmentId,
            permissions = permissions,
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
    private fun extractUserIdNew(payloadMap: Map<String, Any?>): String {
        val mapping = config.userIdMapping ?: throw UserContextExtractionException("userId mapping not configured")
        val extracted = mapping.extract(payloadMap) ?: throw UserContextExtractionException("No userId found in JWT")
        
        // Convert to String as required by UserContext
        return when (extracted) {
            is String -> extracted
            is Int -> extracted.toString()
            is Long -> extracted.toString()
            else -> extracted.toString()
        }
    }
    
    private fun extractEmailNew(payloadMap: Map<String, Any?>): String? {
        return config.emailMapping?.extract(payloadMap) as? String
    }
    
    private fun extractTenantIdNew(payloadMap: Map<String, Any?>): Int? {
        return config.tenantIdMapping?.extract(payloadMap) as? Int
    }
    
    private fun extractRolesNew(payloadMap: Map<String, Any?>): Set<String>? {
        return when (val roles = config.rolesMapping?.extract(payloadMap)) {
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
    
    private fun extractOrganizationIdNew(payloadMap: Map<String, Any?>): Int? {
        return config.organizationIdMapping?.extract(payloadMap) as? Int
    }
    
    private fun extractBranchIdNew(payloadMap: Map<String, Any?>): Int? {
        return config.branchIdMapping?.extract(payloadMap) as? Int
    }
    
    private fun extractDepartmentIdNew(payloadMap: Map<String, Any?>): String? {
        return config.departmentIdMapping?.extract(payloadMap) as? String
    }
    
    private fun extractPermissionsNew(payloadMap: Map<String, Any?>): Set<String>? {
        return when (val permissions = config.permissionsMapping?.extract(payloadMap)) {
            is List<*> -> {
                val stringPermissions = permissions.filterIsInstance<String>()
                // Security: Validate permission names and count
                if (stringPermissions.size > 100) {
                    throw UserContextExtractionException("Too many permissions: ${stringPermissions.size}")
                }
                stringPermissions.forEach { permission ->
                    if (permission.length > 200) {
                        throw UserContextExtractionException("Permission name too long: $permission")
                    }
                }
                stringPermissions.toSet()
            }
            is String -> setOf(permissions)
            else -> null
        }
    }
}