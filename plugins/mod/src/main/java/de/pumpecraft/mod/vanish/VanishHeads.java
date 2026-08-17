package de.pumpecraft.mod.vanish;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * Schwebende Köpfe der versteckten Teamler. Der versteckte Spieler bleibt in seinem eigenen
 * Spielmodus und ist nur unsichtbar geschaltet; damit das Team ihn trotzdem wie einen Spectator
 * sieht, folgt ihm ein {@code item_display} mit seinem Spielerkopf, das ausschließlich
 * berechtigten Zuschauern gezeigt wird.
 */
final class VanishHeads {
    private final Plugin plugin;
    private final Map<UUID, ItemDisplay> heads = new HashMap<>();

    VanishHeads(Plugin plugin) {
        this.plugin = plugin;
    }

    void spawn(Player staff) {
        remove(staff);

        heads.put(staff.getUniqueId(), staff.getWorld().spawn(headLocation(staff), ItemDisplay.class, display -> {
            display.setItemStack(headItem(staff));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTeleportDuration(1);
            display.setVisibleByDefault(false);
            display.setPersistent(false);
        }));
    }

    void showTo(Player viewer, Player staff) {
        ItemDisplay head = heads.get(staff.getUniqueId());
        if (head == null || viewer.equals(staff)) {
            return;
        }

        // Ein Spectator sieht unsichtbare Spieler ohnehin vollständig, der Kopf wäre doppelt.
        if (viewer.getGameMode() == GameMode.SPECTATOR) {
            viewer.hideEntity(plugin, head);
            return;
        }
        viewer.showEntity(plugin, head);
    }

    void hideFrom(Player viewer, Player staff) {
        ItemDisplay head = heads.get(staff.getUniqueId());
        if (head != null && !viewer.equals(staff)) {
            viewer.hideEntity(plugin, head);
        }
    }

    void remove(Player staff) {
        ItemDisplay head = heads.remove(staff.getUniqueId());
        if (head != null) {
            head.remove();
        }
    }

    void removeAll() {
        for (ItemDisplay head : heads.values()) {
            head.remove();
        }
        heads.clear();
    }

    void follow() {
        for (Map.Entry<UUID, ItemDisplay> entry : heads.entrySet()) {
            Player staff = Bukkit.getPlayer(entry.getKey());
            ItemDisplay head = entry.getValue();
            if (staff != null && head.isValid()) {
                head.teleport(headLocation(staff));
            }
        }
    }

    private Location headLocation(Player staff) {
        Location location = staff.getEyeLocation();
        // Ein item_display mit Transform FIXED rendert das Modell um 180° um Y gedreht.
        location.setYaw(location.getYaw() + 180.0F);
        return location;
    }

    private ItemStack headItem(Player staff) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(staff.getPlayerProfile());
        item.setItemMeta(meta);
        return item;
    }
}
