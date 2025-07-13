package com.roastmycode.ktor.currentuser

open class CurrentUserException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class NoCallContextException(message: String) : CurrentUserException(message)

class AuthenticationRequiredException(message: String) : CurrentUserException(message)

class InvalidJwtException(message: String) : CurrentUserException(message)

class MetadataDeserializationException(message: String, cause: Throwable? = null) : CurrentUserException(message, cause)