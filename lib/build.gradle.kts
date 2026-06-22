import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("java-library")
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

// DEX generation task with better logging
tasks.register("dex") {
    dependsOn("shadowJar")
    group = "build"
    description = "Converts JAR to DEX format for Android"
    
    doLast {
        val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
        val outputDir = layout.buildDirectory.dir("libs").get().asFile
        val outputDex = File(outputDir, "${project.name}-${project.version}.dex")
        
        println("Input JAR: ${inputJar.absolutePath}")
        println("Input JAR exists: ${inputJar.exists()}")
        println("Input JAR size: ${inputJar.length()} bytes")
        
        if (!inputJar.exists()) {
            throw GradleException("Input JAR not found: ${inputJar.absolutePath}")
        }
        
        // Ensure output directory exists
        outputDir.mkdirs()
        
        // Get Android SDK path
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (androidHome == null) {
            throw GradleException("ANDROID_HOME or ANDROID_SDK_ROOT environment variable not set.")
        }
        println("Android SDK path: $androidHome")
        
        // Find build-tools
        val buildToolsDir = File(androidHome, "build-tools")
        if (!buildToolsDir.exists()) {
            throw GradleException("Build-tools directory not found: ${buildToolsDir.absolutePath}")
        }
        
        val buildToolsVersions = buildToolsDir.listFiles()?.filter { it.isDirectory }?.map { it.name to it } ?: emptyList()
        println("Available build-tools versions: ${buildToolsVersions.map { it.first }}")
        
        val latestBuildTools = buildToolsVersions.maxByOrNull { it.first }
        if (latestBuildTools == null) {
            throw GradleException("No build-tools found in Android SDK")
        }
        println("Using build-tools: ${latestBuildTools.first}")
        
        // Find d8 or dx
        val toolDir = latestBuildTools.second
        val d8Path = File(toolDir, "d8")
        val dxPath = File(toolDir, "dx")
        
        val dexTool = when {
            d8Path.exists() -> {
                println("Using d8: ${d8Path.absolutePath}")
                d8Path.absolutePath
            }
            dxPath.exists() -> {
                println("Using dx: ${dxPath.absolutePath}")
                dxPath.absolutePath
            }
            else -> throw GradleException("Neither d8 nor dx found in ${toolDir.absolutePath}")
        }
        
        // Run dex command
        println("Generating DEX file: ${outputDex.absolutePath}")
        
        val command = listOf(
            dexTool,
            "--dex",
            "--output=${outputDex.absolutePath}",
            inputJar.absolutePath
        )
        println("Command: ${command.joinToString(" ")}")
        
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .directory(outputDir)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        
        println("Process output: $output")
        if (error.isNotEmpty()) {
            println("Process error: $error")
        }
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("DEX generation failed with exit code $exitCode")
        }
        
        // Verify output
        if (!outputDex.exists()) {
            throw GradleException("DEX file was not created: ${outputDex.absolutePath}")
        }
        
        println("✅ DEX file created successfully: ${outputDex.absolutePath}")
        println("DEX file size: ${outputDex.length()} bytes")
    }
}

// Also create a simpler task for debugging
tasks.register("checkAndroidEnv") {
    group = "verification"
    description = "Check Android SDK environment"
    
    doLast {
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        println("ANDROID_HOME: $androidHome")
        
        if (androidHome != null) {
            val buildToolsDir = File(androidHome, "build-tools")
            println("Build-tools exists: ${buildToolsDir.exists()}")
            
            if (buildToolsDir.exists()) {
                val versions = buildToolsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                println("Available versions: $versions")
                
                versions.forEach { version ->
                    val d8 = File(File(buildToolsDir, version), "d8")
                    val dx = File(File(buildToolsDir, version), "dx")
                    println("  $version - d8: ${d8.exists()}, dx: ${dx.exists()}")
                }
            }
        }
    }
}

tasks.build {
    dependsOn("dex")
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
