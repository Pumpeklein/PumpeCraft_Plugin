pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// The build compiles against a Java 21 toolchain. Without a resolver every machine needs a JDK 21
// installed and discoverable; with it Gradle downloads a matching JDK on demand instead of failing
// with "No matching toolchains found".
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Paperweight userdev adds repositories for mappings and remapping to its project.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }
}

rootProject.name = "pumpecraft-plugins"

include(":plugins:essentials")
include(":plugins:database")
include(":plugins:utils")
include(":plugins:ai")
include(":plugins:mod")
include(":plugins:clan-system")
include(":plugins:base-system")
include(":plugins:skills")
include(":plugins:trader")
include(":plugins:death-messages")
include(":plugins:playtime")
include(":plugins:anticheat")
include(":plugins:chat-control")
include(":plugins:transactions")
include(":plugins:mailbox")
include(":plugins:sub-essentials")
include(":plugins:enchants")

// plugins/briefkasten is the predecessor of plugins/mailbox and deliberately not included any more.
// The folder only stays around until it gets deleted.
