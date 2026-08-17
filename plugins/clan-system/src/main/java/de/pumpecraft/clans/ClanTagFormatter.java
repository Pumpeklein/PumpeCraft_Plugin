package de.pumpecraft.clans;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class ClanTagFormatter {
    private ClanTagFormatter() {
    }

    static Component badge(String tag, String storedColor) {
        return Component.text("◆ ", NamedTextColor.DARK_GRAY)
            .append(Component.text(
                tag.toUpperCase(Locale.ROOT),
                ClanColors.color(storedColor),
                TextDecoration.BOLD
            ));
    }

    static Component prefix(String tag, String storedColor) {
        return badge(tag, storedColor)
            .append(Component.text("  ", NamedTextColor.DARK_GRAY));
    }
}
