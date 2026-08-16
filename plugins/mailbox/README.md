# PumpeMailbox

Briefkasten als Serverobjekt: Item mit `minecraft:item_model`, aufgestelltes Modell aus
Display-Entities, animierte Klappe und Fahne, Postfach mit persistentem Inhalt.

Die Mechanik dahinter - Aufstellen, Wiederfinden, Scharniere, Besitzer und Inhalt - liegt in
`de.pumpecraft.utils.objects` in [plugins/utils](../utils/CLAUDE.md) und wird von jedem weiteren
Serverobjekt genauso genutzt. Hier liegt nur, was den Briefkasten ausmacht: das Modell, die Post
und die Bedienung.

## Commands

- `/mailbox give [Spieler]` - gibt das Briefkasten-Item.
- `/mailbox spawn` - stellt einen Briefkasten auf dem eigenen Block auf.
- `/mailbox remove` - entfernt den nächsten Briefkasten im Umkreis von 5 Blöcken und wirft die
  enthaltene Post aus.
- `/mailbox info` - zeigt Objekt-ID, Modellschlüssel und Basis-Item.

Aliase: `/briefkasten`, `/bk`.

## Bedienung

| Aktion | Ergebnis |
| --- | --- |
| Rechtsklick mit Item auf einen Block | stellt den Briefkasten auf, ausgerichtet zum Spieler |
| Rechtsklick mit einem Brief | Klappe öffnet, Brief landet im Postfach, Fahne geht hoch |
| Rechtsklick mit leerer Hand | öffnet das Postfach (nur Besitzer bzw. `manage`) |
| Postfach schließen | speichert den Inhalt, Klappe zu, Fahne fällt bei leerem Kasten |
| Schleichen + Schlagen | baut ihn ab, gibt Item und Post zurück (`manage`) |

Was als Brief gilt, steht in `config.yml` unter `mail.letters`. Besitzer wird, wer den
Briefkasten aufstellt; ohne Besitzer darf jeder öffnen.

## Aufbau

`MailboxObject` beschreibt das ganze Objekt - mehr braucht ein Serverobjekt nicht:

```java
DisplayObjectType.builder("mailbox")
    .baseMaterial(Material.PAPER)
    .itemModel("pumpecraft:mailbox")
    .bodyModel("pumpecraft:mailbox_body")
    .part("door", "pumpecraft:mailbox_door")
    .part("flag", "pumpecraft:mailbox_flag")
    .hitbox(0.7F, 1.4F)
    .shadow(0.5F)
    .build();

ObjectHinge.fromModel("door", 8.0D, 13.0D, 3.65D);
ObjectHinge.fromModel("flag", 14.85D, 19.0D, 8.0D);
```

Die Zahlen in `fromModel` sind die Scharnierpunkte direkt aus den Modelldateien, sie lassen sich
nach jeder Änderung in Blockbench dort wieder ablesen.

| Klasse | Aufgabe |
| --- | --- |
| `MailboxObject` | Objekt- und Scharnierdefinition, Drehwinkel |
| `MailboxItems` | Item mit Name und Lore |
| `MailboxAnimations` | Klappe auf/zu/klappen, Fahne hoch/runter samt Sounds |
| `mail/MailService` | Einwurf, Postfach-GUI, Speichern, Auswerfen, Besitzerprüfung |
| `mail/MailboxHolder` | Inventar-Holder, verbindet GUI und Objekt |
| `command/MailboxCommand` | `/mailbox` |
| `listener/…` | Aufstellen, Interaktion, Inventar schließen |

## Permissions

- `pumpecraft.mailbox.*`
- `pumpecraft.mailbox.command`
- `pumpecraft.mailbox.place`
- `pumpecraft.mailbox.manage` - fremde Briefkästen öffnen und abbauen
- `pumpecraft.mailbox.use` - Briefe einwerfen, steht auf `true`

Die übrigen Permissions stehen auf `false` und werden über LuckPerms vergeben.

## Resourcepack

```
pack/
├── pack.mcmeta
└── assets/pumpecraft/
    ├── items/                    # mailbox, _body, _door, _flag
    ├── models/item/              # dieselben vier Modelle
    └── textures/item/mailbox.png # 32x32, sieben Zonen
```

`mailbox` ist das komplette Modell in Ruhelage - es steckt im Item, in der Hand und im Inventar.
Die drei Teilmodelle sind für die aufgestellte Version, gebaut im selben Koordinatensystem.

```bash
./gradlew :plugins:mailbox:packZip       # build/pack/mailbox-pack.zip
./gradlew :plugins:mailbox:packSha1      # gibt die server.properties-Zeile aus
./gradlew :plugins:mailbox:deployBundle  # deploy/mailbox mit Jars, Pack und Anleitung
```

Kommt ein zweites Serverobjekt dazu, gehören die Packs zusammengeführt - ein Client kann nur ein
Server-Pack laden. Dann wandert `pack.mcmeta` in einen gemeinsamen Ordner und die Module liefern
nur noch ihre `assets`.

## Modell anpassen

Die Dateien in `pack/assets/pumpecraft/models/item/` lassen sich direkt in Blockbench öffnen.
`display.fixed` muss auf Scale 1 / Translation 0 bleiben und es darf kein `parent` gesetzt werden,
sonst rendert das `item_display` das Modell in falscher Größe. Verschiebst du ein Scharnier, gehört
der zugehörige `ObjectHinge.fromModel`-Aufruf in `MailboxObject` nachgezogen.
