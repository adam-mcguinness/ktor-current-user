# Ktor Current User Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.roastmycode/ktor-current-user.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.roastmycode%22%20AND%20a:%22ktor-current-user%22)
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
    implementation("com.roastmycode:ktor-current-user:1.0.0")
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
val userId: String

// Roles extracted from JWT "roles" claim (default), empty set if missing
val roles: Set<String>

// Permissions extracted from JWT "permissions" claim (default), empty set if missing
val permissions: Set<String>
```

### Methods

```kotlin
// Check if user has the configured admin role or permission
fun isAdmin(): Boolean

// Check if userId matches the provided subject
// Optionally throws error based on throwError config
fun owns(sub: String): Boolean

// Check if user owns the resource or is admin
fun ownsOrAdmin(sub: String): Boolean

// Deserialize app_metadata JWT claim to the specified type
inline fun <reified T : Any> appMetadata(): T
```

## Configuration

### Full Configuration DSL

All configuration options with their defaults:

```kotlin
install(CurrentUserPlugin) {
    // Authorization behavior when owns() fails
    throwError = false  // If true, throws exception when owns() returns false
    errorMessage = "You are not authorized to access this."

    // JWT claim extraction
    extraction {
        user = "sub"  // JWT claim path for user ID
        rolesClaimPath = "roles"  // JWT claim path for roles
        permissionsClaimPath = "permissions"  // JWT claim path for permissions
        json = Json  // JSON instance for deserializing app_metadata
        metadata<YourMetadataClass>()  // Optional: register metadata class
    }

    // Admin detection configuration
    adminConfig {
        adminSource = AdminSource.ROLE  // Use ROLE or PERMISSION
        adminRole = "admin"  // Role to check when adminSource = ROLE
        adminPermission = "admin:super"  // Permission to check when adminSource = PERMISSION
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
    extraction {
        metadata<AppMetadata>()
    }
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
    fun updateDocument(id: Int, ownerId: String, request: UpdateRequest) {
        if (!CurrentUser.ownsOrAdmin(ownerId)) {
            throw ForbiddenException("Not authorized")
        }
        documentRepository.update(id, request)
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

For background tasks, explicitly pass the data:

```kotlin
val userId = CurrentUser.userId
backgroundScope.launch {
    // CurrentUser NOT available here
    processDataForUser(userId)
}
```

## Error Handling

The plugin throws these exceptions:

- `AuthenticationRequiredException` - No JWT principal found
- `InvalidJwtException` - Required JWT claim missing or invalid
- `NoCallContextException` - Accessing CurrentUser outside request context
- `MetadataDeserializationException` - Failed to deserialize app_metadata
- `CurrentUserException` - Base exception class for all plugin errors

Missing claim behavior:
- If `roles` or `permissions` claims are missing/null, returns empty set
- If `sub` claim (or configured user claim) is missing, throws `InvalidJwtException`
- If `app_metadata` is missing when accessed, throws `InvalidJwtException`

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
