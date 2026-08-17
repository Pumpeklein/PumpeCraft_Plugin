package de.pumpecraft.mod.vanish;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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
import org.bukkit.scheduler.BukkitTask;

/**
 * Schwebende Köpfe der versteckten Teamler. Der Client rendert einen echten Spectator nur für
 * andere Spectators; damit das Team einen Vanish trotzdem wie im Spectator-Modus sieht, folgt
 * jedem versteckten Teamler ein {@code item_display} mit seinem Spielerkopf, das ausschließlich
 * berechtigten Zuschauern gezeigt wird.
 */
final class VanishHeads {
    private final Plugin plugin;
    private final Map<UUID, ItemDisplay> heads = new HashMap<>();
    private BukkitTask followTask;

    VanishHeads(Plugin plugin) {
        this.plugin = plugin;
    }

    void spawn(Player staff, Component label) {
        remove(staff);

        ItemDisplay head = staff.getWorld().spawn(headLocation(staff), ItemDisplay.class, display -> {
            display.setItemStack(headItem(staff));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTeleportDuration(1);
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.customName(label);
            display.setCustomNameVisible(true);
        });

        heads.put(staff.getUniqueId(), head);
        if (followTask == null) {
            followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
        }
    }

    void showTo(Player viewer, Player staff) {
        ItemDisplay head = heads.get(staff.getUniqueId());
        if (head == null || viewer.equals(staff)) {
            return;
        }

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
        stopWhenEmpty();
    }

    void removeAll() {
        for (ItemDisplay head : heads.values()) {
            head.remove();
        }
        heads.clear();
        stopWhenEmpty();
    }

    private void follow() {
        for (Map.Entry<UUID, ItemDisplay> entry : heads.entrySet()) {
            Player staff = Bukkit.getPlayer(entry.getKey());
            ItemDisplay head = entry.getValue();
            if (staff != null && head.isValid()) {
                head.teleport(headLocation(staff));
            }
        }
    }

    private void stopWhenEmpty() {
        if (heads.isEmpty() && followTask != null) {
            followTask.cancel();
            followTask = null;
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
