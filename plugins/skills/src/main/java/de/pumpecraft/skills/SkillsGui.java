package de.pumpecraft.skills;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SkillsGui implements Listener {
    private static final int[] OVERVIEW_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] PROGRESS_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final Map<Skill, Material> ICONS = new EnumMap<>(Skill.class);
    private static final Map<Skill, Material> PROGRESS_MATERIALS = new EnumMap<>(Skill.class);
    private static final Map<Skill, List<GuiStat>> STATS = new EnumMap<>(Skill.class);

    static {
        ICONS.put(Skill.FISCHER, Material.FISHING_ROD);
        ICONS.put(Skill.MINER, Material.DIAMOND_PICKAXE);
        ICONS.put(Skill.MOBS, Material.IRON_SWORD);
        ICONS.put(Skill.DORF, Material.EMERALD);
        ICONS.put(Skill.FARMER, Material.WHEAT);
        ICONS.put(Skill.BUILDER, Material.BRICKS);
        ICONS.put(Skill.TIERFREUND, Material.BONE);

        PROGRESS_MATERIALS.put(Skill.FISCHER, Material.CYAN_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.MINER, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.MOBS, Material.RED_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.DORF, Material.LIME_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.FARMER, Material.ORANGE_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.BUILDER, Material.PURPLE_STAINED_GLASS_PANE);
        PROGRESS_MATERIALS.put(Skill.TIERFREUND, Material.YELLOW_STAINED_GLASS_PANE);

        STATS.put(Skill.FISCHER, List.of(
            new GuiStat("Fänge", "caught"), new GuiStat("Fische", "fish"),
            new GuiStat("Schätze", "treasure")));
        STATS.put(Skill.MINER, List.of(
            new GuiStat("Blöcke", "blocks"), new GuiStat("Stein", "stone"),
            new GuiStat("Erze", "ore")));
        STATS.put(Skill.MOBS, List.of(
            new GuiStat("Kills", "kills"), new GuiStat("Monster", "monster"),
            new GuiStat("Bosse", "boss")));
        STATS.put(Skill.DORF, List.of(
            new GuiStat("Handel", "trades"), new GuiStat("Villager", "villagers"),
            new GuiStat("Smaragde", "emeralds")));
        STATS.put(Skill.FARMER, List.of(
            new GuiStat("Ernten", "crops"), new GuiStat("Holz", "logs"),
            new GuiStat("Ackerland", "farmland")));
        STATS.put(Skill.BUILDER, List.of(new GuiStat("Platziert", "placed")));
        STATS.put(Skill.TIERFREUND, List.of(new GuiStat("Gezähmt", "tamed")));
    }

    private final SkillService service;
    private final SkillRepository repository;
    private final SkillRewardService rewards;

    SkillsGui(
        SkillService service,
        SkillRepository repository,
        SkillRewardService rewards
    ) {
        this.service = service;
        this.repository = repository;
        this.rewards = rewards;
    }

    void openOverview(Player viewer, UUID targetId, String targetName) {
        service.runAsync(() -> {
            Map<StatKey, Long> stats = statsOf(targetId);
            service.runSync(() -> {
                if (viewer.isOnline()) {
                    viewer.openInventory(createOverview(targetId, targetName, stats));
                }
            });
        });
    }

    void openDetail(Player viewer, UUID targetId, String targetName, Skill skill) {
        if (!skill.leveled()) {
            service.runSync(() -> viewer.sendMessage(Component.text(
                "Für diesen Bereich gibt es kein Level-GUI.", NamedTextColor.RED)));
            return;
        }
        service.runAsync(() -> {
            Map<StatKey, Long> stats = statsOf(targetId);
            long score = stats.getOrDefault(StatKey.score(skill), 0L);
            int rank = score > 0L ? repository.rankOf(targetId, skill, Skill.SCORE) : 0;
            service.runSync(() -> {
                if (viewer.isOnline()) {
                    viewer.openInventory(createDetail(targetId, targetName, skill, stats, rank));
                }
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SkillsHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        if (holder.skill() == null) {
            int index = indexOf(OVERVIEW_SLOTS, slot);
            if (index >= 0) {
                openDetail(player, holder.targetId(), holder.targetName(), Skill.LEVELED.get(index));
            } else if (slot == 26) {
                player.closeInventory();
            }
            return;
        }

        if (slot == 29) {
            player.closeInventory();
            player.performCommand("skills top " + holder.skill().id());
        } else if (slot == 36) {
            openOverview(player, holder.targetId(), holder.targetName());
        } else if (slot == 44) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SkillsHolder)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private Inventory createOverview(UUID targetId, String targetName, Map<StatKey, Long> stats) {
        SkillsHolder holder = new SkillsHolder(
            targetId, targetName, null, 27, title("Skills · " + targetName));
        Inventory inventory = holder.getInventory();

        long total = Skill.LEVELED.stream()
            .mapToLong(skill -> stats.getOrDefault(StatKey.score(skill), 0L))
            .sum();
        inventory.setItem(4, item(Material.NETHER_STAR,
            text(targetName, NamedTextColor.GOLD),
            List.of(
                lore("Gesamtpunkte: ", number(total), NamedTextColor.WHITE),
                text("Klicke einen Skill für Details.", NamedTextColor.DARK_GRAY))));

        for (int index = 0; index < Skill.LEVELED.size(); index++) {
            Skill skill = Skill.LEVELED.get(index);
            inventory.setItem(OVERVIEW_SLOTS[index], overviewItem(skill,
                stats.getOrDefault(StatKey.score(skill), 0L)));
        }
        inventory.setItem(22, item(Material.CHEST,
            text("Level-Rewards", NamedTextColor.GOLD), rewardOverviewLore(stats)));
        inventory.setItem(26, item(Material.BARRIER,
            text("Schließen", NamedTextColor.RED), List.of()));
        return inventory;
    }

    private Inventory createDetail(
        UUID targetId,
        String targetName,
        Skill skill,
        Map<StatKey, Long> stats,
        int rank
    ) {
        SkillsHolder holder = new SkillsHolder(
            targetId,
            targetName,
            skill,
            45,
            title(skill.displayName() + " · " + targetName)
        );
        Inventory inventory = holder.getInventory();
        long score = stats.getOrDefault(StatKey.score(skill), 0L);
        int level = SkillLevel.levelOf(score);
        long levelScore = SkillLevel.scoreIntoLevel(score);
        long levelTarget = SkillLevel.scoreNeededInLevel(score);
        long toNext = SkillLevel.scoreToNextLevel(score);

        inventory.setItem(11, overviewItem(skill, score));
        inventory.setItem(13, item(Material.CLOCK,
            text("Level " + level, skill.color()),
            List.of(
                level < SkillLevel.MAX_LEVEL
                    ? lore("Nächstes Level: ", String.valueOf(level + 1), NamedTextColor.WHITE)
                    : text("Maximales Level erreicht", NamedTextColor.GOLD),
                lore("Gesamtpunkte: ", number(score), NamedTextColor.WHITE))));
        inventory.setItem(15, item(Material.EXPERIENCE_BOTTLE,
            text(level < SkillLevel.MAX_LEVEL ? "Noch " + number(toNext) + " Punkte" : "Vollständig",
                NamedTextColor.GREEN),
            List.of(level < SkillLevel.MAX_LEVEL
                ? lore("Im Level: ", number(levelScore) + " / " + number(levelTarget),
                    NamedTextColor.WHITE)
                : text("Level 100", NamedTextColor.WHITE))));

        int filled = (int) Math.round(SkillLevel.progress(score) * PROGRESS_SLOTS.length);
        for (int index = 0; index < PROGRESS_SLOTS.length; index++) {
            boolean complete = index < filled;
            inventory.setItem(PROGRESS_SLOTS[index], item(
                complete ? PROGRESS_MATERIALS.get(skill) : Material.GRAY_STAINED_GLASS_PANE,
                text(complete ? "Fortschritt" : "Noch offen",
                    complete ? skill.color() : NamedTextColor.DARK_GRAY),
                List.of(lore("Fortschritt: ", percent(SkillLevel.progress(score)),
                    NamedTextColor.WHITE))));
        }

        inventory.setItem(29, item(Material.GOLD_INGOT,
            text(rank > 0 ? "Platzierung · #" + rank : "Platzierung", NamedTextColor.GOLD),
            List.of(
                text(rank > 0 ? "Aktuelle Position im Skill" : "Noch nicht platziert",
                    rank > 0 ? NamedTextColor.WHITE : NamedTextColor.GRAY),
                text("Klicken: Top 10 anzeigen", NamedTextColor.GREEN))));
        inventory.setItem(31, rewardItem(skill, level));
        inventory.setItem(33, statsItem(skill, stats));
        inventory.setItem(36, item(Material.ARROW,
            text("Zurück", NamedTextColor.YELLOW), List.of(text("Zur Skill-Übersicht", NamedTextColor.GRAY))));
        inventory.setItem(44, item(Material.BARRIER,
            text("Schließen", NamedTextColor.RED), List.of()));
        return inventory;
    }

    private ItemStack overviewItem(Skill skill, long score) {
        int level = SkillLevel.levelOf(score);
        long levelScore = SkillLevel.scoreIntoLevel(score);
        long levelTarget = SkillLevel.scoreNeededInLevel(score);
        long toNext = SkillLevel.scoreToNextLevel(score);
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(text(skill.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(lore("Level: ", String.valueOf(level), NamedTextColor.WHITE));
        lore.add(level < SkillLevel.MAX_LEVEL
            ? lore("Fortschritt: ", number(levelScore) + " / " + number(levelTarget),
                NamedTextColor.WHITE)
            : text("Maximales Level erreicht", NamedTextColor.GOLD));
        lore.add(progressBar(SkillLevel.progress(score), skill.color()));
        if (level < SkillLevel.MAX_LEVEL) {
            lore.add(lore("Noch benötigt: ", number(toNext), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(text("Klicken für Details", NamedTextColor.GREEN));
        return item(ICONS.get(skill), text(skill.displayName() + " · Lv " + level, skill.color()), lore);
    }

    private ItemStack rewardItem(Skill skill, int currentLevel) {
        int rewardLevel = rewards.nextRewardLevel(skill, currentLevel);
        if (rewardLevel == 0) {
            return item(Material.CHEST,
                text("Alle Rewards erreicht", NamedTextColor.GOLD),
                List.of(text("Für diesen Skill gibt es nichts mehr", NamedTextColor.WHITE)));
        }

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(text(rewards.rewardLabel(skill, rewardLevel), NamedTextColor.YELLOW));
        lore.add(text("Wird beim Erreichen automatisch vergeben.", NamedTextColor.GRAY));
        List<Integer> upcoming = rewards.milestonesOf(skill).tailSet(rewardLevel, false)
            .stream().limit(3).toList();
        if (!upcoming.isEmpty()) {
            lore.add(Component.empty());
            lore.add(text("Danach", NamedTextColor.GRAY));
            upcoming.forEach(level -> lore.add(lore(
                "Lv " + level + " · ", rewards.rewardLabel(skill, level), NamedTextColor.DARK_GRAY)));
        }
        return item(Material.CHEST,
            text("Nächster Reward · Level " + rewardLevel, NamedTextColor.GOLD), lore);
    }

    /**
     * Die Stufen sind für alle Skills gleich, solange niemand einen eigenen Level ergänzt -
     * deshalb eine gemeinsame Liste statt einer Zeile je Skill. Persönlich wird es über den
     * Skill, der seiner nächsten Belohnung am nächsten ist.
     */
    private List<Component> rewardOverviewLore(Map<StatKey, Long> stats) {
        List<Component> lore = new java.util.ArrayList<>();
        java.util.TreeSet<Integer> levels = new java.util.TreeSet<>();
        Skill nearestSkill = null;
        int nearestLevel = 0;
        long nearestMissing = Long.MAX_VALUE;

        for (Skill skill : Skill.LEVELED) {
            levels.addAll(rewards.milestonesOf(skill));
            long score = stats.getOrDefault(StatKey.score(skill), 0L);
            int next = rewards.nextRewardLevel(skill, SkillLevel.levelOf(score));
            if (next == 0) {
                continue;
            }
            long missing = SkillLevel.scoreForLevel(next) - score;
            if (missing < nearestMissing) {
                nearestMissing = missing;
                nearestSkill = skill;
                nearestLevel = next;
            }
        }

        if (levels.isEmpty()) {
            lore.add(text("Aktuell sind keine Rewards eingerichtet.", NamedTextColor.GRAY));
            return lore;
        }

        lore.add(text("Belohnungen auf Level", NamedTextColor.GRAY));
        for (String row : levelRows(levels)) {
            lore.add(text(row, NamedTextColor.WHITE));
        }

        if (nearestSkill != null) {
            lore.add(Component.empty());
            lore.add(text("Als Nächstes", NamedTextColor.GRAY));
            lore.add(text(nearestSkill.displayName() + " · Level " + nearestLevel, nearestSkill.color()));
            lore.add(text(rewards.rewardLabel(nearestSkill, nearestLevel), NamedTextColor.YELLOW));
            lore.add(lore("Noch benötigt: ", number(nearestMissing), NamedTextColor.WHITE));
        }

        lore.add(Component.empty());
        lore.add(text("Die Belohnung hängt vom Skill ab.", NamedTextColor.DARK_GRAY));
        return lore;
    }

    private List<String> levelRows(java.util.Collection<Integer> levels) {
        List<String> rows = new java.util.ArrayList<>();
        StringBuilder row = new StringBuilder();
        int count = 0;
        for (int level : levels) {
            if (count > 0 && count % 8 == 0) {
                rows.add(row.toString());
                row.setLength(0);
            }
            row.append(row.isEmpty() ? "" : ", ").append(level);
            count++;
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }

    private ItemStack statsItem(Skill skill, Map<StatKey, Long> stats) {
        List<Component> lore = new java.util.ArrayList<>();
        for (GuiStat stat : STATS.getOrDefault(skill, List.of())) {
            lore.add(lore(stat.label() + ": ",
                number(stats.getOrDefault(new StatKey(skill, stat.key()), 0L)), NamedTextColor.WHITE));
        }
        return item(Material.WRITABLE_BOOK, text("Skill-Zähler", skill.color()), lore);
    }

    private Map<StatKey, Long> statsOf(UUID playerId) {
        PlayerSkillData data = service.data(playerId);
        return data != null ? data.allValues() : repository.loadPlayer(playerId);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(line -> line.decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Component title(String value) {
        return text(value, NamedTextColor.GOLD);
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component lore(String label, String value, NamedTextColor valueColor) {
        return text(label, NamedTextColor.GRAY).append(text(value, valueColor));
    }

    private Component progressBar(double progress, NamedTextColor color) {
        int length = 18;
        int filled = Math.max(0, Math.min(length, (int) Math.round(progress * length)));
        return text("■".repeat(filled), color)
            .append(text("■".repeat(length - filled), NamedTextColor.DARK_GRAY));
    }

    private String percent(double progress) {
        return String.format(java.util.Locale.GERMANY, "%.1f %%", progress * 100.0d);
    }

    private String number(long value) {
        return String.format(java.util.Locale.GERMANY, "%,d", value);
    }

    private int indexOf(int[] values, int needle) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == needle) {
                return index;
            }
        }
        return -1;
    }

    private static final class SkillsHolder implements InventoryHolder {
        private final UUID targetId;
        private final String targetName;
        private final Skill skill;
        private final Inventory inventory;

        private SkillsHolder(
            UUID targetId,
            String targetName,
            Skill skill,
            int size,
            Component title
        ) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.skill = skill;
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        UUID targetId() {
            return targetId;
        }

        String targetName() {
            return targetName;
        }

        Skill skill() {
            return skill;
        }
    }

    private record GuiStat(String label, String key) {
    }
}
