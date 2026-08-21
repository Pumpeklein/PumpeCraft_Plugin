# PumpeMod

Moderation commands, punishments, reports and staff tools.

## Commands

- `/report <Spieler> <Grund>` - reports a known player to the team.
- `/reports [unseen|all]` - shows unseen or all open reports.
- `/warn <Spieler> <Grund>` - warns a known player and stores the warning count.
- `/mute <Spieler> <Zeit> [Grund]` - mutes a known player for the given duration.
- `/unmute <Spieler>` - lifts an active mute.
- `/ban <Spieler> <Grund> [Zeit]` - bans a known player permanently or for a duration.
- `/unban <Spieler> [Grund]` - lifts an active ban.
- `/vanish` - toggles staff vanish: gone for players, sichtbar als Spec fürs Team.
- `/fly [Spieler]` - schaltet Fliegen für dich oder einen Onlinespieler ein oder aus, ohne den Spielmodus zu verändern.
- `/spectate` (`/spec`) - öffnet das Spectate-Menü. `/spec <Spieler>` beobachtet direkt, `/spec stop` beendet.

## Spectate

Das Menü listet alle sichtbaren Onlinespieler als Köpfe: **Linksklick** beobachtet, **Rechtsklick**
öffnet das Inventar. Oben links lädt ein Knopf die Liste neu, dazu gibt es Knöpfe für Inventar,
Enderchest, Zoom zurücksetzen und Beenden.

Während der Beobachtung:

| Eingabe | Wirkung |
| --- | --- |
| Mausrad heraus | Aus der Ego-Perspektive in die Kamera und weiter hinaus |
| Mausrad hinein | Näher heran, unterhalb der ersten Stufe zurück in die Ego-Perspektive |
| Maus (herausgezoomt) | Kreist um den beobachteten Spieler, er bleibt in der Bildmitte |
| Maus (Ego-Perspektive) | Frei, bis das Ziel sich umsieht - dann folgt der Blick wieder ihm |
| Linksklick | Spectate-Menü öffnen |
| F | Inventar des Ziels (`/openinv` aus PumpeEssentials) |
| Rechtsklick | Enderchest des Ziels |
| Schleichen oder Q | Beobachtung beenden |

Schleichen steigt vom Kameramarker ab; genau das ist der Ausstieg.

Beenden hängt bewusst an zwei Tasten. Die Ablegetaste meldet sich nur beim Server, solange die
gespiegelte Hand nicht leer ist - ein Ablegen ohne Gegenstand verwirft der Server, bevor daraus
ein Ereignis wird. Das Absteigen kommt immer an.

Die Kamera liegt der Blickrichtung des **Zuschauers** entgegengesetzt, nicht der des Ziels: Die
Maus führt sie um den Beobachteten herum, der dadurch in der Bildmitte stehen bleibt. Abstand null
ergibt dieselbe Rechnung als Ego-Perspektive - dort gehört die Blickrichtung wieder dem Ziel und
wird über `setRotation` nachgezogen, sobald es sich umsieht. Ein Teleport käme dafür nicht in
Frage: Er würfe den Zuschauer von seinem Kameramarker.

**Bewegt wird nicht der Zuschauer, sondern ein unsichtbarer Marker, auf dem er reitet.** Das ist
der Kern: Ein Spieler, den der Server jeden Tick teleportiert, springt in Zwanzigstelschritten -
der Client interpoliert die eigene Position nicht -, und jeder Teleport schreibt zugleich seine
Blickrichtung neu, weshalb sich die Kamera gegen die Maus stemmt. Als Passagier ist beides gelöst:
Der Client glättet die Bewegung fremder Wesen zwischen den Ticks, und von einem Reiter übernimmt
der Server ausschließlich die Blickrichtung, niemals die Position. Der Marker fährt mit
`RETAIN_PASSENGERS`, sonst würfe ihn jeder Teleport ab. Wie weit ein Reiter unter seinem Fahrzeug
sitzt, misst der erste Tick nach dem Aufsitzen selbst, statt eine Konstante des Spiels
abzuschreiben.

