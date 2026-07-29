package de.pumpecraft.mod;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ModerationRepository {
    private static final String PUNISHMENT_ID_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PumpeModPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    ModerationRepository(PumpeModPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "moderation-data.yml");
    }

    void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        ensureSection("reports");
        ensureSection("warnings");
        ensureSection("mutes");
        ensureSection("punishments");
        ensureSection("staff-seen");
        save();
    }

    synchronized void save() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save moderation data.", exception);
        }
    }

    synchronized ReportRecord createReport(UUID reporterId, String reporterName, UUID targetId, String targetName, String reason) {
        int id = data.getInt("next-report-id", 1);
        long createdAt = Instant.now().toEpochMilli();
        data.set("next-report-id", id + 1);

        String path = "reports." + id;
        data.set(path + ".id", id);
        data.set(path + ".reporter-id", reporterId.toString());
        data.set(path + ".reporter-name", reporterName);
        data.set(path + ".target-id", targetId.toString());
        data.set(path + ".target-name", targetName);
        data.set(path + ".reason", reason);
        data.set(path + ".created-at", createdAt);
        data.set(path + ".open", true);
        save();

        return new ReportRecord(id, reporterName, targetName, reason, createdAt, true);
    }

    synchronized List<ReportRecord> getOpenReports() {
        ConfigurationSection section = data.getConfigurationSection("reports");
        if (section == null) {
            return List.of();
        }

        List<ReportRecord> reports = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String path = "reports." + key;
            if (!data.getBoolean(path + ".open", true)) {
                continue;
            }
            reports.add(new ReportRecord(
                data.getInt(path + ".id"),
                data.getString(path + ".reporter-name", "Unbekannt"),
                data.getString(path + ".target-name", "Unbekannt"),
                data.getString(path + ".reason", "Kein Grund angegeben"),
                data.getLong(path + ".created-at", 0L),
                true
            ));
        }

        reports.sort(Comparator.comparingInt(ReportRecord::id));
        return reports;
    }

    synchronized List<ReportRecord> getUnseenOpenReports(UUID staffId) {
        int lastSeen = data.getInt("staff-seen." + staffId, 0);
        return getOpenReports().stream()
            .filter(report -> report.id() > lastSeen)
            .toList();
    }

    synchronized int countUnseenOpenReports(UUID staffId) {
        return getUnseenOpenReports(staffId).size();
    }

    synchronized void markOpenReportsSeen(UUID staffId) {
        int maxReportId = getOpenReports().stream()
            .mapToInt(ReportRecord::id)
            .max()
            .orElse(data.getInt("staff-seen." + staffId, 0));
        data.set("staff-seen." + staffId, maxReportId);
        save();
    }

    synchronized int addWarning(UUID targetId, String targetName, String staffName, String reason) {
        String path = "warnings." + targetId;
        int count = data.getInt(path + ".count", 0) + 1;
        data.set(path + ".name", targetName);
        data.set(path + ".count", count);

        String entryPath = path + ".entries." + Instant.now().toEpochMilli();
        data.set(entryPath + ".staff", staffName);
        data.set(entryPath + ".reason", reason);
        save();
        return count;
    }

    synchronized void setMute(UUID targetId, String targetName, String staffName, int minutes, String reason) {
        String path = "mutes." + targetId;
        long expiresAt = Instant.now().plusSeconds(minutes * 60L).toEpochMilli();
        data.set(path + ".name", targetName);
        data.set(path + ".staff", staffName);
        data.set(path + ".reason", reason);
        data.set(path + ".muted-at", Instant.now().toEpochMilli());
        data.set(path + ".expires-at", expiresAt);
        save();
    }

    synchronized MuteRecord getActiveMute(UUID targetId) {
        String path = "mutes." + targetId;
        if (!data.isSet(path)) {
            return null;
        }

        long expiresAt = data.getLong(path + ".expires-at", 0L);
        if (expiresAt <= Instant.now().toEpochMilli()) {
            data.set(path, null);
            save();
            return null;
        }

        return new MuteRecord(
            data.getString(path + ".reason", "Kein Grund angegeben"),
            expiresAt
        );
    }

    synchronized String createBanPunishment(UUID targetId, String targetName, String staffName, String reason, Instant expiresAt) {
        String punishmentId = generatePunishmentId();
        String path = "punishments." + punishmentId;

        data.set(path + ".type", "BAN");
        data.set(path + ".target-id", targetId.toString());
        data.set(path + ".target-name", targetName);
        data.set(path + ".staff", staffName);
        data.set(path + ".reason", reason);
        data.set(path + ".created-at", Instant.now().toEpochMilli());
        data.set(path + ".expires-at", expiresAt == null ? null : expiresAt.toEpochMilli());
        save();
        return punishmentId;
    }

    private String generatePunishmentId() {
        String punishmentId;
        do {
            StringBuilder builder = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                builder.append(PUNISHMENT_ID_CHARACTERS.charAt(RANDOM.nextInt(PUNISHMENT_ID_CHARACTERS.length())));
            }
            punishmentId = builder.toString();
        } while (data.isSet("punishments." + punishmentId));

        return punishmentId;
    }

    private void ensureSection(String path) {
        if (data.getConfigurationSection(path) == null) {
            data.createSection(path);
        }
    }
}
