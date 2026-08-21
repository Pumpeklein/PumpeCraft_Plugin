package de.pumpecraft.subessentials;

import de.pumpecraft.utils.subscriber.SubscriberService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class SubscriberStatusService implements SubscriberService {
    private final SubEssentialsPlugin plugin;
    private final TwitchLinkRepository repository;
    private final TwitchSubscriptionClient twitch;
    private final SubscriberTabFormatter tabFormatter = new SubscriberTabFormatter();
    private final Map<UUID, Boolean> subscribers = new ConcurrentHashMap<>();
    private final Set<UUID> notificationsInFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> notificationVersions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> subscriptionNotificationVersions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> unlinkedAt = new ConcurrentHashMap<>();

    SubscriberStatusService(
        SubEssentialsPlugin plugin,
        TwitchLinkRepository repository,
        TwitchSubscriptionClient twitch
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.twitch = twitch;
    }

    @Override
    public boolean isSubscriber(UUID playerId) {
        return subscribers.getOrDefault(playerId, false);
    }

    void load(Player player, boolean checkTwitch) {
        UUID playerId = player.getUniqueId();
        plugin.runAsync(() -> {
            Optional<TwitchLink> found = repository.find(playerId);
            if (found.isEmpty()) {
                apply(playerId, false);
                return;
            }

            TwitchLink link = found.get();
            boolean subscriber = link.subscriber();
            boolean subscriptionChanged = false;
            if (checkTwitch) {
                Optional<Boolean> current = twitch.isSubscriber(link.twitchUserId());
                if (current.isPresent()) {
                    subscriber = current.get();
                    subscriptionChanged = repository.updateSubscription(
                        playerId, subscriber, System.currentTimeMillis()
                    );
                }
            }
            Long unlinkTime = unlinkedAt.get(playerId);
            if (unlinkTime != null) {
                if (link.linkedAt() <= unlinkTime) return;
                unlinkedAt.remove(playerId, unlinkTime);
            }
            apply(playerId, subscriber);
            boolean linkConfirmationPending = link.gameNotifiedAt() == null
                && notificationVersions.getOrDefault(playerId, -1L) != link.linkedAt()
                && notificationsInFlight.add(playerId);
            if (linkConfirmationPending) {
                sendLinkConfirmation(
                    playerId, link.twitchDisplayName(), subscriber, link.linkedAt()
                );
            } else if ((subscriptionChanged || link.subscriptionNotifiedState() == null)
                && !Objects.equals(subscriptionNotificationVersions.get(playerId), subscriber)
                && notificationsInFlight.add(playerId)) {
                sendSubscriptionChange(playerId, link.twitchDisplayName(), subscriber);
            }
        });
    }

    void remove(UUID playerId) {
        subscribers.remove(playerId);
        notificationVersions.remove(playerId);
        subscriptionNotificationVersions.remove(playerId);
    }

    void handleUnlinked(UUID playerId, long removedAt) {
        unlinkedAt.put(playerId, removedAt);
        subscribers.put(playerId, false);
        notificationVersions.remove(playerId);
        subscriptionNotificationVersions.remove(playerId);
        notificationsInFlight.remove(playerId);
        plugin.runSync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) tabFormatter.apply(player, false);
        });
    }

    void clear() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tabFormatter.apply(player, false);
        }
        subscribers.clear();
        notificationsInFlight.clear();
        notificationVersions.clear();
        subscriptionNotificationVersions.clear();
        unlinkedAt.clear();
    }

    private void apply(UUID playerId, boolean subscriber) {
        subscribers.put(playerId, subscriber);
        plugin.runSync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) tabFormatter.apply(player, subscriber);
        });
    }

    private void sendLinkConfirmation(
        UUID playerId,
        String twitchName,
        boolean subscriber,
        long linkedAt
    ) {
        plugin.runSync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                notificationsInFlight.remove(playerId);
                return;
            }

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Twitch erfolgreich verbunden!", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("Konto: ", NamedTextColor.GRAY)
                .append(Component.text(twitchName, NamedTextColor.WHITE)));
            if (subscriber) {
                player.sendMessage(Component.text(
                    "Dein aktives Abo wurde erkannt. /ec, /craft, /anvil und /et sind jetzt freigeschaltet.",
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(
                    "Aktuell wurde kein aktives Abo für den Kanal gefunden.",
                    NamedTextColor.YELLOW
                ));
            }
            player.sendMessage(Component.empty());
            notificationVersions.put(playerId, linkedAt);
            subscriptionNotificationVersions.put(playerId, subscriber);
            plugin.runAsync(() -> {
                try {
                    repository.markGameNotificationDelivered(
                        playerId, System.currentTimeMillis(), subscriber
                    );
                } finally {
                    notificationsInFlight.remove(playerId);
                }
            });
        });
    }

    private void sendSubscriptionChange(UUID playerId, String twitchName, boolean subscriber) {
        plugin.runSync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                notificationsInFlight.remove(playerId);
                return;
            }

            player.sendMessage(Component.empty());
            if (subscriber) {
                player.sendMessage(Component.text("Twitch-Sub erkannt!", NamedTextColor.LIGHT_PURPLE));
                player.sendMessage(Component.text(
                    "Dein Abo als " + twitchName
                        + " ist jetzt aktiv. /ec, /craft, /anvil und /et wurden freigeschaltet.",
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(
                    "Dein Twitch-Abo ist nicht mehr aktiv.", NamedTextColor.YELLOW
                ));
                player.sendMessage(Component.text(
                    "Die Sub-Befehle wurden gesperrt und deine Tab-Farbe zurückgesetzt.",
                    NamedTextColor.GRAY
                ));
            }
            player.sendMessage(Component.empty());
            subscriptionNotificationVersions.put(playerId, subscriber);
            plugin.runAsync(() -> {
                try {
                    repository.markSubscriptionNotificationDelivered(playerId, subscriber);
                } finally {
                    notificationsInFlight.remove(playerId);
                }
            });
        });
    }
}