In der Aktionsleiste stehen Name, Leben, Kameraabstand und das Item in der Hand des Ziels. Die
Bildmitte bleibt frei: Dort steht das Fadenkreuz des Spiels.

Erfolge zählen während der Beobachtung nicht: Jedes erreichte Kriterium wird zurückgenommen. Der
Zuschauer ist körperlich in der Welt und trägt die Hotbar des Ziels - "Cover Me in Debris" verlangt
die vier Netheritteile nur irgendwo im Inventar, Biomerfolge nur die Anwesenheit. Verdient hat er
davon nichts.

Solange kein Fenster offen ist, zeigt die eigene Hotbar die Hotbar des Ziels samt ausgewähltem
Slot; sie ist gesperrt, das eigene Inventar kommt beim Schließen zurück. Zusätzlich liegt es im
PDC des Teamlers; ein Absturz mitten in der Beobachtung gibt es beim nächsten Beitritt zurück.

Der Zuschauer bleibt bewusst **nicht** im Spectator-Modus, sondern wird als unsichtbarer,
unverwundbarer Beobachter im Abenteuermodus geführt: Der Client meldet im Spectator-Modus weder
Mausrad noch Hotbar an den Server - das Rad regelt dort die Fluggeschwindigkeit -, womit weder
Zoom noch die fremde Hotbar möglich wären. Die Kamera hält vor Wänden an, statt in den Block zu
fahren. Der Teleport beim Aufsitzen trägt `RETAIN_OPEN_INVENTORY`, sonst fiele ein offenes Fenster
beim Wechsel des Ziels zu. Beenden stellt Standort, Spielmodus,
Flugzustand, Sättigung und Inventar von davor wieder her; Zielspieler offline, Spielmoduswechsel
von außen und Verlassen des Servers beenden ebenfalls.

Die Zoomstufen wachsen geometrisch und lassen sich in `config.yml` unter `spectate.zoom` einstellen.

Player names can also be entered with an `@` prefix, for example `/report @Fabienne Griefing`.
Targets can be online or known offline players.

## Zeitangaben

Alle Strafbefehle nutzen dasselbe Format:

| Einheit | Suffix | Beispiel |
| --- | --- | --- |
| Sekunden | `s` | `30s` |
| Minuten | `m` | `10m` |
| Stunden | `h` | `2h` |
| Tage | `d` | `7d` |
| Wochen | `w` | `1w` |

Einheiten lassen sich kombinieren (`1h30m`, `2d12h`). Eine Zahl ohne Suffix gilt
als Minuten (`/mute Steve 10` = 10 Minuten). Maximum sind 10 Jahre.

Bei `/ban` steht die Zeit **hinten**: Das letzte Argument wird als Zeitangabe
gelesen, wenn es dem Format entspricht, sonst gehört es zum Grund. Ohne Zeit ist
der Ban permanent.

```mcfunction
/mute Steve 30s Spam im Chat
/mute Steve 1h30m Beleidigung
/ban Steve Griefing 7d
/ban Steve Cheating
/unmute Steve
/unban Steve Einspruch akzeptiert
```

## Bans und Mutes in der Datenbank

Bans und Mutes liegen ausschließlich in MariaDB, nicht in `banned-players.json`:

- Beim Login prüft `AsyncPlayerPreLoginEvent` die Tabelle `pc_punishments` und
  weist gebannte Spieler mit dem eigenen Ban-Bildschirm ab. Panel, Server und
  Datenbank zeigen damit denselben Stand.
- `/unban` setzt `revoked_at`, `revoked_by` und `revoke_reason`, statt Einträge zu
  löschen - die Historie bleibt im Panel sichtbar. Zusätzlich werden alte
  Einträge aus `banned-players.json` entfernt, damit Spieler nicht doppelt
  ausgesperrt bleiben.
