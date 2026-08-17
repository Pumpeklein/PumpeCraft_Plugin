package de.pumpecraft.utils.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zieht zufällig aus einer Vorlagenliste und vermeidet dabei die zuletzt gezogene Vorlage. Die
 * Sperre gilt über alle Listen derselben Rotation, damit zwei Meldungen hintereinander auch dann
 * verschieden sind, wenn sie aus unterschiedlichen Töpfen kommen.
 */
public final class MessageRotation {
    private final Random random = new Random();
    private String last = "";

    public String next(List<String> templates) {
        List<String> candidates = new ArrayList<>(templates);
        candidates.removeIf(template -> template.equals(last));

        List<String> pool = candidates.isEmpty() ? templates : candidates;
        last = pool.get(random.nextInt(pool.size()));
        return last;
    }
}
