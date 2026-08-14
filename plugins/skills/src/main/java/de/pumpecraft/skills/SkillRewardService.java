package de.pumpecraft.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class SkillRewardService {
    private final PumpeSkillsPlugin plugin;
    private final SkillRepository repository;
    private final Map<Integer, Reward> rewards;
    private final Set<RewardKey> deliveriesInProgress = ConcurrentHashMap.newKeySet();

    SkillRewardService(PumpeSkillsPlugin plugin, SkillRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewards = loadRewards();
        repository.syncRewardDefinitions(this.rewards.entrySet().stream().collect(
            java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().label())
        ));
    }

    void scoreChanged(UUID playerId, Skill skill, long previousScore, long currentScore) {
        if (currentScore <= previousScore || rewards.isEmpty()) {
            return;
        }
        int previousLevel = SkillLevel.levelOf(previousScore);
        int currentLevel = SkillLevel.levelOf(currentScore);
        rewards.keySet().stream()
            .filter(level -> level > previousLevel && level <= currentLevel)
            .sorted()
            .forEach(level -> reserve(playerId, skill, level, currentScore));
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
        if (rewards.isEmpty()) {
            return;
        }
        Map<Skill, Integer> levels = new HashMap<>();
        for (Skill skill : Skill.LEVELED) {
            long score = stats.getOrDefault(StatKey.score(skill), 0L);
            levels.put(skill, SkillLevel.levelOf(score));
        }
        List<Integer> milestones = rewards.keySet().stream().sorted().toList();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (SkillRepository.RewardClaim claim
                    : repository.reserveReachedRewards(playerId, levels, milestones)) {
                    scheduleDelivery(playerId, claim.skill(), claim.milestoneLevel());
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "Could not reconcile skill rewards for " + playerId + ".", exception);
            }
        });
    }

    int nextRewardLevel(int currentLevel) {
        return rewards.keySet().stream()
            .filter(level -> level > currentLevel)
            .min(Comparator.naturalOrder())
            .orElse(0);
    }

    String rewardLabel(int level) {
        Reward reward = rewards.get(level);
        return reward == null ? "" : reward.label();
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
        Reward reward = rewards.get(key.level());
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

    private Map<Integer, Reward> loadRewards() {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) {
            return Map.of();
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(
            "rewards.milestones");
        if (section == null) {
            plugin.getLogger().warning("No skill reward milestones are configured.");
            return Map.of();
        }

        Map<Integer, Reward> loaded = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                if (level < 10 || level > SkillLevel.MAX_LEVEL || level % 10 != 0) {
                    throw new IllegalArgumentException("level must be 10, 20, ..., 100");
                }
                List<ItemStack> items = parseItems(section.getStringList(key + ".items"));
                String label = section.getString(key + ".label", "Level-Reward").trim();
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("at least one valid item is required");
                }
                loaded.put(level, new Reward(label, List.copyOf(items)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid skill reward '" + key + "': "
                    + exception.getMessage());
            }
        }
        return Map.copyOf(loaded);
    }

    private List<ItemStack> parseItems(List<String> configuredItems) {
        List<ItemStack> items = new ArrayList<>();
        for (String configured : configuredItems) {
            String[] parts = configured.trim().split(":", 2);
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || material.isAir() || !material.isItem()) {
                throw new IllegalArgumentException("unknown item " + parts[0]);
            }
            int amount = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
            if (amount < 1 || amount > material.getMaxStackSize()) {
                throw new IllegalArgumentException("invalid amount for " + material.name());
            }
            items.add(new ItemStack(material, amount));
        }
        return items;
    }

    private record Reward(String label, List<ItemStack> items) {
    }

    private record RewardKey(UUID playerId, Skill skill, int level) {
    }
}
