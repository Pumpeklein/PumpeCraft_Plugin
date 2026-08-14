package de.pumpecraft.anticheat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
    static final String ALERT_PERMISSION = "pumpecraft.anticheat.command";
    private static final String BRAND_CHANNEL = "minecraft:brand";
    private static final int MAX_BRAND_LENGTH = 80;
    private static final String UNKNOWN_LABEL = "unbekannt";

    private final PumpeAntiCheatPlugin plugin;
    private final BedrockDetector bedrockDetector;
    private final PlayerPlatformRepository platformRepository;
    private final Map<UUID, ClientProfile> profiles = new HashMap<>();
    private List<Signature> signatureCache;
    private List<Signature> loaderCache;
    private List<Signature> modHintCache;
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

    void reload() {
        signatureCache = null;
        loaderCache = null;
        modHintCache = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshProfile(player, profile(player));
        scheduleAnnounce(player, 0);
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

    /** Snapshot of everything the server knows about a player's client, for {@code /anticheat client}. */
    ClientReport report(Player player) {
        ClientProfile profile = profile(player);
        refreshProfile(player, profile);
        scanSignatures(player, profile);
        return new ClientReport(
            bedrockDetector.isBedrock(player.getUniqueId()),
            profile.brand,
            loaderName(profile),
            clientName(profile),
            modHints(profile),
            List.copyOf(profile.channels)
        );
    }

    /**
     * The brand only arrives once the client has finished logging in, so the join message waits for
     * it instead of firing after a fixed delay and reporting "unbekannt".
     */
    private void scheduleAnnounce(Player player, int attempt) {
        long step = Math.max(
            5L,
            plugin.getConfig().getLong("client-detection.announce-poll-ticks", 10L)
        );
        long maxWait = Math.max(
            step,
            plugin.getConfig().getLong("client-detection.brand-wait-ticks", 100L)
        );
        int maxAttempts = (int) Math.max(1L, maxWait / step);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ClientProfile profile = profile(player);
            refreshProfile(player, profile);
            if (profile.brand == null && attempt + 1 < maxAttempts) {
                scheduleAnnounce(player, attempt + 1);
                return;
            }
            announceJoin(player, profile);
        }, step);
    }

    private void announceJoin(Player player, ClientProfile profile) {
        if (profile.joinAnnounced
            || !plugin.getConfig().getBoolean("client-detection.announce-joins", true)) {
            return;
        }

        scanSignatures(player, profile);
        profile.joinAnnounced = true;

        boolean bedrock = bedrockDetector.isBedrock(player.getUniqueId());
        platformRepository.record(player, bedrock);

        Component message = Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
            .append(Component.text(player.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" ist beigetreten: ", NamedTextColor.GRAY))
            .append(Component.text(
                bedrock ? "Bedrock" : summarize(profile),
                bedrock ? NamedTextColor.GOLD : NamedTextColor.AQUA
            ));
        notifyStaff(message);
    }

    /** Builds e.g. {@code Java | Fabric | Lunar Client | Xaero's Minimap, JourneyMap}. */
    private String summarize(ClientProfile profile) {
        List<String> parts = new ArrayList<>();
        parts.add("Java");
        parts.add(loaderName(profile));
        String client = clientName(profile);
        if (client != null) {
            parts.add(client);
        }
        if (plugin.getConfig().getBoolean("client-detection.announce-mod-hints", true)) {
            List<String> mods = modHints(profile);
            int limit = Math.max(
                1,
                plugin.getConfig().getInt("client-detection.max-announced-mod-hints", 4)
            );
            if (mods.size() > limit) {
                parts.add(String.join(", ", mods.subList(0, limit))
                    + " (+" + (mods.size() - limit) + ")");
            } else if (!mods.isEmpty()) {
                parts.add(String.join(", ", mods));
            }
        }
        return String.join(" | ", parts);
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

        Set<String> brandTokens = brandTokens(profile);
        Set<String> channelTokens = channelTokens(profile);
        for (Signature signature : signatures()) {
            boolean matched = matches(signature.brands(), brandTokens)
                || matches(signature.channels(), channelTokens);
            if (!matched || !profile.detections.add(signature.label()) || !profile.joinAnnounced) {
                continue;
            }
            notifyStaff(
                Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" erkannt: ", NamedTextColor.GRAY))
                    .append(Component.text(signature.label(), NamedTextColor.RED))
            );
        }
    }

    /**
     * The mod loader is decided by the client brand alone. Plugin channels are unreliable here:
     * a Fabric client running a Forge compat layer registers {@code fml:*} channels and would
     * otherwise be reported as Forge as well.
     */
    private String loaderName(ClientProfile profile) {
        if (profile.brand == null) {
            return UNKNOWN_LABEL;
        }
        Set<String> brandTokens = brandTokens(profile);
        for (Signature loader : loaders()) {
            if (matches(loader.brands(), brandTokens)) {
                return loader.label();
            }
        }
        return profile.brand;
    }

    private String clientName(ClientProfile profile) {
        return profile.detections.isEmpty() ? null : String.join(", ", profile.detections);
    }

    /**
     * Mods the client gave away by registering their plugin channels. Informational only -
     * no staff alert is raised for these, unlike {@code known-signatures}.
     */
    private List<String> modHints(ClientProfile profile) {
        Set<String> brandTokens = brandTokens(profile);
        Set<String> channelTokens = channelTokens(profile);
        List<String> hints = new ArrayList<>();
        for (Signature hint : modHints()) {
            if (matches(hint.brands(), brandTokens) || matches(hint.channels(), channelTokens)) {
                hints.add(hint.label());
            }
        }
        return hints;
    }

    private Set<String> brandTokens(ClientProfile profile) {
        Set<String> tokens = new LinkedHashSet<>();
        if (profile.brand == null) {
            return tokens;
        }
        String brand = profile.brand.toLowerCase(Locale.ROOT);
        tokens.add(brand);
        for (String part : brand.split("[^a-z0-9_-]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private Set<String> channelTokens(ClientProfile profile) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String channel : profile.channels) {
            String lower = channel.toLowerCase(Locale.ROOT);
            tokens.add(lower);
            int colon = lower.indexOf(':');
            if (colon > 0) {
                tokens.add(lower.substring(0, colon));
            }
        }
        return tokens;
    }

    /** Exact token match, with an optional trailing {@code *} for prefixes such as {@code labymod*}. */
    private boolean matches(List<String> patterns, Set<String> tokens) {
        for (String pattern : patterns) {
            if (!pattern.endsWith("*")) {
                if (tokens.contains(pattern)) {
                    return true;
                }
                continue;
            }
            String prefix = pattern.substring(0, pattern.length() - 1);
            if (prefix.isEmpty()) {
                continue;
            }
            for (String token : tokens) {
                if (token.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Signature> signatures() {
        if (signatureCache == null) {
            signatureCache = readSignatures("client-detection.known-signatures");
        }
        return signatureCache;
    }

    private List<Signature> loaders() {
        if (loaderCache == null) {
            loaderCache = readSignatures("client-detection.loaders");
        }
        return loaderCache;
    }

    private List<Signature> modHints() {
        if (modHintCache == null) {
            modHintCache = readSignatures("client-detection.mod-hints");
        }
        return modHintCache;
    }

    private List<Signature> readSignatures(String path) {
        List<Signature> signatures = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return signatures;
        }
        for (String label : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(label);
            List<String> brands;
            List<String> channels;
            if (entry == null) {
                // Legacy flat form: one list that may hold brands as well as channel namespaces.
                List<String> legacy = normalize(section.getStringList(label));
                brands = legacy;
                channels = legacy;
            } else {
                brands = normalize(entry.getStringList("brands"));
                channels = normalize(entry.getStringList("channels"));
            }
            if (!brands.isEmpty() || !channels.isEmpty()) {
                signatures.add(new Signature(label, brands, channels));
            }
        }
        return signatures;
    }

    private List<String> normalize(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (!value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private void notifyStaff(Component message) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ALERT_PERMISSION)) {
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

    /**
     * Control characters become spaces rather than being dropped, so Forge's legacy
     * {@code forge\0FML\0} brand still splits into the tokens {@code forge} and {@code fml}.
     */
    private String sanitizeBrand(String brand) {
        if (brand == null) {
            return null;
        }
        String sanitized = brand.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return sanitized.isEmpty()
            ? null
            : sanitized.substring(0, Math.min(MAX_BRAND_LENGTH, sanitized.length()));
    }

    record ClientReport(
        boolean bedrock,
        String brand,
        String loader,
        String client,
        List<String> mods,
        List<String> channels
    ) {
    }

    private record Signature(String label, List<String> brands, List<String> channels) {
    }

    private static final class ClientProfile {
        private String brand;
        private final Set<String> channels = new LinkedHashSet<>();
        private final Set<String> detections = new LinkedHashSet<>();
        private boolean joinAnnounced;
    }
}
