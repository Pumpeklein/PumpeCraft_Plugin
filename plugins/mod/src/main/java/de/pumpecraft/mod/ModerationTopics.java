package de.pumpecraft.mod;

import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;

/** Themen der Strafen-Meldungen, die der ganze Server sieht. Gründe bleiben beim Team. */
final class ModerationTopics {
    static final MessageTopic MUTED = MessageTopic.of(
        "moderation-muted",
        "Schreibe Meldungen für einen Spieler, der für eine bestimmte Zeit gemutet wurde. Die Dauer"
            + " steht in {duration}. Der Grund ist nicht öffentlich und darf nicht erfunden werden.",
        "{player} ist für {duration} stumm. Der Chat gönnt sich eine Pause.",
        "{player} darf {duration} lang nur zuhören.",
        "{player} wurde für {duration} auf lautlos gestellt.",
        "{player} macht {duration} Schweigeminute. Unfreiwillig.",
        "{player} ist für {duration} gemutet. Tippen hilft jetzt auch nicht.",
        "Für {player} gilt {duration} Funkstille."
    );

    static final MessageTopic UNMUTED = MessageTopic.of(
        "moderation-unmuted",
        "Schreibe Meldungen für einen Spieler, dessen Mute aufgehoben wurde.",
        "{player} darf wieder reden. Mal sehen, wie lange.",
        "{player} ist entmutet und hat garantiert einiges nachzuholen.",
        "{player} hat die Stimme zurück.",
        "{player} ist wieder im Chat zugelassen.",
        "Der Mute von {player} ist Geschichte."
    );

    static final MessageTopic BANNED = MessageTopic.of(
        "moderation-banned",
        "Schreibe Meldungen für einen Spieler, der vom Server gebannt wurde. Die Dauer steht in"
            + " {duration} und lautet zum Beispiel 'permanent' oder '7 Tage'. Der Grund ist nicht"
            + " öffentlich und darf nicht erfunden werden. Nicht nachtreten, nur trocken feststellen.",
        "{player} wurde {duration} gebannt. Die Regeln standen da.",
        "{player} ist {duration} raus. Man hätte es kommen sehen können.",
        "{player} hat sich {duration} Hausverbot verdient.",
        "{player} macht {duration} Pause vom Server. Nicht freiwillig.",
        "Für {player} ist {duration} Schluss."
    );

    static final MessageTopic UNBANNED = MessageTopic.of(
        "moderation-unbanned",
        "Schreibe Meldungen für einen Spieler, dessen Ban aufgehoben wurde.",
        "{player} darf wieder mitspielen. Zweite Chance läuft.",
        "{player} ist entbannt. Diesmal mit Regeln lesen.",
        "Der Ban von {player} wurde aufgehoben.",
        "{player} ist zurück im Rennen.",
        "{player} darf wiederkommen. Wir schauen zu."
    );

    static void register() {
        Messages.register(MUTED, UNMUTED, BANNED, UNBANNED);
    }

    private ModerationTopics() {
    }
}
