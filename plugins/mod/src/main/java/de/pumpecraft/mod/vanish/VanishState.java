package de.pumpecraft.mod.vanish;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

record VanishState(
    Component tabName,
    boolean allowFlight,
    boolean flying,
    boolean silent,
    boolean collidable,
    boolean invisible,
    boolean visibleByDefault
) {
    static VanishState capture(Player player) {
        return new VanishState(
            player.playerListName(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isSilent(),
            player.isCollidable(),
            player.isInvisible(),
            player.isVisibleByDefault()
        );
    }

    void restore(Player player) {
        player.playerListName(tabName);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && player.getAllowFlight());
        player.setSilent(silent);
        player.setCollidable(collidable);
        player.setInvisible(invisible);
        player.setVisibleByDefault(visibleByDefault);
    }
}
