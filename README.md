# Ktor Current User Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.roastmycode/ktor-current-user.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.roastmycode/ktor-current-user)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Ktor plugin that provides thread-safe access to JWT user information anywhere in your application without passing it through method parameters.

```kotlin
// Before: Passing user context everywhere
class OrderService {
    fun createOrder(request: OrderRequest, userId: String): Order {
        return orderRepository.create(request, userId)
    }
}

// After: Access user context directly
class OrderService {
    fun createOrder(request: OrderRequest): Order {
        return orderRepository.create(request, CurrentUser.userId)
    }
}
```

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.roastmycode:ktor-current-user:0.4.0")
}
```

Requirements: Kotlin 1.8+, Ktor 3.0+, Java 11+

## Quick Start

```kotlin
import com.roastmycode.ktor.currentuser.CurrentUserPlugin
import com.roastmycode.ktor.currentuser.CurrentUser

fun Application.module() {
    install(Authentication) {
        jwt("auth-jwt") {
            // Your JWT configuration
        }
    }

    install(CurrentUserPlugin)
}

routing {
    authenticate("auth-jwt") {
        get("/profile") {
            call.respond(mapOf("userId" to CurrentUser.userId))
        }
    }
}

class UserService {
    fun getCurrentUserProfile(): UserProfile {
        return UserProfile(
            id = CurrentUser.userId,
            roles = CurrentUser.roles,
            isAdmin = CurrentUser.isAdmin()
        )
    }
}
```

## How It Works

The plugin intercepts authenticated requests and stores the ApplicationCall and configuration in ThreadLocal storage. The context is propagated through coroutines using `asContextElement()` and automatically cleaned up after the request completes.

This allows `CurrentUser` to be accessed from anywhere in code executing within the request scope: routes, services, repositories, or any layer.

## API Reference

### Properties

```kotlin
// User ID extracted from JWT "sub" claim (default)
// Returns null when accessed from async/background context
val userId: String?

// Roles extracted from JWT "roles" claim (default), empty set if missing
// Returns empty set when accessed from async/background context
val roles: Set<String>

// Permissions extracted from JWT "permissions" claim (default), empty set if missing
// Returns empty set when accessed from async/background context
val permissions: Set<String>
```

### Methods

```kotlin
// Check if CurrentUser context is available (has JWT token in current thread)
fun hasContext(): Boolean

// Check if user has the configured admin role or permission
// Returns true when no context available (for backwards compatibility)
fun isAdmin(): Boolean

// Check if userId matches the provided subject
// Returns true when no context available (for backwards compatibility)
// Optionally throws error based on throwError config
fun owns(sub: String): Boolean

// Check if user owns the resource or is admin
fun ownsOrAdmin(sub: String): Boolean

// Deserialize app_metadata JWT claim to the specified type
// Returns null when accessed from async/background context
inline fun <reified T : Any> appMetadata(): T?
```

## Configuration

### Full Configuration DSL

All configuration options with their defaults:

```kotlin
install(CurrentUserPlugin) {
    // Authorization behavior when owns() fails
    throwError = false  // Default: false
    errorMessage = "You are not authorized to access this."  // Default error message

    // JWT claim extraction (defaults shown)
    extraction {
        user = "sub"  // JWT claim path for user ID
        rolesClaimPath = "roles"  // JWT claim path for roles
        permissionsClaimPath = "permissions"  // JWT claim path for permissions
        json = Json  // JSON serializer for app_metadata
    }

    // Optional: register metadata class for property delegation
    metadata<YourMetadataClass>()

    // OPTIONAL: Admin detection configuration
    // Only include this block if you need isAdmin() functionality
    // Without this block, isAdmin() will throw an error
    adminConfig {
        adminSource = AdminSource.ROLE  // Default: ROLE (options: ROLE or PERMISSION)
        adminRole = "admin"  // Default: "admin" (checked when adminSource = ROLE)
        adminPermission = "admin:super"  // Default: "admin:super" (checked when adminSource = PERMISSION)
    }
}
```

### Minimal Configuration

Using all defaults:

```kotlin
install(CurrentUserPlugin)
```

### Custom JWT Claim Paths

If your JWT uses non-standard claim names:

```kotlin
install(CurrentUserPlugin) {
    extraction {
        user = "userId"
        rolesClaimPath = "user_roles"
        permissionsClaimPath = "user_permissions"
    }
}
```

### Admin Detection via Permission

Check admin status using a permission instead of a role:

```kotlin
install(CurrentUserPlugin) {
    adminConfig {
        adminSource = AdminSource.PERMISSION
        adminPermission = "admin:all"
    }
}
```

### Ownership Validation

Automatically throw errors when ownership checks fail:

```kotlin
install(CurrentUserPlugin) {
    throwError = true
    errorMessage = "Access denied: resource ownership required"
}

