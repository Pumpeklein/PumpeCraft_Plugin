# Plugin Overview

This workspace contains the PumpeCraft Paper plugin modules:

| Module | Plugin name | Main class |
| --- | --- | --- |
| `plugins/database` | `PumpeDatabase` | `de.pumpecraft.database.PumpeDatabasePlugin` |
| `plugins/essentials` | `PumpeEssentials` | `de.pumpecraft.essentials.PumpeEssentialsPlugin` |
| `plugins/mod` | `PumpeMod` | `de.pumpecraft.mod.PumpeModPlugin` |
| `plugins/clan-system` | `PumpeClanSystem` | `de.pumpecraft.clans.PumpeClanSystemPlugin` |
| `plugins/skills` | `PumpeSkills` | `de.pumpecraft.skills.PumpeSkillsPlugin` |
| `plugins/trader` | `PumpeTrader` | `de.pumpecraft.trader.PumpeTraderPlugin` |
| `plugins/death-messages` | `PumpeDeathMessages` | `de.pumpecraft.deathmessages.PumpeDeathMessagesPlugin` |
| `plugins/playtime` | `PumpePlaytime` | `de.pumpecraft.playtime.PumpePlaytimePlugin` |
| `plugins/anticheat` | `PumpeAntiCheat` | `de.pumpecraft.anticheat.PumpeAntiCheatPlugin` |
| `plugins/chat-control` | `PumpeChatControl` | `de.pumpecraft.chatcontrol.PumpeChatControlPlugin` |

Keep shared configuration in the root Gradle files. Keep gameplay logic inside the matching module.
