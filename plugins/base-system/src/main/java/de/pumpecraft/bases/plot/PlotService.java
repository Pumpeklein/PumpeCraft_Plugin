package de.pumpecraft.bases.plot;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PlotSettings;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.PlayerIdentity;
import de.pumpecraft.bases.plot.PlotPricing.Quote;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import de.pumpecraft.utils.Texts;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Alle Grundstücksaktionen samt Rückmeldung. Befehl und Menü rufen dieselben Methoden auf.
 *
 * <p>Kauf und Verkauf laufen unter einer gemeinsamen Sperre: Prüfung, Abbuchung, Einfügen und
 * Eintrag in den Index gehören zusammen. Ohne sie könnten zwei gleichzeitige Käufe dieselbe Fläche
 * beide freigeben, weil jeder von ihnen vor dem anderen geprüft hat. Der Index wird dabei
 * absichtlich noch im Hintergrund-Task geschrieben und nicht erst im Haupt-Thread: Ein Eintrag,
 * der einen Tick später käme, läge außerhalb der Sperre und machte sie wirkungslos.
 */
public final class PlotService {
    private static final int MAX_NAME_LENGTH = 32;

    private final PumpeBaseSystemPlugin plugin;
    private final PlotRepository repository;
    private final PlotIndex index;
    private final PlotPricing pricing;
    private final PlotSettings settings;
    private final PlotGuard guard;
    private final PlotTool tool;
    private final PlotSelections selections = new PlotSelections();
    private final Object claimLock = new Object();

    public PlotService(
        PumpeBaseSystemPlugin plugin,
        PlotRepository repository,
        PlotIndex index,
        PlotSettings settings,
        PlotGuard guard,
        PlotTool tool
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.index = index;
        this.settings = settings;
        this.guard = guard;
        this.tool = tool;
        this.pricing = new PlotPricing(settings);
    }

    public PlotIndex index() {
        return index;
    }

    public PlotPricing pricing() {
        return pricing;
    }

    public PlotSettings settings() {
        return settings;
    }

    public PlotSelections selections() {
        return selections;
    }

    public void reload() {
        plugin.runAsync(() -> {
            var plots = repository.loadAll();
            plugin.runSync(() -> {
                index.replaceAll(plots);
                plugin.getLogger().info("Loaded " + plots.size() + " plots.");
            });
        });
    }

    /** @return der Grund, warum die Auswahl nicht gekauft werden kann, oder {@code null} */
    public String rejectionFor(Player player, PlotArea area) {
        if (area == null) {
            return "Setze zuerst beide Ecken mit dem Grundstücksmesser.";
        }
        if (player.getWorld() != null && !settings.worldAllowed(player.getWorld())) {
            return "In dieser Welt gibt es keine Grundstücke.";
        }
        if (area.width() < settings.minSize() || area.depth() < settings.minSize()) {
            return "Ein Grundstück muss mindestens " + settings.minSize() + " Blöcke breit sein.";
        }
        if (area.width() > settings.maxSize() || area.depth() > settings.maxSize()) {
            return "Ein Grundstück darf höchstens " + settings.maxSize() + " Blöcke breit sein.";
        }
        Plot overlap = index.overlapping(area);
        if (overlap != null) {
            return "Die Fläche überschneidet sich mit dem Grundstück " + overlap.name() + ".";
        }
        return null;
    }

    public String rejectionForName(String name) {
        if (name == null || name.isBlank()) {
            return "Gib deinem Grundstück einen Namen.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Der Name darf höchstens " + MAX_NAME_LENGTH + " Zeichen haben.";
        }
        if (!name.matches("[A-Za-z0-9ÄÖÜäöüß_-]+")) {
            return "Der Name darf nur Buchstaben, Ziffern, Bindestrich und Unterstrich enthalten.";
        }
        if (index.byName(name) != null) {
            return "Diesen Namen gibt es schon.";
        }
        return null;
    }

