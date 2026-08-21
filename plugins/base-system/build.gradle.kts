description = "PumpeCraft player base and plot plugin"

// PumpeTransactions ist kein Bibliotheksmodul, seine Klassen liegen also nicht auf dem gemeinsamen
// Übersetzungspfad. Gebraucht wird nur die PointsService-Schnittstelle; zur Laufzeit löst sie
// depend: in der plugin.yml auf.
dependencies {
    compileOnly(project(":plugins:transactions"))
    add("ideClasspath", project(":plugins:transactions"))
}