// Now owns() throws exception instead of returning false
class DocumentService {
    fun getDocument(ownerId: String): Document {
        CurrentUser.owns(ownerId)  // Throws if userId != ownerId
        return documentRepository.findByOwner(ownerId)
    }
}
```

## Working with app_metadata

If your JWT includes an `app_metadata` claim with structured data, you can deserialize it to a typed object.

### Define Metadata Class

```kotlin
@Serializable
data class AppMetadata(
    val tenantId: Int,
    val organizationId: Int
)
```

### Configure Metadata (Optional)

```kotlin
install(CurrentUserPlugin) {
    metadata<AppMetadata>()
}
```

### Option 1: Direct Access

```kotlin
class DocumentService {
    fun getDocuments(): List<Document> {
        val metadata = CurrentUser.appMetadata<AppMetadata>()
        return docRepository.findByTenant(metadata.tenantId)
    }
}
```

### Option 2: Property Delegation

Create extension properties once:

```kotlin
import com.roastmycode.ktor.currentuser.currentUserProperty

val CurrentUser.tenantId: Int by currentUserProperty<AppMetadata, Int> { it.tenantId }
val CurrentUser.organizationId: Int by currentUserProperty<AppMetadata, Int> { it.organizationId }
```

Then use them anywhere:

```kotlin
class DocumentService {
    fun getDocuments(): List<Document> {
        return docRepository.findByTenant(CurrentUser.tenantId)
    }
}
```

## Examples

### Multi-Tenant Application

```kotlin
@Serializable
data class AppMetadata(val tenantId: Int)

val CurrentUser.tenantId: Int by currentUserProperty<AppMetadata, Int> { it.tenantId }

class ProductService {
    fun getProducts(): List<Product> {
        return productRepository.findByTenantId(CurrentUser.tenantId)
    }
}
```

### Role-Based Authorization

```kotlin
class AdminService {
    fun deleteUser(userId: String) {
        require(CurrentUser.isAdmin()) { "Admin role required" }
        userRepository.delete(userId)
    }
}
```

### Permission-Based Authorization

```kotlin
class DocumentService {
    fun deleteDocument(id: Int) {
        require(CurrentUser.permissions.contains("delete:documents")) {
            "Missing permission: delete:documents"
        }
        documentRepository.delete(id)
    }
}
```

### Ownership Checks

```kotlin
class DocumentService {
    fun getDocument(document: Document): Document {
        // Check if current user owns this document
        if (!CurrentUser.owns(document.userId)) {
            throw ForbiddenException("Not authorized")
        }
        return document
    }

    fun updateDocument(document: Document, request: UpdateRequest) {
        // Allow if user owns it OR is admin
        if (!CurrentUser.ownsOrAdmin(document.userId)) {
            throw ForbiddenException("Not authorized")
        }
        documentRepository.update(document.id, request)
    }
}
```

## When CurrentUser is Available

CurrentUser works in any code executing within an authenticated request:
- Route handlers inside `authenticate` blocks
- Services called from authenticated routes
- Repositories/DAOs called from services
- Any code in the request coroutine scope
- Suspend functions called within the request

CurrentUser is NOT available:
- In background jobs or scheduled tasks
- In code running outside the request context
- After launching a new coroutine with a different scope

### Handling Async/Background Tasks

Starting from version 0.4.0, CurrentUser handles missing context gracefully instead of throwing exceptions. When accessed from background tasks or async processes without JWT context:

- `userId` returns `null` instead of throwing
- `roles` and `permissions` return empty sets
- `appMetadata()` returns `null`
- `hasContext()` returns `false` to check availability

For background tasks, you can now safely check context:

```kotlin
backgroundScope.launch {
    // Check if context is available
    if (CurrentUser.hasContext()) {
        val userId = CurrentUser.userId
        processDataForUser(userId)
    } else {
        // Handle missing context case
        // Or explicitly pass data from the request context
    }
}
```

Or continue to explicitly pass the data:

```kotlin
val userId = CurrentUser.userId  // Get it while in request context
backgroundScope.launch {
    processDataForUser(userId)  // Use the captured value
}
```

## Error Handling

The plugin throws these exceptions when JWT context is available but invalid:

- `AuthenticationRequiredException` - No JWT principal found when context exists
- `InvalidJwtException` - Required JWT claim missing or invalid
- `MetadataDeserializationException` - Failed to deserialize app_metadata
- `CurrentUserException` - Base exception class for all plugin errors

Behavior when accessed from async/background context (no JWT available):
- `userId` returns `null` (no exception thrown)
- `roles` and `permissions` return empty sets
- `appMetadata()` returns `null`
- `isAdmin()` and `owns()` return `true` for backwards compatibility
- `hasContext()` returns `false`

Missing claim behavior (when JWT context exists):
- If `roles` or `permissions` claims are missing/null, returns empty set
- If `sub` claim (or configured user claim) is missing, throws `InvalidJwtException`
- If `app_metadata` is missing when accessed, throws `InvalidJwtException`

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
