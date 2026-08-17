package de.pumpecraft.deathmessages;

import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementMessageListener implements Listener {
    static final MessageTopic ADVANCEMENT = MessageTopic.of(
        "advancement",
        "Schreibe Meldungen für einen Spieler, der einen Fortschritt geschafft hat. Der Name des"
            + " Fortschritts steht in {advancement} und gehört in jede Meldung.",
        "{player} hat {advancement} geschafft. Reine Absicht, natürlich.",
        "{player} kann jetzt {advancement}. Der Server ist mäßig beeindruckt.",
        "{player} hat {advancement} freigeschaltet und wird gleich unerträglich.",
        "{player} holt sich {advancement}. Screenshot folgt bestimmt.",
        "{advancement} für {player}. Wir tun mal so, als wäre das schwer gewesen.",
        "{player} hat {advancement} erledigt. Beim wievielten Versuch, verrät niemand.",
        "{player} sammelt {advancement} ein wie andere Leute Staub.",
        "{player} schafft {advancement} und erwartet jetzt Applaus."
    );

    /**
     * Ohne Meldung im Event würde auch Vanilla nichts ankündigen - Rezepte und versteckte
     * Fortschritte gehören nicht in den Chat.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (event.message() == null || display == null) {
            return;
        }

        event.message(Messages.render(ADVANCEMENT, NamedTextColor.GRAY, Map.of(
            "player", event.getPlayer().getName(),
            "advancement", PlainTextComponentSerializer.plainText().serialize(display.title())
        )));
    }
}
