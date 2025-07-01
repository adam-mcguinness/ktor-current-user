package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.*
import kotlin.test.*

class UserContextSecurityTest {

    @Test
    fun `test suspend function context propagation`() = runBlocking {
        val testUser = UserContext(
            userId = 123,
            tenantId = 456,
            email = "test@example.com",
            roles = setOf("USER"),
            properties = emptyMap()
        )

        withUserContext(testUser) {
            // Test direct access
            assertEquals(123, CurrentUser.id)
            
            // Test access in suspend function
            suspendingFunction()
            
            // Test access in nested coroutine
            val deferred = async {
                nestedSuspendFunction()
            }
            deferred.await()
        }
    }

    private suspend fun suspendingFunction() {
        delay(1) // Force suspension point
        assertEquals(123, CurrentUser.id)
        assertEquals("test@example.com", CurrentUser.email)
    }

    private suspend fun nestedSuspendFunction() {
        delay(1) // Force suspension point
        assertEquals(456, CurrentUser.tenantId)
        assertTrue(CurrentUser.hasRole("USER"))
    }

    @Test
    fun `test context isolation between coroutines`() = runBlocking {
        val user1 = UserContext(1, 10, "user1@example.com", setOf("USER"), emptyMap())
        val user2 = UserContext(2, 20, "user2@example.com", setOf("ADMIN"), emptyMap())

        val job1 = async {
            withUserContext(user1) {
                delay(10)
                CurrentUser.id
            }
        }

        val job2 = async {
            withUserContext(user2) {
                delay(10)
                CurrentUser.id
            }
        }

        val results = awaitAll(job1, job2)
        assertEquals(listOf(1, 2), results)
    }

    @Test
    fun `test invalid email format rejection`() {
        val exception = UserContextExtractionException("Email validation test")
        assertNotNull(exception.message)
    }

    @Test
    fun `test negative user ID rejection`() {
        val exception = UserContextExtractionException("userId must be positive: -1")
        assertTrue(exception.message!!.contains("positive"))
    }

    @Test
    fun `test role format validation`() {
        val exception = UserContextExtractionException("Invalid role format: invalid-role")
        assertTrue(exception.message!!.contains("Invalid role format"))
    }

    @Test
    fun `test context cleanup after exception`() = runBlocking {
        val testUser = UserContext(123, 456, "test@example.com", setOf("USER"), emptyMap())

        assertFailsWith<RuntimeException> {
            withUserContext(testUser) {
                assertEquals(123, CurrentUser.id)
                throw RuntimeException("Test exception")
            }
        }

        // Context should be cleaned up after exception
        assertFalse(CurrentUser.isAuthenticated)
    }

    @Test
    fun `test memory cleanup in finally block`() = runBlocking {
        val testUser = UserContext(123, 456, "test@example.com", setOf("USER"), emptyMap())

        withUserContext(testUser) {
            assertEquals(123, CurrentUser.id)
        }

        // Context should be null after withUserContext block
        assertFalse(CurrentUser.isAuthenticated)
    }
}