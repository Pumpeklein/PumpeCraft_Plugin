package de.pumpecraft.chatcontrol;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;

final class ChatFilter {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern REPEATED_CHARACTERS = Pattern.compile("([a-z0-9])\\1+");

    private static final int MIN_STEM_LENGTH = 4;
    private static final int MAX_MISSING_CHARACTERS = 2;

    private final List<String> blockedTerms;
    private final long windowMillis;
    private final int maxMessages;
    private final long repeatMillis;
    private final Map<UUID, MessageHistory> histories = new ConcurrentHashMap<>();

    ChatFilter(FileConfiguration config) {
        blockedTerms = config.getStringList("filter.blocked-terms").stream()
            .map(ChatFilter::normalize)
            .filter(term -> !term.isBlank())
            .toList();
        windowMillis = Duration.ofSeconds(Math.max(1, config.getLong("filter.spam.window-seconds", 8))).toMillis();
        maxMessages = Math.max(2, config.getInt("filter.spam.max-messages", 5));
        repeatMillis = Duration.ofSeconds(Math.max(1, config.getLong("filter.spam.repeat-seconds", 30))).toMillis();
    }

    FilterResult inspect(UUID playerId, String message) {
        String original = message.trim();
        if (original.isBlank()) {
            return FilterResult.block("Die Nachricht enthält keinen lesbaren Inhalt.");
        }
        String normalized = normalize(original);
        for (String term : blockedTerms) {
            if (containsTerm(normalized, term)) {
                return FilterResult.hold("Der Chatfilter hat eine unzulässige Formulierung erkannt.");
            }
        }

        long now = System.currentTimeMillis();
        MessageHistory history = histories.computeIfAbsent(playerId, ignored -> new MessageHistory());
        synchronized (history) {
            while (!history.timestamps.isEmpty() && now - history.timestamps.peekFirst() > windowMillis) {
                history.timestamps.removeFirst();
            }
            if (history.timestamps.size() >= maxMessages) {
                return FilterResult.block("Du schreibst zu viele Nachrichten in kurzer Zeit.");
            }
            String comparisonKey = normalized.isBlank()
                ? original.toLowerCase(Locale.ROOT)
                : normalized;
            if (comparisonKey.equals(history.lastMessage) && now - history.lastMessageAt <= repeatMillis) {
                return FilterResult.block("Bitte wiederhole nicht mehrfach dieselbe Nachricht.");
            }
            history.timestamps.addLast(now);
            history.lastMessage = comparisonKey;
            history.lastMessageAt = now;
        }
        return FilterResult.allow();
    }

    private static boolean containsTerm(String message, String term) {
        String paddedMessage = " " + message + " ";
        String paddedTerm = " " + term + " ";
        if (paddedMessage.contains(paddedTerm)) {
            return true;
        }
        if (matchesStem(message, term)) {
            return true;
        }

        String collapsedTerm = term.replace(" ", "");
        StringBuilder obfuscated = new StringBuilder("(?:^|\\s)");
        for (int index = 0; index < collapsedTerm.length(); index++) {
            obfuscated.append(Pattern.quote(String.valueOf(collapsedTerm.charAt(index))));
            if (index + 1 < collapsedTerm.length()) {
                obfuscated.append("\\s*");
            }
        }
        obfuscated.append("(?:$|\\s)");
        return Pattern.compile(obfuscated.toString()).matcher(message).find();
    }

    /**
     * In der Liste steht "fotze", geschrieben wird "Fotz" oder "fotzen": ein Wort zaehlt auch
     * dann, wenn es den Begriff nur anfaengt oder um eine Endung verlaengert. Kurze Woerter
     * bleiben aussen vor, sonst traefe "hur" auf halbe Sprache zu.
     */
    private static boolean matchesStem(String message, String term) {
        if (term.contains(" ")) {
            return false;
        }
        for (String word : message.split(" ")) {
            if (word.length() < MIN_STEM_LENGTH) {
                continue;
            }
            if (word.startsWith(term)) {
                return true;
            }
            if (term.startsWith(word) && term.length() - word.length() <= MAX_MISSING_CHARACTERS) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = COMBINING_MARKS.matcher(
            Normalizer.normalize(value, Normalizer.Form.NFKD)
        ).replaceAll("").toLowerCase(Locale.ROOT);
        normalized = normalized
            .replace("ß", "ss")
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .replace('$', 's')
            .replace('@', 'a');
        normalized = REPEATED_CHARACTERS.matcher(normalized).replaceAll("$1");
        return SEPARATORS.matcher(normalized).replaceAll(" ").trim().replaceAll(" +", " ");
    }

    private static final class MessageHistory {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private String lastMessage = "";
        private long lastMessageAt;
    }
}
