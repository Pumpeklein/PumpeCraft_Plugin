package de.pumpecraft.essentials.back;

import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;

final class BackView {
    private static final Component DIVIDER = Component.text("─".repeat(30), NamedTextColor.DARK_GRAY);
    private static final Component SEPARATOR = Component.text(" · ", NamedTextColor.DARK_GRAY);

    private BackView() {
    }

    static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    static List<Component> history(
        String title,
        List<BackLocation> entries,
        String selectPrefix,
        String teleportCommand
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(DIVIDER);
        lines.add(Component.text(title, NamedTextColor.GOLD));
        if (entries.isEmpty()) {
            lines.add(info("Keine gespeicherten Punkte."));
        }
        long now = System.currentTimeMillis();
        for (int index = 0; index < entries.size(); index++) {
            lines.add(line(index + 1, entries.get(index), selectPrefix, teleportCommand, now));
        }
        lines.add(DIVIDER);
        return lines;
    }

    /**
     * Der anklickbare Index hängt an einem eigenen Kind: Adventure vererbt Klick- und
     * Hoverereignisse an alle Kinder, ein Klickziel auf der ganzen Zeile wäre die Folge.
     */
    private static Component line(
        int index,
        BackLocation entry,
        String selectPrefix,
        String teleportCommand,
        long now
    ) {
        String select = selectPrefix + index;
        Component number = Teleports.clickable(
            Component.text(String.valueOf(index), NamedTextColor.YELLOW),
            select,
            Component.text("Klicken für " + select, NamedTextColor.GRAY)
        );
        return Component.text()
            .append(Component.text(" "))
            .append(number)
            .append(SEPARATOR)
            .append(Component.text(entry.cause().label(), color(entry.cause())))
            .append(SEPARATOR)
            .append(position(entry, teleportCommand))
            .append(Component.text(" " + entry.world(), NamedTextColor.GRAY))
            .append(SEPARATOR)
            .append(Component.text(Texts.since(now - entry.createdAt()), NamedTextColor.DARK_GRAY))
            .build();
    }

    static Component position(BackLocation entry, String teleportCommand) {
        Optional<Location> location = entry.resolve();
        return location
            .map(resolved -> Teleports.locationLink(resolved, NamedTextColor.AQUA, teleportCommand))
            .orElseGet(() -> Component.text(
                "[" + Math.round(entry.x()) + ", " + Math.round(entry.y()) + ", " + Math.round(entry.z()) + "]",
                NamedTextColor.DARK_GRAY
            ));
    }

    private static TextColor color(BackCause cause) {
        return cause == BackCause.DEATH ? NamedTextColor.RED : NamedTextColor.AQUA;
    }
}
