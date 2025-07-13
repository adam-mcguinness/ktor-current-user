package com.roastmycode.ktor.currentuser

import io.ktor.server.application.*
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.roastmycode.ktor.currentuser.CurrentUserPlugin")

val CurrentUserPlugin = createApplicationPlugin(
    name = "CurrentUserPlugin",
    createConfiguration = ::CurrentUserConfiguration
) {
    val config = this@createApplicationPlugin.pluginConfig
    
    logger.debug("CurrentUserPlugin installed with configuration")
    
    application.intercept(ApplicationCallPipeline.ApplicationPhase.Call) {
        logger.trace("Intercepting call")
        
        try {
            withContext(
                callThreadLocal.asContextElement(call) + 
                configThreadLocal.asContextElement(config)
            ) {
                proceed()
            }
        } finally {
            // Clean up ThreadLocal to prevent memory leaks
            logger.trace("Cleaning up ThreadLocal values")
            callThreadLocal.remove()
            configThreadLocal.remove()
        }
    }
}