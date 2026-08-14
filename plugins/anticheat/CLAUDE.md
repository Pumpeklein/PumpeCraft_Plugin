# PumpeAntiCheat

Verhaltens- und Item-Prüfung ohne Paketzugriff — alles läuft über die Bukkit-Events, die
Paper bereitstellt. Das begrenzt, was erkennbar ist: Timer, InventoryMove und
Velocity-Manipulation brauchen Paketebene und sind bewusst nicht enthalten.

## Aufbau

```
de.pumpecraft.anticheat
├─ PumpeAntiCheatPlugin     Verdrahtung, Config-Migration, Lebenszyklus
├─ AntiCheatCommand         /anticheat
├─ core/
│  ├─ CheckType             Katalog aller Checks inkl. Kategorie und Config-Schlüssel
│  ├─ CheckSettings         einziger Zugriffspunkt auf checks.<key>.<setting>
│  ├─ PlayerState           Laufzeitdaten, gruppiert nach Bereich
│  ├─ PlayerStateStore      Zustand pro UUID
│  ├─ ViolationService      Violation-Level, Decay, Cancel-Entscheidung
│  └─ AlertDispatcher       Bündelung der Team-Meldungen
├─ check/
│  ├─ AbstractCheck         gemeinsame Basis, Gamemode- und Bypass-Ausnahme
│  ├─ MovementChecks        Speed, Fly, NoFall (Schadenshöhe, nicht nur Vorhandensein)
│  ├─ CombatChecks          Reach, AutoClicker, KillAura
│  ├─ BlockChecks           FastPlace, FastBreak, Nuker, BlockReach, Scaffold
│  ├─ XrayChecks            Xray
│  ├─ ItemChecks            Item-Validierung, Event-Verdrahtung
│  └─ EffectChecks          Potion-Effekte am Spieler
├─ item/
│  ├─ ItemPolicy            Grenzwerte aus der Config
│  ├─ ItemInspector         reine Prüfung, verändert nichts
│  ├─ ItemSanitizer         repariert, was reparierbar ist
│  └─ ItemFinding           ein Befund, reparierbar oder nicht
├─ client/                  Brand- und Channel-Auswertung
├─ platform/BedrockDetector Floodgate/Geyser per Reflection
└─ storage/                 Persistenz in pc_anticheat_events und pc_players
```

## Einen Check hinzufügen

1. Eintrag in `CheckType` mit Anzeigename, Config-Schlüssel und Kategorie.
2. Abschnitt unter `checks.<key>` in `config.yml`, mindestens `enabled` und `alert-level`.
   Werte, die sich zwischen Java und Bedrock unterscheiden, mit `-java`/`-bedrock`-Suffix
   anlegen und über `settings.platform*` lesen.
3. Logik in eine bestehende `*Checks`-Klasse der passenden Kategorie oder eine neue Klasse,
   die `AbstractCheck` erweitert.
4. In `PumpeAntiCheatPlugin.onEnable` registrieren. Klassen mit eigenem Task brauchen
   `start()`/`shutdown()` und einen Aufruf in `onDisable`.

Grenzwerte niemals direkt über `plugin.getConfig()` lesen — `CheckSettings` ist der einzige
Zugriffspunkt, sonst weichen die Pfade auseinander.

### alert-level richtig setzen

`ViolationService.flag(...)` verwirft alles unterhalb von `alert-level` — keine Meldung, kein
Datenbankeintrag, keine Konsolenzeile. Ein Check mit `alert-level: 3.0`, der pro Treffer `1.0`
vergibt, braucht also drei Treffer, bevor überhaupt etwas sichtbar wird; bei `decay-per-second`
ist der erste nach gut zwölf Sekunden schon wieder abgebaut. Bei eindeutigen Checks (NoFall,
Item, BlockReach) gehört `alert-level` deshalb auf die Höhe eines einzelnen Treffers —
gegen Wiederholungen hilft der `alerts`-Block, nicht eine hohe Schwelle. Nur bei Checks mit
echter Grauzone (Speed, Scaffold, AutoClicker) ist eine Schwelle über einem Treffer sinnvoll.

`checks.<key>.debug: true` schreibt die Messwerte eines Checks in die Konsole, auch wenn
nichts gemeldet wird — über `debug(check, player, message)` aus `AbstractCheck`. Ohne das
ist ein stiller Check nicht von einem zu unterscheiden, der gar nicht läuft.

### Ausnahmen zuerst prüfen

Meldet ein Check gar nichts, liegt es meist an einer Ausnahme, nicht an der Logik.
`AbstractCheck.exemptReason(player)` nennt den Grund, `/anticheat status <Spieler>` zeigt ihn
als `Prüfung: ausgesetzt (...)`. Häufigster Fall: `pumpecraft.anticheat.bypass` über eine
Wildcard-Gruppe im Rechte-Plugin — das setzt für Admins sämtliche Checks still aus.

