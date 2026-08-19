package de.pumpecraft.utils.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlayerVanishChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean vanished;

    public PlayerVanishChangeEvent(Player player, boolean vanished) {
        super(player);
        this.vanished = vanished;
    }

    public boolean isVanished() {
        return vanished;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
