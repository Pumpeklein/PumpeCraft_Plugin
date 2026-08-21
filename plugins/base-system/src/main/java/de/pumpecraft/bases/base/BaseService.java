package de.pumpecraft.bases.base;

import de.pumpecraft.bases.BaseSettings;
import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.plot.PlotGuard;
import de.pumpecraft.utils.Cooldowns;
import de.pumpecraft.utils.Texts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Alle Base-Aktionen samt Rückmeldung an den Spieler. Befehl und Menü rufen dieselben Methoden
 * auf, damit eine Regeländerung nicht an zwei Stellen nachgezogen werden muss.
 */
public final class BaseService {
    private final PumpeBaseSystemPlugin plugin;
    private final BaseRepository repository;
    private final BaseSettings settings;
    private final PlotGuard plots;
    private final BaseDirectory directory = new BaseDirectory();
    private final Cooldowns<UUID> visitCooldowns = new Cooldowns<>();

    public BaseService(
        PumpeBaseSystemPlugin plugin,
        BaseRepository repository,
        BaseSettings settings,
        PlotGuard plots
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
        this.plots = plots;
    }

    public BaseRepository repository() {
        return repository;
    }

    public BaseSettings settings() {
        return settings;
    }

    public BaseDirectory directory() {
        return directory;
    }

    public boolean mayInspectPrivate(CommandSender sender) {
        return sender.hasPermission(plugin.permission("base-admin"));
    }

    public void refreshDirectory() {
        plugin.runAsync(() -> {
            List<String> names = repository.ownerNames();
            plugin.runSync(() -> {
                directory.update(names);
                visitCooldowns.purgeExpired();
            });
        });
    }

    public void lookup(CommandSender viewer, String ownerName, Consumer<Optional<PlayerBase>> after) {
        plugin.runAsync(viewer, () -> repository.baseOf(ownerName), after);
    }

    public void lookup(CommandSender viewer, UUID ownerId, Consumer<Optional<PlayerBase>> after) {
        plugin.runAsync(viewer, () -> repository.baseOf(ownerId), after);
    }

