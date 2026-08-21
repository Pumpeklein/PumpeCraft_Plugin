package de.pumpecraft.enchants.integration;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** A rare PumpePoints find while mining or fighting. */
public final class LuckyDrops {
    private final Plugin plugin;
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final DailyBudget budget = new DailyBudget();
    private PointsService points;

    public LuckyDrops(Plugin plugin, EnchantService enchants, EnchantSettings settings) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.settings = settings;
    }

    public void roll(Player player, ItemStack tool) {
        int level = enchants.activeLevel(tool, EnchantRegistry.LUCKY);
        if (level < 1) {
            return;
        }
        double chance = settings.perLevel(EnchantRegistry.LUCKY, "chance-percent", level, 0.5, 1.0);
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= chance) {
            return;
        }

        int minimum = settings.amount(EnchantRegistry.LUCKY, "points.minimum", 1);
        int maximum = Math.max(minimum, settings.amount(EnchantRegistry.LUCKY, "points.maximum", 5));
        int amount = ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
        int dailyLimit = settings.amount(EnchantRegistry.LUCKY, "daily-limit", 250);
        int granted = budget.take(player.getUniqueId(), amount, dailyLimit);
        if (granted <= 0) {
            return;
        }

        PointsService service = points();
        if (service == null) {
            return;
        }
        service.runAsync(() -> service.deposit(
            player.getUniqueId(),
            player.getName(),
            granted,
            TransactionType.ENCHANT_REWARD,
            "Glückspilz",
            "Fund beim Spielen"));
        player.sendActionBar(Component.text(
            "Glückspilz: +" + granted + " PP", NamedTextColor.GOLD));
    }

    private PointsService points() {
        if (points == null) {
            RegisteredServiceProvider<PointsService> registration = plugin.getServer()
                .getServicesManager().getRegistration(PointsService.class);
            points = registration == null ? null : registration.getProvider();
        }
        return points;
    }
}
