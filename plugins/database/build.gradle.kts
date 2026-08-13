plugins {
    id("com.gradleup.shadow") version "9.4.2"
}

description = "Shared PumpeCraft MariaDB service and schema migrations"

dependencies {
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    implementation("org.flywaydb:flyway-core:11.10.5")
    implementation("org.flywaydb:flyway-mysql:11.10.5")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Shadow drops duplicate paths before the transformers run, so mergeServiceFiles() would
    // silently keep only the last META-INF/services/org.flywaydb.core.extensibility.Plugin
    // file: flyway-mysql's two entries would replace flyway-core's 28. Flyway then starts
    // without its core plugins and rejects every migration as "Unrecognised migration name
    // format". Letting duplicates through for service files only keeps the rest of the jar
    // free of duplicate entries.
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
