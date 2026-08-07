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
