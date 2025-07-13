package com.roastmycode.ktor.currentuser

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import kotlinx.coroutines.withContext

/**
 * Ktor plugin for automatic user context management
 * 
 * This plugin extracts user information from JWT tokens and makes it available
 * throughout the request lifecycle via the CurrentUser object.
 */
val CurrentUserPlugin = createApplicationPlugin(
    name = "CurrentUserPlugin",
    createConfiguration = ::UserContextConfiguration
) {
    println("[CurrentUserPlugin] Plugin is being installed...")
    // Store the configuration globally so route extensions can access it
    CurrentUserPluginHolder.configuration = pluginConfig
    println("[CurrentUserPlugin] Configuration stored: requireAuth=${pluginConfig.requireAuthentication}")
    
    val extractor = pluginConfig.extractor
        ?: ConfigurableJwtExtractor(pluginConfig.extraction)
    println("[CurrentUserPlugin] Extractor configured: ${extractor::class.simpleName}")

    val requireAuth = pluginConfig.requireAuthentication
    val onExtracted = pluginConfig.onContextExtracted
    val onError = pluginConfig.onExtractionError
    
    // Intercept at the Fallback phase - this runs after routing and authentication
    println("[CurrentUserPlugin] Setting up interception at ApplicationCallPipeline.Fallback phase")
    
    application.intercept(ApplicationCallPipeline.Fallback) {
        // Check if we already processed this call
        if (call.attributes.contains(UserContextKey)) {
            println("[CurrentUserPlugin] User context already processed, skipping")
            proceed()
            return@intercept
        }
        
        println("\n[CurrentUserPlugin] ===== Request intercepted at Fallback phase =====")
        val principal = call.principal<Any>()
        println("[CurrentUserPlugin] Principal extracted: ${principal?.let { "${it::class.simpleName} - $it" } ?: "NULL (not authenticated)"}")
        
        when {
            principal != null -> {
                println("[CurrentUserPlugin] Principal found, extracting user context...")
                try {
                    val userContext = extractor.extract(principal)
                    println("[CurrentUserPlugin] User context extracted successfully:")
                    println("[CurrentUserPlugin]   - userId: ${userContext.userId}")
                    println("[CurrentUserPlugin]   - email: ${userContext.email}")
                    println("[CurrentUserPlugin]   - tenantId: ${userContext.tenantId}")
                    println("[CurrentUserPlugin]   - roles: ${userContext.roles}")
                    println("[CurrentUserPlugin]   - properties: ${userContext.properties}")
                    
                    onExtracted?.invoke(userContext)
                    println("[CurrentUserPlugin] onContextExtracted callback invoked: ${onExtracted != null}")
                    
                    // Store in call attributes
                    call.attributes.put(UserContextKey, userContext)
                    CurrentUser.set(userContext)
                    println("[CurrentUserPlugin] User context set in CurrentUser")
                    
                    println("[CurrentUserPlugin] Proceeding with request in user context...")
                    withContext(CurrentUser.asContextElement()) {
                        proceed()
                    }
                    println("[CurrentUserPlugin] Request completed successfully")
                } catch (e: Exception) {
                    println("[CurrentUserPlugin] ERROR during context extraction: ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                    onError?.invoke(e)
                    println("[CurrentUserPlugin] onExtractionError callback invoked: ${onError != null}")
                    throw e
                } finally {
                    println("[CurrentUserPlugin] Cleaning up user context")
                    CurrentUser.set(null)
                }
            }
            requireAuth -> {
                println("[CurrentUserPlugin] No principal found but authentication is required!")
                println("[CurrentUserPlugin] Throwing UnauthorizedException")
                throw UnauthorizedException("Authentication required")
            }
            else -> {
                println("[CurrentUserPlugin] No principal found and authentication not required, proceeding without user context")
                proceed()
            }
        }
    }
    
    // Also handle earlier in the pipeline to set context before route handlers
    application.intercept(ApplicationCallPipeline.Call) {
        println("[CurrentUserPlugin] Early intercept - checking for principal")
        val principal = call.principal<Any>()
        
        if (principal != null && !call.attributes.contains(UserContextKey)) {
            println("[CurrentUserPlugin] Principal found early, setting context")
            try {
                val userContext = extractor.extract(principal)
                call.attributes.put(UserContextKey, userContext)
                CurrentUser.set(userContext)
                println("[CurrentUserPlugin] Early context set: User(id=${userContext.userId})")
                
                withContext(CurrentUser.asContextElement()) {
                    proceed()
                }
            } catch (e: Exception) {
                println("[CurrentUserPlugin] Early extraction failed: ${e.message}")
                proceed() // Continue anyway, fallback will handle it
            } finally {
                CurrentUser.set(null)
            }
        } else {
            proceed()
        }
    }
}

// Attribute key for storing user context in the call
private val UserContextKey = AttributeKey<UserContext>("UserContext")

// Internal holder for configuration
internal object CurrentUserPluginHolder {
    var configuration: UserContextConfiguration? = null
    
    val extractor: UserContextExtractor
        get() = configuration?.extractor 
            ?: configuration?.extraction?.let { ConfigurableJwtExtractor(it) }
            ?: throw IllegalStateException("CurrentUserPlugin not configured")
}