package com.roastmycode.ktor.currentuser

import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

class CurrentUserConfiguration {
    var json: Json = Json
    internal var metadataClass: KClass<*>? = null
    
    fun <T : Any> metadata(klass: KClass<T>) {
        metadataClass = klass
    }
}

inline fun <reified T : Any> CurrentUserConfiguration.metadata() {
    metadata(T::class)
}