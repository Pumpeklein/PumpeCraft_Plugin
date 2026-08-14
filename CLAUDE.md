# PumpeCraft Plugins

Gradle-Multiprojekt mit Paper-Plugins für den PumpeCraft-Server. Java 21, Paper-API aus
`gradle.properties` (`paperApiVersion`).

## Verbindliche Konventionen

**Kommentare.** Keine Kommentare, die beschreiben *was* der Code tut — das steht im Code.
Ein Kommentar ist nur gerechtfertigt, wenn er ein nicht offensichtliches *Warum* festhält:
eine Protokoll-Eigenheit, einen Workaround, eine Entscheidung gegen die naheliegende Lösung.
Javadoc auf einer Klasse oder Methode nur, wenn deren Zweck aus Name und Signatur nicht hervorgeht.

```java
// falsch – beschreibt das Offensichtliche
// Setzt den Brand des Profils
profile.brand(value);

// richtig – hält fest, warum es so und nicht anders ist
// Steuerzeichen werden zu Leerzeichen, sonst wird Forges forge\0FML\0 zu einem Token.
```

**Modularität.** Eine Klasse, eine Aufgabe. Sobald eine Datei mehrere Zuständigkeiten
vermischt, in Subpackages aufteilen (siehe `plugins/anticheat` als Referenz). Listener
enthalten Event-Verdrahtung, die Regeln liegen in eigenen Klassen.

**Gemeinsame Helfer.** Vor dem Schreiben einer Hilfsmethode in `plugins/utils` nachsehen.
Wird sie von mehr als einem Plugin gebraucht, gehört sie dorthin — nicht kopiert werden.
Siehe [plugins/utils/CLAUDE.md](plugins/utils/CLAUDE.md).

**Sprache.** Bezeichner, Logausgaben und Javadoc auf Englisch. Alles, was ein Spieler oder
Teammitglied im Spiel sieht, auf Deutsch.

**Konfiguration.** Jedes Plugin hat `config-version`. Wird das Layout einer bestehenden
Option geändert, gehört eine Migration in die Hauptklasse; `copyDefaults(true)` ergänzt nur
neue Schlüssel, es korrigiert keine alten.

## Module

| Modul | Plugin | Rolle |
| --- | --- | --- |
| `plugins/database` | `PumpeDatabase` | HikariCP-Pool, Flyway-Migrationen, `DatabaseService` als Bukkit-Service |
| `plugins/utils` | `PumpeUtils` | Statische Helfer ohne Zustand, von allen Gameplay-Plugins genutzt |
| `plugins/anticheat` | `PumpeAntiCheat` | Checks, Client-Erkennung, Item-Validierung |
| `plugins/essentials` | `PumpeEssentials` | Inventar- und Enderchest-Zugriff für das Team |
| `plugins/mod` | `PumpeMod` | Bans, Mutes, Reports, Notizen |
| `plugins/clan-system` | `PumpeClanSystem` | Clans, Basen, Tab-Darstellung |
| `plugins/skills` | `PumpeSkills` | Skill-Level, Belohnungen, GUI |
| `plugins/trader` | `PumpeTrader` | Handels-NPCs |
| `plugins/death-messages` | `PumpeDeathMessages` | Todesmeldungen und Zähler |
| `plugins/playtime` | `PumpePlaytime` | Spielzeit-Erfassung |
| `plugins/chat-control` | `PumpeChatControl` | Chatfilter, Privatnachrichten, Persistenz |

`database` und `utils` sind Bibliotheks-Plugins: sie werden per `compileOnly` eingebunden
und zur Laufzeit über `depend:` in der `plugin.yml` aufgelöst. Wer eines davon nutzt, muss
es dort eintragen — sonst schlägt der Klassenzugriff erst im laufenden Server fehl.

## Build

```bash
./gradlew collectPluginJars      # alle Jars nach build/plugins
./gradlew :plugins:anticheat:build
```

Neues Modul: in `settings.gradle.kts` includen und in `pluginModulePaths` in
`build.gradle.kts` eintragen. Bibliotheks-Module zusätzlich in `libraryModulePaths`.
