# PumpeCraft Plugins

Multi-plugin workspace for the PumpeCraft server.

## Structure

- `plugins/essentials` - basic server commands and quality-of-life features
- `plugins/mod` - moderation tools
- `plugins/clan-system` - clan and group gameplay
- `plugins/skills` - skill progression
- `plugins/trader` - trader and economy interactions
- `plugins/death-messages` - custom death messages
- `plugins/playtime` - playtime tracking and rewards

Each folder is an independent Paper plugin module with its own `plugin.yml`, Java entrypoint and README.

## Build

Use the Gradle wrapper once it is available:

```powershell
.\gradlew.bat build
.\gradlew.bat collectPluginJars
```

The collected plugin jars will be placed in `build/plugins`.
