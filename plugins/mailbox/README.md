# PumpeMailbox

Briefkasten als Serverobjekt: Item mit `minecraft:item_model`, aufgestelltes Modell aus
Display-Entities, animierte Klappe und Fahne, Namensschild, Postfach und bezahlter Versand mit
Lieferzeit.

Die Objektmechanik - Aufstellen, Wiederfinden, Scharniere, Label, Besitzer und Inhalt - liegt in
`de.pumpecraft.utils.objects` in [plugins/utils](../utils/CLAUDE.md) und wird von jedem weiteren
Serverobjekt genauso genutzt. Hier liegt nur, was den Briefkasten ausmacht.

`depend: [PumpeUtils, PumpeDatabase, PumpeTransactions]` - Positionen und Sendungen liegen in der
Datenbank, der Versand kostet PumpePoints.

## Commands

- `/mailbox give [Spieler]` - gibt das Briefkasten-Item.
- `/mailbox spawn` - stellt den eigenen Briefkasten auf.
- `/mailbox remove` - baut den nächsten eigenen Briefkasten ab und wirft die Post aus.
- `/mailbox send <Spieler>` - öffnet das Versandmenü zu einem fremden Briefkasten.
- `/mailbox status` - Standort des eigenen Briefkastens und was unterwegs ist.
- `/mailbox info` - Objekt-ID, Modellschlüssel und Basis-Item.

Aliase: `/briefkasten`, `/bk`.

## Rezept

An einen Briefkasten kommt man über die Werkbank; `/mailbox give` bleibt beim Team. Das Muster im
Gitter sieht aus wie das fertige Objekt - Eisenkorpus, Truhe als Postfach, rote Fahne an der Seite,
Zaun als Pfosten:

```
E E E      E  Eisenbarren (4x)
E T R      T  Truhe
  Z        R  roter Farbstoff
           Z  Holzzaun (jede Holzart)
```

Das Rezept wird beim Login freigeschaltet, steht also im Rezeptbuch. Das Item ist **nicht
stapelbar** (`max_stack_size` 1): ein Briefkasten ist ein einzelnes Objekt, kein Baumaterial.
Abschalten über `crafting.enabled: false` in der `config.yml` - dann kommt man nur noch über das
Team an einen.

## Bedienung

| Aktion | Ergebnis |
| --- | --- |
| Rechtsklick mit Item auf einen Block | stellt den Briefkasten auf, ausgerichtet zum Spieler |
| Rechtsklick mit einem Brief | Klappe öffnet, Brief landet im Postfach, Fahne geht hoch |
| Rechtsklick mit leerer Hand | öffnet das Postfach (nur Besitzer bzw. `manage`) |
| Postfach schließen | speichert den Inhalt, Klappe zu, Fahne fällt bei leerem Kasten |
| Schleichen + Schlagen | baut den eigenen Briefkasten ab (`manage` auch fremde) |

Direkt einwerfen ist kostenlos, dafür muss man vor dem Briefkasten stehen. Versand über
`/mailbox send` kostet PumpePoints und braucht Zeit.

## Ein Briefkasten pro Spieler

Wer schon einen hat, bekommt beim Aufstellen die Koordinaten des vorhandenen genannt. Die
Zuordnung steht in `pc_mailboxes` und überlebt Neustarts; Position und Body-UUID stehen mit drin,
damit eine Lieferung den Briefkasten auch findet, während sein Chunk nicht geladen ist - dann wird
er kurz nachgeladen.

Über dem Briefkasten schwebt ein Namensschild mit dem Besitzer, darunter Anzahl der wartenden
Items und der Sendungen, die noch unterwegs sind. Es ist ein `text_display` mit begrenzter
Sichtweite, taucht also erst ein paar Blöcke davor auf.

## Versand

`/mailbox send <Spieler>` öffnet ein Menü: oben 36 Felder für die Ware, unten Entfernung, Preis,
Lieferzeit und die beiden Knöpfe. Preis und Zeit rechnen sich nach jedem Einlegen neu.

```
Preis   = base + per-item * Items + per-100-blocks * Distanz / 100
Zeit    = base-seconds + seconds-per-100-blocks * Distanz / 100 + seconds-per-stack * Stapel
          gedeckelt auf max-seconds (Standard 10 Minuten)
```

Andere Welt zählt als feste Ersatzdistanz (`cross-world-blocks`). Bezahlt wird beim Abschicken über
`PointsService`; reicht das Guthaben nicht, kommt alles zurück.

Ab dem Abschicken sind im Zielbriefkasten so viele Slots mit Barrieren blockiert, wie die Sendung
Stapel hat. Sie zeigen Absender und Restzeit, lassen sich nicht herausnehmen und werden nie
gespeichert - sie existieren nur in der offenen Ansicht. Damit kann der Empfänger den Kasten nicht
so voll machen, dass die Lieferung nicht mehr passt, und muss regelmäßig hineinsehen.

