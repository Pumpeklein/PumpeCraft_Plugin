import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

description = "PumpeCraft essentials plugin"

val transactionsJar = project(":plugins:transactions").tasks.named<Jar>("jar").flatMap { it.archiveFile }

dependencies {
    paperweight.paperDevBundle("26.1.2.build.72-stable")
    compileOnly(project(":plugins:transactions"))
    add("ideClasspath", files(transactionsJar))
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
