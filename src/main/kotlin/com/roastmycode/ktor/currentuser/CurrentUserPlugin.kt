package com.roastmycode.ktor.currentuser

import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.roastmycode.ktor.currentuser.CurrentUserPlugin")

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
    val currentUserInstance = pluginConfig.currentUser ?: CurrentUser

    logger.debug("CurrentUserPlugin initialized with requireAuthentication: {}", requireAuth)

    application.intercept(ApplicationCallPipeline.Call) {
        val principal = call.principal<Any>()

        when {
            principal != null -> {
                logger.debug("Processing authenticated request with principal type: {}", principal::class.simpleName)
                try {
                    val userContext = extractor.extract(principal)
                    logger.debug("Extracted user context: userId={}, email={}", userContext.userId, userContext.email)
                    onExtracted?.invoke(userContext)
                    currentUserInstance.set(userContext)

                    withContext(currentUserInstance.asContextElement()) {
                        proceed()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to extract user context from principal", e)
                    onError?.invoke(e)
                    throw e
                } finally {
                    currentUserInstance.set(null)
                    logger.trace("Cleared user context after request")
                }
            }
            requireAuth -> {
                logger.warn("Unauthenticated request rejected - authentication required")
                throw UnauthorizedException("Authentication required")
            }
            else -> {
                logger.trace("Processing unauthenticated request")
                proceed()
            }
        }
    }
}