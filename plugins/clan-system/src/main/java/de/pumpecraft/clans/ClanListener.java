package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.Invitation;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

final class ClanListener implements Listener {
    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final ClanTabService tabService;

    ClanListener(
        PumpeClanSystemPlugin plugin,
        ClanRepository repository,
        ClanTabService tabService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.tabService = tabService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> tabService.apply(player), 2L);
        plugin.runAsync(
            player,
            () -> {
                repository.touchPlayer(new ClanData.PlayerIdentity(
                    player.getUniqueId(), player.getName()));
                return repository.invitations(player.getUniqueId(), System.currentTimeMillis());
            },
            invitations -> {
                sendInvitations(player, invitations);
                plugin.refreshDirectory();
            }
        );
    }

    private void sendInvitations(Player player, List<Invitation> invitations) {
        if (invitations.isEmpty()) {
            return;
        }
        player.sendMessage(Component.text(
            "Du hast " + invitations.size() + " offene Clan-Einladung(en):",
            NamedTextColor.GOLD
        ));
        long now = System.currentTimeMillis();
        for (Invitation invitation : invitations) {
            long minutes = Math.max(1L, Duration.ofMillis(invitation.expiresAt() - now).toMinutes());
            player.sendMessage(
                ClanTagFormatter.prefix(invitation.clanTag(), "AQUA")
                    .append(Component.text(invitation.clanName(), NamedTextColor.WHITE))
                    .append(Component.text(" · noch " + minutes + " Min. ", NamedTextColor.GRAY))
                    .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand(
                            "/clan accept " + invitation.clanTag()
                        ))
                        .hoverEvent(HoverEvent.showText(Component.text(
                            "Clan-Einladung annehmen", NamedTextColor.GREEN
                        ))))
            );
        }
    }
}
