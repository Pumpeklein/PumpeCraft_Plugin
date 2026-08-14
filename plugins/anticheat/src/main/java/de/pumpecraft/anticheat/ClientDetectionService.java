package de.pumpecraft.anticheat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

final class ClientDetectionService implements Listener, PluginMessageListener {
    private static final String BRAND_CHANNEL = "minecraft:brand";
    private static final int MAX_BRAND_LENGTH = 80;

    private final PumpeAntiCheatPlugin plugin;
    private final BedrockDetector bedrockDetector;
    private final PlayerPlatformRepository platformRepository;
    private final Map<UUID, ClientProfile> profiles = new HashMap<>();
    private BukkitTask scanTask;

    ClientDetectionService(
        PumpeAntiCheatPlugin plugin,
        BedrockDetector bedrockDetector,
        PlayerPlatformRepository platformRepository
    ) {
        this.plugin = plugin;
        this.bedrockDetector = bedrockDetector;
        this.platformRepository = platformRepository;
    }

    void start() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BRAND_CHANNEL, this);
        long interval = Math.max(
            40L,
            plugin.getConfig().getLong("client-detection.scan-interval-ticks", 100L)
        );
        scanTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::scanOnlinePlayers,
            interval,
            interval
        );
    }

    void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BRAND_CHANNEL, this);
        profiles.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshProfile(player, profile(player));
        long delay = Math.max(
            20L,
            plugin.getConfig().getLong("client-detection.join-message-delay-ticks", 60L)
        );
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> announceJoin(player), delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        ClientProfile profile = profile(event.getPlayer());
        profile.channels.add(event.getChannel());
        scanSignatures(event.getPlayer(), profile);
    }

    @EventHandler
    public void onUnregisterChannel(PlayerUnregisterChannelEvent event) {
        ClientProfile profile = profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.channels.remove(event.getChannel());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getConfig().getBoolean("client-detection.server-resource-pack-status", true)) {
            return;
        }
        notifyStaff(
            Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                .append(Component.text(event.getPlayer().getName(), NamedTextColor.YELLOW))
                .append(Component.text(
                    " Server-Resourcepack: " + event.getStatus().name(),
                    NamedTextColor.GRAY
                ))
        );
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BRAND_CHANNEL.equals(channel) || message.length == 0) {
            return;
        }
        ClientProfile profile = profile(player);
        profile.brand = decodeBrand(message);
        scanSignatures(player, profile);
    }

    private void announceJoin(Player player) {
        if (!player.isOnline()
            || !plugin.getConfig().getBoolean("client-detection.announce-joins", true)) {
            return;
        }

        ClientProfile profile = profile(player);
        refreshProfile(player, profile);
        scanSignatures(player, profile);
        profile.joinAnnounced = true;

        boolean bedrock = bedrockDetector.isBedrock(player.getUniqueId());
        platformRepository.record(player, bedrock);
        String client = bedrock
            ? "Bedrock"
            : "Java | Client: " + identifyJavaClient(profile);
        Component message = Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
            .append(Component.text(player.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" ist beigetreten: ", NamedTextColor.GRAY))
            .append(Component.text(client, bedrock ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        notifyStaff(message);
    }

    private void scanOnlinePlayers() {
        if (!plugin.getConfig().getBoolean("client-detection.enabled", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ClientProfile profile = profile(player);
            refreshProfile(player, profile);
            scanSignatures(player, profile);
        }
    }

    private void scanSignatures(Player player, ClientProfile profile) {
        if (!plugin.getConfig().getBoolean("client-detection.enabled", true)) {
            return;
        }

        String brand = profile.brand == null ? "" : profile.brand;
        String observable = (brand + " " + String.join(" ", profile.channels))
            .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : configuredSignatures().entrySet()) {
            boolean matched = entry.getValue().stream().anyMatch(observable::contains);
            if (!matched || !profile.detections.add(entry.getKey()) || !profile.joinAnnounced) {
                continue;
            }
            notifyStaff(
                Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" erkannt: ", NamedTextColor.GRAY))
                    .append(Component.text(entry.getKey(), NamedTextColor.RED))
            );
        }
    }

    private Map<String, List<String>> configuredSignatures() {
        Map<String, List<String>> signatures = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig()
            .getConfigurationSection("client-detection.known-signatures");
        if (section == null) {
            return signatures;
        }
        for (String label : section.getKeys(false)) {
            List<String> values = new ArrayList<>();
            for (String value : section.getStringList(label)) {
                if (!value.isBlank()) {
                    values.add(value.toLowerCase(Locale.ROOT));
                }
            }
            if (!values.isEmpty()) {
                signatures.put(label, values);
            }
        }
        return signatures;
    }

    private void notifyStaff(Component message) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (plugin.getCommand("anticheat").testPermissionSilent(staff)) {
                staff.sendMessage(message);
            }
        }
    }

    private ClientProfile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), ignored -> new ClientProfile());
    }

    private void refreshProfile(Player player, ClientProfile profile) {
        String paperBrand = sanitizeBrand(player.getClientBrandName());
        if (paperBrand != null) {
            profile.brand = paperBrand;
        }
        profile.channels.addAll(player.getListeningPluginChannels());
    }

    private String identifyJavaClient(ClientProfile profile) {
        if (!profile.detections.isEmpty()) {
            return String.join(", ", profile.detections);
        }
        if (profile.brand != null) {
            return friendlyBrand(profile.brand);
        }
        return "Standard";
    }

    private String friendlyBrand(String brand) {
        return switch (brand.toLowerCase(Locale.ROOT)) {
            case "vanilla" -> "Vanilla";
            case "fabric" -> "Fabric";
            case "forge" -> "Forge";
            case "neoforge" -> "NeoForge";
            case "quilt" -> "Quilt";
            default -> brand;
        };
    }

    private String decodeBrand(byte[] message) {
        int index = 0;
        int length = 0;
        int shift = 0;
        while (index < message.length && shift < 35) {
            int value = message[index++] & 0xFF;
            length |= (value & 0x7F) << shift;
            if ((value & 0x80) == 0) {
                break;
            }
            shift += 7;
        }

        String decoded;
        if (length >= 0 && length <= message.length - index) {
            decoded = new String(message, index, length, StandardCharsets.UTF_8);
        } else {
            decoded = new String(message, StandardCharsets.UTF_8);
        }
        return sanitizeBrand(decoded);
    }

    private String sanitizeBrand(String brand) {
        if (brand == null) {
            return null;
        }
        String sanitized = brand.replaceAll("[\\p{Cntrl}&&[^\\t]]", "").trim();
        return sanitized.isEmpty()
            ? null
            : sanitized.substring(0, Math.min(MAX_BRAND_LENGTH, sanitized.length()));
    }

    private static final class ClientProfile {
        private String brand;
        private final Set<String> channels = new LinkedHashSet<>();
        private final Set<String> detections = new LinkedHashSet<>();
        private boolean joinAnnounced;
    }
}
