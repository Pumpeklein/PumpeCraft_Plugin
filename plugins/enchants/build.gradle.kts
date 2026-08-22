description = "PumpeCraft custom enchantments plugin"

// Weder PumpeTransactions noch PumpeMailbox sind Bibliotheksmodule. Gebraucht werden nur
// PointsService und MailboxService; beide werden zur Laufzeit über den ServicesManager geholt,
// damit Lucky und Courier ohne diese Plugins einfach ausfallen statt den Start zu verhindern.
dependencies {
    compileOnly(project(":plugins:transactions"))
    compileOnly(project(":plugins:mailbox"))
    add("ideClasspath", project(":plugins:transactions"))
    add("ideClasspath", project(":plugins:mailbox"))
}
