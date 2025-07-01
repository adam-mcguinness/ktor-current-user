# Ktor Current User Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.roastmycode/ktor-current-user.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.roastmycode%22%20AND%20a:%22ktor-current-user%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Ktor plugin that provides thread-safe user context management for authenticated requests, with a clean, type-safe DSL for JWT claim mapping. Access user information anywhere in your application without passing it through method parameters.

```kotlin
// Before: Passing user context everywhere 😫
class OrderService {
    fun createOrder(request: OrderRequest, user: UserContext): Order {
        validateUser(user)
        return orderRepository.create(request, user)
    }
}

// After: Clean APIs with automatic context 🎉
class OrderService {
    fun createOrder(request: OrderRequest): Order {
        CurrentUser.context.requireRole("USER")
        return Order(
            items = request.items,
            userId = CurrentUser.id,      // Automatically available!
            tenantId = CurrentUser.tenantId
        )
    }
}
```

## Features

- 🔒 **Thread-safe user context** management across coroutines
- 🎯 **Type-safe DSL** for JWT claim mapping with nested object support
- ⚡ **Zero boilerplate** - access user context anywhere without passing parameters
- 🧩 **Easy integration** with existing Ktor authentication
- 🏗️ **Automatic availability** throughout entire request lifecycle
- 📦 **Clean object-oriented configuration** for complex JWT structures
- 🔧 **Flexible claim extraction** from any JWT structure

## Key Architectural Benefits

### No Parameter Passing Required

Unlike traditional approaches where you need to pass user context through every method:

❌ **Without this plugin (traditional approach):**
```kotlin
// Routes
get("/documents/{id}") {
    val user = extractUserFromJWT(call)  // Manual extraction
    val doc = documentService.getDocument(docId, user)  // Pass user everywhere
    call.respond(doc)
}

// Services - user must be passed as parameter
class DocumentService {
    fun getDocument(id: Int, user: UserContext): Document {
        val doc = repository.findById(id)
        if (!user.owns(doc.ownerId)) {  // Manual checks
            throw ForbiddenException()
        }
        auditService.log(id, user)  // Pass user to other services
        return doc
    }
}
```

✅ **With this plugin:**
```kotlin
// Routes
get("/documents/{id}") {
    val doc = documentService.getDocument(docId)  // Clean API
    call.respond(doc)
}

// Services - user context automatically available
class DocumentService {
    fun getDocument(id: Int): Document {
        val doc = repository.findById(id)
        if (!CurrentUser.owns(doc.ownerId)) {  // Direct access
            throw ForbiddenException()
        }
        auditService.log(id)  // No need to pass user
        return doc
    }
}
```

This approach:
- Keeps your service APIs clean and focused on business logic
- Reduces boilerplate and parameter passing
- Makes the code more maintainable and testable
- Works seamlessly across all layers of your application

### Comparison

| Aspect | Traditional Approach | With CurrentUser Plugin |
|--------|---------------------|-------------------------|
| **Service APIs** | `fun getOrders(user: UserContext): List<Order>` | `fun getOrders(): List<Order>` |
| **Deep call chains** | Pass user through every method | Automatically available |
| **Testing** | Mock user parameter everywhere | Use `withUserContext` once |
| **Refactoring** | Update all method signatures | No changes needed |
| **Cross-cutting concerns** | Inject into every service | Access directly where needed |
| **Code clarity** | Business logic mixed with auth | Clean separation of concerns |

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.roastmycode:ktor-current-user:1.0.0")
}
```

### Requirements

- Kotlin 1.8.0 or higher
- Ktor 2.0.0 or higher
- Java 11 or higher


## Quick Start

### 1. Install the plugin

**Minimal setup (uses JWT standard claims):**
```kotlin
import com.roastmycode.ktor.currentuser.CurrentUserPlugin

fun Application.module() {
    install(Authentication) {
        jwt("auth-jwt") {
            // Configure your JWT authentication
        }
    }
    
    install(CurrentUserPlugin)  // Uses sub, email, roles claims by default
}
```

**Custom configuration:**
```kotlin
import com.roastmycode.ktor.currentuser.CurrentUserPlugin

