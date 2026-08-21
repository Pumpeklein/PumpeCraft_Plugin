# PumpeEnchants

Eigene Verzauberungen für PumpeCraft, ohne Eingriff in die Paper-Registry.

## Technischer Ansatz

Eine Verzauberung ist ein Eintrag im `PersistentDataContainer` des Items plus eine gerenderte
Lore-Zeile. Echte Enchantments über `RegistryEvents.ENCHANTMENT` bräuchten einen Bootstrapper und
`paper-plugin.yml`, während alle Module hier auf `plugin.yml` mit `depend:` laufen. Der Preis:
kein Vanilla-Verzauberungstisch, der Erwerb läuft über Bücher und den Amboss.

Eigene Lore-Zeilen werden an ihrem Text erkannt, nicht an einem Marker im Stil: `ItemSanitizer`
aus `PumpeAntiCheat` baut zu lange Zeilen als reinen Text neu auf und würde einen Marker
mitsamt der Formatierung verlieren. Fremde Lore bleibt beim Rendern erhalten.

## Verzauberungen

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Telekinese | I | Gewöhnlich | Werkzeuge | Drops und XP landen direkt im Inventar, bei vollem Inventar fallen sie normal zu Boden |
| Schmelzofen | I | Selten | Spitzhacken, Schaufeln | Erze, Netherit-Schutt und Sand werden beim Abbau geschmolzen; unverträglich mit Behutsamkeit |
| Federleicht | I–II | Gewöhnlich | Stiefel | Kein Fallschaden bis 6 bzw. 12 Blöcke |

Glück wirkt vor dem Schmelzen: die Rohdrops kommen bereits vervielfacht aus dem Block, jeder
Stapel wird danach umgewandelt. Behutsamkeit setzt den Schmelzofen aus, sonst würde aus dem
behutsam abgebauten Erzblock wieder ein Barren.

Pro Item sind zwei eigene Verzauberungen erlaubt (`anvil.max-enchants-per-item`).

## Erwerb

Verzauberungsbücher tragen die Verzauberung im PDC und werden im Amboss auf das Item übertragen.
Zwei Bücher derselben Stufe ergeben die nächste Stufe. Unverträgliche oder wirkungslose
Kombinationen werden mit einer Meldung in der Aktionsleiste abgelehnt.

## Befehle

- `/customenchant <Spieler> <Verzauberung> <Stufe>` – legt die Verzauberung auf das gehaltene
  Item. Hält der Spieler ein Buch, entsteht daraus ein Verzauberungsbuch.
  Aliase: `cenchant`, `verzaubern`.
- `/enchantbooks [Spieler]` – gibt jedes Buch jeder aktiven Verzauberung einmal aus, zum Testen.
  Alias: `testenchants`.

`/enchant` bleibt bewusst der Vanilla-Befehl; ein Plugin-Befehl dieses Namens würde ihn verdecken.

## Berechtigungen

- `pumpecraft.enchants.admin` – Standard `op`, gilt für beide Befehle.

## Konfiguration

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `enchants.telekinesis.enabled` | `true` | Telekinese an oder aus |
| `enchants.furnace.enabled` | `true` | Schmelzofen an oder aus |
| `enchants.featherweight.enabled` | `true` | Federleicht an oder aus |
| `enchants.featherweight.safe-fall-distance.level-1` | `6.0` | Sturzhöhe ohne Schaden, Stufe I |
| `enchants.featherweight.safe-fall-distance.level-2` | `12.0` | Sturzhöhe ohne Schaden, Stufe II |
| `anvil.level-cost` | `5` | Stufenkosten der Amboss-Kombination |
| `anvil.max-enchants-per-item` | `2` | Eigene Verzauberungen pro Item |

## Dienst

`EnchantService` liegt als Bukkit-Service bereit: `level`, `activeLevel`, `list`, `set`, `remove`
und `createBook`. Andere Plugins holen ihn über den `ServicesManager` und brauchen dafür
`softdepend: [PumpeEnchants]`.