- `/unmute` setzt `unmuted_at` und `unmuted_by`; abgelaufene Mutes bleiben
  ebenfalls als Historie stehen.
- Der aktive Mute wird beim Login einmalig geladen und im Speicher gehalten. Der
  Chat-Check läuft dadurch ohne Datenbankabfrage pro Nachricht.

Fällt die Datenbank beim Login aus, wird der Fehler geloggt und der Spieler
durchgelassen, statt den kompletten Login zu blockieren.

Die Spalten kommen mit Migration `V3__punishment_lifecycle.sql` aus
`plugins/database`.

## Servermeldungen

`/mute`, `/unmute`, `/ban` und `/unban` melden dem ganzen Server, was passiert ist - über
`ModerationTopics` und `Messages.render` aus [plugins/utils](../utils/CLAUDE.md), damit
[PumpeAI](../ai/README.md) die Formulierung übernehmen kann, wenn es läuft. Der Grund einer Strafe
bleibt beim Team und steht in keiner dieser Meldungen; öffentlich sind nur Name und Dauer.

## Vanish

`/vanish` trennt Spieler und Team:

- **Ohne `pumpecraft.mod.vanish.see`**: Der Teamler verschwindet komplett. Es kommt eine echte
  Leave-Meldung aus `ConnectionMessages` in [plugins/utils](../utils/CLAUDE.md) - demselben Topf,
  aus dem auch [plugins/death-messages](../death-messages/README.md) beim echten Ausloggen zieht,
  damit ein Vanish nicht an der Formulierung erkennbar ist. Der Eintrag fällt aus der Tabliste und
  die Spielerfigur wird nicht mehr gesendet. Beim Ausschalten kommt die passende Join-Meldung.
- **Mit `pumpecraft.mod.vanish.see`**: Der Teamler bleibt in der Tabliste, ausgegraut als
  `Name [Spec]`, und schwebt im Spiel als Kopf mit seinem Namensschild - so, als wäre er im
  Spectator-Modus.

Der Spielmodus bleibt unangetastet: Der Teamler kann im Vanish weiter bauen und alles anfassen.
Er wird nur unsichtbar geschaltet, ist stumm, kollidiert nicht, wird von Mobs nicht anvisiert und
darf fliegen wie im Kreativmodus (Leertaste doppelt). Der Flugstand von vorher wird beim
Ausschalten, beim Ausloggen und beim Serverstop wiederhergestellt; er liegt zusätzlich im PDC des
Spielers, damit ein Absturz niemanden unsichtbar und fliegend zurücklässt.

Die Optik für das Team besteht aus drei Teilen: der unsichtbar geschaltete Spieler (nur sein
Namensschild bleibt sichtbar), ein `item_display` mit seinem Spielerkopf, das ihm folgt und nur
berechtigten Zuschauern gezeigt wird, und leer geschickte Ausrüstungs-Slots - ein unsichtbarer
Spieler würde sonst weiter Rüstung und Handitem zeigen. Wer selbst Spectator ist, sieht
unsichtbare Spieler ohnehin vollständig und bekommt den zusätzlichen Kopf deshalb nicht.

## Permissions

- `pumpecraft.mod.*`
- `pumpecraft.mod.report`
- `pumpecraft.mod.reports`
- `pumpecraft.mod.warn`
- `pumpecraft.mod.mute`
- `pumpecraft.mod.unmute`
- `pumpecraft.mod.ban`
- `pumpecraft.mod.unban`
- `pumpecraft.mod.vanish`
- `pumpecraft.mod.vanish.see`
- `pumpecraft.mod.fly`
- `pumpecraft.mod.spectate`

All permissions default to `false` and should be assigned through LuckPerms.

Recommended LuckPerms setup:

```mcfunction
/lp group support permission set pumpecraft.mod.* true
/lp group default permission set pumpecraft.mod.report true
/lp group builder permission set pumpecraft.mod.vanish.see true
```
