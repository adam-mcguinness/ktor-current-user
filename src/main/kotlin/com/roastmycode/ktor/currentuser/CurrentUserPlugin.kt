package com.roastmycode.ktor.currentuser

import io.ktor.server.application.*
import io.ktor.server.auth.*
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
    // Use custom extractor or create configurable one
    val extractor = pluginConfig.extractor
        ?: ConfigurableJwtExtractor(pluginConfig.extraction)

    val requireAuth = pluginConfig.requireAuthentication
    val onExtracted = pluginConfig.onContextExtracted
    val onError = pluginConfig.onExtractionError

    application.intercept(ApplicationCallPipeline.Call) {
        val principal = call.principal<Principal>()

        when {
            principal != null -> {
                try {
                    val userContext = extractor.extract(principal)
                    onExtracted?.invoke(userContext)
                    CurrentUser.set(userContext)

                    withContext(CurrentUser.asContextElement()) {
                        proceed()
                    }
                } catch (e: Exception) {
                    onError?.invoke(e)
                    throw e
                } finally {
                    CurrentUser.set(null)
                }
            }
            requireAuth -> {
                throw UnauthorizedException("Authentication required")
            }
            else -> {
                proceed()
            }
        }
    }
}