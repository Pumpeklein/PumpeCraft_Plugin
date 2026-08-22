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

## Automatic review

The term list only catches what is spelled out in it, so every message it lets through is sent to
the moderation service of [PumpeAI](../ai/README.md) for a second opinion. What comes back has two
levels, because a detection that is not certain must not cost the chat its flow:

| Level | Chat | Staff sees |
| --- | --- | --- |
| light hit | delivered right away | `[Detectet]` in yellow and `[DEL]` to remove it afterwards |
| heavy hit | held back | `[Detectet]` in gold, `[DEL]` and `[BEHALTEN]` to publish it |

The border between the two are the `hold-thresholds` in the PumpeAI config; `action: block` turns
the heavy hit into an outright rejection. A term list hit is always a heavy one - it is spelled
out, so there is nothing uncertain about it.

A light hit is delivered but not forgotten: the row in `pc_chat_messages` keeps its
`block_reason` while `blocked` stays false, which is what tells a detection apart from an
ordinary message afterwards.

The check runs while the player waits, so it is bounded: after `max-wait-millis` the message goes
through unchecked. The same holds for a missing key, an unreachable endpoint or a disabled PumpeAI -
a review that cannot answer never costs a message. Private messages are checked too, but they
cannot be held: nobody could release them, so a hit blocks them.

## Configuration

- `config.yml`: filter terms, spam thresholds, automatic review and player-facing text
- `permissions.yml`: LuckPerms-compatible permission nodes
- thresholds and the OpenAI key for the review itself live in `plugins/PumpeAI/config.yml`

## Permissions

- `pumpecraft.chatcontrol.delete`: view and use the global chat delete control

## Commands

- `/msg <player> <message>` (aliases: `/tell`, `/w`; click a message to reply)
- `/chatcontrol delete <message-id>` (immediately deletes via the `[DEL]` control)
- `/chatcontrol keep <message-id>` (releases a message the filter held back)

All commands can also be executed from the server console.
