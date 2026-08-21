package de.pumpecraft.subessentials;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class TwitchLinkCommand implements CommandExecutor, TabCompleter {
    private final SubEssentialsPlugin plugin;
    private final TwitchLinkRepository repository;
    private final TwitchSettings settings;
    private final SubscriberStatusService subscribers;
    private final SecureRandom random = new SecureRandom();

    TwitchLinkCommand(
        SubEssentialsPlugin plugin,
        TwitchLinkRepository repository,
        TwitchSettings settings,
        SubscriberStatusService subscribers
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
        this.subscribers = subscribers;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur im Spiel verwendet werden.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Verwendung: /twitch <link|unlink>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("unlink")) {
            unlink(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("link")) {
            player.sendMessage(Component.text("Verwendung: /twitch <link|unlink>", NamedTextColor.RED));
            return true;
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256(token);
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        player.sendMessage(Component.text("Dein sicherer Twitch-Link wird erstellt …", NamedTextColor.GRAY));
        plugin.runAsync(() -> {
            repository.createRequest(
                playerId, playerName, tokenHash, now, now + settings.linkLifetimeMillis()
            );
            String separator = settings.linkUrl().contains("?") ? "&" : "?";
            String url = settings.linkUrl() + separator + "token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
            plugin.runSync(() -> {
                if (!player.isOnline()) return;
                player.sendMessage(Component.text("[Twitch jetzt verbinden]", NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text(
                        "Klicken, um Twitch im Browser zu öffnen", NamedTextColor.GRAY
                    ))));
                player.sendMessage(Component.text(
                    "Der Link ist " + settings.linkLifetimeMillis() / 60_000L
                        + " Minuten gültig und kann nur einmal benutzt werden.",
                    NamedTextColor.DARK_GRAY
                ));
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase();
        return List.of("link", "unlink").stream()
            .filter(value -> value.startsWith(prefix))
            .toList();
    }

    private void unlink(Player player) {
        UUID playerId = player.getUniqueId();
        long removedAt = System.currentTimeMillis();
        player.sendMessage(Component.text("Twitch-Verknüpfung wird entfernt …", NamedTextColor.GRAY));
        plugin.runAsync(() -> {
            boolean removed = repository.unlink(playerId);
            subscribers.handleUnlinked(playerId, removedAt);
            plugin.runSync(() -> {
                if (!player.isOnline()) return;
                if (removed) {
                    player.sendMessage(Component.text(
                        "Deine Twitch-Verknüpfung wurde entfernt.", NamedTextColor.LIGHT_PURPLE
                    ));
                    player.sendMessage(Component.text(
                        "Sub-Vorteile und die lila Tab-Farbe wurden deaktiviert.",
                        NamedTextColor.GRAY
                    ));
                } else {
                    player.sendMessage(Component.text(
                        "Du hast aktuell kein Twitch-Konto verbunden.", NamedTextColor.YELLOW
                    ));
                }
            });
        });
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
