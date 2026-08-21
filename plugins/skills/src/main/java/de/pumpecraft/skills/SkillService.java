package de.pumpecraft.skills;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hält die Zähler der eingeloggten Spieler im Speicher und schreibt sie
 * regelmäßig asynchron weg. Events dürfen dadurch pro Aktion keine Datenbank
 * anfassen.
 */
final class SkillService {
    private static final long SAVE_INTERVAL_TICKS = 20L * 30L;

    private final PumpeSkillsPlugin plugin;
    private final SkillRepository repository;
    private final SkillRewardService rewards;
    private final ScholarBonus scholar;
    private final Map<UUID, PlayerSkillData> cache = new ConcurrentHashMap<>();
    private BukkitTask saveTask;

    SkillService(
        PumpeSkillsPlugin plugin,
        SkillRepository repository,
        SkillRewardService rewards
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewards = rewards;
        this.scholar = new ScholarBonus(plugin);
    }

    void start() {
        saveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::flushAll,
            SAVE_INTERVAL_TICKS,
            SAVE_INTERVAL_TICKS
        );
    }

    void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        flushAll();
        cache.clear();
    }

    SkillRepository repository() {
        return repository;
    }

    /** Wird im AsyncPlayerPreLogin aufgerufen, damit die Daten beim Join bereitstehen. */
    void load(UUID playerId) {
        PlayerSkillData data = new PlayerSkillData(repository.loadPlayer(playerId));
        cache.put(playerId, data);
        rewards.reconcileReachedMilestones(playerId, data.allValues());
    }

    void unload(UUID playerId) {
        PlayerSkillData data = cache.remove(playerId);
        if (data == null) {
            return;
        }
        runAsync(() -> persist(playerId, data));
    }

    PlayerSkillData data(UUID playerId) {
        return cache.get(playerId);
    }

    /**
     * Zählt nur im Überlebensmodus. Creative- oder Spectator-Aktionen sollen
     * keine Bestenlisten verzerren.
     */
    boolean tracks(Player player) {
        return player != null
            && player.getGameMode() == GameMode.SURVIVAL
            && cache.containsKey(player.getUniqueId());
    }

    void add(Player player, Skill skill, String statKey, long delta) {
        PlayerSkillData data = cache.get(player.getUniqueId());
        if (data != null) {
            data.add(new StatKey(skill, statKey), delta);
        }
    }

    /** Detailzähler plus Punkte in einem Aufruf. */
    void record(Player player, Skill skill, String statKey, long delta, long points) {
        PlayerSkillData data = cache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.add(new StatKey(skill, statKey), delta);
        long awarded = scholar.apply(player, points);
        StatKey scoreKey = StatKey.score(skill);
        long updatedScore = data.addAndGet(scoreKey, awarded);
        rewards.scoreChanged(player.getUniqueId(), skill, updatedScore - awarded, updatedScore);
    }

    /**
     * Wie {@link #record(Player, Skill, String, long, long)}, aber nur über die UUID -
     * nutzbar aus asynchronen Aufgaben und verzögerten Prüfungen.
     */
    void recordById(UUID playerId, Skill skill, String statKey, long delta, long points) {
        PlayerSkillData data = cache.get(playerId);
        if (data == null) {
            return;
        }
        data.add(new StatKey(skill, statKey), delta);
        long awarded = scholar.apply(plugin.getServer().getPlayer(playerId), points);
        StatKey scoreKey = StatKey.score(skill);
        long updatedScore = data.addAndGet(scoreKey, awarded);
        rewards.scoreChanged(playerId, skill, updatedScore - awarded, updatedScore);
    }

    void keepMinimum(Player player, Skill skill, String statKey, long value) {
        PlayerSkillData data = cache.get(player.getUniqueId());
        if (data != null) {
            data.keepMinimum(new StatKey(skill, statKey), value);
        }
    }

    long get(UUID playerId, Skill skill, String statKey) {
        PlayerSkillData data = cache.get(playerId);
        return data == null ? 0L : data.get(new StatKey(skill, statKey));
    }

    /** Schreibt die Änderungen eines Spielers sofort weg, statt auf den Speicherlauf zu warten. */
    void persistNow(UUID playerId) {
        PlayerSkillData data = cache.get(playerId);
        if (data != null) {
            runAsync(() -> persist(playerId, data));
        }
    }

    void runAsync(Runnable action) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, action);
    }

    void runSync(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private void flushAll() {
        for (Map.Entry<UUID, PlayerSkillData> entry : cache.entrySet()) {
            persist(entry.getKey(), entry.getValue());
        }
    }

    private void persist(UUID playerId, PlayerSkillData data) {
        Map<StatKey, Long> pending = data.takeDirty();
        if (pending.isEmpty()) {
            return;
        }
        try {
            repository.save(playerId, pending);
        } catch (RuntimeException exception) {
            data.markDirtyAgain(pending);
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not save skill stats for " + playerId + "; retrying on the next save.",
                exception
            );
        }
    }
}
