description = "PumpeCraft skills plugin"

// PumpeEnchants ist kein Bibliotheksmodul, seine Klassen liegen also nicht auf dem gemeinsamen
// Übersetzungspfad. Gebraucht wird nur EnchantService für den Gelehrter-Zuschlag; fehlt das
// Plugin zur Laufzeit, rechnet ScholarBonus ohne Zuschlag weiter.
dependencies {
    compileOnly(project(":plugins:enchants"))
    add("ideClasspath", project(":plugins:enchants"))
}
