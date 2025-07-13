package com.roastmycode.ktor.currentuser

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SimpleIntegrationTest {

    @Test
    fun `test UserContext data class creation`() {
        val userContext = UserContext(
            userId = "123",
            tenantId = 456,
            email = "test@example.com",
            roles = setOf("USER", "ADMIN"),
            properties = mapOf("custom" to "value")
        )

        assertEquals("123", userContext.userId)
        assertEquals(456, userContext.tenantId)
        assertEquals("test@example.com", userContext.email)
        assertEquals(setOf("USER", "ADMIN"), userContext.roles)
        assertEquals("value", userContext.properties["custom"])
    }

    @Test
    fun `test UserContext convenience methods`() {
        val userContext = UserContext(
            userId = "123",
            tenantId = 456,
            email = "test@example.com",
            roles = setOf("USER", "ADMIN"),
            properties = emptyMap()
        )

        assertTrue(userContext.hasRole("USER"))
        assertTrue(userContext.hasRole("ADMIN"))
        assertFalse(userContext.hasRole("SUPER_ADMIN"))
        
        assertTrue(userContext.owns("123"))
        assertFalse(userContext.owns("999"))
    }

    @Test
    fun `test UserContext requires role throws on missing role`() {
        val userContext = UserContext(
            userId = "123",
            tenantId = 456,
            email = "test@example.com",
            roles = setOf("USER"),
            properties = emptyMap()
        )

        // Should not throw
        userContext.requireRole("USER")
        
        // Should throw
        assertFailsWith<ForbiddenException> {
            userContext.requireRole("ADMIN")
        }
    }

    @Test
    fun `test withUserContext sets and cleans up context`() = runBlocking {
        val testUser = UserContext(
            userId = "789",
            tenantId = 101,
            email = "context@test.com",
            roles = setOf("TEST"),
            properties = emptyMap()
        )

        // Context should not be authenticated initially
        assertFalse(CurrentUser.isAuthenticated)

        withUserContext(testUser) {
            // Context should be available inside block
            assertTrue(CurrentUser.isAuthenticated)
            assertEquals("789", CurrentUser.id)
            assertEquals(101, CurrentUser.tenantId)
            assertEquals("context@test.com", CurrentUser.email)
            assertTrue(CurrentUser.hasRole("TEST"))
        }

        // Context should not be authenticated after block
        assertFalse(CurrentUser.isAuthenticated)
    }

    @Test
    fun `test configuration creates proper mapping objects`() {
        val config = UserContextExtractionConfiguration().apply {
            userId = string("sub")
            email = string("email")
            tenantId = int("tenant_id")
            roles = list<String>("roles")
            defaultTenantId = 999
        }

        assertNotNull(config.userIdMapping)
        assertNotNull(config.emailMapping)
        assertNotNull(config.tenantIdMapping)
        assertNotNull(config.rolesMapping)
        assertEquals(999, config.defaultTenantId)
    }

    @Test
    fun `test property mapping creates correct types`() {
        val config = UserContextExtractionConfiguration()
        
        val stringProp = config.string("test_claim")
        val intProp = config.int("number_claim")
        val boolProp = config.boolean("flag_claim")
        val listProp = config.list<String>("list_claim")

        assertEquals("test_claim", stringProp.claimPath)
        assertEquals("number_claim", intProp.claimPath)
        assertEquals("flag_claim", boolProp.claimPath)
        assertEquals("list_claim", listProp.claimPath)
    }

    @Test
    fun `test property mapping extracts simple values correctly`() {
        val stringMapping = SimplePropertyMapping("test", "claim", String::class)
        val intMapping = SimplePropertyMapping("test", "claim", Int::class)
        val boolMapping = SimplePropertyMapping("test", "claim", Boolean::class)

        // Test string extraction
        assertEquals("hello", stringMapping.extract(mapOf("claim" to "hello")))
        
        // Test integer extraction
        assertEquals(42, intMapping.extract(mapOf("claim" to 42)))
        assertEquals(123, intMapping.extract(mapOf("claim" to "123")))
        
        // Test boolean extraction
        assertEquals(true, boolMapping.extract(mapOf("claim" to true)))
        assertEquals(false, boolMapping.extract(mapOf("claim" to false)))
        assertEquals(true, boolMapping.extract(mapOf("claim" to "true")))
        assertEquals(false, boolMapping.extract(mapOf("claim" to "false")))
    }

    @Test
    fun `test property mapping handles null values`() {
        val mapping = SimplePropertyMapping("test", "nonexistent", String::class)
        
        assertNull(mapping.extract(mapOf("other" to "value")))
        assertNull(mapping.extract(mapOf("nonexistent" to null)))
    }

    @Test
    fun `test nested claim path extraction`() {
        val mapping = SimplePropertyMapping("test", "app_metadata.tenant", String::class)
        
        val payload = mapOf(
            "app_metadata" to mapOf(
                "tenant" to "acme-corp",
                "role" to "admin"
            )
        )
        
        assertEquals("acme-corp", mapping.extract(payload))
    }

    @Test
    fun `test security validation prevents malicious paths`() {
        // Path traversal should fail
        assertFailsWith<UserContextExtractionException> {
            SimplePropertyMapping("test", "../etc/passwd", String::class)
                .extract(mapOf("test" to "value"))
        }
        
        // Double slashes should fail
        assertFailsWith<UserContextExtractionException> {
            SimplePropertyMapping("test", "valid//malicious", String::class)
                .extract(mapOf("test" to "value"))
        }
        
        // Empty path should fail
        assertFailsWith<UserContextExtractionException> {
            SimplePropertyMapping("test", "", String::class)
                .extract(mapOf("test" to "value"))
        }
        
        // Very long path should fail
        val longPath = "x".repeat(501)
        assertFailsWith<UserContextExtractionException> {
            SimplePropertyMapping("test", longPath, String::class)
                .extract(mapOf("test" to "value"))
        }
    }
}