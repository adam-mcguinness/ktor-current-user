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
    
    val userId: String
        get() {
            logger.debug("Accessing userId")
            val principal = getCall().principal<JWTPrincipal>()
            if (principal == null) {
                logger.warn("No JWT principal found when accessing userId")
                throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")
            }
            
            val subject = principal.payload?.subject
            if (subject == null) {
                logger.error("JWT token missing required 'sub' claim")
                throw InvalidJwtException("User ID (sub) claim not found in JWT token.")
            }
            
            logger.debug("Retrieved userId: {}", subject)
            return subject
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
                    getConfig().json.decodeFromString(klass.serializer() as kotlinx.serialization.KSerializer<T>, jsonString)
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
                    getConfig().json.decodeFromJsonElement(klass.serializer() as kotlinx.serialization.KSerializer<T>, jsonObject)
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
        val metadataClass = config.metadataClass
            ?: throw IllegalStateException("No metadata class configured. Use metadata<T>() in plugin configuration.")
            
        val principal = getCall().principal<JWTPrincipal>()
            ?: throw AuthenticationRequiredException("No JWT principal found. Ensure the user is authenticated.")
            
        val appMetadataClaim = principal.payload?.getClaim("app_metadata")
            ?: throw InvalidJwtException("app_metadata claim not found in JWT token.")
        
        // Handle both string and object formats
        return try {
            when {
                appMetadataClaim.isNull -> {
                    throw InvalidJwtException("app_metadata claim is null in JWT token.")
                }
                appMetadataClaim.asString() != null -> {
                    // It's already a JSON string
                    config.json.decodeFromString(metadataClass.serializer() as kotlinx.serialization.KSerializer<Any>, appMetadataClaim.asString()!!)
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
                    config.json.decodeFromJsonElement(metadataClass.serializer() as kotlinx.serialization.KSerializer<Any>, jsonObject)
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