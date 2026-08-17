description = "DeepSeek text generation service for the PumpeCraft plugins"

// Ein Bibliotheks-Modul bekommt die anderen Bibliotheken nicht automatisch; PumpeAI hängt seine
// erzeugten Texte aber in de.pumpecraft.utils.messages ein.
dependencies {
    compileOnly(project(":plugins:utils"))
}
