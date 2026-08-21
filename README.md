# PumpeCraft Plugins

Multi-plugin workspace for the PumpeCraft server.

## Structure

- `plugins/database` - shared MariaDB pool, schema migrations and database API
- `plugins/essentials` - basic server commands and quality-of-life features
- `plugins/mod` - moderation tools
- `plugins/clan-system` - clan and group gameplay
- `plugins/base-system` - player bases and plots with PP pricing, roles, flags and protection
- `plugins/skills` - skill progression, statistics and leaderboards
- `plugins/trader` - trader and economy interactions
- `plugins/death-messages` - custom death messages
- `plugins/playtime` - playtime tracking and rewards
- `plugins/anticheat` - Bedrock-aware server-side cheat detection
- `plugins/chat-control` - global/private chat tracking, filtering and DEL moderation

Each folder is an independent Paper plugin module with its own `plugin.yml`, Java entrypoint and README.

## Build

Use the Gradle wrapper once it is available:

```powershell
.\gradlew.bat build
.\gradlew.bat collectPluginJars
```

The collected plugin jars will be placed in `build/plugins`.

## Database

Deploy `database-<version>.jar` together with the other plugin jars and provide
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` and `DB_CHARSET` in
the server environment. PumpeDatabase applies versioned Flyway migrations
before database-dependent plugins start.