    public void claim(Player player, String name, Consumer<Plot> after) {
        PlotSelections.Selection selection = selections.of(player);
        PlotArea area = selection == null ? null : selection.area();
        String rejection = rejectionFor(player, area);
        if (rejection == null) {
            rejection = rejectionForName(name);
        }
        if (rejection == null && settings.maxPerPlayer() > 0
            && index.countOwnedBy(player.getUniqueId()) >= settings.maxPerPlayer()) {
            rejection = "Du besitzt bereits " + settings.maxPerPlayer() + " Grundstücke.";
        }
        if (rejection != null) {
            fail(player, rejection);
            accept(after, null);
            return;
        }

        Quote quote = pricing.quote(area);
        PlayerIdentity owner = new PlayerIdentity(player.getUniqueId(), player.getName());
        plugin.runAsync(player, () -> buy(owner, name, area, quote), result -> {
            Plot plot = result.plot();
            if (plot == null) {
                fail(player, result.reason());
                accept(after, null);
                return;
            }
            selections.clear(player);
            tool.takeFrom(player);
            player.sendMessage(BaseText.success("Grundstück " + plot.name() + " gekauft für "
                + Currency.format(quote.price()) + "."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.2F);
            accept(after, plot);
        });
    }

    public void claimForServer(Player player, String name, Consumer<Plot> after) {
        PlotSelections.Selection selection = selections.of(player);
        PlotArea area = selection == null ? null : selection.area();
        String rejection = rejectionFor(player, area);
        if (rejection == null) {
            rejection = rejectionForName(name);
        }
        if (rejection != null) {
            fail(player, rejection);
            accept(after, null);
            return;
        }
        plugin.runAsync(player, () -> {
            synchronized (claimLock) {
                if (index.overlapping(area) != null || repository.nameTaken(name)) {
                    return null;
                }
                long id = repository.create(name, null, area, true, 0L, System.currentTimeMillis());
                Plot plot = new Plot(
                    id, name, null, null, area, true, 0L, System.currentTimeMillis());
                index.add(plot);
                return plot;
            }
        }, plot -> {
            if (plot == null) {
                fail(player, "Die Fläche ist inzwischen belegt.");
                accept(after, null);
                return;
            }
            selections.clear(player);
            tool.takeFrom(player);
            player.sendMessage(BaseText.success(
                "Admingebiet " + plot.name() + " angelegt. Bauen ist dort für alle gesperrt."));
            accept(after, plot);
        });
    }

    public void sell(Player player, Plot plot, Consumer<Boolean> after) {
        if (plot.adminPlot()) {
            fail(player, "Ein Admingebiet lässt sich nicht verkaufen.");
            accept(after, false);
            return;
        }
        if (!player.getUniqueId().equals(plot.ownerId())) {
            fail(player, "Nur der Besitzer kann verkaufen.");
            accept(after, false);
            return;
        }
        long refund = pricing.refundFor(plot);
        PlayerIdentity owner = new PlayerIdentity(player.getUniqueId(), player.getName());
        plugin.runAsync(player, () -> {
            synchronized (claimLock) {
                repository.delete(plot.id());
                if (refund > 0L) {
                    points().deposit(
                        owner.playerId(),
                        owner.playerName(),
                        refund,
                        TransactionType.PLOT_REFUND,
                        "System",
                        "Grundstück " + plot.name()
                    );
                }
                index.remove(plot);
                return true;
            }
        }, ignored -> {
            player.sendMessage(BaseText.success("Grundstück " + plot.name()
                + " verkauft. Erstattet: " + Currency.format(refund) + "."));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6F, 1.4F);
            accept(after, true);
        });
    }

    public void deleteAsAdmin(Player player, Plot plot, Consumer<Boolean> after) {
        plugin.runAsync(player, () -> {
            synchronized (claimLock) {
                repository.delete(plot.id());
                index.remove(plot);
                return true;
            }
        }, ignored -> {
            player.sendMessage(BaseText.success("Grundstück " + plot.name() + " entfernt."));
            accept(after, true);
        });
    }

    public void setMember(
        Player actor,
        Plot plot,
        PlayerIdentity target,
        PlotRole role,
        Consumer<Boolean> after
    ) {
        if (target.playerId().equals(plot.ownerId())) {
            fail(actor, "Der Besitzer steht ohnehin über allem.");
            accept(after, false);
            return;
        }
        plugin.runAsync(actor, () -> {
            repository.setMember(plot.id(), target, role, System.currentTimeMillis());
            return true;
        }, ignored -> {
            plot.members().put(target.playerId(), new PlotMember(
                target.playerId(), target.playerName(), role, System.currentTimeMillis()));
            actor.sendMessage(BaseText.success(target.playerName() + " ist jetzt "
                + role.displayName() + " auf " + plot.name() + "."));
            notifyTarget(target, plot, role);
            accept(after, true);
        });
    }

    public void removeMember(Player actor, Plot plot, PlayerIdentity target, Consumer<Boolean> after) {
        if (!plot.members().containsKey(target.playerId())) {
            fail(actor, target.playerName() + " gehört nicht zu diesem Grundstück.");
            accept(after, false);
            return;
        }
        plugin.runAsync(actor, () -> {
            repository.removeMember(plot.id(), target.playerId());
            return true;
        }, ignored -> {
            plot.members().remove(target.playerId());
            actor.sendMessage(BaseText.success(
                target.playerName() + " wurde von " + plot.name() + " entfernt."));
            evictLockedOut(plot);
            accept(after, true);
        });
    }

