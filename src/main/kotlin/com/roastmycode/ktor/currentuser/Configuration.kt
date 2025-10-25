package com.roastmycode.ktor.currentuser

import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

enum class AdminSource {
    ROLE,
    PERMISSION
}

class CurrentUserConfiguration{
    var throwError: Boolean = false

    var errorMessage: String = "You are not authorized to access this."

    var extraction: Extraction = Extraction()

    var adminConfig: AdminConfig? = null

    fun extraction(block: Extraction.() -> Unit) {
        extraction.apply(block)
    }

    fun adminConfig(block: AdminConfig.() -> Unit) {
        if (adminConfig == null) {
            adminConfig = AdminConfig()
        }
        adminConfig?.apply(block)
    }

}

class Extraction {
    var json: Json = Json
    internal var metadataClass: KClass<*>? = null

    var user: String = "sub"

    var rolesClaimPath: String = "roles"

    var permissionsClaimPath: String = "permissions"

    fun <T : Any> metadata(klass: KClass<T>) {
        metadataClass = klass
    }
}

class AdminConfig {
    var adminSource: AdminSource = AdminSource.ROLE
    var adminRole: String? = "admin"
    var adminPermission: String? = "admin:super"
}

inline fun <reified T : Any> CurrentUserConfiguration.metadata() {
    extraction.metadata(T::class)
}