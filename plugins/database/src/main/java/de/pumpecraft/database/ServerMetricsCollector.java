package de.pumpecraft.database;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

final class ServerMetricsCollector {
    private static final long CAPTURE_INTERVAL_TICKS = 20L * 60L;
    private static final long INITIAL_DELAY_TICKS = 20L * 20L;
    private static final long RETENTION_MILLIS = Duration.ofDays(30).toMillis();

    private final PumpeDatabasePlugin plugin;
    private final DatabaseService database;
    private final AtomicBoolean writeInProgress = new AtomicBoolean();
    private BukkitTask captureTask;

    ServerMetricsCollector(PumpeDatabasePlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    void start() {
        captureTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::capture,
            INITIAL_DELAY_TICKS,
            CAPTURE_INTERVAL_TICKS
        );
    }

    void shutdown() {
        if (captureTask != null) {
            captureTask.cancel();
            captureTask = null;
        }
    }

    private void capture() {
        if (!writeInProgress.compareAndSet(false, true)) {
            return;
        }

        Snapshot snapshot;
        try {
            snapshot = snapshot();
        } catch (RuntimeException exception) {
            writeInProgress.set(false);
            plugin.getLogger().log(Level.WARNING, "Could not capture server metrics.", exception);
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                persist(snapshot);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not persist server metrics.", exception);
            } finally {
                writeInProgress.set(false);
            }
        });
    }

    private Snapshot snapshot() {
        double[] tps = Bukkit.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long memoryMax = Math.max(0L, runtime.maxMemory());
        int loadedChunks = 0;
        int entityCount = 0;
        Map<String, int[]> dimensionCounts = new LinkedHashMap<>();
        dimensionCounts.put("OVERWORLD", new int[3]);
        dimensionCounts.put("NETHER", new int[3]);
        dimensionCounts.put("END", new int[3]);
        for (World world : Bukkit.getWorlds()) {
            int worldChunks = world.getLoadedChunks().length;
            int worldEntities = world.getEntityCount();
            loadedChunks += worldChunks;
            entityCount += worldEntities;
            int[] counts = dimensionCounts.get(dimensionKey(world));
            if (counts != null) {
                counts[1] += worldChunks;
                counts[2] += worldEntities;
            }
        }
        Bukkit.getOnlinePlayers().forEach(player -> {
            int[] counts = dimensionCounts.get(dimensionKey(player.getWorld()));
            if (counts != null) {
                counts[0]++;
            }
        });
        List<DimensionSnapshot> dimensions = new ArrayList<>(dimensionCounts.size());
        for (Map.Entry<String, int[]> entry : dimensionCounts.entrySet()) {
            int[] counts = entry.getValue();
            dimensions.add(new DimensionSnapshot(
                entry.getKey(), counts[0], counts[1], counts[2]
            ));
        }

        CpuLoad cpuLoad = cpuLoad();
        DiskSpace diskSpace = diskSpace();
        long capturedAt = System.currentTimeMillis();
        long uptimeSeconds = Math.max(
            0L,
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000L
        );
        return new Snapshot(
            capturedAt,
            tpsValue(tps, 0),
            tpsValue(tps, 1),
            tpsValue(tps, 2),
            Math.max(0.0D, Bukkit.getAverageTickTime()),
            Bukkit.getOnlinePlayers().size(),
            Bukkit.getMaxPlayers(),
            memoryUsed,
            memoryMax,
            cpuLoad.processPercent(),
            cpuLoad.systemPercent(),
            loadedChunks,
            entityCount,
            Bukkit.getWorlds().size(),
            diskSpace.usedBytes(),
            diskSpace.totalBytes(),
            uptimeSeconds,
            Bukkit.getServer().getName(),
            Bukkit.getServer().getVersion(),
            Bukkit.getServer().getBukkitVersion(),
            System.getProperty("java.version", "Unknown"),
            System.getProperty("os.name", "Unknown"),
            System.getProperty("os.version", "Unknown"),
            System.getProperty("os.arch", "Unknown"),
            runtime.availableProcessors(),
            dimensions
        );
    }

    private void persist(Snapshot snapshot) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pc_server_specs
                    (server_id, server_name, server_version, bukkit_version,
                     java_version, os_name, os_version, os_arch, processors,
                     max_memory_bytes, updated_at)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    server_name = VALUES(server_name),
                    server_version = VALUES(server_version),
                    bukkit_version = VALUES(bukkit_version),
                    java_version = VALUES(java_version),
                    os_name = VALUES(os_name),
                    os_version = VALUES(os_version),
                    os_arch = VALUES(os_arch),
                    processors = VALUES(processors),
                    max_memory_bytes = VALUES(max_memory_bytes),
                    updated_at = VALUES(updated_at)
                """)) {
                statement.setString(1, snapshot.serverName());
                statement.setString(2, snapshot.serverVersion());
                statement.setString(3, snapshot.bukkitVersion());
                statement.setString(4, snapshot.javaVersion());
                statement.setString(5, snapshot.osName());
                statement.setString(6, snapshot.osVersion());
                statement.setString(7, snapshot.osArch());
                statement.setInt(8, snapshot.processors());
                statement.setLong(9, snapshot.memoryMaxBytes());
                statement.setLong(10, snapshot.capturedAt());
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pc_server_metrics
                    (captured_at, tps_1m, tps_5m, tps_15m, mspt,
                     online_players, max_players, memory_used_bytes,
                     memory_max_bytes, process_cpu_percent, system_cpu_percent,
                     loaded_chunks, entity_count, world_count, disk_used_bytes,
                     disk_total_bytes, uptime_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                statement.setLong(1, snapshot.capturedAt());
                statement.setDouble(2, snapshot.tps1m());
                statement.setDouble(3, snapshot.tps5m());
                statement.setDouble(4, snapshot.tps15m());
                statement.setDouble(5, snapshot.mspt());
                statement.setInt(6, snapshot.onlinePlayers());
                statement.setInt(7, snapshot.maxPlayers());
                statement.setLong(8, snapshot.memoryUsedBytes());
                statement.setLong(9, snapshot.memoryMaxBytes());
                setNullableDouble(statement, 10, snapshot.processCpuPercent());
                setNullableDouble(statement, 11, snapshot.systemCpuPercent());
                statement.setInt(12, snapshot.loadedChunks());
                statement.setInt(13, snapshot.entityCount());
                statement.setInt(14, snapshot.worldCount());
                statement.setLong(15, snapshot.diskUsedBytes());
                statement.setLong(16, snapshot.diskTotalBytes());
                statement.setLong(17, snapshot.uptimeSeconds());
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pc_dimension_server_metrics
                    (captured_at, dimension, online_players, loaded_chunks, entity_count)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                for (DimensionSnapshot dimension : snapshot.dimensions()) {
                    statement.setLong(1, snapshot.capturedAt());
                    statement.setString(2, dimension.dimension());
                    statement.setInt(3, dimension.onlinePlayers());
                    statement.setInt(4, dimension.loadedChunks());
                    statement.setInt(5, dimension.entityCount());
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_server_metrics WHERE captured_at < ?"
            )) {
                statement.setLong(1, snapshot.capturedAt() - RETENTION_MILLIS);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_dimension_server_metrics WHERE captured_at < ?"
            )) {
                statement.setLong(1, snapshot.capturedAt() - RETENTION_MILLIS);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static String dimensionKey(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> null;
        };
    }

    private CpuLoad cpuLoad() {
        if (!(ManagementFactory.getOperatingSystemMXBean()
            instanceof com.sun.management.OperatingSystemMXBean bean)) {
            return new CpuLoad(null, null);
        }
        return new CpuLoad(asPercent(bean.getProcessCpuLoad()), asPercent(bean.getCpuLoad()));
    }

    private DiskSpace diskSpace() {
        try {
            FileStore store = Files.getFileStore(Bukkit.getWorldContainer().toPath());
            long total = Math.max(0L, store.getTotalSpace());
            long available = Math.max(0L, store.getUsableSpace());
            return new DiskSpace(Math.max(0L, total - available), total);
        } catch (IOException exception) {
            return new DiskSpace(0L, 0L);
        }
    }

    private static double tpsValue(double[] values, int index) {
        if (index >= values.length || !Double.isFinite(values[index])) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(20.0D, values[index]));
    }

    private static Double asPercent(double load) {
        if (!Double.isFinite(load) || load < 0.0D) {
            return null;
        }
        return Math.min(100.0D, load * 100.0D);
    }

    private static void setNullableDouble(
        PreparedStatement statement,
        int index,
        Double value
    ) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private record CpuLoad(Double processPercent, Double systemPercent) {
    }

    private record DiskSpace(long usedBytes, long totalBytes) {
    }

    private record DimensionSnapshot(
        String dimension,
        int onlinePlayers,
        int loadedChunks,
        int entityCount
    ) {
    }

    private record Snapshot(
        long capturedAt,
        double tps1m,
        double tps5m,
        double tps15m,
        double mspt,
        int onlinePlayers,
        int maxPlayers,
        long memoryUsedBytes,
        long memoryMaxBytes,
        Double processCpuPercent,
        Double systemCpuPercent,
        int loadedChunks,
        int entityCount,
        int worldCount,
        long diskUsedBytes,
        long diskTotalBytes,
        long uptimeSeconds,
        String serverName,
        String serverVersion,
        String bukkitVersion,
        String javaVersion,
        String osName,
        String osVersion,
        String osArch,
        int processors,
        List<DimensionSnapshot> dimensions
    ) {
    }
}
