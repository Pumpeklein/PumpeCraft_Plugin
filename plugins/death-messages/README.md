# PumpeDeathMessages

Custom death, join, leave and advancement messages for PumpeCraft.

## Features

- Replaces vanilla death messages with custom German messages.
- Covers every current Paper `DamageCause` with 9 message variants.
- Every 5th death for a player uses a special milestone message (14 variants).
- Replaces the vanilla join, leave and advancement messages with the same kind of custom German
  messages; a player joining for the very first time gets a separate welcome pool.
- Avoids using the same message template twice in a row globally, across players and topics.
- Tracks player death counts in MariaDB through PumpeDatabase.
- Imports an existing `plugins/PumpeDeathMessages/death-message-data.yml` once and leaves it untouched as a backup.

The messages are intentionally varied, sarcastic and less vanilla-like.

## Themen statt fester Texte

Jede Meldung ist ein `MessageTopic` aus [PumpeUtils](../utils/CLAUDE.md): Schlüssel, Aufgabe für
eine Textquelle und die eigenen Vorlagen. Gerendert wird über `Messages.render`. Läuft
[PumpeAI](../ai/README.md) mit gültigem Schlüssel, kommen die Texte von dort und die eigenen
Vorlagen sind der Fallback; ohne PumpeAI ändert sich nichts.

| Thema | Vorlagen | Platzhalter |
| --- | --- | --- |
| `death-<URSACHE>` (33 Stück) | 9 je Ursache | `{player}`, bei Gegnern `{killer}` |
| `death-milestone` | 14 | `{player}`, `{deaths}`, `{previousDeaths}` |
| `connection-join` / `connection-leave` | je 12 | `{player}` |
| `connection-first-join` | 6 | `{player}` |
| `advancement` | 8 | `{player}`, `{advancement}` |

Die Themen für Tode liegen in `DeathTopics`, die für Join und Leave in `ConnectionMessages` in
PumpeUtils - der Vanish aus [plugins/mod](../mod/README.md) braucht denselben Topf, sonst wäre ein
versteckter Teamler an der Formulierung erkennbar.

| Klasse | Aufgabe |
| --- | --- |
| `DeathMessageListener` | Event-Verdrahtung für Tode, Zähler, Platzhalterwerte |
| `ConnectionMessageListener` | Event-Verdrahtung für Join und Leave |
| `AdvancementMessageListener` | Event-Verdrahtung für Fortschritte samt Thema |
| `DeathTopics` | Themen und Vorlagen je Schadensursache, Meilenstein alle fünf Tode |
| `DeathCounterRepository` | Todeszähler in MariaDB |

Fortschritte ohne Chat-Ankündigung - Rezepte und versteckte Advancements - bleiben unangetastet:
Wo Vanilla nichts meldet, meldet das Plugin auch nichts.
