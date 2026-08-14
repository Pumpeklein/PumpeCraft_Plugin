package de.pumpecraft.skills;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Meilensteine dürfen auf jedem Level liegen und je Skill abweichen. Ein Skill ohne eigene
 * Definition nutzt die Standardstufe, ein Skill mit eigener Definition ergänzt sie.
 */
final class SkillRewardService {
    private static final String DEFAULT_TRACK = "*";
    private static final int MAX_REWARD_AMOUNT = 576;

    private final PumpeSkillsPlugin plugin;
    private final SkillRepository repository;
    private final Map<Integer, Reward> defaults;
    private final Map<Skill, Map<Integer, Reward>> overrides;
    private final Map<Skill, NavigableSet<Integer>> milestones = new EnumMap<>(Skill.class);
    private final Set<RewardKey> deliveriesInProgress = ConcurrentHashMap.newKeySet();

    SkillRewardService(PumpeSkillsPlugin plugin, SkillRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        boolean enabled = plugin.getConfig().getBoolean("rewards.enabled", true);
        this.defaults = enabled
            ? loadTrack(plugin.getConfig().getConfigurationSection("rewards.milestones"), "milestones")
            : Map.of();
        this.overrides = enabled ? loadOverrides() : Map.of();
        for (Skill skill : Skill.LEVELED) {
            NavigableSet<Integer> levels = new TreeSet<>(defaults.keySet());
            levels.addAll(overrides.getOrDefault(skill, Map.of()).keySet());
            milestones.put(skill, levels);
        }
        if (enabled && defaults.isEmpty() && overrides.isEmpty()) {
            plugin.getLogger().warning("No skill reward milestones are configured.");
        }
        repository.syncRewardDefinitions(definitions());
    }

    void scoreChanged(UUID playerId, Skill skill, long previousScore, long currentScore) {
        if (currentScore <= previousScore) {
            return;
        }
        int previousLevel = SkillLevel.levelOf(previousScore);
        int currentLevel = SkillLevel.levelOf(currentScore);
        for (int level : milestonesOf(skill)) {
            if (level > previousLevel && level <= currentLevel) {
                reserve(playerId, skill, level, currentScore);
            }
        }
    }

