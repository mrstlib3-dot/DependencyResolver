import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

// DEX generation task
tasks.register<Exec>("dex") {
    dependsOn("shadowJar")
    
    val inputJar = tasks.shadowJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("libs").get().asFile
    val outputDex = File(outputDir, "${project.name}-${project.version}.dex")
    
    inputs.file(inputJar)
    outputs.file(outputDex)
    
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    requireNotNull(androidHome) { "ANDROID_HOME or ANDROID_SDK_ROOT environment variable not set." }
    
    val buildToolsDir = File(androidHome, "build-tools")
    val latestBuildTools = buildToolsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.maxByOrNull { it.name }
        ?: error("No build-tools found in Android SDK")
    
    val d8Path = File(latestBuildTools, "d8")
    val dxPath = File(latestBuildTools, "dx")
    
    val dexTool = when {
        d8Path.exists() -> d8Path.absolutePath
        dxPath.exists() -> dxPath.absolutePath
        else -> error("Neither d8 nor dx found in $latestBuildTools")
    }
    
    outputDir.mkdirs()
    
    commandLine(dexTool, "--dex", "--output=${outputDex.absolutePath}", inputJar.absolutePath)
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
