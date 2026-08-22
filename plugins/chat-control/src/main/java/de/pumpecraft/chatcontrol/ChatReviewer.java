package de.pumpecraft.chatcontrol;

import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Zweite Meinung zu einer Nachricht, die der Wortfilter durchgelassen hat. Die Prüfung selbst
 * liegt in PumpeAI; hier steht nur, was ihr Urteil für den Chat bedeutet.
 */
interface ChatReviewer {
    boolean active();

    /**
     * Blockierende Prüfung mit Zeitgrenze - nur vom asynchronen Chat-Thread aus aufrufen. Läuft
     * die Zeit ab, gilt die Nachricht als erlaubt: ein langsamer Endpunkt darf den Chat nicht
     * anhalten.
     */
    FilterResult inspect(String message);

    CompletableFuture<FilterResult> review(String message);

    static ChatReviewer none() {
        return InactiveReviewer.INSTANCE;
    }

    static ChatReviewer create(Plugin plugin, ConfigurationSection config) {
        if (config != null && !config.getBoolean("enabled", true)) {
            return none();
        }
        // PumpeAI steht in softdepend: Ohne das Plugin darf keine seiner Klassen geladen werden,
        // darum wird der Prüfer erst hinter dieser Abfrage angefasst.
        if (plugin.getServer().getPluginManager().getPlugin("PumpeAI") == null) {
            return none();
        }
        return AiChatReviewer.create(plugin, config);
    }
}
