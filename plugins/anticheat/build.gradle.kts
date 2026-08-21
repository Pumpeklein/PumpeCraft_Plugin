description = "PumpeCraft anticheat plugin"

// PumpeEnchants ist kein Bibliotheksmodul. Gebraucht wird nur EnchantService, um Blockprüfungen
// während eines Aderabbaus auszusetzen; fehlt das Plugin, prüft der AntiCheat wie bisher.
dependencies {
    compileOnly(project(":plugins:enchants"))
    add("ideClasspath", project(":plugins:enchants"))
}
