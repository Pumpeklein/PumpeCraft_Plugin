# PumpeAI

Bibliotheks-Plugin für alles, was ein Modell erledigt. Es entscheidet selbst nichts - es liefert
Textzeilen und Urteile an Plugins, die danach fragen. Zwei Dienste, zwei Anbieter:

| Dienst | Anbieter | Wofür |
| --- | --- | --- |
| `AiService` | DeepSeek | Erzeugte Servermeldungen |
| `ModerationService` | OpenAI | Prüfung von Spielertexten auf Beleidigung, Hass, Sexuelles |

Beide sind unabhängig voneinander: Fehlt ein Schlüssel, bleibt genau dieser Dienst still.

## Einrichtung Textgenerierung

`plugins/PumpeAI/config.yml`:

| Schlüssel | Bedeutung |
| --- | --- |
| `enabled` | Schaltet den Dienst ab, ohne den Schlüssel zu entfernen |
| `api-key` | DeepSeek-Schlüssel. Bleibt er leer, wird `DEEPSEEK_API_KEY` aus der Umgebung gelesen |
| `base-url` | Endpunkt, Standard `https://api.deepseek.com` |
| `model` | Standard `deepseek-chat` |
| `system-prompt` | Der Ton für alle erzeugten Texte |
| `temperature` | Höher = mehr Varianz |
| `max-tokens`, `request-timeout-seconds` | Grenzen pro Anfrage |
| `failure-cooldown-seconds` | Sperrzeit nach einem Fehlschlag |

Ohne Schlüssel bleibt der Dienst still: `available()` ist `false` und jedes Plugin nutzt weiter
seine eigenen Texte.

## Einrichtung Moderation

Der Moderations-Endpunkt von OpenAI kostet nichts, braucht aber einen **eigenen** Schlüssel - der
DeepSeek-Schlüssel funktioniert dort nicht. Abschnitt `moderation` in derselben `config.yml`:

| Schlüssel | Bedeutung |
| --- | --- |
| `enabled` | Schaltet die Prüfung ab, ohne den Schlüssel zu entfernen |
| `api-key` | OpenAI-Schlüssel. Bleibt er leer, wird `OPENAI_API_KEY` aus der Umgebung gelesen |
| `base-url` | Endpunkt, Standard `https://api.openai.com/v1` |
| `model` | Standard `omni-moderation-latest` |
| `request-timeout-seconds`, `failure-cooldown-seconds` | Grenzen pro Anfrage, Sperrzeit nach einem Fehlschlag |
| `max-characters` | Längere Texte werden vor dem Versand gekürzt |
| `default-threshold` | Ab welchem Wert eine Kategorie ohne eigene Schwelle als Treffer gilt |
| `thresholds` | Schwelle je Kategorie, niedriger = mehr Treffer |
| `default-hold-threshold`, `hold-thresholds` | Ab hier wiegt der Treffer schwer |
| `ignored-categories` | Kategorien, die nie als Treffer zählen |

OpenAI liefert zu jedem Text dreizehn Kategorien mit einem Wert zwischen 0 und 1 und ein eigenes
`flagged`. Dieses `flagged` setzt es erst sehr spät - deshalb entscheiden bei uns die Schwellen,
und `flagged` zählt nur zusätzlich.

Wie spät, zeigt ein gemessenes Beispiel: "Bring dich um" kommt auf 0.12 `self-harm/intent`, 0.02
`harassment` und `flagged=False`. Kurze deutsche Sätze bewertet das Modell durchgehend niedrig,
und darum stehen die Schwellen hier bei 0.05 bis 0.2 statt bei den 0.5, die man erwarten würde.
Harmlose Nachrichten liegen in denselben Kategorien bei 0.0001 und tiefer - der Abstand trägt das.
Was die Wortliste von `PumpeChatControl` ohnehin kennt, kommt hier gar nicht erst an.

