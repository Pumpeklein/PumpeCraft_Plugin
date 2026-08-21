package de.pumpecraft.bases.plot;

import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Beantwortet die eine Frage, die alle Schutzlistener stellen: Darf dieser Spieler das hier?
 *
 * <p>Rollen und Flaggen greifen ineinander. Eine Flagge mit {@link PlotFlag.Scope#STRANGERS} lässt
 * Mitglieder unberührt; eine mit {@link PlotFlag.Scope#EVERYONE} beschreibt den Ort und gilt auch
 * für den Besitzer. Ein Grundstück ohne Eintrag verhält sich wie freies Land.
 */
public final class PlotGuard {
    private final PumpeBaseSystemPlugin plugin;
    private final PlotIndex index;

    public PlotGuard(PumpeBaseSystemPlugin plugin, PlotIndex index) {
        this.plugin = plugin;
        this.index = index;
    }

    public PlotIndex index() {
        return index;
    }

    public Plot at(Location location) {
        return index.at(location);
    }

    /** Das Grundstück der Säule - für Schritte und alles andere, was eine Fläche meint. */
    public Plot column(Location location) {
        return index.column(location);
    }

    public boolean bypasses(Player player) {
        return player.hasPermission(plugin.permission("plot-bypass"));
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission(plugin.permission("plot-admin"));
    }

    public boolean canBuild(Player player, Location location) {
        return allows(player, index.at(location), PlotFlag.PUBLIC_BUILD);
    }

    public boolean canInteract(Player player, Location location) {
        return allows(player, index.at(location), PlotFlag.PUBLIC_INTERACT);
    }

    public boolean canOpenContainer(Player player, Location location) {
        return allows(player, index.at(location), PlotFlag.CONTAINERS);
    }

    public boolean canSleep(Player player, Location location) {
        return allows(player, index.at(location), PlotFlag.SLEEPING);
    }

    public boolean canEnter(Player player, Plot plot) {
        return allows(player, plot, PlotFlag.ENTRY);
    }

    /**
     * PvP gilt beidseitig: Steht einer der beiden auf einem Grundstück ohne PvP, fällt der Schlag
     * aus. Sonst wäre ein Grundstück am Rand einer Kampfzone ein Ort, von dem aus man gefahrlos
     * schießt.
     */
    public boolean canFight(Player attacker, Location attackerLocation, Location victimLocation) {
        if (bypasses(attacker)) {
            return true;
        }
        return allowsHere(index.at(attackerLocation), PlotFlag.PVP)
            && allowsHere(index.at(victimLocation), PlotFlag.PVP);
    }

    /** Tierschutz beschreibt den Ort: Ist er an, verletzt hier niemand ein Tier, auch der Besitzer nicht. */
    public boolean canHurtAnimals(Player player, Location location) {
        return bypasses(player) || allowsHere(index.at(location), PlotFlag.ANIMAL_DAMAGE);
    }

    public boolean allowsHere(Location location, PlotFlag flag) {
        return allowsHere(index.at(location), flag);
    }

    public boolean allowsHere(Plot plot, PlotFlag flag) {
        return plot == null || plot.flag(flag);
    }

    /** Verwalten darf, wer im Grundstück Verwalter oder Besitzer ist - oder das Team. */
    public boolean canManage(Player player, Plot plot) {
        if (isAdmin(player)) {
            return true;
        }
        PlotRole role = plot.roleOf(player.getUniqueId());
        return role != null && role.canManage();
    }

    public boolean canChangeFlag(Player player, PlotFlag flag) {
        return !flag.staffOnly() || isAdmin(player);
    }

    public boolean canSell(Player player, Plot plot) {
        return !plot.adminPlot() && player.getUniqueId().equals(plot.ownerId());
    }

    private boolean allows(Player player, Plot plot, PlotFlag flag) {
        if (plot == null || bypasses(player)) {
            return true;
        }
        if (flag.scope() == PlotFlag.Scope.STRANGERS
            && plot.roleOf(player.getUniqueId()) != null) {
            return true;
        }
        return plot.flag(flag);
    }
}
