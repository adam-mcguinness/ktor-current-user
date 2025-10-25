package com.roastmycode.ktor.currentuser

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlin.reflect.KProperty
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

internal val callThreadLocal = ThreadLocal<ApplicationCall>()
internal val configThreadLocal = ThreadLocal<CurrentUserConfiguration>()

private val logger = LoggerFactory.getLogger("com.roastmycode.ktor.currentuser.CurrentUser")

object CurrentUser {
    private fun getCall(): ApplicationCall {
        return callThreadLocal.get()
            ?: throw NoCallContextException("No ApplicationCall in context. Ensure CurrentUserPlugin is installed and you're accessing CurrentUser within a request context.")
    }
    
    private fun getConfig(): CurrentUserConfiguration {
        return configThreadLocal.get()
            ?: throw NoCallContextException("No configuration in context. Ensure CurrentUserPlugin is installed properly.")
    }

    private fun extractStringClaim(claimPath: String): String {
        logger.debug("Extracting string claim from '{}'", claimPath)
        val principal = getCall().principal<JWTPrincipal>()
            ?: throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")

        val claim = principal.payload.getClaim(claimPath)
            ?: throw InvalidJwtException("Claim '$claimPath' not found in JWT token.")

        val value = claim.asString()
            ?: throw InvalidJwtException("Claim '$claimPath' is not a valid string.")

        logger.debug("Retrieved string claim '{}': {}", claimPath, value)
        return value
    }

    private fun extractListClaim(claimPath: String): Set<String> {
        logger.debug("Extracting list claim from '{}'", claimPath)
        val principal = getCall().principal<JWTPrincipal>()
            ?: throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")

        val claim = principal.payload.getClaim(claimPath)
        if (claim == null || claim.isNull) {
            logger.debug("No claim found at path '{}', returning empty set", claimPath)
            return emptySet()
        }

        val list = claim.asList(String::class.java).toSet()
        logger.debug("Retrieved {} items from claim '{}'", list.size, claimPath)
        return list
    }

    fun isAdmin(): Boolean {
        logger.debug("Checking if user is admin")
        val adminConfig = getConfig().adminConfig
        if (adminConfig == null) {
            logger.debug("No adminConfig set, returning false")
            return false
        }

        return when (adminConfig.adminSource) {
            AdminSource.ROLE -> {
                val adminRole = adminConfig.adminRole
                if (adminRole == null) {
                    logger.debug("adminRole is null, returning false")
                    false
                } else {
                    val isAdmin = roles.contains(adminRole)
                    logger.debug("Checking role '{}': {}", adminRole, isAdmin)
                    isAdmin
                }
            }
            AdminSource.PERMISSION -> {
                val adminPerm = adminConfig.adminPermission
                if (adminPerm == null) {
                    logger.debug("adminPermission is null, returning false")
                    false
                } else {
                    val isAdmin = permissions.contains(adminPerm)
                    logger.debug("Checking permission '{}': {}", adminPerm, isAdmin)
                    isAdmin
                }
            }
        }
    }

    val userId: String
        get() = extractStringClaim(getConfig().extraction.user)

    val roles: Set<String>
        get() = extractListClaim(getConfig().extraction.rolesClaimPath)

    val permissions: Set<String>
        get() = extractListClaim(getConfig().extraction.permissionsClaimPath)

    fun owns(sub: String): Boolean {
        logger.debug("Checking ownership for sub: {}", sub)
        if (sub == userId) {
            logger.debug("User owns resource")
            return true
        }

        // User doesn't own the resource
        val config = getConfig()
        if (config.throwError) {
            logger.warn("Authorization failed: user does not own resource")
            throw AuthenticationRequiredException(config.errorMessage)
        }

        logger.debug("User does not own resource, returning false")
        return false
    }

    fun ownsOrAdmin(sub: String): Boolean {
        logger.debug("Checking ownership or admin for sub: {}", sub)
        return owns(sub) || isAdmin()
    }

