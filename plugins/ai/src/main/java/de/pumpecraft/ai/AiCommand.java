package de.pumpecraft.ai;

import de.pumpecraft.utils.messages.Messages;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

final class AiCommand implements CommandExecutor, TabCompleter {
    private static final String TEST_PROMPT =
        "Schreibe Beispielmeldungen für einen Testlauf. Nutze den Platzhalter {player} wörtlich.";

    private final Plugin plugin;
    private final AiService service;
    private final AiSettings settings;
    private final AiMessagePool pool;

    AiCommand(Plugin plugin, AiService service, AiSettings settings, AiMessagePool pool) {
        this.plugin = plugin;
        this.service = service;
        this.settings = settings;
        this.pool = pool;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> status(sender);
            case "test" -> test(sender);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !command.testPermissionSilent(sender)) {
            return List.of();
        }
        return List.of("status", "test").stream()
            .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(line("Schlüssel", settings.apiKey().isBlank() ? "fehlt" : "gesetzt"));
        sender.sendMessage(line("Modell", settings.model()));
        sender.sendMessage(line("Endpunkt", settings.baseUrl()));
        sender.sendMessage(line("Bereit", service.available() ? "ja" : "nein"));
        sender.sendMessage(line("Vorrat", pool.summary()));
        sender.sendMessage(line("Angemeldete Themen", String.valueOf(Messages.registered().size())));
        return true;
    }

    private boolean test(CommandSender sender) {
        if (!service.available()) {
            sender.sendMessage(Component.text(
                "DeepSeek ist nicht bereit. /pumpeai status zeigt, woran es liegt.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Frage DeepSeek …", NamedTextColor.GRAY));
        service.lines(TEST_PROMPT, 3).thenAccept(lines ->
            Bukkit.getScheduler().runTask(plugin, () -> report(sender, lines)));
        return true;
    }

    private void report(CommandSender sender, List<String> lines) {
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text(
                "Keine Antwort erhalten; Details stehen in der Konsole.", NamedTextColor.RED));
            return;
        }
        lines.forEach(text -> sender.sendMessage(Component.text("• " + text, NamedTextColor.GRAY)));
    }

    private Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text(value, NamedTextColor.AQUA));
    }
}