    /**
     * @param requestedVisibility {@code null} übernimmt die Sichtbarkeit einer bestehenden Base;
     *     erst ohne Base entscheidet die Konfiguration. Ein Umzug soll eine private Base nicht
     *     unbemerkt öffentlich machen.
     */
    public void setBase(Player player, Boolean requestedVisibility, Consumer<PlayerBase> after) {
        Location location = player.getLocation();
        // Eine Base auf fremdem Grundstück wäre ein Besuchsziel mitten in fremdem Eigentum.
        if (!plots.canBuild(player, location)) {
            player.sendMessage(BaseText.error(
                "Hier darfst du nicht bauen - such dir einen Platz für deine Base."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, null);
            return;
        }
        BaseLocation baseLocation = new BaseLocation(
            location.getWorld().getUID(),
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        PlayerIdentity owner = identity(player);
        plugin.runAsync(player, () -> {
            boolean publicBase = requestedVisibility != null
                ? requestedVisibility
                : repository.baseOf(owner.playerId())
                    .map(PlayerBase::publicBase)
                    .orElse(settings.defaultPublic());
            repository.setBase(owner, baseLocation, publicBase, System.currentTimeMillis());
            return repository.baseOf(owner.playerId()).orElse(null);
        }, base -> {
            player.sendMessage(BaseText.success("Deine Base wurde gesetzt und ist "
                + (base != null && base.publicBase() ? "öffentlich." : "privat.")));
            play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.2F);
            refreshDirectory();
            accept(after, base);
        });
    }

    public void setVisibility(Player player, boolean publicBase, Consumer<PlayerBase> after) {
        plugin.runAsync(player, () -> {
            if (!repository.setVisibility(player.getUniqueId(), publicBase, System.currentTimeMillis())) {
                return null;
            }
            return repository.baseOf(player.getUniqueId()).orElse(null);
        }, base -> {
            if (base == null) {
                player.sendMessage(BaseText.error("Du hast noch keine Base gesetzt."));
                play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            } else {
                player.sendMessage(BaseText.success("Deine Base ist jetzt "
                    + (publicBase ? "öffentlich." : "privat.")));
                play(player, Sound.BLOCK_LEVER_CLICK, publicBase ? 1.6F : 1.0F);
            }
            accept(after, base);
        });
    }

    public void toggleVisibility(Player player, Consumer<PlayerBase> after) {
        plugin.runAsync(
            player,
            () -> repository.baseOf(player.getUniqueId()).orElse(null),
            base -> {
                if (base == null) {
                    player.sendMessage(BaseText.error("Du hast noch keine Base gesetzt."));
                    play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
                    accept(after, null);
                    return;
                }
                setVisibility(player, !base.publicBase(), after);
            }
        );
    }

    public void delete(Player player, Consumer<Boolean> after) {
        plugin.runAsync(player, () -> repository.deleteBase(player.getUniqueId()), deleted -> {
            if (deleted) {
                player.sendMessage(BaseText.success("Deine Base und ihre Statistiken wurden gelöscht."));
                play(player, Sound.BLOCK_ANVIL_LAND, 0.8F);
                refreshDirectory();
            } else {
                player.sendMessage(BaseText.error("Du hast noch keine Base gesetzt."));
                play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            }
            accept(after, deleted);
        });
    }

    public void visit(Player player, String ownerName, Consumer<VisitOutcome> after) {
        plugin.runAsync(
            player,
            () -> repository.baseOf(ownerName),
            base -> completeVisit(player, base.orElse(null), after)
        );
    }

    public void visit(Player player, UUID ownerId, Consumer<VisitOutcome> after) {
        plugin.runAsync(
            player,
            () -> repository.baseOf(ownerId),
            base -> completeVisit(player, base.orElse(null), after)
        );
    }

    public void toggleLike(Player player, UUID ownerId, Consumer<LikeOutcome> after) {
        plugin.runAsync(player, () -> repository.baseOf(ownerId), base -> completeLike(player, base.orElse(null), after));
    }

    public void toggleLike(Player player, String ownerName, Consumer<LikeOutcome> after) {
        plugin.runAsync(player, () -> repository.baseOf(ownerName), base -> completeLike(player, base.orElse(null), after));
    }

    private void completeVisit(Player player, PlayerBase base, Consumer<VisitOutcome> after) {
        if (base == null) {
            player.sendMessage(BaseText.error("Dieser Spieler hat keine Base gesetzt."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new VisitOutcome(VisitOutcome.Status.NO_BASE, null, 0L));
            return;
        }
        boolean ownBase = base.ownerId().equals(player.getUniqueId());
        if (!base.publicBase() && !ownBase && !mayInspectPrivate(player)) {
            player.sendMessage(BaseText.error("Diese Base ist privat."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new VisitOutcome(VisitOutcome.Status.PRIVATE, base, 0L));
            return;
        }
        if (!ownBase && settings.visitCooldownMillis() > 0L
            && !visitCooldowns.tryAcquire(player.getUniqueId(), settings.visitCooldownMillis())) {
            long remaining = visitCooldowns.remainingMillis(player.getUniqueId());
            player.sendMessage(BaseText.error("Warte noch "
                + Math.max(1L, remaining / 1000L) + " Sekunden bis zum nächsten Besuch."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new VisitOutcome(VisitOutcome.Status.COOLDOWN, base, remaining));
            return;
        }
        Location destination = base.bukkitLocation();
        if (destination == null) {
            player.sendMessage(BaseText.error("Die Welt dieser Base ist momentan nicht verfügbar."));
            accept(after, new VisitOutcome(VisitOutcome.Status.WORLD_MISSING, base, 0L));
            return;
        }
        if (!player.teleport(destination)) {
            player.sendMessage(BaseText.error("Die Teleportation zur Base ist fehlgeschlagen."));
            accept(after, new VisitOutcome(VisitOutcome.Status.TELEPORT_FAILED, base, 0L));
            return;
        }
        player.sendMessage(BaseText.success(ownBase
            ? "Willkommen zurück in deiner Base."
            : "Du besuchst die Base von " + base.ownerName() + "."));
        play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F);
        if (!ownBase) {
            PlayerIdentity visitor = identity(player);
            plugin.runAsync(() -> repository.recordVisit(
                base.ownerId(), visitor, System.currentTimeMillis()));
            notifyOwner(base, visitor);
        }
        accept(after, new VisitOutcome(VisitOutcome.Status.VISITED, base, 0L));
    }

    private void completeLike(Player player, PlayerBase base, Consumer<LikeOutcome> after) {
        if (base == null) {
            player.sendMessage(BaseText.error("Dieser Spieler hat keine Base gesetzt."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new LikeOutcome(LikeOutcome.Status.NO_BASE, null));
            return;
        }
        if (base.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(BaseText.error("Du kannst deine eigene Base nicht liken."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new LikeOutcome(LikeOutcome.Status.OWN_BASE, base));
            return;
        }
        if (!base.publicBase() && !mayInspectPrivate(player)) {
            player.sendMessage(BaseText.error("Diese Base ist privat."));
            play(player, Sound.ENTITY_VILLAGER_NO, 1.0F);
            accept(after, new LikeOutcome(LikeOutcome.Status.PRIVATE, base));
            return;
        }
        PlayerIdentity liker = identity(player);
        plugin.runAsync(
            player,
            () -> repository.toggleLike(base.ownerId(), liker, System.currentTimeMillis()),
            liked -> {
                player.sendMessage(liked
                    ? BaseText.success("Du hast die Base von " + base.ownerName() + " geliked.")
                    : BaseText.hint("Dein Like für die Base von " + base.ownerName()
                        + " wurde zurückgezogen."));
                play(player, liked
                    ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                    : Sound.BLOCK_NOTE_BLOCK_BASS, liked ? 1.4F : 0.8F);
                if (liked) {
                    notifyLike(base, liker);
                }
                accept(after, new LikeOutcome(
                    liked ? LikeOutcome.Status.LIKED : LikeOutcome.Status.UNLIKED, base));
            }
        );
    }

    private void notifyOwner(PlayerBase base, PlayerIdentity visitor) {
        Player owner = plugin.getServer().getPlayer(base.ownerId());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(BaseText.label(
                "Besuch: ", visitor.playerName() + " ist gerade in deiner Base.",
                NamedTextColor.WHITE));
        }
    }

    private void notifyLike(PlayerBase base, PlayerIdentity liker) {
        Player owner = plugin.getServer().getPlayer(base.ownerId());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(BaseText.success(liker.playerName() + " mag deine Base."));
            play(owner, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6F);
        }
    }

    public String relativeTime(long timestamp) {
        return Texts.since(System.currentTimeMillis() - timestamp);
    }

    private PlayerIdentity identity(Player player) {
        return new PlayerIdentity(player.getUniqueId(), player.getName());
    }

    private void play(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 0.7F, pitch);
    }

    private <T> void accept(Consumer<T> callback, T value) {
        if (callback != null) {
            callback.accept(value);
        }
    }
}
