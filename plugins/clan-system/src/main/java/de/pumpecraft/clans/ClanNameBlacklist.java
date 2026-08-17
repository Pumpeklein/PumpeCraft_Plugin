package de.pumpecraft.clans;

import java.io.File;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class ClanNameBlacklist {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern REPEATED_CHARACTERS = Pattern.compile("(.)\\1+");

    private final Set<String> exactTerms;
    private final Set<String> containedTerms;

    private ClanNameBlacklist(Set<String> exactTerms, Set<String> containedTerms) {
        this.exactTerms = exactTerms;
        this.containedTerms = containedTerms;
    }

    static ClanNameBlacklist load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "clan-name-blacklist.yml");
        if (!file.isFile()) {
            plugin.saveResource("clan-name-blacklist.yml", false);
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        Set<String> exactTerms = normalizedTerms(configuration.getStringList("blocked-exact"));
        Set<String> containedTerms = normalizedTerms(
            configuration.getStringList("blocked-contains"));
        plugin.getLogger().info(
            "Loaded " + (exactTerms.size() + containedTerms.size())
                + " blocked clan name patterns."
        );
        return new ClanNameBlacklist(exactTerms, containedTerms);
    }

    boolean isBlocked(String value) {
        String normalized = normalize(value);
        String collapsed = collapseRepeatedCharacters(normalized);
        if (normalized.isBlank()) {
            return false;
        }
        if (exactTerms.contains(normalized) || exactTerms.contains(collapsed)) {
            return true;
        }
        for (String term : containedTerms) {
            if (normalized.contains(term) || collapsed.contains(collapseRepeatedCharacters(term))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizedTerms(List<String> values) {
        Set<String> terms = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                terms.add(normalized);
            }
        }
        return Set.copyOf(terms);
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
            .replace('8', 'b')
            .replace('$', 's')
            .replace('@', 'a')
            .replace('!', 'i')
            .replace('а', 'a')
            .replace('е', 'e')
            .replace('о', 'o')
            .replace('р', 'p')
            .replace('с', 'c')
            .replace('х', 'x')
            .replace('у', 'y')
            .replace('і', 'i');
        return NON_ALPHANUMERIC.matcher(normalized).replaceAll("");
    }

    private static String collapseRepeatedCharacters(String value) {
        return REPEATED_CHARACTERS.matcher(value).replaceAll("$1");
    }
}
