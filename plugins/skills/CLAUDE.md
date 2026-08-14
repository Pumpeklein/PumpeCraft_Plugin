# PumpeSkills

Skill-Punkte, daraus abgeleitete Level, Meilenstein-Belohnungen, GUIs und Bestenlisten.

## Datenmodell

Gespeichert wird ausschließlich der **Punktestand** (`pc_skill_stats`, `stat_key = "score"`
pro Skill). Das Level ist eine reine Ableitung über `SkillLevel.levelOf(score)` — es gibt
keine Level-Spalte. Ein Level zu setzen heißt deshalb immer: Punkte auf
`SkillLevel.scoreForLevel(level)` setzen.

Daneben liegen Detailzähler unter eigenen `stat_key`-Werten (`ore.diamond_ore`, …). Die
bleiben von den Verwaltungsbefehlen unberührt.

## Zustand und Threads

`SkillService` hält die Zähler eingeloggter Spieler im Speicher und schreibt sie alle 30
Sekunden asynchron weg. Daraus folgt die wichtigste Regel:

**Für einen eingeloggten Spieler niemals direkt in die Datenbank schreiben** — der nächste
Speicherlauf überschreibt es mit dem Cache-Stand. Stattdessen `PlayerSkillData` ändern und
mit `service.persistNow(uuid)` sofort wegschreiben lassen. Nur für Offline-Spieler geht der
Weg über `SkillRepository`, und danach muss geprüft werden, ob der Spieler zwischenzeitlich
eingeloggt hat (siehe `SkillAdmin.apply`).

## Verwaltungsbefehle

`SkillAdmin` bündelt alles, was Punktestände verändert, hinter
`pumpecraft.skills.admin`. Diese Unterbefehle laufen **vor** der Spieler-Prüfung in
`SkillsCommand`, damit sie auch von der Konsole aus funktionieren.

| Befehl | Wirkung |
| --- | --- |
| `/skills set <Spieler> <Skill> <Punkte>` | Punkte exakt setzen |
| `/skills add <Spieler> <Skill> <Punkte>` | Punkte addieren, negativ zum Abziehen |
| `/skills setlevel <Spieler> <Skill> <Level>` | Punkte auf den Anfang dieses Levels |
| `/skills reset <Spieler> [Skill]` | Punkte auf 0, ohne Skill für alle |

Offline-Spieler werden über `SkillRepository.findPlayerByName` aufgelöst und funktionieren
genauso.

Belohnungen werden auch beim Setzen vergeben — `pc_skill_rewards` hat einen Unique-Key und
`INSERT IGNORE`, ein bereits ausgeschütteter Meilenstein kommt also nicht doppelt. Wer
Punkte senkt und wieder anhebt, löst deshalb keine erneute Ausschüttung aus.

## Belohnungen

Die **Config ist die einzige Quelle**. `rewards.milestones` gilt für jeden Skill,
`rewards.skills.<skill>.<level>` ersetzt eine Stufe oder ergänzt eine zusätzliche; die
Meilensteine eines Skills sind die Vereinigung beider Mengen. Stufen dürfen auf jedem Level
zwischen 2 und `SkillLevel.MAX_LEVEL` liegen.

Items entweder kurz als `MATERIAL:Anzahl` oder als Block mit `material`, `amount`, `name`
und `enchantments`. Verzauberungen werden ohne `ignoreLevelRestriction` gesetzt: eine zu
hohe Stufe wird beim Laden abgelehnt statt ein Item zu erzeugen, das die Item-Prüfung des
AntiCheats auslöst.

### pc_skill_reward_definitions

Reiner **Schreibspiegel**, kein Zustand. `SkillRewardService` ruft im Konstruktor
`repository.syncRewardDefinitions(...)` auf, das die Tabelle leert und aus der Config neu
füllt. Weder das Plugin noch NGLive_Web liest sie je. Wer dort etwas ändert, verliert es
beim nächsten Serverstart — die Config ist immer stärker. Die Tabelle existiert nur, damit
externe Oberflächen die Reward-Stufen anzeigen können, ohne die Config zu parsen.
