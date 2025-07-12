val kotlin_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
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
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
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