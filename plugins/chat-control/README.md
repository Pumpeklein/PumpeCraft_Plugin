# PumpeChatControl

Tracks accepted and blocked global and `/msg` player messages in
`pc_chat_messages`, blocks configured terms and spam for every player, and
gives authorized staff a clickable `[DEL]` control for deletable signed global
messages.

## Configuration

- `config.yml`: filter terms, spam thresholds and player-facing text
- `permissions.yml`: LuckPerms-compatible permission nodes

## Permissions

- `pumpecraft.chatcontrol.delete`: view and use the global chat delete control

## Commands

- `/msg <player> <message>` (aliases: `/tell`, `/w`)
- `/chatcontrol delete <message-id>` (normally invoked by the `[DEL]` control)
