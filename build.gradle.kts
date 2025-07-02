val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "com.roastmycode"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.roastmycode", "ktor-current-user", "0.0.1")

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