package de.pumpecraft.essentials;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

final class OfflineInventoryBridge {
    private final JavaPlugin plugin;
    private final Object api;
    private final Method mainInventoryMethod;
    private final Method enderInventoryMethod;
    private final Method responseIsSuccessMethod;
    private final Method responseInventoryMethod;
    private final Method responseReasonMethod;

    OfflineInventoryBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        Plugin invSeePlugin = plugin.getServer().getPluginManager().getPlugin("InvSeePlusPlus");
        if (invSeePlugin == null || !invSeePlugin.isEnabled()) {
            throw new IllegalStateException("InvSeePlusPlus is not enabled");
        }

        try {
            api = invSeePlugin.getClass().getMethod("getApi").invoke(invSeePlugin);
            mainInventoryMethod = api.getClass().getMethod(
                "mainSpectatorInventory", String.class);
            enderInventoryMethod = api.getClass().getMethod(
                "enderSpectatorInventory", String.class);
            Class<?> responseType = Class.forName(
                "com.janboerman.invsee.spigot.api.response.SpectateResponse",
                true,
                invSeePlugin.getClass().getClassLoader()
            );
            responseIsSuccessMethod = responseType.getMethod("isSuccess");
            responseInventoryMethod = responseType.getMethod("getInventory");
            responseReasonMethod = responseType.getMethod("getReason");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported InvSeePlusPlus API", exception);
        }
    }

    void openMainInventory(Player viewer, OfflinePlayer target) {
        open(viewer, target, mainInventoryMethod, "Inventar");
    }

    void openEnderChest(Player viewer, OfflinePlayer target) {
        open(viewer, target, enderInventoryMethod, "Enderchest");
    }

    private void open(
        Player viewer,
        OfflinePlayer target,
        Method inventoryMethod,
        String inventoryName
    ) {
        String targetName = target.getName() == null
            ? target.getUniqueId().toString()
            : target.getName();
        viewer.sendMessage(Component.text(
            inventoryName + " von " + targetName + " wird geladen ...",
            NamedTextColor.GRAY
        ));

        try {
            Object result = inventoryMethod.invoke(api, target.getUniqueId().toString());
            if (!(result instanceof CompletableFuture<?> future)) {
                throw new IllegalStateException("InvSeePlusPlus returned no future");
            }
            future.whenComplete((response, error) -> plugin.getServer().getScheduler().runTask(
                plugin,
                () -> completeOpen(viewer, targetName, inventoryName, response, error)
            ));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not request offline " + inventoryName.toLowerCase() + " for " + targetName,
                exception
            );
            viewer.sendMessage(error("Die Offline-Daten konnten nicht geladen werden."));
        }
    }

    private void completeOpen(
        Player viewer,
        String targetName,
        String inventoryName,
        Object response,
        Throwable failure
    ) {
        if (!viewer.isOnline()) {
            return;
        }
        if (failure != null) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not load offline " + inventoryName.toLowerCase() + " for " + targetName,
                failure
            );
            viewer.sendMessage(error("Die Offline-Daten konnten nicht geladen werden."));
            return;
        }

        try {
            if (!Boolean.TRUE.equals(responseIsSuccessMethod.invoke(response))) {
                Object reason = responseReasonMethod.invoke(response);
                plugin.getLogger().warning(
                    "InvSeePlusPlus rejected offline access for " + targetName + ": " + reason);
                viewer.sendMessage(error(
                    "Die Offline-Daten dieses Spielers konnten nicht geöffnet werden."));
                return;
            }

            Object openedInventory = responseInventoryMethod.invoke(response);
            if (!(openedInventory instanceof Inventory inventory)) {
                throw new IllegalStateException("InvSeePlusPlus returned an invalid inventory");
            }
            viewer.openInventory(inventory);
            viewer.sendMessage(
                Component.text(inventoryName + " von ", NamedTextColor.GRAY)
                    .append(Component.text(targetName, NamedTextColor.AQUA))
                    .append(Component.text(" geöffnet. Änderungen werden gespeichert.",
                        NamedTextColor.GRAY))
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not open offline " + inventoryName.toLowerCase() + " for " + targetName,
                exception
            );
            viewer.sendMessage(error("Die Offline-Daten konnten nicht geöffnet werden."));
        }
    }

    private static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
