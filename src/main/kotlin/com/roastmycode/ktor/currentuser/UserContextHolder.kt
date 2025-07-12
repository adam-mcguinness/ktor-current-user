package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.asContextElement

/**
 * Holder for UserContext that manages ThreadLocal storage
 */
internal class UserContextHolder {
    private val contextThreadLocal = ThreadLocal<UserContext>()
    
    fun get(): UserContext? = contextThreadLocal.get()
    
    fun set(context: UserContext?) {
        if (context != null) {
            contextThreadLocal.set(context)
        } else {
            contextThreadLocal.remove()
        }
    }
    
    fun asContextElement() = contextThreadLocal.asContextElement()
}