import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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




// Force Java compile to use Java 8
tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(8)  // Force Java 8
}

// Also set toolchain
kotlin {
    jvmToolchain(8)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "org.cosmic.ide.dependency.resolver.MainKt"
    }

    exclude("META-INF/versions/**")
    exclude("module-info.class")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    enabled = false
}

// DEX generation task
tasks.register("dex") {
    dependsOn("shadowJar")
    group = "build"
    description = "Converts JAR to DEX format for Android"
    
    doLast {
        val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
        val outputDir = layout.buildDirectory.dir("dex").get().asFile
        val dexOutputDir = File(outputDir, "classes")
        val outputDex = File(layout.buildDirectory.dir("libs").get().asFile, "${project.name}-${project.version}.dex")
        
        println("Input JAR: ${inputJar.absolutePath}")
        println("Input JAR exists: ${inputJar.exists()}")
        println("Input JAR size: ${inputJar.length()} bytes")
        
        if (!inputJar.exists()) {
            throw GradleException("Input JAR not found: ${inputJar.absolutePath}")
        }
        
        // Clean and create output directories
        dexOutputDir.deleteRecursively()
        dexOutputDir.mkdirs()
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
        
        // Find d8
        val toolDir = latestBuildTools.second
        val d8Path = File(toolDir, "d8")
        
        if (!d8Path.exists()) {
            throw GradleException("d8 not found in ${toolDir.absolutePath}")
        }
        
        println("Using d8: ${d8Path.absolutePath}")
        
        // Find android.jar
        val platformDir = File("${androidHome}/platforms")
        val platforms = platformDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
        val androidJar = platforms.firstOrNull { File(it, "android.jar").exists() }?.let { File(it, "android.jar") }
        
        if (androidJar == null) {
            println("Warning: android.jar not found, using default")
        } else {
            println("Using android.jar: ${androidJar.absolutePath}")
        }
        
        // Build d8 command - output to directory
        val command = mutableListOf(
            d8Path.absolutePath,
            "--output", dexOutputDir.absolutePath,
            inputJar.absolutePath
        )
        
        if (androidJar != null && androidJar.exists()) {
            command.add(1, "--lib")
            command.add(2, androidJar.absolutePath)
        }
        
        println("Command: ${command.joinToString(" ")}")
        
        val process = ProcessBuilder(command)
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
        
        // Check if DEX files were created
        val dexFiles = dexOutputDir.listFiles()?.filter { it.extension == "dex" } ?: emptyList()
        
        if (dexFiles.isEmpty()) {
            throw GradleException("No DEX files were created in ${dexOutputDir.absolutePath}")
        }
        
        println("Found ${dexFiles.size} DEX file(s):")
        dexFiles.forEach { println("  - ${it.name} (${it.length()} bytes)") }
        
        // If multiple DEX files (multi-dex), combine them or use the first one
        if (dexFiles.size == 1) {
            // Single DEX file
            dexFiles.first().copyTo(outputDex, overwrite = true)
            println("✅ DEX file created: ${outputDex.absolutePath}")
            println("DEX file size: ${outputDex.length()} bytes")
        } else {
            // Multi-dex: zip them or copy all
            println("Multi-dex detected. Creating DEX archive...")
            
            // Create a zip containing all DEX files
            val dexZip = File(layout.buildDirectory.dir("libs").get().asFile, "${project.name}-${project.version}.dex.zip")
            ZipOutputStream(dexZip.outputStream()).use { zos ->
                dexFiles.forEach { dexFile ->
                    val entry = ZipEntry(dexFile.name)
                    zos.putNextEntry(entry)
                    dexFile.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            
            // Also copy the first DEX as the main one
            dexFiles.first().copyTo(outputDex, overwrite = true)
            println("✅ Multi-DEX archive created: ${dexZip.absolutePath}")
            println("✅ Primary DEX file: ${outputDex.absolutePath}")
        }
        
        // Clean up temporary directory
        dexOutputDir.deleteRecursively()
    }
}

// Simple task using directory output
tasks.register("dexSimple") {
    dependsOn("shadowJar")
    group = "build"
    
    doLast {
        val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
        val dexDir = layout.buildDirectory.dir("dex_temp").get().asFile
        val outputDex = File(layout.buildDirectory.dir("libs").get().asFile, "${project.name}-${project.version}.dex")
        
        dexDir.mkdirs()
        
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (androidHome == null) {
            throw GradleException("ANDROID_HOME not set")
        }
        
        // Find d8
        val buildToolsDir = File(androidHome, "build-tools")
        val d8 = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstNotNullOfOrNull { dir ->
                val d8File = File(dir, "d8")
                if (d8File.exists()) d8File else null
            }
        
        if (d8 == null) {
            throw GradleException("d8 not found in build-tools")
        }
        
        println("Using d8: ${d8.absolutePath}")
        
        // Run d8 with directory output
        val command = listOf(
            d8.absolutePath,
            "--output", dexDir.absolutePath,
            inputJar.absolutePath
        )
        
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw GradleException("d8 failed: $output")
        }
        
        // Find DEX files
        val dexFiles = dexDir.listFiles()?.filter { it.extension == "dex" } ?: emptyList()
        if (dexFiles.isEmpty()) {
            throw GradleException("No DEX files generated")
        }
        
        // Copy first DEX
        dexFiles.first().copyTo(outputDex, overwrite = true)
        println("✅ DEX created: ${outputDex.absolutePath}")
        println("Size: ${outputDex.length()} bytes")
        
        // Cleanup
        dexDir.deleteRecursively()
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
                buildToolsDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.forEach { version ->
                        val d8 = File(version, "d8")
                        println("  ${version.name} - d8: ${d8.exists()}")
                    }
            }
            
            val platformDir = File(androidHome, "platforms")
            if (platformDir.exists()) {
                platformDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.forEach { platform ->
                        val androidJar = File(platform, "android.jar")
                        println("  ${platform.name} - android.jar: ${androidJar.exists()}")
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
