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
│  ├─ MovementChecks        Speed, Fly, NoFall
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

Launcher (Modrinth App, Prism, CurseForge, MultiMC) senden nichts an den Server und sind
grundsätzlich nicht erkennbar. `/anticheat client <Spieler>` zeigt die Rohdaten und ist der
Ausgangspunkt für neue Signaturen.

## Nicht abgedeckt

Keine Paketebene, also kein Timer, kein InventoryMove, keine Velocity-Prüfung.
Cheat-Clients auf Fabric-Basis (Wurst, Meteor, LiquidBounce) melden `fabric` und
registrieren keine eigenen Channels — die Signaturen dafür existieren, greifen aber nur bei
Clients, die sich selbst zu erkennen geben.