    /** @param value {@code null} setzt die Flagge auf ihren Standard zurück */
    public void setFlag(Player actor, Plot plot, PlotFlag flag, Boolean value, Runnable after) {
        plugin.runAsync(actor, () -> {
            if (value == null) {
                repository.clearFlag(plot.id(), flag);
            } else {
                repository.setFlag(plot.id(), flag, value);
            }
            return true;
        }, ignored -> {
            if (value == null) {
                plot.flags().remove(flag);
            } else {
                plot.flags().put(flag, value);
            }
            actor.sendMessage(BaseText.success(flag.displayName() + " auf " + plot.name()
                + ": " + (plot.flag(flag) ? "an" : "aus")
                + (value == null ? " (Standard)" : "")));
            if (flag == PlotFlag.ENTRY) {
                evictLockedOut(plot);
            }
            if (after != null) {
                after.run();
            }
        });
    }

    public void setHeight(Player actor, Plot plot, Integer minY, Integer maxY, Runnable after) {
        plugin.runAsync(actor, () -> {
            repository.setHeight(plot.id(), minY, maxY);
            return true;
        }, ignored -> {
            // Die Höhe steckt in der Fläche, und die Fläche bestimmt die Chunks des Index -
            // deshalb wird das Grundstück ausgetragen und mit neuer Fläche wieder eingetragen.
            index.remove(plot);
            plot.area(plot.area().withHeight(minY, maxY));
            index.add(plot);
            actor.sendMessage(BaseText.success(
                "Höhe von " + plot.name() + ": " + plot.area().heightLabel()));
            if (after != null) {
                after.run();
            }
        });
    }

    /**
     * Wer nach einer Änderung nicht mehr auf das Grundstück dürfte, aber darauf steht, wird davor
     * gesetzt - Rechte zu entziehen darf niemanden einsperren.
     */
    public void evictLockedOut(Plot plot) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!plot.area().containsColumn(
                online.getWorld().getUID(), online.getLocation().getBlockX(),
                online.getLocation().getBlockZ())) {
                continue;
            }
            if (guard.canEnter(online, plot)) {
                continue;
            }
            Location outside = PlotEviction.outside(online.getLocation(), plot.area());
            if (outside != null) {
                online.teleport(outside);
                online.sendMessage(BaseText.error(
                    "Du wurdest vom Grundstück " + plot.name() + " gesetzt."));
            }
        }
    }

    public void teleport(Player player, Plot plot) {
        Location center = plot.center();
        if (center == null) {
            fail(player, "Die Welt dieses Grundstücks ist momentan nicht verfügbar.");
            return;
        }
        if (player.teleport(center)) {
            player.sendMessage(BaseText.success("Du stehst jetzt auf " + plot.name() + "."));
        }
    }

    public Component describe(Plot plot) {
        Quote quote = pricing.quote(plot.area());
        return BaseText.label("Fläche: ",
            Texts.number(plot.area().area()) + " Blöcke (" + plot.area().width()
                + " × " + plot.area().depth() + ")", NamedTextColor.WHITE)
            .append(Component.newline())
            .append(BaseText.label("Lage: ", plot.area().corners(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(BaseText.label("Neupreis: ",
                Currency.format(quote.price()), Currency.COLOR));
    }

    private ClaimResult buy(PlayerIdentity owner, String name, PlotArea area, Quote quote) {
        synchronized (claimLock) {
            if (index.overlapping(area) != null) {
                return new ClaimResult(null, "Die Fläche ist inzwischen belegt.");
            }
            if (repository.nameTaken(name)) {
                return new ClaimResult(null, "Diesen Namen gibt es schon.");
            }
            boolean paid = points().withdraw(
                owner.playerId(),
                owner.playerName(),
                quote.price(),
                TransactionType.PLOT_PURCHASE,
                "System",
                "Grundstück " + name
            );
            if (!paid) {
                return new ClaimResult(
                    null, "Dafür fehlen dir " + Currency.format(quote.price()) + ".");
            }
            long now = System.currentTimeMillis();
            long id = repository.create(name, owner, area, false, quote.price(), now);
            Plot plot = new Plot(
                id, name, owner.playerId(), owner.playerName(), area, false, quote.price(), now);
            index.add(plot);
            return new ClaimResult(plot, null);
        }
    }

    private record ClaimResult(Plot plot, String reason) {
    }

    private void notifyTarget(PlayerIdentity target, Plot plot, PlotRole role) {
        Player online = plugin.getServer().getPlayer(target.playerId());
        if (online != null && online.isOnline()) {
            online.sendMessage(BaseText.success("Du bist jetzt " + role.displayName()
                + " auf dem Grundstück " + plot.name() + "."));
        }
    }

    private PointsService points() {
        return plugin.points();
    }

    private void fail(Player player, String message) {
        player.sendMessage(BaseText.error(message));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7F, 1.0F);
    }

    private <T> void accept(Consumer<T> callback, T value) {
        if (callback != null) {
            callback.accept(value);
        }
    }

    public String normalizeName(String input) {
        return input == null ? null : input.trim().replace(' ', '_');
    }
}
