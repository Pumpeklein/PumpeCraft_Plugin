pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
include(":plugins:mod")
include(":plugins:clan-system")
include(":plugins:skills")
include(":plugins:trader")
include(":plugins:death-messages")
include(":plugins:playtime")