install(CurrentUserPlugin) {
    extraction {
        userId = string("sub")
        email = string("email")
        tenantId = int("tenant_id")
    }
}
```

### 2. Access user context anywhere

```kotlin
import com.roastmycode.ktor.currentuser.CurrentUser

routing {
    authenticate("auth-jwt") {
        get("/profile") {
            val profile = userService.getCurrentUserProfile()
            call.respond(profile)
        }
    }
}

class UserService {
    fun getCurrentUserProfile(): UserProfile {
        return UserProfile(
            id = CurrentUser.id,
            email = CurrentUser.email,
            tenant = CurrentUser.tenantId
        )
    }
}
```

## How It Works

The plugin uses Kotlin's coroutine context elements combined with ThreadLocal storage to ensure the user context is available throughout the entire request lifecycle:

1. **Authentication**: When a request comes in with a valid JWT token
2. **Extraction**: The plugin extracts user information from the JWT claims
3. **Context Storage**: User context is stored in a coroutine context element
4. **Automatic Propagation**: The context automatically flows through all coroutines in the request
5. **Cleanup**: Context is automatically cleaned up after the request completes

This means you can access `CurrentUser` from anywhere in your code that's executing within the request scope - routes, services, repositories, or any other layer.

### Thread Safety & Coroutine Context

The plugin ensures thread safety by:
- Using `ThreadLocal` for storage, preventing context leakage between concurrent requests
- Properly propagating context through `asContextElement()` for coroutine suspension points
- Automatically cleaning up context after each request to prevent memory leaks

```kotlin
// The plugin handles this complexity for you:
withContext(CurrentUser.asContextElement()) {
    // Your entire request executes with the user context available
    // Even across suspend functions and different coroutines
}
```

## Configuration

The plugin uses a clean, object-oriented DSL for mapping JWT claims to user context:

### Basic Configuration

```kotlin
install(CurrentUserPlugin) {
    extraction {
        // Core properties - map directly to JWT claims
        userId = string("sub")
        email = string("email")
        tenantId = int("tenant_id")
        roles = list<String>("roles")
    }
}
```

### Nested Object Configuration

You have two approaches for handling complex nested objects:

#### Approach 1: Inline Object Definition
```kotlin
install(CurrentUserPlugin) {
    extraction {
        userId = string("sub")
        email = string("email")
        
        // Define object structure inline
        appMetadata = object("app_metadata") {
            tenantId = int("tenant")
            branchId = int("branch")
            department = string("department")
            permissions = list<String>("permissions")
        }
    }
}
```

#### Approach 2: Serializable Objects
For this approach, add kotlinx-serialization to your dependencies:
```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

```kotlin
@Serializable
data class UserMetadata(
    @SerializedName("email_full")
    val email: String,
    @SerializedName("user_scope")
    val scope: String,
    @SerializedName("user_permissions")
    val permissions: List<String>
)

install(CurrentUserPlugin) {
    extraction {
        userId = string("sub")
        email = string("email")
        
        // Map entire object - @SerializedName handles field mapping
        userMetadata = serializable<UserMetadata>("user_metadata")
        
        // Can mix with inline definitions
        appMetadata = object("app_metadata") {
            tenantId = int("tenant")
            branchId = int("branch")
        }
    }
}
```

### Auth0 Configuration Example

```kotlin
install(CurrentUserPlugin) {
    extraction {
        userId = string("sub")
        email = string("email")
        
        // Auth0 namespaced claims
        appMetadata = object("https://myapp.com/app_metadata") {
            tenantId = int("tenant")
            branchId = int("branch") 
            department = string("dept")
            permissions = list<String>("permissions")
        }
        
        userMetadata = object("https://myapp.com/user_metadata") {
            scope = string("scope")
            email = string("recovery_email")
        }
    }
}
```

### Mixed Direct and Nested Claims

```kotlin
install(CurrentUserPlugin) {
    extraction {
        // Direct claims at JWT root level
        userId = string("sub")
        email = string("email")
        tenantId = int("tenant_id")  // Direct claim
        roles = list<String>("roles")  // Direct claim
        
        // Nested claims in app_metadata
        appMetadata = object("app_metadata") {
            branchId = int("branch_id")
            department = string("department")
            permissions = list<String>("custom_permissions")
        }
    }
}
```