Bei Bewegungschecks kommt `movementExemptReason` dazu (Flug, Elytra, Wasser, Teleport- und
Velocity-Schonzeit). Die Schonzeiten stehen unter `movement:` und sind beim Testen per `/tp`
relevant: ein Sturz, der komplett in die Schonzeit fällt, wird nie gemessen.

## Meldungen

Der `AlertDispatcher` ist die einzige gedrosselte Stelle. Konsole, Datenbank und
`/anticheat recent` sehen **jeden** Treffer; gedrosselt wird nur der Chat.

- Treffer werden pro Spieler und Check gesammelt und alle `alerts.flush-interval-ticks`
  als eine Zeile ausgegeben, mit `xN` bei Wiederholungen.
- Eine bereits gemeldete Kombination meldet erst wieder, wenn `repeat-interval-seconds`
  vergangen sind **oder** das Violation-Level um `escalation-step` gestiegen ist.
- Pro Durchlauf höchstens `max-lines-per-flush` Zeilen, sortiert nach Violation-Level;
  der Rest wird als Zähler angehängt.
- `/anticheat alerts` schaltet die Meldungen pro Teammitglied stumm (nur zur Laufzeit).

Wer eine neue Meldung ergänzt, schickt sie über `violations.flag(...)`. Direkte
`sendMessage`-Aufrufe an das Team umgehen die Bündelung und gehören nicht in Checks.

### Klickbare Ziele

Jede Meldung an das Team trägt den Spielernamen und, sofern eine Position bekannt ist, die
Koordinaten als Klickziel. Der Klick **schreibt** den Befehl in die Chatzeile
(`ClickEvent.suggestCommand`), ausgeführt wird er vom Teammitglied selbst — ein Fehlklick
teleportiert also niemanden.

Nie selbst zusammenbauen, sondern `AlertDispatcher.playerLink(name)` und
`AlertDispatcher.locationLink(location)` verwenden; die lesen die Vorlagen aus
`alerts.teleport-command` und `alerts.teleport-coordinates-command`. Ausserhalb des
AntiCheat direkt `de.pumpecraft.utils.Teleports`.

**Sobald irgendwo Koordinaten ausgegeben werden, gehören sie als `locationLink` dorthin —
nie als reiner Text.**

## Item- und Effekt-Prüfung

`ItemInspector` prüft und verändert nie — dasselbe Ergebnis trägt Meldung, Reparatur und
Löschung. Befunde sind entweder reparierbar (Enchant-Stufe, Stapelgröße, unzerstörbar,
Attribute, Name/Lore, Potion-Werte) oder nicht (verbotenes Material, Buch-Exploit,
verschachtelte Container). `checks.item.action` entscheidet:

| Wert | Verhalten |
| --- | --- |
| `alert` | nur melden, nichts anfassen |
| `sanitize` | reparierbares korrigieren, nicht reparierbares löschen |
| `remove` | Item löschen |

`EffectChecks` deckt ab, was übrig bleibt, nachdem ein Exploit-Item konsumiert wurde.
Effekte aus `ignored-causes` werden pro Spieler und Effekttyp gemerkt, damit der
periodische Scan vom Team vergebene Effekte nicht doch noch entfernt.

## Client-Erkennung

Der Loader kommt **ausschließlich** aus dem Client-Brand. Plugin-Channels taugen dafür
nicht: ein Fabric-Client mit Forge-Kompatibilitätsmods registriert `fml:*` und würde sonst
zusätzlich als Forge gelten. Abgeglichen wird gegen exakte Brand-Tokens beziehungsweise
Channel-Namespaces, nie als Teilstring.

Launcher (Modrinth App, Prism, CurseForge, MultiMC, LiquidLauncher) senden nichts an den
Server und sind grundsätzlich nicht erkennbar. `/anticheat client <Spieler>` zeigt die
Rohdaten und ist der Ausgangspunkt für neue Signaturen.

`spoof-detection` ist das einzige Signal, das gegen einen gefälschten Brand hält: ein echter
Vanilla-Client registriert keinen einzigen Plugin-Channel, die Kombination aus
Vanilla-Brand und vorhandenen Channels ist also ein Widerspruch. Bedrock-Spieler sind
ausgenommen, weil Geyser ihre Kanäle serverseitig verwaltet.

## Nicht abgedeckt

Keine Paketebene, also kein Timer, kein InventoryMove, keine Velocity-Prüfung.
Cheat-Clients auf Fabric-Basis (Wurst, Meteor, LiquidBounce) melden `fabric` und
registrieren keine eigenen Channels — die Signaturen dafür existieren, greifen aber nur bei
Clients, die sich selbst zu erkennen geben.
