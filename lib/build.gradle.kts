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

// DEX generation task with correct d8/dx commands
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
        
        val (dexTool, commandArgs) = when {
            d8Path.exists() -> {
                println("Using d8: ${d8Path.absolutePath}")
                // d8 uses different syntax: d8 --lib ... --output ... input.jar
                Pair(
                    d8Path.absolutePath,
                    listOf(
                        "--lib", "${androidHome}/platforms/android-34/android.jar",
                        "--output", outputDex.absolutePath,
                        inputJar.absolutePath
                    )
                )
            }
            dxPath.exists() -> {
                println("Using dx: ${dxPath.absolutePath}")
                // dx uses --dex --output
                Pair(
                    dxPath.absolutePath,
                    listOf(
                        "--dex",
                        "--output=${outputDex.absolutePath}",
                        inputJar.absolutePath
                    )
                )
            }
            else -> throw GradleException("Neither d8 nor dx found in ${toolDir.absolutePath}")
        }
        
        // Run dex command
        println("Generating DEX file: ${outputDex.absolutePath}")
        println("Command: $dexTool ${commandArgs.joinToString(" ")}")
        
        val process = ProcessBuilder(listOf(dexTool) + commandArgs)
            .redirectErrorStream(true)
            .directory(outputDir)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        
        if (output.isNotEmpty()) {
            println("Process output: $output")
        }
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

// Alternative using Android Gradle plugin if available
tasks.register("dexSimple") {
    dependsOn("shadowJar")
    group = "build"
    
    doLast {
        val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
        val outputDir = layout.buildDirectory.dir("libs").get().asFile
        val outputDex = File(outputDir, "${project.name}-${project.version}.dex")
        
        outputDir.mkdirs()
        
        // Try using d8 directly
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (androidHome == null) {
            throw GradleException("ANDROID_HOME not set")
        }
        
        // Try multiple build-tools versions
        val buildToolsDirs = File(androidHome, "build-tools").listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?: emptyList()
        
        var success = false
        for (buildTools in buildToolsDirs) {
            val d8 = File(buildTools, "d8")
            if (d8.exists()) {
                try {
                    println("Trying d8 from ${buildTools.name}")
                    
                    val platformJar = File("${androidHome}/platforms/android-34/android.jar")
                    if (!platformJar.exists()) {
                        println("Warning: android.jar not found at ${platformJar.absolutePath}")
                    }
                    
                    val command = mutableListOf(
                        d8.absolutePath,
                        "--output", outputDex.absolutePath,
                        inputJar.absolutePath
                    )
                    
                    if (platformJar.exists()) {
                        command.add(1, "--lib")
                        command.add(2, platformJar.absolutePath)
                    }
                    
                    val process = ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start()
                    
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    
                    if (exitCode == 0 && outputDex.exists()) {
                        println("✅ DEX created with d8 (${buildTools.name})")
                        success = true
                        break
                    } else {
                        println("d8 failed: exitCode=$exitCode, output=$output")
                    }
                } catch (e: Exception) {
                    println("d8 error: ${e.message}")
                }
            }
        }
        
        if (!success) {
            throw GradleException("Failed to create DEX file using any d8 version")
        }
    }
}

// Check environment
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
                
                versions.sortedDescending().forEach { version ->
                    val d8 = File(File(buildToolsDir, version), "d8")
                    val dx = File(File(buildToolsDir, version), "dx")
                    println("  $version - d8: ${d8.exists()}, dx: ${dx.exists()}")
                }
            }
            
            // Check if android.jar exists
            val platformsDir = File(androidHome, "platforms")
            if (platformsDir.exists()) {
                val platforms = platformsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                println("Available platforms: $platforms")
                platforms.forEach { platform ->
                    val androidJar = File(File(platformsDir, platform), "android.jar")
                    println("  $platform - android.jar: ${androidJar.exists()}")
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
