package de.pumpecraft.anticheat.client;

import de.pumpecraft.anticheat.core.AlertDispatcher;
import de.pumpecraft.anticheat.platform.BedrockDetector;
import de.pumpecraft.anticheat.storage.PlayerPlatformRepository;
import de.pumpecraft.utils.Staff;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.Texts;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

public final class ClientDetectionService implements Listener, PluginMessageListener {
    private static final String BRAND_CHANNEL = "minecraft:brand";
    private static final int MAX_BRAND_LENGTH = 80;
    private static final String UNKNOWN_LABEL = "unbekannt";

    private final Plugin plugin;
    private final BedrockDetector bedrockDetector;
    private final PlayerPlatformRepository platformRepository;
    private final Map<UUID, ClientProfile> profiles = new HashMap<>();
    private List<ClientSignature> signatureCache;
    private List<ClientSignature> loaderCache;
    private List<ClientSignature> modHintCache;
    private BukkitTask scanTask;

    public ClientDetectionService(
        Plugin plugin,
        BedrockDetector bedrockDetector,
        PlayerPlatformRepository platformRepository
    ) {
        this.plugin = plugin;
        this.bedrockDetector = bedrockDetector;
        this.platformRepository = platformRepository;
    }

    public void start() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BRAND_CHANNEL, this);
        long interval = Math.max(
            40L,
            plugin.getConfig().getLong("client-detection.scan-interval-ticks", 100L)
        );
        scanTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::scanOnlinePlayers, interval, interval);
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BRAND_CHANNEL, this);
        profiles.clear();
    }

    public void reload() {
        signatureCache = null;
        loaderCache = null;
        modHintCache = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refresh(player, profile(player));
        scheduleAnnounce(player, 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        ClientProfile profile = profile(event.getPlayer());
        profile.channels().add(event.getChannel());
        scanSignatures(event.getPlayer(), profile);
    }

    @EventHandler
    public void onUnregisterChannel(PlayerUnregisterChannelEvent event) {
        ClientProfile profile = profiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.channels().remove(event.getChannel());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getConfig().getBoolean("client-detection.server-resource-pack-status", true)) {
            return;
        }
        Staff.broadcast(
            AlertDispatcher.PERMISSION,
            Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                .append(playerLink(event.getPlayer()))
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
        profile.brand(decodeBrand(message));
        scanSignatures(player, profile);
    }

    public ClientReport report(Player player) {
        ClientProfile profile = profile(player);
        refresh(player, profile);
        scanSignatures(player, profile);
        return new ClientReport(
            bedrockDetector.isBedrock(player.getUniqueId()),
            profile.brand(),
            loaderName(profile),
            profile.detections().isEmpty() ? null : String.join(", ", profile.detections()),
            modHints(profile),
            List.copyOf(profile.channels())
        );
    }

    /**
     * The brand arrives asynchronously after login, so the join message polls for it instead of
     * firing on a fixed delay and reporting "unbekannt" for slow connections.
     */
    private void scheduleAnnounce(Player player, int attempt) {
        long step = Math.max(5L, plugin.getConfig().getLong("client-detection.announce-poll-ticks", 10L));
        long maximumWait = Math.max(
            step,
            plugin.getConfig().getLong("client-detection.brand-wait-ticks", 100L)
        );
        int maximumAttempts = (int) Math.max(1L, maximumWait / step);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ClientProfile profile = profile(player);
            refresh(player, profile);
            if (profile.brand() == null && attempt + 1 < maximumAttempts) {
                scheduleAnnounce(player, attempt + 1);
                return;
            }
            announceJoin(player, profile);
        }, step);
    }

    private void announceJoin(Player player, ClientProfile profile) {
        if (profile.joinAnnounced()
            || !plugin.getConfig().getBoolean("client-detection.announce-joins", true)) {
            return;
        }

        scanSignatures(player, profile);
        profile.markAnnounced();

        boolean bedrock = bedrockDetector.isBedrock(player.getUniqueId());
        platformRepository.record(player, bedrock);

        Staff.broadcast(
            AlertDispatcher.PERMISSION,
            Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                .append(playerLink(player))
                .append(Component.text(" ist beigetreten: ", NamedTextColor.GRAY))
                .append(Component.text(
                    bedrock ? "Bedrock" : summarize(profile),
                    bedrock ? NamedTextColor.GOLD : NamedTextColor.AQUA
                ))
        );
    }

    private String summarize(ClientProfile profile) {
        List<String> parts = new ArrayList<>();
        parts.add("Java");
        parts.add(loaderName(profile));
        if (!profile.detections().isEmpty()) {
            parts.add(String.join(", ", profile.detections()));
        }
        if (plugin.getConfig().getBoolean("client-detection.announce-mod-hints", true)) {
            List<String> mods = modHints(profile);
            if (!mods.isEmpty()) {
                int limit = Math.max(
                    1,
                    plugin.getConfig().getInt("client-detection.max-announced-mod-hints", 4)
                );
                parts.add(Texts.joinLimited(mods, limit, "(+{count})"));
            }
        }
        return String.join(" | ", parts);
    }

    private void scanOnlinePlayers() {
        if (!plugin.getConfig().getBoolean("client-detection.enabled", true)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ClientProfile profile = profile(player);
            refresh(player, profile);
            scanSignatures(player, profile);
        }
    }

    private void scanSignatures(Player player, ClientProfile profile) {
        if (!plugin.getConfig().getBoolean("client-detection.enabled", true)) {
            return;
        }

        Set<String> brandTokens = profile.brandTokens();
        Set<String> channelTokens = profile.channelTokens();
        for (ClientSignature signature : signatures()) {
            if (!signature.matches(brandTokens, channelTokens)
                || !profile.detections().add(signature.label())
                || !profile.joinAnnounced()) {
                continue;
            }
            Staff.broadcast(
                AlertDispatcher.PERMISSION,
                Component.text("[ClientCheck] ", NamedTextColor.DARK_AQUA)
                    .append(playerLink(player))
                    .append(Component.text(" erkannt: ", NamedTextColor.GRAY))
                    .append(Component.text(signature.label(), NamedTextColor.RED))
                    .append(Component.space())
                    .append(locationLink(player))
            );
        }
    }

    /**
     * Decided by the client brand alone. Plugin channels lie here: a Fabric client running a
     * Forge compatibility layer registers fml:* channels and would report as Forge too.
     */
    private String loaderName(ClientProfile profile) {
        if (profile.brand() == null) {
            return UNKNOWN_LABEL;
        }
        Set<String> brandTokens = profile.brandTokens();
        for (ClientSignature loader : loaders()) {
            if (loader.matches(brandTokens, Set.of())) {
                return loader.label();
            }
        }
        return profile.brand();
    }

    private List<String> modHints(ClientProfile profile) {
        Set<String> brandTokens = profile.brandTokens();
        Set<String> channelTokens = profile.channelTokens();
        List<String> hints = new ArrayList<>();
        for (ClientSignature hint : modHints()) {
            if (hint.matches(brandTokens, channelTokens)) {
                hints.add(hint.label());
            }
        }
        return hints;
    }

    private List<ClientSignature> signatures() {
        if (signatureCache == null) {
            signatureCache = ClientSignature.read(
                plugin.getConfig().getConfigurationSection("client-detection.known-signatures")
            );
        }
        return signatureCache;
    }

    private List<ClientSignature> loaders() {
        if (loaderCache == null) {
            loaderCache = ClientSignature.read(
                plugin.getConfig().getConfigurationSection("client-detection.loaders")
            );
        }
        return loaderCache;
    }

    private List<ClientSignature> modHints() {
        if (modHintCache == null) {
            modHintCache = ClientSignature.read(
                plugin.getConfig().getConfigurationSection("client-detection.mod-hints")
            );
        }
        return modHintCache;
    }

    private Component playerLink(Player player) {
        return Teleports.playerLink(
            player.getName(),
            NamedTextColor.YELLOW,
            plugin.getConfig().getString("alerts.teleport-command", Teleports.DEFAULT_PLAYER_COMMAND)
        );
    }

    private Component locationLink(Player player) {
        if (!plugin.getConfig().getBoolean("alerts.show-coordinates", true)) {
            return Component.empty();
        }
        return Teleports.locationLink(
            player.getLocation(),
            NamedTextColor.DARK_AQUA,
            plugin.getConfig().getString(
                "alerts.teleport-coordinates-command",
                Teleports.DEFAULT_LOCATION_COMMAND
            )
        );
    }

    private ClientProfile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), ignored -> new ClientProfile());
    }

    private void refresh(Player player, ClientProfile profile) {
        String paperBrand = sanitizeBrand(player.getClientBrandName());
        if (paperBrand != null) {
            profile.brand(paperBrand);
        }
        profile.channels().addAll(player.getListeningPluginChannels());
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

        String decoded = length >= 0 && length <= message.length - index
            ? new String(message, index, length, StandardCharsets.UTF_8)
            : new String(message, StandardCharsets.UTF_8);
        return sanitizeBrand(decoded);
    }

    /** Control characters become spaces so Forge's legacy {@code forge\0FML\0} still tokenises. */
    private String sanitizeBrand(String brand) {
        if (brand == null) {
            return null;
        }
        String sanitized = brand.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return sanitized.isEmpty() ? null : Texts.truncate(sanitized, MAX_BRAND_LENGTH);
    }
}