Ein Urteil hat deshalb zwei Stufen: Wer nur die erste Schwelle reisst, ist ein Verdacht
(`ModerationSeverity.LOW`), wer auch die zweite reisst, ist deutlich (`HIGH`). Was ein Plugin
daraus macht, ist seine Sache - `PumpeChatControl` stellt einen Verdacht zu und markiert ihn nur,
und hält erst die deutlichen Treffer auf. Ohne diese Trennung hängt bei tiefen Schwellen der halbe
Chat fest, und bei hohen wird wieder nichts erkannt. Was in `ignored-categories` steht, zählt in keinem Fall:
`violence` steht dort, weil im Spiel dauernd jemand jemanden umbringt und meistens ein Creeper
gemeint ist.

`/pumpeai check <Text>` ist der Weg, die Schwellen einzustellen: Es zeigt für eine echte Nachricht
die fünf stärksten Kategorien mit ihren Werten.

## Befehl

- `/pumpeai status` - Schlüssel gesetzt, Modell, Endpunkt, bereit ja/nein, Zustand der Moderation.
- `/pumpeai test` - holt drei Beispielzeilen und zeigt sie an. Der Weg zum Prüfen der Verbindung.
- `/pumpeai check <Text>` - prüft einen Text und zeigt Urteil und die stärksten Kategorien.

Permission: `pumpecraft.ai.admin` (Standard: op).

## Was das Plugin tut

Beim Start hängt es sich über `Messages.use(...)` in die Sektion `messages` von
[PumpeUtils](../utils/CLAUDE.md) ein. Ab dann kommen die Meldungen **aller** Plugins von DeepSeek,
sofern rechtzeitig welche bereitliegen - Tode, Join und Leave, Fortschritte, Strafen, Trader.
Die Plugins merken davon nichts: Sie rendern ihr `MessageTopic`, den Rest macht diese Schicht.

Ohne PumpeAI, ohne Schlüssel oder nach einem Fehlschlag rendert `Messages` die Vorlagen des
Themas - das Verhalten ist dann exakt wie vorher.

So läuft eine Meldung:

1. Ein Plugin ruft `Messages.render(topic, farbe, werte)`.
2. `AiMessagePool` schaut in den Vorrat dieses Themas. Ist etwas da, wird es genommen.
3. Sinkt der Vorrat unter drei Zeilen, wird im Hintergrund um zehn neue gebeten - pro Thema
   immer nur eine Anfrage gleichzeitig. Gewartet wird nie.
4. `TopicPrompt` baut die Aufgabe aus `task` und drei Vorlagen des Themas als Stilbeispiel und
   leitet aus den Vorlagen ab, welche Platzhalter erlaubt und welche Pflicht sind.
5. Zeilen über 140 Zeichen, ohne Pflicht-Platzhalter oder mit erfundenen Platzhaltern werden
   verworfen.

Der erste Griff in ein leeres Thema liefert immer eine eigene Vorlage - deshalb wird jedes Thema
vorgewärmt, sobald es sich über `Messages.register` anmeldet. Beim Serverstart sind das rund 45
Anfragen (33 Todesursachen, Meilenstein, Join, First Join, Leave, Fortschritt, vier Strafen, zwei
Trader), die über drei Threads im Hintergrund laufen. `/pumpeai status` zeigt, wie voll die
Vorräte sind.

Ein Thema, das sich nicht anmeldet, wird erst beim ersten Gebrauch nachgefüllt - dann kommt genau
diese eine Meldung noch aus der Vorlage.

## Direkte Nutzung

Für alles, was keine Servermeldung ist:

```java
AiService ai = Ai.service(plugin);          // null, wenn PumpeAI nicht läuft
if (ai != null && ai.available()) {
    ai.lines("Schreibe Begrüßungen für neue Spieler.", 10)
        .thenAccept(lines -> /* in einen eigenen Vorrat legen */);
}
```

`softdepend: [PumpeAI]` in die `plugin.yml` eintragen, damit das eigene Plugin auch ohne die
KI startet. Niemals im Spielverlauf auf das Ergebnis warten.

