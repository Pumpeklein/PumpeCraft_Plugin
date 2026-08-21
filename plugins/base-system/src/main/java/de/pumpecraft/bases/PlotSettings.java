package de.pumpecraft.bases;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class PlotSettings {
    private final List<String> allowedWorlds;
    private final long pricePerBlock;
    private final long minimumPrice;
    private final int refundPercent;
    private final double fullPriceRadius;
    private final double cheapestAt;
    private final double minimumFactor;
    private final int maxPerPlayer;
    private final int minSize;
    private final int maxSize;
    private final Material selectionTool;

    PlotSettings(FileConfiguration config) {
        allowedWorlds = config.getStringList("plots.allowed-worlds").stream()
            .map(world -> world.toLowerCase(Locale.ROOT))
            .toList();
        pricePerBlock = Math.max(0L, config.getLong("plots.price-per-block", 2L));
        minimumPrice = Math.max(0L, config.getLong("plots.minimum-price", 500L));
        refundPercent = Math.clamp(config.getInt("plots.refund-percent", 60), 0, 100);
        fullPriceRadius = Math.max(0.0D, config.getDouble("plots.distance.full-price-radius", 250.0D));
        cheapestAt = Math.max(fullPriceRadius, config.getDouble("plots.distance.cheapest-at", 4000.0D));
        minimumFactor = Math.clamp(config.getDouble("plots.distance.minimum-factor", 0.25D), 0.0D, 1.0D);
        maxPerPlayer = Math.max(0, config.getInt("plots.limits.max-per-player", 3));
        minSize = Math.max(1, config.getInt("plots.limits.min-size", 8));
        maxSize = Math.max(minSize, config.getInt("plots.limits.max-size", 128));
        selectionTool = tool(config.getString("plots.selection-tool", "GOLDEN_SHOVEL"));
    }

    public boolean worldAllowed(World world) {
        return allowedWorlds.isEmpty()
            || allowedWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public long pricePerBlock() {
        return pricePerBlock;
    }

    public long minimumPrice() {
        return minimumPrice;
    }

    public int refundPercent() {
        return refundPercent;
    }

    public double fullPriceRadius() {
        return fullPriceRadius;
    }

    public double cheapestAt() {
        return cheapestAt;
    }

    public double minimumFactor() {
        return minimumFactor;
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public Material selectionTool() {
        return selectionTool;
    }

    private Material tool(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null || !material.isItem() ? Material.GOLDEN_SHOVEL : material;
    }
}
