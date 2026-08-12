# PumpeDatabase

Shared MariaDB infrastructure for the PumpeCraft plugins.

## Configuration

The plugin reads these environment variables:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
DB_CHARSET
```

Environment variables override `plugins/PumpeDatabase/config.yml`. Keep the
password in the server environment instead of committing it to this repository.
Optional tuning variables are `DB_POOL_SIZE` and `DB_CONNECTION_TIMEOUT_MS`.

## Migrations

Flyway runs the versioned scripts from `src/main/resources/db/migration` during
server startup. A failed connection or migration disables `PumpeDatabase`;
Paper then prevents all plugins declaring `depend: [PumpeDatabase]` from
starting.

On their first database-backed startup, PumpeMod, PumpePlaytime and
PumpeDeathMessages import any existing YAML data in a transaction. Completed
imports are tracked in `pc_legacy_imports`. The YAML files remain untouched as
backups and are no longer used for persistence.

### Versions

| Version | Script | Inhalt |
| --- | --- | --- |
| V1 | `V1__initial_schema.sql` | Grundschema für Reports, Warnungen, Mutes, Bans, Playtime und Tode |
| V2 | `V2__readable_uuid_columns.sql` | UUID-Spalten von `BINARY(16)` auf lesbares `CHAR(36)` |
| V3 | `V3__punishment_lifecycle.sql` | `revoked_at`/`revoked_by`/`revoke_reason` für Bans, `unmuted_at`/`unmuted_by` für Mutes |
| V4 | `V4__skill_stats.sql` | `pc_players` (Name je UUID), `pc_skill_stats` und `pc_skill_village_partners` für PumpeSkills |

V3 macht aufgehobene Strafen nachvollziehbar: `/unban` und `/unmute` markieren
den Eintrag, statt ihn zu löschen. Das Web-Panel erkennt selbst, ob die Spalten
schon vorhanden sind, und funktioniert auch vor der Migration.