## Texte prüfen

```java
ModerationService moderation = Ai.moderation(plugin);   // null, wenn PumpeAI nicht läuft
if (moderation != null && moderation.available()) {
    moderation.inspect(message).thenAccept(verdict -> {
        if (verdict.flagged()) {
            // verdict.label() ist der deutsche Kategoriename, verdict.score() die Sicherheit
        }
    });
}
```

Zwei Regeln, die aus einer Prüfung erst eine brauchbare Moderation machen:

- **Ein Fehlschlag darf keine Nachricht kosten.** `inspect` liefert bei Timeout, HTTP-Fehler oder
  fehlendem Schlüssel `ModerationVerdict.CLEAN`, und nach einem Fehlschlag pausiert der Dienst.
  Wer prüft, muss den unauffälligen Fall als Normalfall behandeln.
- **Wer wartet, wartet mit Grenze.** Ein Aufrufer auf einem asynchronen Thread darf
  `get(timeout, …)` benutzen und fällt danach auf "erlaubt" zurück. Auf dem Server-Thread wird
  nie gewartet, dort zählt nur `thenAccept` und ein Sprung zurück über den Scheduler.

Die Prüfung läuft auf einem eigenen Thread-Pool, getrennt von der Textgenerierung: Eine Anfrage,
auf die der Chat wartet, darf nicht hinter minutenlangem Meldungsnachschub hängen.

Beispiel im Bestand: `AiChatReviewer` in [plugins/chat-control](../chat-control/README.md).

## Aufbau

| Klasse | Aufgabe |
| --- | --- |
| `PumpeAiPlugin` | Config, Executor, Service-Registrierung |
| `AiSettings` | Konfiguration als Record, inklusive Schlüssel aus der Umgebung |
| `AiService` | Öffentliche API: `available()`, `lines(instruction, count)`, Sperrzeit nach Fehlern |
| `DeepSeekClient` | Der HTTP-Aufruf, blockierend, nur auf dem eigenen Thread |
| `ChatPayload` | Request-Body und Antwort der Chat-Completions-API |
| `TextLines` | Zerlegt die Antwort in Zeilen und entfernt Nummerierung und Anführungszeichen |
| `Ai` | Service-Lookup, `null` wenn das Plugin fehlt |
| `AiMessagePool` | Vorrat je Thema, Nachschub im Hintergrund, Prüfung der Zeilen |
| `TopicPrompt` | Aufgabe und erlaubte Platzhalter aus einem `MessageTopic` |
| `AiCommand` | `/pumpeai status\|test\|check` |
| `support/JsonHttp` | JSON-POST mit Bearer-Token, für beide Anbieter |
| `support/FailureCooldown` | Sperrzeit nach einem Fehlschlag |
| `support/DaemonThreads` | Benannte Daemon-Threads für die Executor-Pools |

| Klasse in `moderation` | Aufgabe |
| --- | --- |
| `ModerationService` | Öffentliche API: `available()`, `inspect(text)`, eigener Thread-Pool |
| `ModerationSettings` | Abschnitt `moderation` als Record, Schlüssel auch aus der Umgebung |
| `ModerationClient` | Der HTTP-Aufruf, blockierend, nur auf dem eigenen Thread |
| `ModerationPayload` | Request-Body und Antwort des Moderations-Endpunkts |
| `ModerationScores` | Die rohen Werte je Kategorie plus das `flagged` von OpenAI |
| `ModerationRules` | Schwellen und Ausnahmen - hier entsteht das Urteil |
| `ModerationVerdict` | Das Urteil: Stufe, stärkste Kategorie, alle Werte |
| `ModerationSeverity` | Die Stufen `NONE`, `LOW`, `HIGH` |
| `ModerationCategories` | Die Kategorien mit deutschem Namen |

Beide Schlüssel stehen ausschließlich im Authorization-Header und werden nirgends geloggt.