### Accessing Properties

All configured properties are available through `CurrentUser`:

```kotlin
class UserService {
    fun getUserInfo() {
        // Core fields are always available
        val userId: Int = CurrentUser.id
        val tenantId: Int = CurrentUser.tenantId
        val email: String = CurrentUser.email
        
        // Inline object properties accessible by name
        val branchId: Int = CurrentUser.context.require("branchId")
        val department: String = CurrentUser.context.require("department")
        val permissions: List<String> = CurrentUser.context.get("permissions") ?: emptyList()
        
        // Serializable objects accessible as raw data
        val userMetadataRaw = CurrentUser.context.get<Map<String, Any?>>("userMetadata")
        
        // Deserialize to your data class using kotlinx.serialization
        val userMetadata: UserPreferences? = userMetadataRaw?.let { 
            Json.decodeFromString<UserPreferences>(Json.encodeToString(it))
        }
        
        // Property delegation still works
        val scope: String? by CurrentUser.context
    }
}
```

### Type-Safe Property Types

The DSL supports these property types:

- `string(claimPath)` - Maps to String
- `int(claimPath)` - Maps to Int  
- `boolean(claimPath)` - Maps to Boolean
- `list<T>(claimPath)` - Maps to List<T>
- `serializable<T>(claimPath)` - Maps entire object to custom type T
- `object(claimPath) { ... }` - Inline object definition

### Benefits of Serializable Objects

Using `serializable<T>()` with `@SerializedName` annotations provides:

✅ **Clean Property Names** - Use camelCase in Kotlin, snake_case in JSON  
✅ **Type Safety** - Full compile-time type checking  
✅ **IDE Support** - Autocomplete and refactoring  
✅ **Maintainability** - Changes to data structure are centralized  
✅ **Flexibility** - Handle complex nested structures easily

```kotlin
// JWT contains: "user_metadata": { "email_full": "user@example.com", "user_scope": "admin" }
// Your Kotlin code gets clean property names:
val email = userMetadata.email        // Not userMetadata.email_full
val scope = userMetadata.scope        // Not userMetadata.user_scope
```

### Default Values

You can set a default tenant ID if not found in claims:

```kotlin
install(CurrentUserPlugin) {
    extraction {
        userId = string("sub")
        email = string("email")
        tenantId = int("tenant_id")
        
        // Default tenant for single-tenant apps
        defaultTenantId = 1
    }
}
```


## Advanced Usage

### Conditional Access

```kotlin
get("/resource/{id}") {
    val resourceId = call.parameters["id"]?.toInt() ?: 0
    
    CurrentUser.use { user ->
        if (user.owns(resourceId) || user.hasRole("ADMIN")) {
            // Allow access
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}
```

### Testing Services That Use CurrentUser

When testing services that depend on `CurrentUser`, use the `withUserContext` helper:

```kotlin
import com.roastmycode.ktor.currentuser.UserContext
import com.roastmycode.ktor.currentuser.withUserContext

@Test
fun `test document service authorization`() = runBlocking {
    val testUser = UserContext(
        userId = 123,
        tenantId = 456,
        email = "test@example.com",
        roles = setOf("USER"),
        properties = emptyMap()
    )
    
    withUserContext(testUser) {
        // Now your service can access CurrentUser
        val service = DocumentService()
        
        // Test ownership check
        assertFailsWith<ForbiddenException> {
            service.getDocument(999) // Document owned by different user
        }
    }
}

@Test
fun `test admin service`() = runBlocking {
    val adminUser = UserContext(
        userId = 1,
        tenantId = 100,
        email = "admin@example.com",
        roles = setOf("USER", "ADMIN"),
        properties = emptyMap()
    )
    
    withUserContext(adminUser) {
        val service = AdminService()
        val users = service.getAllUsers() // Works because user has ADMIN role
        assertTrue(users.isNotEmpty())
    }
}
```


## Common Use Cases

