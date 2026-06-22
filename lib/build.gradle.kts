import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.jrengelman.gradle.plugins.shadow.tasks.ShadowJar

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("java-library")
    id("maven-publish")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"  // ✅ Shadow plugin
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

// ✅ Shadow JAR configuration
tasks.shadowJar {
    // JAR name - "all" suffix இல்லாமல்
    archiveClassifier.set("")
    
    // Merge service files (META-INF/services)
    mergeServiceFiles()
    
    // Main class set பண்ண
    manifest {
        attributes(
            "Main-Class" to "org.cosmic.ide.dependency.resolver.MainKt",
            "Implementation-Title" to "Cosmic IDE Dependency Resolver",
            "Implementation-Version" to "1.0.2"
        )
    }
    
    // Dependencies-ஐ include பண்ண
    configurations = listOf(project.configurations.runtimeClasspath.get())
    
    // Duplicate files-ஐ exclude பண்ண
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // Exclude unnecessary files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// ✅ Default JAR-ஐ disable பண்ண (shadow JAR-ஐ use பண்ண)
tasks.jar {
    enabled = false
}

// ✅ Build-ல் shadowJAR run ஆக
tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// ✅ Publishing-ல் shadow JAR-ஐ publish பண்ண
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.cosmic.ide"
            artifactId = "dependency-resolver"
            version = "1.0.2"
            
            // Shadow JAR-ஐ artifact ஆக add பண்ண
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

// ✅ Optional: Custom shadow JAR task with different name
tasks.register<ShadowJar>("uberJar") {
    archiveClassifier.set("uber")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "org.cosmic.ide.dependency.resolver.MainKt"
    }
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
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