Ist bei Ankunft trotzdem kein Platz, bleibt die Sendung stehen und der Empfänger bekommt eine
Meldung. Absender und Empfänger werden beim Abschicken, bei Ankunft und beim Einloggen informiert.

Liegen mindestens `mail.parcel-threshold` Items im Briefkasten, steht zusätzlich ein Paket-Modell
daneben (`pumpecraft:mailbox_parcel`), das wieder verschwindet, sobald geleert wurde.

## Anti-Dupe

Der offensichtliche Weg - jeder Öffner bekommt eine Kopie des gespeicherten Inhalts - dupliziert,
sobald zwei Leute gleichzeitig öffnen oder jemand den Briefkasten abbaut, während er offen ist.
Deshalb:

- Ein Briefkasten hat genau **ein** `Inventory`-Objekt, solange er offen ist; alle Öffner sehen
  dieselbe Instanz (`MailboxInventories`).
- Geschrieben wird beim Schließen des letzten Öffners und nach jedem Einwurf.
- Abbauen ruft `drain`: erst alle Ansichten schließen (das schreibt zurück), dann den gespeicherten
  Inhalt einmal auswerfen.
- Reservierungs-Barrieren werden beim Speichern herausgefiltert und können nicht angeklickt,
  gezogen oder per Zahlentaste getauscht werden.
- Im Versandmenü wandern die Items beim Abschicken erst aus dem Menü in die Sendung, dann wird
  geschlossen und erst danach abgebucht - während der Zahlung existieren sie nirgends im Spiel.
- Beim Abschalten des Plugins werden alle offenen Briefkästen gespeichert und geschlossen.

## Aufbau

| Klasse | Aufgabe |
| --- | --- |
| `MailboxObject` | Objekt-, Scharnier- und Paketdefinition |
| `MailboxItems` | Item, Reservierungs-Barriere, Menüknöpfe |
| `craft/MailboxRecipe` | Werkbank-Rezept, Freischaltung im Rezeptbuch |
| `MailboxAnimations` | Klappe, Fahne |
| `MailboxService` | Klammer: Aufstellen, Öffnen, Einwerfen, Abbauen, Label/Fahne/Paket aktualisieren |
| `box/MailboxIndex` + `MailboxRepository` | ein Briefkasten pro Spieler, `pc_mailboxes` |
| `box/MailboxInventories` | die eine lebende Ansicht, Persistenz, Anti-Dupe |
| `mail/DeliveryService` + `DeliveryRepository` | Preis, Laufzeit, Reservierungen, Zustellung, `pc_mail_deliveries` |
| `mail/SendMenu` + `SendHolder` | Versandmenü |
| `command/MailboxCommand` | `/mailbox` |
| `listener/…` | Aufstellen, Interaktion, Inventar, Login |

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
    .stackable(false)
    .label(1.7F, 0.16F)
    .build();

ObjectHinge.fromModel("door", 8.0D, 13.0D, 3.65D);
ObjectHinge.fromModel("flag", 14.85D, 19.0D, 8.0D);
```

Die Zahlen in `fromModel` sind die Scharnierpunkte direkt aus den Modelldateien.

## Permissions

| Permission | Standard | Wofür |
| --- | --- | --- |
| `pumpecraft.mailbox.command` | `true` | `/mailbox` mit `send`, `status`, `remove`, `info` |
| `pumpecraft.mailbox.place` | `true` | Briefkasten aus dem Item aufstellen |
| `pumpecraft.mailbox.use` | `true` | Briefe einwerfen |
| `pumpecraft.mailbox.give` | `false` | `/mailbox give` und `/mailbox spawn` - erzeugt Briefkästen aus dem Nichts, gehört dem Team |
| `pumpecraft.mailbox.manage` | `false` | fremde Briefkästen öffnen und abbauen |

Spieler brauchen also nichts zugewiesen zu bekommen; nur Team-Ränge bekommen `give` und `manage`
über LuckPerms. Spieler stellen sich ihren Briefkasten selbst her, `give` und `spawn` erzeugen ihn
aus dem Nichts und bleiben deshalb beim Team.

## Datenbank

`V18__mailboxes_and_deliveries.sql` legt an:

- `pc_mailboxes` - ein Eintrag pro Spieler mit Welt, Position und Body-UUID.
- `pc_mail_deliveries` - jede Sendung mit Inhalt, Stapelzahl, Kosten, Absende- und Ankunftszeit;
  `delivered_at` bleibt bis zur Zustellung leer, offene Sendungen werden beim Start neu geladen.

## Resourcepack

```
pack/
├── pack.mcmeta
└── assets/pumpecraft/
    ├── items/                    # mailbox, _body, _door, _flag, _parcel
    ├── models/item/              # dieselben fünf Modelle
    └── textures/item/mailbox.png # 32x32, sieben Zonen
```

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
