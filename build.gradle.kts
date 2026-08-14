import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.eclipse.model.EclipseModel

plugins {
    base
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val pluginModulePaths = listOf(
    ":plugins:database",
    ":plugins:utils",
    ":plugins:essentials",
    ":plugins:mod",
    ":plugins:clan-system",
    ":plugins:skills",
    ":plugins:trader",
    ":plugins:death-messages",
    ":plugins:playtime",
    ":plugins:anticheat",
    ":plugins:chat-control",
    ":plugins:transactions",
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
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    tasks.withType<Jar>().configureEach {
        archiveBaseName.set(project.name)
    }
}

val libraryModulePaths = listOf(":plugins:database", ":plugins:utils")

configure(pluginProjects.filter { it.path !in libraryModulePaths }) {
    apply(plugin = "eclipse")

    dependencies {
        libraryModulePaths.forEach { add("compileOnly", project(it)) }
    }

    // Gradle's Eclipse model drops compileOnly project dependencies, so IDEs that build on
    // it (VS Code / Buildship) cannot resolve de.pumpecraft.database even though the Gradle
    // compile classpath is correct. Re-declare it in an IDE-only configuration; this changes
    // neither the compile classpath nor the produced jars.
    val ideClasspath = configurations.create("ideClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies {
        libraryModulePaths.forEach { add(ideClasspath.name, project(it)) }
    }
    extensions.configure<EclipseModel> {
        classpath.plusConfigurations.add(ideClasspath)
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