### Multi-Tenant Applications
```kotlin
class ProductService {
    fun getProducts(): List<Product> {
        // Automatically filter by current tenant
        return productRepository.findByTenantId(CurrentUser.tenantId)
    }
    
    fun createProduct(request: CreateProductRequest): Product {
        return Product(
            name = request.name,
            price = request.price,
            tenantId = CurrentUser.tenantId  // Ensure tenant isolation
        )
    }
}
```

### Audit Logging
```kotlin
class AuditInterceptor {
    fun <T> auditOperation(operation: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = block()
            auditLog.info(
                "User ${CurrentUser.email} performed $operation " +
                "in ${System.currentTimeMillis() - startTime}ms"
            )
            result
        } catch (e: Exception) {
            auditLog.error(
                "User ${CurrentUser.email} failed $operation: ${e.message}"
            )
            throw e
        }
    }
}
```

### Row-Level Security
```kotlin
class DocumentRepository {
    fun findById(id: Int): Document? {
        val doc = db.findDocument(id)
        
        // Automatic security check
        return when {
            doc == null -> null
            CurrentUser.owns(doc.ownerId) -> doc
            CurrentUser.hasRole("ADMIN") -> doc
            CurrentUser.canAccessTenant(doc.tenantId) -> doc
            else -> throw ForbiddenException("Access denied")
        }
    }
}
```


## API Reference

### CurrentUser

- `CurrentUser.id` - User ID
- `CurrentUser.tenantId` - Tenant ID  
- `CurrentUser.email` - Email address
- `CurrentUser.roles` - User roles
- `CurrentUser.hasRole(role)` - Check for specific role
- `CurrentUser.owns(resourceOwnerId)` - Check resource ownership
- `CurrentUser.requireRole(role)` - Require specific role or throw

### Configuration DSL

- `string(claimPath)` - Map string claim
- `int(claimPath)` - Map integer claim
- `list<T>(claimPath)` - Map list claim
- `serializable<T>(claimPath)` - Map complex object
- `object(claimPath) { ... }` - Inline object definition

## Important Notes

### When CurrentUser is Available

- ✅ In route handlers within `authenticate` blocks
- ✅ In services called from authenticated routes
- ✅ In repositories/DAOs called from services
- ✅ In any code executing within the request coroutine scope
- ✅ In suspend functions called within the request

### When CurrentUser is NOT Available

- ❌ In background jobs or scheduled tasks
- ❌ In code running outside the request context
- ❌ In static/companion object methods (unless called from request context)
- ❌ After launching a new coroutine with a different scope

For background tasks that need user context, explicitly pass it:

```kotlin
// In your route or service
val userId = CurrentUser.id
val tenantId = CurrentUser.tenantId

// Launch background job with explicit context
backgroundScope.launch {
    // CurrentUser NOT available here
    processDataForUser(userId, tenantId)
}
```


## Security & Production Ready

### Security Features
✅ **Input Validation**: All JWT claims validated for type, format, and size  
✅ **Injection Protection**: Path traversal and injection attack prevention  
✅ **Memory Safety**: Automatic cleanup prevents context leakage  
✅ **Thread Isolation**: Each request has isolated context using ThreadLocal + coroutine context  
✅ **Fail-Fast Security**: Invalid data throws exceptions instead of silent failures

### Performance & Reliability
✅ **Minimal Overhead**: < 0.1ms per request with O(1) context access  
✅ **Battle-tested Patterns**: ThreadLocal + coroutine context propagation  
✅ **Memory Efficient**: Automatic cleanup after each request  
✅ **Cloud Native**: Works in containerized and serverless environments

### Compliance
✅ **JWT Standards**: RFC 7519 compliant claim handling  
✅ **Email Validation**: RFC 5321 compliant email format checking  
✅ **Role Security**: Validates role format and prevents privilege escalation

## FAQ

**Q: Is this thread-safe?**  
A: Yes! Each request has isolated context using ThreadLocal + coroutine context.

**Q: Does it work with suspending functions?**  
A: Yes, context propagates across all suspend points.

**Q: Can I use custom authentication?**  
A: Yes, implement the `UserContextExtractor` interface.

## Summary

Provides automatic user context access throughout your Ktor application without parameter passing. Eliminates boilerplate, improves API design, and simplifies testing while maintaining performance.

Perfect for multi-tenant SaaS, microservices, and clean architecture implementations.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.