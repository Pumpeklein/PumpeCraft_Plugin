# PumpeChatControl

Tracks accepted and blocked global and `/msg` player messages in
`pc_chat_messages`, blocks configured terms and spam for every player, and
gives authorized staff a clickable `[DEL]` control for deletable signed global
messages. Filter detections are held back from public chat until staff chooses to
delete or publish them.

A held message is not the same as a blocked one, and the table keeps them apart:
`held_at` marks the hold, `approved_at` plus `approved_by_uuid`/`approved_by_name`
record who released it. Without those columns a released message was
indistinguishable from an ordinary one, because releasing only reset `blocked`.

## Configuration

- `config.yml`: filter terms, spam thresholds and player-facing text
- `permissions.yml`: LuckPerms-compatible permission nodes

## Permissions

- `pumpecraft.chatcontrol.delete`: view and use the global chat delete control

## Commands

- `/msg <player> <message>` (aliases: `/tell`, `/w`; click a message to reply)
- `/chatcontrol delete <message-id>` (immediately deletes via the `[DEL]` control)
- `/chatcontrol keep <message-id>` (releases a message the filter held back)
