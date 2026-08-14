# PumpeTransactions

PumpePoints (PP), the PumpeCraft server currency, and the ledger behind it.

## Features

- Integer currency `PP` (PumpePoints); balances never go below zero.
- Every balance change is written to `pc_transactions` with signed amount, resulting balance,
  type, actor and counterparty - nothing changes an account without a ledger row.
- Timed payout: a player collects active playtime and receives 500 PP for every 30 minutes.
  Progress lives in `pc_currency_payouts` and survives a restart.
- Time only counts while the player is active; 5 minutes without input pause the timer
  (`payout.max-idle-minutes`, `0` disables the check).
- Player transfers with minimum and maximum per transfer; both accounts are locked in a fixed
  order inside one transaction, so concurrent transfers cannot deadlock or lose points.
- Leaderboard and per-player history straight from the ledger.
- `PointsService` is registered as a Bukkit service, so other plugins can charge or pay out
  PumpePoints via `Points.require(plugin)`.

## Commands

- `/pp` - own balance and time until the next payout.
- `/pp pay <Spieler> <Betrag>` - transfer PumpePoints.
- `/pp top` - leaderboard.
- `/pp history [Spieler]` - last bookings.
- `/pp <Spieler>` - balance of another player.
- `/pp give|take|set <Spieler> <Betrag> [Grund]` - staff corrections.

Aliases: `/points`, `/pumpepoints`.

## Permissions

- `pumpecraft.points.use` - defaults to `true`.
- `pumpecraft.points.pay` - defaults to `true`.
- `pumpecraft.points.others` - defaults to `op`.
- `pumpecraft.points.admin` - defaults to `op`.

## Configuration

`payout.amount`, `payout.interval-minutes`, `payout.max-idle-minutes`, `payout.announce`,
`transfer.minimum`, `transfer.maximum`, `history.size`, `leaderboard.size`.

## Database

Migration `V15__pumpe_points.sql` creates `pc_currency_accounts`, `pc_transactions` and
`pc_currency_payouts`.
