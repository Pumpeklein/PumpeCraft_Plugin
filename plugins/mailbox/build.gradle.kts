import java.security.MessageDigest
import org.gradle.jvm.tasks.Jar

description = "PumpeCraft mailbox plugin"

val packZip = tasks.register<Zip>("packZip") {
    group = "build"
    description = "Bundles the mailbox resource pack into build/pack."
    from(layout.projectDirectory.dir("pack"))
    // A hand packed archive left in the source folder must not end up inside the built pack.
    exclude("**/*.zip")
    archiveFileName.set("mailbox-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.register("packSha1") {
    group = "build"
    description = "Prints the resource-pack-sha1 line for server.properties."
    val archive = packZip.flatMap { it.archiveFile }
    inputs.file(archive)
    doLast {
        println("resource-pack-sha1=" + sha1(archive.get().asFile))
    }
}

tasks.register<Copy>("deployBundle") {
    group = "build"
    description = "Assembles jars, resource pack and instructions in deploy/mailbox."

    val archive = packZip.flatMap { it.archiveFile }
    val bundleDir = rootProject.layout.projectDirectory.dir("deploy/mailbox")

    into(bundleDir)
    into("plugins") {
        from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
        from(project(":plugins:utils").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    }
    into("resourcepack") {
        from(layout.projectDirectory.dir("pack"))
        exclude("**/*.zip")
    }
    from(archive)
    from(layout.projectDirectory.file("deploy/ANLEITUNG.md"))

    doLast {
        // The prompt keeps its umlaut as a JSON escape: server.properties survives being edited
        // and saved in any encoding, and the client still shows the correct text.
        bundleDir.file("server.properties-snippet.txt").asFile.writeText(
            """
            resource-pack=https://deine-domain.de/mailbox-pack.zip?v=1
            resource-pack-sha1=${sha1(archive.get().asFile)}
            resource-pack-prompt={"text":"F\u00fcr eigene Objekte auf diesem Server"}
            require-resource-pack=true
            """.trimIndent() + System.lineSeparator(),
            Charsets.UTF_8
        )
    }
}

fun sha1(file: File): String =
    MessageDigest.getInstance("SHA-1").digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }
