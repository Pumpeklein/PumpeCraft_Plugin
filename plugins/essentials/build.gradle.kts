import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

description = "PumpeCraft essentials plugin"

dependencies {
    paperweight.paperDevBundle("26.1.2.build.72-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.add("-Xlint:unchecked")
}

paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
