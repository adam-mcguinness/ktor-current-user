package com.roastmycode.ktor.currentuser

open class UserContextException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when a user lacks required permissions
 */
class ForbiddenException(message: String = "Access forbidden", cause: Throwable? = null) :
    UserContextException(message, cause)

/**
 * Exception thrown when authentication is required but not provided
 */
class UnauthorizedException(message: String = "Authentication required", cause: Throwable? = null) :
    UserContextException(message, cause)

/**
 * Exception thrown when user context extraction fails
 */
class UserContextExtractionException(message: String, cause: Throwable? = null) :
    UserContextException(message, cause)