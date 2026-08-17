# PumpeUtils

Bibliotheks-Plugin ohne Spiellogik. Das Wurzelpaket enthält ausschließlich zustandslose Helfer,
die mindestens zwei Plugins brauchen könnten. Daneben gibt es Sektionen in Subpaketen — `messages`
für Meldungstexte, `objects` für Serverobjekte —, die auch Zustand halten dürfen, weil sie eine
Mechanik statt einer Hilfsmethode anbieten.

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
| `Recipes` | `register`, `unregister` — Rezepte unter ihrem Key, ersetzt ein altes statt am Duplikat zu scheitern |

## Sektion `messages` — Meldungstexte

| Klasse | Inhalt |
| --- | --- |
| `MessageTopic` | Eine Sorte Meldung: `key`, `task` für die Textquelle, eigene Vorlagen |
| `Messages` | `register`, `template`, `render`, `use` — rendert ein Thema, mit oder ohne Quelle |
| `MessageSource` | Vorlagen von außerhalb; `null` je Aufruf fällt auf die Vorlagen des Themas zurück |
| `MessageRotation` | Zufällige Vorlage aus einer Liste, ohne die zuletzt gezogene zu wiederholen |
| `ConnectionMessages` | Themen und fertige Meldungen für Betreten und Verlassen |

**Jede Meldung, die der ganze Server sieht, ist ein `MessageTopic`.** Das Plugin legt das Thema
neben seiner Logik an (`ModerationTopics`, `TraderTopics`, `DeathTopics`) und rendert es über
`Messages.render(topic, farbe, werte)`. Ob dabei ein erzeugter Text oder eine eigene Vorlage
herauskommt, ist nicht die Sache des Plugins: `PumpeAI` hängt sich über `Messages.use` ein, sonst
gelten die Vorlagen. Kein Plugin braucht dafür eine Abhängigkeit zur KI.

**Themen melden sich beim Start an.** `Messages.register(...)` in `onEnable`, sonst wärmt eine
Quelle sie nicht vor und die jeweils erste Meldung jedes Themas fällt zwangsläufig auf die eigene
Vorlage zurück. Bei 33 Todesursachen heißt das: einmal pro Ursache umsonst sterben.

Zwei weitere Regeln für ein Thema:

- **Vorlagen sind Fallback und Stilvorgabe zugleich.** Aus ihnen liest die Quelle den Ton ab und
  welche Platzhalter erlaubt sind. Ein Platzhalter, der in keiner Vorlage steht, existiert für sie
  nicht — er käme roh in den Chat.
- **Platzhalter, die in jeder Vorlage stehen, gelten als Pflicht.** So bleibt die Information
  erhalten: eine Trader-Meldung ohne `{location}` oder eine Mute-Meldung ohne `{duration}` wird
  verworfen, statt halb informiert im Chat zu landen.

`ConnectionMessages` liegt hier und nicht in `plugins/death-messages`, wo die Events verdrahtet
werden: Der Vanish in `plugins/mod` täuscht dasselbe Verlassen vor und muss denselben Topf
benutzen. Eine eigene Formulierung dort würde einen versteckten Teamler sofort verraten.

## Sektion `objects` — Serverobjekte

Ein Server kann dem Client keine neuen Blöcke beibringen. Ein Serverobjekt ist deshalb ein Satz
Vanilla-Items mit `item_model`-Komponente: ein `item_display` für den Korpus, eins pro bewegtem
Teil und ein `interaction` als Hitbox — alle an derselben Position, Teile unterscheiden sich nur
in ihrer Transformation. Ein Plugin beschreibt sein Objekt, die Sektion erledigt den Rest.

| Klasse | Inhalt |
| --- | --- |
| `DisplayObjectType` | Beschreibung eines Objekts: Basis-Item, Item-Modelle für Korpus und Teile, Hitbox, Schatten, `stackable`. Über `builder(id)` |
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
