# Plugin Overview

This workspace contains seven separate plugins:

| Module | Plugin name | Main class |
| --- | --- | --- |
| `plugins/essentials` | `PumpeEssentials` | `de.pumpecraft.essentials.PumpeEssentialsPlugin` |
| `plugins/mod` | `PumpeMod` | `de.pumpecraft.mod.PumpeModPlugin` |
| `plugins/clan-system` | `PumpeClanSystem` | `de.pumpecraft.clans.PumpeClanSystemPlugin` |
| `plugins/skills` | `PumpeSkills` | `de.pumpecraft.skills.PumpeSkillsPlugin` |
| `plugins/trader` | `PumpeTrader` | `de.pumpecraft.trader.PumpeTraderPlugin` |
| `plugins/death-messages` | `PumpeDeathMessages` | `de.pumpecraft.deathmessages.PumpeDeathMessagesPlugin` |
| `plugins/playtime` | `PumpePlaytime` | `de.pumpecraft.playtime.PumpePlaytimePlugin` |

Keep shared configuration in the root Gradle files. Keep gameplay logic inside the matching module.
