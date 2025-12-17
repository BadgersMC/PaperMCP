plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.badgersmc"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")

    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    // Config
    implementation("com.typesafe:config:1.4.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
}

application {
    mainClass.set("com.badgersmc.papermcp.client.PaperMCPClientKt")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("PaperMCP-Client-${project.version}.jar")
        manifest {
            attributes["Main-Class"] = "com.badgersmc.papermcp.client.PaperMCPClientKt"
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
