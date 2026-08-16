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

**Chatmeldungen.** Spielernamen und Koordinaten in Meldungen an das Team sind klickbare
Teleport-Ziele über `Teleports` aus `plugins/utils`. Der Klick schreibt den Befehl in die
Chatzeile, er wird nie automatisch ausgeführt. Koordinaten als reiner Text sind ein Fehler.

**Konfiguration.** Jedes Plugin hat `config-version`. Wird das Layout einer bestehenden
Option geändert, gehört eine Migration in die Hauptklasse; `copyDefaults(true)` ergänzt nur
neue Schlüssel, es korrigiert keine alten.

## Module

| Modul | Plugin | Rolle |
| --- | --- | --- |
| `plugins/database` | `PumpeDatabase` | HikariCP-Pool, Flyway-Migrationen, `DatabaseService` als Bukkit-Service |
| `plugins/utils` | `PumpeUtils` | Statische Helfer ohne Zustand plus Sektion `objects` für Serverobjekte |
| `plugins/anticheat` | `PumpeAntiCheat` | Checks, Client-Erkennung, Item-Validierung |
| `plugins/essentials` | `PumpeEssentials` | Inventar- und Enderchest-Zugriff für das Team |
| `plugins/mod` | `PumpeMod` | Bans, Mutes, Reports, Notizen |
| `plugins/clan-system` | `PumpeClanSystem` | Clans, Basen, Tab-Darstellung |
| `plugins/skills` | `PumpeSkills` | Skill-Level, Belohnungen, GUI |
| `plugins/trader` | `PumpeTrader` | Handels-NPCs |
| `plugins/death-messages` | `PumpeDeathMessages` | Todesmeldungen und Zähler |
| `plugins/playtime` | `PumpePlaytime` | Spielzeit-Erfassung |
| `plugins/chat-control` | `PumpeChatControl` | Chatfilter, Privatnachrichten, Persistenz |
| `plugins/transactions` | `PumpeTransactions` | PumpePoints (PP), Buchungen, Zeitgutschrift |
| `plugins/mailbox` | `PumpeMailbox` | Briefkasten als Serverobjekt: Klappe, Fahne, Namensschild, Postfach, bezahlter Versand mit Lieferzeit |

`plugins/briefkasten` ist der Vorläufer von `plugins/mailbox`, nicht mehr im Build und wartet nur
noch darauf, gelöscht zu werden. Nichts von dort übernehmen — die Mechanik liegt jetzt in
`plugins/utils` unter `de.pumpecraft.utils.objects`.

Neue Serverobjekte (Briefkasten, Mülltonne, Schild, …) bekommen ein eigenes Modul und beschreiben
sich über `DisplayObjectType`; sie bringen nur ihr Modell und ihre Spiellogik mit. Siehe
[plugins/utils/CLAUDE.md](plugins/utils/CLAUDE.md), Sektion `objects`.

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
