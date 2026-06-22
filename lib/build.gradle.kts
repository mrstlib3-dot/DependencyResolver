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

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "org.cosmic.ide.dependency.resolver.MainKt"
    }
}

tasks.jar {
    enabled = false
}

// NEW: Task to generate DEX file
tasks.register<Exec>("dex") {
    dependsOn("shadowJar")
    
    val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
    val outputDex = file("${buildDir}/libs/${project.name}-${project.version}.dex")
    
    inputs.file(inputJar)
    outputs.file(outputDex)
    
    // Path to Android SDK's dx/d8 tool
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    
    // Try using d8 (preferred) or fallback to dx
    val dexTool = if (androidHome != null) {
        val d8Path = file("$androidHome/build-tools/30.0.3/d8")
        val dxPath = file("$androidHome/build-tools/30.0.3/dx")
        
        when {
            d8Path.exists() -> d8Path.absolutePath
            dxPath.exists() -> dxPath.absolutePath
            else -> throw GradleException("Neither d8 nor dx found in Android SDK. Please install build tools.")
        }
    } else {
        throw GradleException("ANDROID_HOME or ANDROID_SDK_ROOT environment variable not set.")
    }
    
    commandLine(
        dexTool,
        "--dex",
        "--output=${outputDex.absolutePath}",
        inputJar.absolutePath
    )
    
    // For dx tool (older Android SDKs), use:
    // commandLine(
    //     dexTool,
    //     "--dex",
    //     "--output=${outputDex.absolutePath}",
    //     inputJar.absolutePath
    // )
}

// Also add to build task to generate both
tasks.build {
    dependsOn("dex")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.cosmic.ide"
            artifactId = "dependency-resolver"
            version = "1.0.2"
            artifact(tasks.shadowJar)
            // Also publish DEX if needed
            artifact(tasks.register<Copy>("dexArtifact") {
                from(tasks.named("dex").map { it.outputs.files.singleFile })
                into(buildDir.resolve("libs"))
                rename { "${project.name}-${project.version}.dex" }
            }.map { it.destinationDir.resolve("${project.name}-${project.version}.dex") }) {
                classifier = "dex"
                extension = "dex"
            }
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
}publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.cosmic.ide"
            artifactId = "dependency-resolver"
            version = "1.0.2"
            artifact(tasks.shadowJar)
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
