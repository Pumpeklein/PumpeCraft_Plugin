import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    base
}

val paperApiVersion: String by project
val pluginModulePaths = listOf(
    ":plugins:database",
    ":plugins:essentials",
    ":plugins:mod",
    ":plugins:clan-system",
    ":plugins:skills",
    ":plugins:trader",
    ":plugins:death-messages",
    ":plugins:playtime",
    ":plugins:anticheat",
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

configure(pluginProjects.filter { it.path != ":plugins:database" }) {
    dependencies {
        add("compileOnly", project(":plugins:database"))
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
