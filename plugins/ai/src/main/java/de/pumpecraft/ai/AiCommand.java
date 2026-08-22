package de.pumpecraft.ai;

import de.pumpecraft.ai.moderation.ModerationCategories;
import de.pumpecraft.ai.moderation.ModerationService;
import de.pumpecraft.ai.moderation.ModerationSeverity;
import de.pumpecraft.ai.moderation.ModerationVerdict;
import de.pumpecraft.utils.Texts;
import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final List<String> ACTIONS = List.of("status", "test", "check", "topics");
    private static final int SHOWN_CATEGORIES = 5;

    private final Plugin plugin;
    private final AiService service;
    private final AiSettings settings;
    private final MessageSettings messageSettings;
    private final AiMessagePool pool;
    private final ModerationService moderation;

    AiCommand(
        Plugin plugin,
        AiService service,
        AiSettings settings,
        MessageSettings messageSettings,
        AiMessagePool pool,
        ModerationService moderation
    ) {
        this.plugin = plugin;
        this.service = service;
        this.settings = settings;
        this.messageSettings = messageSettings;
        this.pool = pool;
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> status(sender);
            case "test" -> test(sender);
            case "check" -> check(sender, args);
            case "topics" -> topics(sender);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !command.testPermissionSilent(sender)) {
            return List.of();
        }
        return ACTIONS.stream()
            .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(line("Schlüssel", settings.apiKey().isBlank() ? "fehlt" : "gesetzt"));
        sender.sendMessage(line("Modell", settings.model()));
        sender.sendMessage(line("Endpunkt", settings.baseUrl()));
        sender.sendMessage(line("Bereit", service.available() ? "ja" : "nein"));
        sender.sendMessage(line("Erzeugte Meldungen", messageSettings.enabled() ? "an" : "aus"));
        sender.sendMessage(line("Ausgenommene Themen", messageSettings.excludedTopics().isEmpty()
            ? "keine"
            : String.join(", ", messageSettings.excludedTopics())));
        sender.sendMessage(line("Vorrat", pool.summary()));
        sender.sendMessage(line("Verbrauch seit Start", service.usage().summary()));
        sender.sendMessage(line("Angemeldete Themen", String.valueOf(Messages.registered().size())));
        sender.sendMessage(line("Moderation", moderationState()));
        sender.sendMessage(line("Moderations-Modell", moderation.model()));
        return true;
    }

    private boolean topics(CommandSender sender) {
        List<MessageTopic> registered = Messages.registered().stream()
            .sorted(Comparator.comparing(MessageTopic::key))
            .toList();
        sender.sendMessage(line("Angemeldete Themen", String.valueOf(registered.size())));
        for (MessageTopic topic : registered) {
            String state = messageSettings.excluded(topic)
                ? "ausgenommen"
                : pool.stored(topic) + " Zeilen im Vorrat";
            sender.sendMessage(Component.text("• " + topic.key() + ": " + state, NamedTextColor.GRAY));
        }
        return true;
    }

    private String moderationState() {
        if (!moderation.enabled()) {
            return "abgeschaltet";
        }
        if (!moderation.configured()) {
            return "kein Schlüssel";
        }
        return moderation.available() ? "bereit" : "gesperrt nach Fehlschlag";
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

    private boolean check(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        if (!moderation.available()) {
            sender.sendMessage(Component.text(
                "Die Moderation ist nicht bereit. /pumpeai status zeigt, woran es liegt.", NamedTextColor.RED));
            return true;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        sender.sendMessage(Component.text("Prüfe …", NamedTextColor.GRAY));
        moderation.inspect(text).thenAccept(verdict ->
            Bukkit.getScheduler().runTask(plugin, () -> report(sender, verdict)));
        return true;
    }

    private void report(CommandSender sender, ModerationVerdict verdict) {
        sender.sendMessage(line("Urteil", describe(verdict.severity())));
        if (verdict.scores().isEmpty()) {
            sender.sendMessage(Component.text(
                "Keine Bewertung erhalten; Details stehen in der Konsole.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(line("Stärkste Kategorie", verdict.label() + " " + Texts.percent(verdict.score())));
        for (Map.Entry<String, Double> entry : verdict.highest(SHOWN_CATEGORIES)) {
            sender.sendMessage(Component.text(
                "• " + ModerationCategories.label(entry.getKey()) + ": " + Texts.percent(entry.getValue()),
                NamedTextColor.GRAY));
        }
    }

    private String describe(ModerationSeverity severity) {
        return switch (severity) {
            case NONE -> "unauffällig";
            case LOW -> "markieren, Nachricht geht raus";
            case HIGH -> "anhalten, Team entscheidet";
        };
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