    void deliverPending(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (SkillRepository.RewardClaim claim : repository.pendingRewards(playerId)) {
                    scheduleDelivery(playerId, claim.skill(), claim.milestoneLevel());
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "Could not load pending skill rewards for " + playerId + ".", exception);
            }
        });
    }

    void reconcileReachedMilestones(UUID playerId, Map<StatKey, Long> stats) {
        Map<Skill, List<Integer>> reached = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.LEVELED) {
            int level = SkillLevel.levelOf(stats.getOrDefault(StatKey.score(skill), 0L));
            List<Integer> levels = milestonesOf(skill).headSet(level, true).stream().toList();
            if (!levels.isEmpty()) {
                reached.put(skill, levels);
            }
        }
        if (reached.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (SkillRepository.RewardClaim claim
                    : repository.reserveReachedRewards(playerId, reached)) {
                    scheduleDelivery(playerId, claim.skill(), claim.milestoneLevel());
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "Could not reconcile skill rewards for " + playerId + ".", exception);
            }
        });
    }

    NavigableSet<Integer> milestonesOf(Skill skill) {
        return milestones.getOrDefault(skill, new TreeSet<>());
    }

    int nextRewardLevel(Skill skill, int currentLevel) {
        Integer next = milestonesOf(skill).higher(currentLevel);
        return next == null ? 0 : next;
    }

    String rewardLabel(Skill skill, int level) {
        Reward reward = reward(skill, level);
        return reward == null ? "" : reward.label();
    }

    private Reward reward(Skill skill, int level) {
        Reward override = overrides.getOrDefault(skill, Map.of()).get(level);
        return override != null ? override : defaults.get(level);
    }

    private void reserve(UUID playerId, Skill skill, int level, long score) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (repository.reserveReward(playerId, skill, level, score)) {
                    scheduleDelivery(playerId, skill, level);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "Could not reserve level " + level + " reward for " + playerId + ".", exception);
            }
        });
    }

    private void scheduleDelivery(UUID playerId, Skill skill, int level) {
        RewardKey key = new RewardKey(playerId, skill, level);
        if (!deliveriesInProgress.add(key)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> deliver(key));
    }

    private void deliver(RewardKey key) {
        Player player = Bukkit.getPlayer(key.playerId());
        Reward reward = reward(key.skill(), key.level());
        if (player == null || !player.isOnline() || reward == null) {
            deliveriesInProgress.remove(key);
            return;
        }

        for (ItemStack item : reward.items()) {
            player.getInventory().addItem(item.clone()).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.sendMessage(Component.text("Skill-Reward freigeschaltet!", NamedTextColor.GOLD,
                TextDecoration.BOLD)
            .append(Component.text(" " + key.skill().displayName() + " Level " + key.level()
                + ": " + reward.label(), NamedTextColor.YELLOW)));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.markRewardDelivered(key.playerId(), key.skill(), key.level());
                deliveriesInProgress.remove(key);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                    "Reward was delivered but could not be marked as delivered for "
                        + key.playerId() + ".",
                    exception);
            }
        });
    }

    private List<SkillRepository.RewardDefinition> definitions() {
        List<SkillRepository.RewardDefinition> rows = new ArrayList<>();
        defaults.forEach((level, reward) -> rows.add(new SkillRepository.RewardDefinition(
            DEFAULT_TRACK, level, reward.label(), reward.summary())));
        overrides.forEach((skill, track) -> track.forEach((level, reward) ->
            rows.add(new SkillRepository.RewardDefinition(
                skill.id(), level, reward.label(), reward.summary()))));
        return rows;
    }

    private Map<Skill, Map<Integer, Reward>> loadOverrides() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rewards.skills");
        if (section == null) {
            return Map.of();
        }
        Map<Skill, Map<Integer, Reward>> loaded = new EnumMap<>(Skill.class);
        for (String key : section.getKeys(false)) {
            Skill skill = Skill.byId(key);
            if (skill == null || !skill.leveled()) {
                plugin.getLogger().warning("Unknown skill in rewards.skills: " + key);
                continue;
            }
            Map<Integer, Reward> track =
                loadTrack(section.getConfigurationSection(key), "skills." + key);
            if (!track.isEmpty()) {
                loaded.put(skill, track);
            }
        }
        return loaded;
    }

    private Map<Integer, Reward> loadTrack(ConfigurationSection section, String path) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, Reward> loaded = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                if (level < 2 || level > SkillLevel.MAX_LEVEL) {
                    throw new IllegalArgumentException(
                        "level must be between 2 and " + SkillLevel.MAX_LEVEL);
                }
                List<ItemStack> items = parseItems(section.getList(key + ".items"));
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("at least one valid item is required");
                }
                loaded.put(level, new Reward(
                    section.getString(key + ".label", "Level-Reward").trim(),
                    List.copyOf(items)
                ));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid skill reward 'rewards." + path + "." + key
                    + "': " + exception.getMessage());
            }
        }
        return Map.copyOf(loaded);
    }

    /** Kurzform {@code MATERIAL:Anzahl} oder ein Block mit material/amount/name/enchantments. */
    private List<ItemStack> parseItems(List<?> configured) {
        List<ItemStack> items = new ArrayList<>();
        if (configured == null) {
            return items;
        }
        for (Object entry : configured) {
            if (entry instanceof String shorthand) {
                items.addAll(parseShorthand(shorthand));
            } else if (entry instanceof Map<?, ?> detailed) {
                items.addAll(parseDetailed(detailed));
            } else {
                throw new IllegalArgumentException("unsupported item entry " + entry);
            }
        }
        return items;
    }

    private List<ItemStack> parseShorthand(String configured) {
        String[] parts = configured.trim().split(":", 2);
        Material material = material(parts[0]);
        int amount = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : 1;
        return split(new ItemStack(material), checkedAmount(amount));
    }

    private List<ItemStack> parseDetailed(Map<?, ?> configured) {
        Material material = material(String.valueOf(configured.get("material")));
        int amount = configured.get("amount") instanceof Number number ? number.intValue() : 1;
        ItemStack prototype = new ItemStack(material);

        ItemMeta meta = prototype.getItemMeta();
        if (meta != null) {
            Object name = configured.get("name");
            if (name != null) {
                meta.displayName(Component.text(String.valueOf(name))
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (configured.get("enchantments") instanceof Map<?, ?> enchantments) {
                applyEnchantments(meta, enchantments);
            }
            prototype.setItemMeta(meta);
        }
        return split(prototype, checkedAmount(amount));
    }

    /**
     * Sattel, Shulkerkiste und Pferderüstung stapeln nicht. Eine Menge darüber wird auf
     * mehrere Stapel verteilt, statt die Definition abzulehnen - ein Stapel über der
     * Vanilla-Grenze würde die Item-Prüfung des AntiCheats auslösen.
     */
    private List<ItemStack> split(ItemStack prototype, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int maximum = prototype.getType().getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            stack.setAmount(Math.min(maximum, remaining));
            stacks.add(stack);
            remaining -= stack.getAmount();
        }
        return stacks;
    }

    /**
     * Ohne {@code ignoreLevelRestriction} - Rewards bleiben damit im Vanilla-Rahmen und
     * lösen die Item-Prüfung des AntiCheats nicht aus.
     */
    private void applyEnchantments(ItemMeta meta, Map<?, ?> configured) {
        for (Map.Entry<?, ?> entry : configured.entrySet()) {
            String id = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            Enchantment enchantment = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(id));
            if (enchantment == null) {
                throw new IllegalArgumentException("unknown enchantment " + id);
            }
            if (!(entry.getValue() instanceof Number level)) {
                throw new IllegalArgumentException("enchantment " + id + " needs a level");
            }
            boolean applied = meta instanceof EnchantmentStorageMeta storage
                ? storage.addStoredEnchant(enchantment, level.intValue(), false)
                : meta.addEnchant(enchantment, level.intValue(), false);
            if (!applied) {
                throw new IllegalArgumentException(
                    id + " level " + level.intValue() + " is not valid for this item");
            }
        }
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("unknown item " + name);
        }
        return material;
    }

    private int checkedAmount(int amount) {
        if (amount < 1 || amount > MAX_REWARD_AMOUNT) {
            throw new IllegalArgumentException(
                "amount must be between 1 and " + MAX_REWARD_AMOUNT + ", got " + amount);
        }
        return amount;
    }

    private record Reward(String label, List<ItemStack> items) {
        String summary() {
            List<String> parts = new ArrayList<>();
            for (ItemStack item : items) {
                parts.add(item.getAmount() + "x " + item.getType().name());
            }
            String joined = String.join(", ", parts);
            return joined.length() <= 255 ? joined : joined.substring(0, 255);
        }
    }

    private record RewardKey(UUID playerId, Skill skill, int level) {
    }
}
