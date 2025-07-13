# CurrentUser Plugin Usage Example

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.roastmycode:usercontext:0.0.0-SNAPSHOT")
    
    // Other Ktor dependencies
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
}

// For Maven Local
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Basic Usage

```kotlin
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import com.roastmycode.ktor.currentuser.*
import kotlinx.serialization.Serializable

// Define your metadata structure
@Serializable
data class UserMetadata(
    val tenantId: String,
    val roles: List<String>,
    val email: String
)

fun Application.configureAuth() {
    // Configure JWT authentication
    authentication {
        jwt("auth-jwt") {
            // Your JWT configuration
            verifier(/* your JWT verifier */)
            validate { credential ->
                // Return JWTPrincipal if valid
                JWTPrincipal(credential.payload)
            }
        }
    }
    
    // Install the CurrentUser plugin
    install(CurrentUserPlugin) {
        // Optional: Configure custom JSON settings
        json = Json { 
            ignoreUnknownKeys = true 
        }
        
        // Configure the metadata class for type-safe access
        metadata<UserMetadata>()
    }
}

fun Application.configureRouting() {
    routing {
        authenticate("auth-jwt") {
            get("/profile") {
                // Access user ID (from JWT 'sub' claim)
                val userId = CurrentUser.userId
                
                // Get the full metadata object
                val metadata = CurrentUser.appMetadata<UserMetadata>()
                
                call.respond(mapOf(
                    "userId" to userId,
                    "tenantId" to metadata.tenantId,
                    "email" to metadata.email,
                    "roles" to metadata.roles
                ))
            }
            
            get("/tenant-info") {
                // Direct property access
                val tenantId: String = CurrentUser.get("tenantId")
                val roles: List<String> = CurrentUser.get("roles")
                
                call.respond(mapOf(
                    "tenantId" to tenantId,
                    "userRoles" to roles
                ))
            }
        }
    }
}
```

## Advanced Usage with Extension Properties

For cleaner code, you can define extension properties:

```kotlin
import com.roastmycode.ktor.currentuser.*

// Define extension properties for CurrentUser
val CurrentUser.tenantId: String by currentUserProperty<UserMetadata> { it.tenantId }
val CurrentUser.email: String by currentUserProperty<UserMetadata> { it.email }
val CurrentUser.roles: List<String> by currentUserProperty<UserMetadata> { it.roles }

// Alternative syntax using metadata helper
val CurrentUser.tenantId: String by metadata<UserMetadata> { it.tenantId }

fun Application.configureRouting() {
    routing {
        authenticate("auth-jwt") {
            get("/dashboard") {
                // Use the extension properties - clean and type-safe!
                val tenantId = CurrentUser.tenantId
                val email = CurrentUser.email
                val roles = CurrentUser.roles
                
                call.respond(mapOf(
                    "tenant" to tenantId,
                    "email" to email,
                    "access" to roles
                ))
            }
        }
    }
}
```

## JWT Token Structure

The plugin expects a JWT token with this structure:

```json
{
  "sub": "user123",  // User ID (required)
  "app_metadata": "{\"tenantId\":\"tenant456\",\"roles\":[\"admin\",\"user\"],\"email\":\"user@example.com\"}"
}
```

Note: The `app_metadata` claim should be a JSON string that can be deserialized into your metadata class.

## Logging Configuration

Configure logging levels in your `logback.xml`:

```xml
<configuration>
    <!-- Debug level for plugin installation and configuration -->
    <logger name="com.roastmycode.ktor.currentuser.CurrentUserPlugin" level="DEBUG"/>
    
    <!-- Info level for user operations -->
    <logger name="com.roastmycode.ktor.currentuser.CurrentUser" level="INFO"/>
    
    <!-- Or configure for the entire package -->
    <logger name="com.roastmycode.ktor.currentuser" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Error Handling

The plugin throws specific exceptions for different error scenarios:

```kotlin
get("/protected") {
    try {
        val userId = CurrentUser.userId
        val metadata = CurrentUser.appMetadata<UserMetadata>()
        // ... use the data
    } catch (e: NoCallContextException) {
        // Plugin not installed or accessed outside request context
        call.respond(HttpStatusCode.InternalServerError, "Configuration error")
    } catch (e: AuthenticationRequiredException) {
        // No JWT token present
        call.respond(HttpStatusCode.Unauthorized, "Authentication required")
    } catch (e: InvalidJwtException) {
        // JWT missing required claims
        call.respond(HttpStatusCode.BadRequest, "Invalid token structure")
    } catch (e: MetadataDeserializationException) {
        // Failed to deserialize app_metadata
        call.respond(HttpStatusCode.BadRequest, "Invalid metadata format")
    }
}
```

## Complete Example Application

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        configureAuth()
        configureRouting()
    }.start(wait = true)
}

fun Application.configureAuth() {
    authentication {
        jwt("auth-jwt") {
            realm = "myapp"
            verifier(
                JWT.require(Algorithm.HMAC256("secret"))
                    .withIssuer("myapp")
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
    
    install(CurrentUserPlugin) {
        metadata<UserMetadata>()
    }
}
```