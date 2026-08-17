# PumpeAI

DeepSeek-Anbindung als Bibliotheks-Plugin. Es erzeugt selbst keine Meldungen - es liefert nur
Textzeilen an Plugins, die danach fragen.

## Einrichtung

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

## Befehl

- `/pumpeai status` - Schlüssel gesetzt, Modell, Endpunkt, bereit ja/nein.
- `/pumpeai test` - holt drei Beispielzeilen und zeigt sie an. Der Weg zum Prüfen der Verbindung.

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
| `AiCommand` | `/pumpeai status\|test` |

Der Schlüssel steht ausschließlich im Authorization-Header und wird nirgends geloggt.
