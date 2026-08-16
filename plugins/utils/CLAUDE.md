# PumpeUtils

Bibliotheks-Plugin ohne Spiellogik. Das Wurzelpaket enthält ausschließlich zustandslose Helfer,
die mindestens zwei Plugins brauchen könnten. Daneben gibt es Sektionen in Subpaketen — aktuell
`objects` für Serverobjekte —, die auch Zustand halten dürfen, weil sie eine Mechanik statt einer
Hilfsmethode anbieten.

## Regel

Bevor irgendwo eine Hilfsmethode entsteht: hier nachsehen. Wenn sie hier fehlt und mehr als
ein Plugin sie brauchen könnte, gehört sie hierher — nicht in das aufrufende Plugin und erst
recht nicht kopiert. Wenn sie nur an einer Stelle Sinn ergibt, bleibt sie privat im
aufrufenden Plugin.

Nutzung: `depend: [PumpeUtils]` in der `plugin.yml`. Der Gradle-Classpath kommt automatisch
über `libraryModulePaths` in `build.gradle.kts`.

## Bestand

| Klasse | Inhalt |
| --- | --- |
| `Texts` | `decimal`, `percent`, `truncate`, `joinLimited` (Liste mit `(+N)`-Überlauf), `lower` |
| `Players` | `online`, `known`, `self`, `completeKnownNames`, `completeOnlineNames`, `filterPrefix`, `displayName`, `stripSelector` (`@Name`) |
| `Locations` | `horizontalDistance`, `samePosition`, `sameWorld`, `distanceToBox`, `aimDot`, `clamp` |
| `Cooldowns<K>` | Schlüssel-basierte Sperren: `tryAcquire`, `active`, `remainingMillis`, `purgeExpired` |
| `Rates` | Gleitfenster über `Deque<Long>`: `record`, `trim`, `spread` (Rate + Streuung der Intervalle) |
| `Staff` | `withPermission`, `broadcast` — Meldungen an Berechtigte |
| `Teleports` | `playerLink`, `locationLink`, `clickable`, `coordinates` — klickbare Teleport-Ziele in Chatmeldungen |
| `Configs` | `lowerStringList`, `matchesAny` (exakter Token-Abgleich mit `*`-Präfix) |

## Sektion `objects` — Serverobjekte

Ein Server kann dem Client keine neuen Blöcke beibringen. Ein Serverobjekt ist deshalb ein Satz
Vanilla-Items mit `item_model`-Komponente: ein `item_display` für den Korpus, eins pro bewegtem
Teil und ein `interaction` als Hitbox — alle an derselben Position, Teile unterscheiden sich nur
in ihrer Transformation. Ein Plugin beschreibt sein Objekt, die Sektion erledigt den Rest.

| Klasse | Inhalt |
| --- | --- |
| `DisplayObjectType` | Beschreibung eines Objekts: Basis-Item, Item-Modelle für Korpus und Teile, Hitbox, Schatten. Über `builder(id)` |
| `DisplayObject` | Ein aufgestelltes Objekt: `body`, `parts`, `hitbox`, `location`, `part(name)` |
| `DisplayObjects` | `spawn`, `resolve`, `nearest`, `remove`, `isPart`, `createItem`, `isItem`, `baseOf`, `facingYaw` |
| `ObjectHinge` | Scharnier, per `fromModel(part, x, y, z)` direkt aus den Modellkoordinaten |
| `HingeAnimator` | Dreht Teile um ihr Scharnier: `rotate`, `angle`, `delay`, `cancel`, `cancelAll` |
| `ObjectStorage` | Besitzer und Inhalt im PDC des Korpus: `setOwner`, `owner`, `ownerName`, `contents`, `setContents`, `hasContents` |

Marker und Verweise liegen im Namespace `pumpeutils`, nicht im Namespace des aufrufenden Plugins:
so kann jedes Plugin die Objekte jedes anderen finden und aufräumen.

`HingeAnimator` ist die einzige Klasse mit Zustand — sie hält die laufenden Tasks und gehört in
`onDisable` über `cancelAll()` abgeräumt.

Zwei Eigenheiten der Darstellung stecken in `HingeAnimator` und sind der Grund, warum das hier
und nicht in jedem Plugin einzeln steht: Ein `item_display` mit Transform `FIXED` rendert das
Modell um 180° um Y gedreht, deshalb werden X und Z eines Scharniers gespiegelt. Und der Client
interpoliert Rotation und Translation getrennt, weshalb der Server in Tick-Schritten dreht statt
die ganze Drehung in einem Schritt zu schicken. Referenz: [plugins/mailbox](../mailbox/README.md).

## Offene Migration

`PumpeMod`, `PumpeClanSystem` und `PumpeSkills` haben noch eigene Kopien von
`findKnownPlayer`, `completeKnownPlayers` und `format(double)`. Wer dort ohnehin etwas
anfasst, ersetzt sie durch `Players` beziehungsweise `Texts`.
