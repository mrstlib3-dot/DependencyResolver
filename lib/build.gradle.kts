import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("java-library")
    id("maven-publish")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Simple shadow JAR configuration
shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "org.cosmic.ide.dependency.resolver.MainKt"
    }
}

tasks.jar {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.cosmic.ide"
            artifactId = "dependency-resolver"
            version = "1.0.2"
            from(components["java"])
        }
    }
}

application {
    mainClass.set("org.cosmic.ide.dependency.resolver.MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.16")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
}
