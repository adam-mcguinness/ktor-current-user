val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "com.roastmycode"
version = System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    pom {
        name.set("Ktor Current User")
        description.set("A Ktor plugin that provides thread-safe user context management for authenticated requests")
        url.set("https://github.com/adam-mcguinness/ktor-current-user")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("adam-mcguinness")
                name.set("Adam")
                email.set("adam@innope.com")
            }
        }

        scm {
            url.set("https://github.com/adam-mcguinness/ktor-current-user")
            connection.set("scm:git:git://github.com/adam-mcguinness/ktor-current-user.git")
            developerConnection.set("scm:git:ssh://github.com:adam-mcguinness/ktor-current-user.git")
        }
    }
}
