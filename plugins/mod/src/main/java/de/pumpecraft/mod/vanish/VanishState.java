package de.pumpecraft.mod.vanish;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

record VanishState(
    Component tabName,
    GameMode gameMode,
    boolean silent,
    boolean collidable,
    boolean visibleByDefault
) {
    static VanishState capture(Player player) {
        return new VanishState(
            player.playerListName(),
            player.getGameMode(),
            player.isSilent(),
            player.isCollidable(),
            player.isVisibleByDefault()
        );
    }

    void restore(Player player) {
        player.playerListName(tabName);
        player.setGameMode(gameMode);
        player.setSilent(silent);
        player.setCollidable(collidable);
        player.setVisibleByDefault(visibleByDefault);
    }
}
