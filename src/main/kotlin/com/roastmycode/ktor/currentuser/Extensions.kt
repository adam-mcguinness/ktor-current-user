package com.roastmycode.ktor.currentuser

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Creates a delegated property that extracts a value from the user's app_metadata.
 * 
 * Usage:
 * ```
 * val CurrentUser.tenantId: String by currentUserProperty { it.tenantId }
 * val CurrentUser.roles: List<String> by currentUserProperty { it.roles }
 * ```
 */
inline fun <reified T : Any, R> currentUserProperty(
    crossinline selector: (T) -> R
): ReadOnlyProperty<Any?, R?> = ReadOnlyProperty { _, _ ->
    CurrentUser.appMetadata<T>()?.let { selector(it) }
}

/**
 * Alternative syntax using a specific metadata class.
 * 
 * Usage:
 * ```
 * val CurrentUser.tenantId: String by metadata<UserMetadata> { it.tenantId }
 * ```
 */
inline fun <reified T : Any, R> metadata(
    crossinline selector: (T) -> R
): ReadOnlyProperty<Any?, R?> = currentUserProperty(selector)