# PumpeCraft Plugins

Gradle-Multiprojekt mit Paper-Plugins für den PumpeCraft-Server. Java 21, Paper-API aus
`gradle.properties` (`paperApiVersion`). `plugins/essentials` baut als einziges Modul gegen
Java 25, weil das Paperweight-Dev-Bundle es verlangt.

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
| `plugins/utils` | `PumpeUtils` | Statische Helfer ohne Zustand plus Sektionen `messages` und `objects` |
| `plugins/ai` | `PumpeAI` | DeepSeek-Anbindung als Dienst; erzeugt Textzeilen für andere Plugins |
| `plugins/anticheat` | `PumpeAntiCheat` | Checks, Client-Erkennung, Item-Validierung |
| `plugins/essentials` | `PumpeEssentials` | Inventar- und Enderchest-Zugriff für das Team, Item-Dienste, Rücksprungpunkte (`/back`), Haltungen (`/sit`, `/crawl`) |
| `plugins/mod` | `PumpeMod` | Bans, Mutes, Reports, Notizen |
| `plugins/clan-system` | `PumpeClanSystem` | Clans, Tab-Darstellung |
| `plugins/base-system` | `PumpeBaseSystem` | Spielerbasen und Grundstücke: Kauf über PP, Rollen, Flaggen, Schutz |
| `plugins/skills` | `PumpeSkills` | Skill-Level, Belohnungen, GUI |
| `plugins/trader` | `PumpeTrader` | Handels-NPCs |
| `plugins/death-messages` | `PumpeDeathMessages` | Todes-, Join-, Leave- und Fortschrittsmeldungen, Todeszähler |
| `plugins/playtime` | `PumpePlaytime` | Spielzeit-Erfassung |
| `plugins/chat-control` | `PumpeChatControl` | Chatfilter, Privatnachrichten, Persistenz |
| `plugins/transactions` | `PumpeTransactions` | PumpePoints (PP), Buchungen, Zeitgutschrift |
| `plugins/mailbox` | `PumpeMailbox` | Briefkasten als Serverobjekt: Klappe, Fahne, Namensschild, Postfach, bezahlter Versand mit Lieferzeit |
| `plugins/enchants` | `PumpeEnchants` | Registrierte eigene Verzauberungen, Bücher und Amboss-Kombination, `EnchantService` als Bukkit-Service |
| `plugins/sub-essentials` | `SubEssentials` | Twitch-Verknüpfung und Menübefehle für Subs |

`plugins/briefkasten` ist der Vorläufer von `plugins/mailbox`, nicht mehr im Build und wartet nur
noch darauf, gelöscht zu werden. Nichts von dort übernehmen — die Mechanik liegt jetzt in
`plugins/utils` unter `de.pumpecraft.utils.objects`.

Neue Serverobjekte (Briefkasten, Mülltonne, Schild, …) bekommen ein eigenes Modul und beschreiben
sich über `DisplayObjectType`; sie bringen nur ihr Modell und ihre Spiellogik mit. Siehe
[plugins/utils/CLAUDE.md](plugins/utils/CLAUDE.md), Sektion `objects`.

`database`, `utils` und `ai` sind Bibliotheks-Plugins: sie werden per `compileOnly` eingebunden
und zur Laufzeit über `depend:` in der `plugin.yml` aufgelöst. Wer eines davon nutzt, muss
es dort eintragen — sonst schlägt der Klassenzugriff erst im laufenden Server fehl. `ai` gehört
in `softdepend:`, nicht in `depend:`: Ein Plugin, das ohne erzeugte Texte nicht mehr startet,
hat seinen Fallback nicht verstanden.

**Servermeldungen.** Alles, was der ganze Server sieht, ist ein `MessageTopic` aus
`plugins/utils` (Sektion `messages`): Thema mit eigenen Vorlagen neben der Logik anlegen, in
`onEnable` über `Messages.register` anmelden, über `Messages.render` ausgeben. Ein hart
formulierter `Bukkit.broadcast` ist ein Fehler. Ob ein erzeugter Text oder eine Vorlage
herauskommt, entscheidet `PumpeAI` über `Messages.use` — kein Plugin braucht dafür eine
Abhängigkeit zur KI.

**Erzeugte Texte.** Was von `PumpeAI` kommt, ist Kür. Die eigenen Vorlagen bleiben stehen und
gelten immer dann, wenn nichts Erzeugtes bereitliegt — nie im Spielverlauf auf eine Antwort
warten, nie ungeprüft in den Chat schreiben. Referenz: `AiMessagePool` in `plugins/ai`.

## Build

```bash
./gradlew collectPluginJars      # alle Jars nach build/plugins
./gradlew :plugins:anticheat:build
```

Neues Modul: in `settings.gradle.kts` includen und in `pluginModulePaths` in
`build.gradle.kts` eintragen. Bibliotheks-Module zusätzlich in `libraryModulePaths`.

## IDE

```bash
./gradlew syncIdeConfig
```

Schreibt die JDKs, mit denen dieser Build arbeitet, als `java.configuration.runtimes` in die
VS-Code-Einstellungen — in `.vscode/settings.json` und zusätzlich in jede `*.code-workspace`
im übergeordneten Ordner, die dieses Projekt einbindet. Nötig ist das, weil ein Multi-Root-Workspace
die Java-Einstellungen nur aus der Workspace-Datei liest und die Ordner-Einstellungen ignoriert.
Ohne passende Runtime bleibt der Classpath-Container eines Moduls ungebunden ("Unbound classpath
container: JRE System Library [JavaSE-25]") und die IDE findet danach nicht einmal mehr
`java.lang.Object`, während `./gradlew build` sauber durchläuft. Erneut ausführen, sobald sich
eine Toolchain oder eine JDK-Installation ändert.

Klassen aus einem anderen Modul, die die IDE nicht auflöst, der Compiler aber schon: Das Eclipse-Modell
von Gradle lässt `compileOnly`-Projektabhängigkeiten fallen. Der Build spiegelt sie deshalb in die
Konfiguration `ideClasspath` — das gilt für jedes Modul, auch für neue. Ist eine Abhängigkeit trotzdem
unsichtbar, hilft "Java: Clean Java Language Server Workspace" und danach ein Reload.
