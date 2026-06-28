package org.cosmic.ide.dependency.resolver

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean
) {
    companion object {
        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://repo.hortonworks.com/content/repositories/releases", "name": "HortanWorks"},
          |    {"url": "https://maven.atlassian.com/content/repositories/atlassian-public", "name": "Atlassian"},
          |    {"url": "https://jcenter.bintray.com", "name": "JCenter"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype"},
          |    {"url": "https://repo.spring.io/plugins-release", "name": "Spring Plugins"},
          |    {"url": "https://repo.spring.io/libs-milestone", "name": "Spring Milestone"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Apache Maven"}
          |]
        """.trimMargin()
        
        private val TYPE_MAP_LIST = object : TypeToken<List<Map<String, String>>>() {}.type
    }

    private val downloadPath: String =
        System.getProperty("user.home") + "/.dependency-resolver/libs/local_libs"

    private val repositoriesJson = Paths.get(
        System.getProperty("user.home"),
        ".dependency-resolver",
        "libs",
        "repositories.json"
    )

    init {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }
        val gson = Gson()
        val reposList: List<Map<String, String>> = gson.fromJson(repositoriesJson.readText(), TYPE_MAP_LIST)
        reposList.forEach { repoMap ->
            val url: String? = repoMap["url"]
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return repoMap["name"] ?: "Unknown"
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        val dependency = getArtifact(groupId, artifactId, version) ?: return@runBlocking

        if (dependency.extension != "jar" && dependency.extension != "aar") {
            callback.invalidPackaging(dependency)
            return@runBlocking
        }

        val libraryJars = getLibraryJars()
        val dependencyClasspath = getDependencyClasspath()

        dependency.downloadTo(
            File(downloadPath + "/${dependency.artifactId}-v${dependency.version}/classes.${dependency.extension}")
                .apply {
                    parentFile?.mkdirs()
                }
        )

        if (dependency.extension == "aar") {
            callback.unzipping(dependency)
            unzipAar(dependency)
            val packageName = findPackageName(
                Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}")
                    .toAbsolutePath().toString(),
                dependency.groupId
            )
            Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}", "config")
                .writeText(packageName)
        }

        val jar = Paths.get(
            downloadPath,
            "${dependency.artifactId}-v${dependency.version}",
            "classes.jar"
        )

        callback.dexing(dependency)
        try {
            compileJar(jar, dependencyClasspath, libraryJars)
            callback.onResolutionComplete(dependency)
        } catch (e: Exception) {
            callback.dexingFailed(dependency, e)
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
            callback.onTaskCompleted(listOf("${dependency.artifactId}-v${dependency.version}"))
            return@runBlocking
        }
        dependency.resolveDependencyTree()

        dependency.getAllDependencies().forEach { dep ->
            println("Resolving dependency: ${dep.artifactId} v${dep.version}")
            if (dep.extension != "jar" && dep.extension != "aar") {
                callback.invalidPackaging(dep)
                return@forEach
            }

            if (dep.version.isEmpty()) {
                callback.onVersionNotFound(dep)
                return@forEach
            }

            val path = Paths.get(
                downloadPath,
                "${dep.artifactId}-v${dep.version}",
                "classes.${dep.extension}"
            )

            Files.createDirectories(path.parent)

            dep.downloadTo(File(path.toString()))

            if (dep.extension == "aar") {
                callback.unzipping(dep)
                unzip(path)
                Files.delete(path)
                val packageName =
                    findPackageName(path.parent.toAbsolutePath().toString(), dep.groupId)
                path.parent.resolve("config").writeText(packageName)
            }

            val jar = if (dep.extension == "jar") path else Paths.get(
                downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar"
            )
            if (Files.notExists(jar)) {
                callback.onDependenciesNotFound(dep)
                return@forEach
            }

            dependencyClasspath.add(jar)
        }

        dependency.getAllDependencies().forEach { dep ->
            val jar = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar")

            callback.dexing(dep)
            try {
                compileJar(
                    jar, dependencyClasspath.toMutableList().apply { remove(jar) }, libraryJars
                )
                callback.onResolutionComplete(dep)
            } catch (e: Exception) {
                callback.dexingFailed(dep, e)
                return@forEach
            }
        }

        callback.onTaskCompleted(
            dependency.getAllDependencies().map { "${it.artifactId}-v${it.version}" })
    }

    // Public methods for external access
    fun getLibraryJars(): List<Path> {
        // Pure Java/Kotlin paths - no Android dependencies
        val userHome = System.getProperty("user.home")
        return listOf(
            Paths.get(userHome, ".dependency-resolver", "compile", "core-lambda-stubs.jar"),
            Paths.get(userHome, ".dependency-resolver", "compile", "android.jar")
        )
    }

    fun getDependencyClasspath(): MutableList<Path> {
        val dependencyClasspath = mutableListOf<Path>()
        // Empty classpath - external class can add
        return dependencyClasspath
    }

    fun getDownloadPath(): String = downloadPath

    fun unzip(path: Path) {
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = path.parent.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun unzipAar(dependency: Artifact) {
        unzip(
            Paths.get(
                downloadPath,
                "${dependency.artifactId}-v${dependency.version}",
                "classes.aar"
            )
        )
        Files.delete(
            Paths.get(
                downloadPath,
                "${dependency.artifactId}-v${dependency.version}",
                "classes.aar"
            )
        )
    }

    fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }
        return defaultValue
    }

    fun compileJar(jarFile: Path, jars: List<Path>, libraryJars: List<Path>) {
        // External class will override this method
        // This is a placeholder - implement in external class
        throw UnsupportedOperationException("compileJar must be implemented externally")
    }
}
