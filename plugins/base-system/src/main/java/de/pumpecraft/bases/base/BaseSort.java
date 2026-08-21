package de.pumpecraft.bases.base;

import java.util.Locale;
import org.bukkit.Material;

public enum BaseSort {
    LIKES("Beliebteste", Material.HEART_OF_THE_SEA, "like_count DESC, visit_count DESC, owner_name ASC"),
    VISITS("Meistbesucht", Material.ENDER_EYE, "visit_count DESC, like_count DESC, owner_name ASC"),
    NEWEST("Neueste", Material.CLOCK, "updated_at DESC, owner_name ASC"),
    NAME("Nach Name", Material.NAME_TAG, "owner_name ASC");

    private final String displayName;
    private final Material icon;
    private final String orderBy;

    BaseSort(String displayName, Material icon, String orderBy) {
        this.displayName = displayName;
        this.icon = icon;
        this.orderBy = orderBy;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /**
     * Feste Sortierklausel je Konstante; sie wird in das Statement eingesetzt und darf deshalb
     * niemals aus einer Eingabe entstehen.
     */
    public String orderBy() {
        return orderBy;
    }

    public BaseSort next() {
        BaseSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
