import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    base
    id("jvm-toolchains")
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val pluginModulePaths = listOf(
    ":plugins:database",
    ":plugins:utils",
    ":plugins:ai",
    ":plugins:essentials",
    ":plugins:mod",
    ":plugins:clan-system",
    ":plugins:base-system",
    ":plugins:skills",
    ":plugins:trader",
    ":plugins:death-messages",
    ":plugins:playtime",
    ":plugins:anticheat",
    ":plugins:chat-control",
    ":plugins:transactions",
    ":plugins:mailbox",
    ":plugins:sub-essentials",
    ":plugins:enchants",
)
val pluginProjects = pluginModulePaths.map { project(it) }

allprojects {
    apply(plugin = "base")

    group = "de.pumpecraft"
    version = "26.1.2"
}

project(":plugins") {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("intermediate/plugins"))
}

configure(pluginProjects) {
    apply(plugin = "java-library")

    val pluginVersion = version.toString()

    dependencies {
        add("compileOnly", "io.papermc.paper:paper-api:$paperApiVersion")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.named<ProcessResources>("processResources") {
        filteringCharset = "UTF-8"
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand("version" to pluginVersion)
        }
    }

    tasks.withType<Jar>().configureEach {
        archiveBaseName.set(project.name)
    }
}

val libraryModulePaths = listOf(":plugins:database", ":plugins:utils", ":plugins:ai")

configure(pluginProjects.filter { it.path !in libraryModulePaths }) {
    dependencies {
        libraryModulePaths.forEach { add("compileOnly", project(it)) }
    }
}

// Gradle's Eclipse model drops compileOnly project dependencies, so IDEs that build on it
// (VS Code / Buildship) cannot resolve de.pumpecraft.utils even though the Gradle compile
// classpath is correct. Mirroring every compileOnly project dependency into an IDE-only
// configuration covers each module, current and future; it changes neither the compile
// classpath nor the produced jars.
configure(pluginProjects) {
    apply(plugin = "eclipse")

    val ideClasspath = configurations.create("ideClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    extensions.configure<EclipseModel> {
        classpath.plusConfigurations.add(ideClasspath)
    }

    afterEvaluate {
        val mirrored = configurations.getByName("compileOnly").dependencies
            .withType(ProjectDependency::class.java)
            .map { it.path }
        dependencies {
            mirrored.forEach { add(ideClasspath.name, project(it)) }
        }
    }
}

tasks.register<Copy>("collectPluginJars") {
    group = "build"
    description = "Copies all plugin jars into build/plugins."
    outputs.upToDateWhen { false }

    pluginProjects.forEach { pluginProject ->
        val jarTask = if (pluginProject.path == ":plugins:database") {
            pluginProject.tasks.named<Jar>("shadowJar")
        } else {
            pluginProject.tasks.named<Jar>("jar")
        }
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("plugins"))
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("plugins/build"))
}

// The IDE has to be told which JDK belongs to which execution environment; without it the
// Java language server leaves the container of a module unbound ("Unbound classpath container:
// JRE System Library [JavaSE-25]") and then fails to resolve even java.lang.Object, while the
// command line build stays green. Deriving the entries from the toolchains this build actually
// uses keeps both in step - after changing a toolchain, run this task again.
val javaToolchains = extensions.getByType<JavaToolchainService>()
val ideJavaHomes = mutableMapOf<Int, Provider<String>>()
val gradleJavaHome: String = System.getProperty("java.home")

gradle.projectsEvaluated {
    pluginProjects
        .map { it.extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.get().asInt() }
        .distinct()
        .forEach { languageVersion ->
            ideJavaHomes[languageVersion] = javaToolchains.launcherFor {
                this.languageVersion.set(JavaLanguageVersion.of(languageVersion))
            }.map { it.metadata.installationPath.asFile.absolutePath }
        }
}

fun readSettingsJson(file: File): MutableMap<String, Any> {
    if (!file.isFile || file.readText().isBlank()) return linkedMapOf()
    val parsed = try {
        JsonSlurper().parseText(file.readText())
    } catch (cause: Exception) {
        throw GradleException("$file is no valid JSON, fix it before running syncIdeConfig", cause)
    }
    @Suppress("UNCHECKED_CAST")
    return parsed as MutableMap<String, Any>
}

fun mergeIdeSettings(file: File, managed: Map<String, Any>) {
    val root = readSettingsJson(file)
    @Suppress("UNCHECKED_CAST")
    val target = if (file.name.endsWith(".code-workspace")) {
        root.getOrPut("settings") { linkedMapOf<String, Any>() } as MutableMap<String, Any>
    } else {
        root
    }
    target.putAll(managed)
    file.parentFile.mkdirs()
    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)) + "\n")
}

fun workspaceContains(workspaceFile: File, folder: File): Boolean {
    val root = readSettingsJson(workspaceFile)
    val folders = root["folders"] as? List<*> ?: return false
    return folders.filterIsInstance<Map<*, *>>().any { entry ->
        val path = entry["path"] as? String ?: return@any false
        File(workspaceFile.parentFile, path).canonicalFile == folder.canonicalFile
    }
}

tasks.register("syncIdeConfig") {
    group = "ide"
    description = "Writes the JDKs this build uses into the VS Code settings."
    outputs.upToDateWhen { false }

    val rootDir = layout.projectDirectory.asFile
    doLast {
        val runtimes = ideJavaHomes.toSortedMap().map { (languageVersion, javaHome) ->
            linkedMapOf("name" to "JavaSE-$languageVersion", "path" to javaHome.get())
        }
        check(runtimes.isNotEmpty()) { "No Java toolchain resolved, cannot write IDE settings" }

        val managed = linkedMapOf<String, Any>(
            "java.configuration.runtimes" to runtimes,
            "java.import.gradle.wrapper.enabled" to true,
            // Same JVM as the terminal build, so the IDE reuses its Gradle daemon instead of
            // starting a second one with a different toolchain view.
            "java.import.gradle.java.home" to gradleJavaHome,
            "java.configuration.updateBuildConfiguration" to "automatic",
        )

        val targets = mutableListOf(File(rootDir, ".vscode/settings.json"))
        // These settings are window scoped: inside a multi root workspace VS Code reads them from
        // the .code-workspace file and ignores the folder settings, so both have to carry them.
        rootDir.parentFile?.listFiles { file: File -> file.name.endsWith(".code-workspace") }
            ?.filter { workspaceContains(it, rootDir) }
            ?.forEach { targets.add(it) }

        targets.forEach { file ->
            mergeIdeSettings(file, managed)
            logger.lifecycle("IDE settings written: $file")
        }
        runtimes.forEach { logger.lifecycle("  ${it["name"]} -> ${it["path"]}") }
    }
}