    @OptIn(InternalSerializationApi::class)
    fun <T : Any> appMetadata(klass: KClass<T>): T {
        logger.debug("Accessing appMetadata as {}", klass.simpleName)
        
        val principal = getCall().principal<JWTPrincipal>()
        if (principal == null) {
            logger.warn("No JWT principal found when accessing appMetadata")
            throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")
        }
            
        val appMetadataClaim = principal.payload?.getClaim("app_metadata")
        if (appMetadataClaim == null) {
            logger.error("JWT token missing 'app_metadata' claim")
            throw InvalidJwtException("app_metadata claim not found in JWT token.")
        }
        
        // Handle both string and object formats
        return try {
            when {
                appMetadataClaim.isNull -> {
                    logger.error("JWT token 'app_metadata' claim is null")
                    throw InvalidJwtException("app_metadata claim is null in JWT token.")
                }
                appMetadataClaim.asString() != null -> {
                    // It's already a JSON string
                    val jsonString = appMetadataClaim.asString()!!
                    logger.trace("Deserializing app_metadata from string: {}", jsonString)
                    getConfig().extraction.json.decodeFromString(klass.serializer() as kotlinx.serialization.KSerializer<T>, jsonString)
                }
                else -> {
                    // It's a JSON object, deserialize directly from the map
                    val map = appMetadataClaim.asMap()
                    val jsonObject = buildJsonObject {
                        map.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, JsonPrimitive(value))
                                is Number -> put(key, JsonPrimitive(value))
                                is Boolean -> put(key, JsonPrimitive(value))
                                is List<*> -> {
                                    // Handle lists
                                    val jsonArray = kotlinx.serialization.json.buildJsonArray {
                                        value.forEach { item ->
                                            when (item) {
                                                is String -> add(JsonPrimitive(item))
                                                is Number -> add(JsonPrimitive(item))
                                                is Boolean -> add(JsonPrimitive(item))
                                                else -> add(JsonPrimitive(item.toString()))
                                            }
                                        }
                                    }
                                    put(key, jsonArray)
                                }
                                else -> put(key, JsonPrimitive(value.toString()))
                            }
                        }
                    }
                    logger.trace("Deserializing app_metadata from object: {}", jsonObject)
                    getConfig().extraction.json.decodeFromJsonElement(klass.serializer() as kotlinx.serialization.KSerializer<T>, jsonObject)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to deserialize app_metadata to {}: {}", klass.simpleName, e.message)
            throw MetadataDeserializationException(
                "Failed to deserialize app_metadata to ${klass.simpleName}: ${e.message}", 
                e
            )
        }
    }
    
    inline fun <reified T : Any> appMetadata(): T = appMetadata(T::class)
    
    @OptIn(InternalSerializationApi::class)
    internal fun getMetadata(): Any {
        val config = getConfig()
        val metadataClass = config.extraction.metadataClass
            ?: throw IllegalStateException("No metadata class configured. Use metadata<T>() in plugin configuration.")
            
        val principal = getCall().principal<JWTPrincipal>()
            ?: throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")
            
        val appMetadataClaim = principal.payload.getClaim("app_metadata")
            ?: throw InvalidJwtException("app_metadata claim not found in JWT token.")
        
        // Handle both string and object formats
        return try {
            when {
                appMetadataClaim.isNull -> {
                    throw InvalidJwtException("app_metadata claim is null in JWT token.")
                }
                appMetadataClaim.asString() != null -> {
                    // It's already a JSON string
                    config.extraction.json.decodeFromString(metadataClass.serializer() as kotlinx.serialization.KSerializer<Any>, appMetadataClaim.asString()!!)
                }
                else -> {
                    // It's a JSON object, deserialize directly from the map
                    val map = appMetadataClaim.asMap()
                    val jsonObject = buildJsonObject {
                        map.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, JsonPrimitive(value))
                                is Number -> put(key, JsonPrimitive(value))
                                is Boolean -> put(key, JsonPrimitive(value))
                                is List<*> -> {
                                    // Handle lists
                                    val jsonArray = buildJsonArray {
                                        value.forEach { item ->
                                            when (item) {
                                                is String -> add(JsonPrimitive(item))
                                                is Number -> add(JsonPrimitive(item))
                                                is Boolean -> add(JsonPrimitive(item))
                                                else -> add(JsonPrimitive(item.toString()))
                                            }
                                        }
                                    }
                                    put(key, jsonArray)
                                }
                                else -> put(key, JsonPrimitive(value.toString()))
                            }
                        }
                    }
                    config.extraction.json.decodeFromJsonElement(metadataClass.serializer() as kotlinx.serialization.KSerializer<Any>, jsonObject)
                }
            }
        } catch (e: Exception) {
            throw MetadataDeserializationException(
                "Failed to deserialize app_metadata to ${metadataClass.simpleName}: ${e.message}", 
                e
            )
        }
    }
    
    internal inline fun <reified T> get(propertyName: String): T {
        logger.debug("Accessing property '{}' as {}", propertyName, T::class.simpleName)
        
        val metadata = getMetadata()
        val property = metadata::class.members.find { it.name == propertyName }
        if (property == null) {
            logger.error("Property '{}' not found in metadata class {}", propertyName, metadata::class.simpleName)
            throw IllegalArgumentException("Property '$propertyName' not found in metadata class ${metadata::class.simpleName}")
        }
        
        return try {
            val value = property.call(metadata) as T
            logger.debug("Retrieved property '{}' with value: {}", propertyName, value)
            value
        } catch (e: Exception) {
            logger.error("Failed to access property '{}': {}", propertyName, e.message)
            throw CurrentUserException("Failed to access property '$propertyName': ${e.message}", e)
        }
    }
}