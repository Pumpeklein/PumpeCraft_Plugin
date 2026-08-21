# Plugin Overview

This workspace contains the PumpeCraft Paper plugin modules:

| Module | Plugin name | Main class |
| --- | --- | --- |
| `plugins/database` | `PumpeDatabase` | `de.pumpecraft.database.PumpeDatabasePlugin` |
| `plugins/utils` | `PumpeUtils` | `de.pumpecraft.utils.PumpeUtilsPlugin` |
| `plugins/essentials` | `PumpeEssentials` | `de.pumpecraft.essentials.PumpeEssentialsPlugin` |
| `plugins/mod` | `PumpeMod` | `de.pumpecraft.mod.PumpeModPlugin` |
| `plugins/clan-system` | `PumpeClanSystem` | `de.pumpecraft.clans.PumpeClanSystemPlugin` |
| `plugins/base-system` | `PumpeBaseSystem` | `de.pumpecraft.bases.PumpeBaseSystemPlugin` |
| `plugins/skills` | `PumpeSkills` | `de.pumpecraft.skills.PumpeSkillsPlugin` |
| `plugins/trader` | `PumpeTrader` | `de.pumpecraft.trader.PumpeTraderPlugin` |
| `plugins/death-messages` | `PumpeDeathMessages` | `de.pumpecraft.deathmessages.PumpeDeathMessagesPlugin` |
| `plugins/playtime` | `PumpePlaytime` | `de.pumpecraft.playtime.PumpePlaytimePlugin` |
| `plugins/anticheat` | `PumpeAntiCheat` | `de.pumpecraft.anticheat.PumpeAntiCheatPlugin` |
| `plugins/chat-control` | `PumpeChatControl` | `de.pumpecraft.chatcontrol.PumpeChatControlPlugin` |
| `plugins/transactions` | `PumpeTransactions` | `de.pumpecraft.transactions.PumpeTransactionsPlugin` |
| `plugins/mailbox` | `PumpeMailbox` | `de.pumpecraft.mailbox.PumpeMailboxPlugin` |

`database` and `utils` are library plugins: consumers declare them via `compileOnly` in Gradle
and `depend:` in their `plugin.yml`.

Keep shared configuration in the root Gradle files. Keep gameplay logic inside the matching module.
Conventions live in [CLAUDE.md](../CLAUDE.md); module specifics in the `CLAUDE.md` of the module.
