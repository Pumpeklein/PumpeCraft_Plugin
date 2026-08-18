# PumpeChatControl

Tracks accepted and blocked global and `/msg` player messages in
`pc_chat_messages`, blocks configured terms and spam for every player, and
gives authorized staff a clickable `[DEL]` control for deletable signed global
messages. Filter detections remain visible until staff chooses to delete or keep them.

## Configuration

- `config.yml`: filter terms, spam thresholds and player-facing text
- `permissions.yml`: LuckPerms-compatible permission nodes

## Permissions

- `pumpecraft.chatcontrol.delete`: view and use the global chat delete control

## Commands

- `/msg <player> <message>` (aliases: `/tell`, `/w`)
- `/chatcontrol delete <message-id>` (immediately deletes via the `[DEL]` control)
- `/chatcontrol keep <message-id>` (keeps a message flagged by the filter)
