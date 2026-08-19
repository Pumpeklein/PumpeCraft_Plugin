package de.pumpecraft.essentials.pose;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import de.pumpecraft.utils.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PoseCommand implements CommandExecutor, TabCompleter {
    private final SeatService seats;
    private final CrawlService crawl;

    public PoseCommand(SeatService seats, CrawlService crawl) {
        this.seats = seats;
        this.crawl = crawl;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }
        Player player = self.get();
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sit" -> sit(player);
            case "crawl" -> crawl(player);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return List.of();
    }

    private boolean sit(Player player) {
        if (seats.isSitting(player)) {
            seats.stand(player);
            player.sendMessage(info("Du stehst wieder."));
            return true;
        }
        String blocker = blocker(player);
        if (blocker != null) {
            player.sendMessage(error(blocker));
            return true;
        }

        crawl.stop(player);
        if (!seats.sit(player)) {
            player.sendMessage(error("Hier kannst du dich nicht hinsetzen."));
            return true;
        }
        player.sendMessage(info("Du sitzt. Mit schleichen oder /sit kannst du wieder aufstehen."));
        return true;
    }

    private boolean crawl(Player player) {
        if (crawl.isCrawling(player)) {
            crawl.stop(player);
            player.sendMessage(info("Du bist aufgestanden."));
            return true;
        }
        String blocker = blocker(player);
        if (blocker != null) {
            player.sendMessage(error(blocker));
            return true;
        }

        seats.stand(player);
        crawl.start(player);
        player.sendMessage(info("Du liegst. Mit schleichen oder /crawl kannst du wieder aufstehen."));
        return true;
    }

    /** @return der Grund, warum die Haltung gerade nicht geht, oder {@code null}. */
    private String blocker(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return "Als Zuschauer geht das nicht.";
        }
        if (player.isInsideVehicle() && !seats.isSitting(player)) {
            return "Steig erst aus.";
        }
        if (player.isSleeping()) {
            return "Du liegst schon.";
        }
        if (player.isFlying() || player.isGliding()) {
            return "Nicht im Flug.";
        }
        return null;
    }

    private static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
