/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package org.cosmic.ide.dependency.resolver

import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.repository.GoogleMaven
import org.cosmic.ide.dependency.resolver.repository.Jitpack
import org.cosmic.ide.dependency.resolver.repository.MavenCentral
import org.cosmic.ide.dependency.resolver.repository.SonatypeSnapshots
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import java.util.concurrent.ConcurrentLinkedQueue

// Global repositories list
val repositories = ConcurrentLinkedQueue<Repository>().apply {
    addAll(listOf(MavenCentral(), GoogleMaven(), Jitpack(), SonatypeSnapshots()))
}

// Define event receiver (you need to implement this properly)
val eventReciever = object {
    fun onArtifactNotFound(artifact: Artifact) {
        println("Artifact not found: ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
    }
}

@OptIn(ExperimentalTime::class)
suspend fun main() {
    val artifact = Artifact("com.github.PranavPurwar", "filepicker", "e6ed776a13")
    println(Jitpack().checkExists(artifact))
    val dir = File("test")
    dir.deleteRecursively()
    dir.mkdir()
    println("Starting...")
    val time = measureTimeMillis {
        artifact.resolveDependencyTree()
        artifact.showDependencyTree()
    }
    artifact.downloadArtifact(dir)
    println("Time taken: $time ms")
}

suspend fun getArtifact(groupId: String, artifactId: String, version: String): Artifact? {
    val artifact = initHost(Artifact(groupId, artifactId, version)) ?: return null

    val pom = artifact.getPOM()!!
    artifact.extension = if (pom.packaging != null && pom.packaging != "bundle") pom.packaging else "jar"

    return artifact
}

/*
 * Finds the host repository of the artifact and initialises it.
 * Returns null if no repository hosts this artifact
 */
fun initHost(artifact: Artifact): Artifact? {
    if (artifact.repository != null) {
        return artifact // Already initialized or repository was set externally
    }
    // Attempt to find a repository only if not already set
    for (repository in repositories) {
        if (repository.checkExists(artifact)) {
            artifact.repository = repository
            return artifact
        }
    }
    eventReciever.onArtifactNotFound(artifact)
    return null
}
