package de.pumpecraft.utils.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Owner and contents of an object, kept in the persistent data of its body display. That makes an
 * object survive a restart without a database table and keeps it with the entity when it is moved
 * or removed.
 */
public final class ObjectStorage {
    private ObjectStorage() {
    }

    public static void setOwner(DisplayObject object, OfflinePlayer owner) {
        ItemDisplay body = object.body();
        if (body == null) {
            return;
        }
        PersistentDataContainer data = body.getPersistentDataContainer();
        data.set(ObjectKeys.OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        if (owner.getName() != null) {
            data.set(ObjectKeys.OWNER_NAME, PersistentDataType.STRING, owner.getName());
        }
    }

    public static Optional<UUID> owner(DisplayObject object) {
        String id = value(object, ObjectKeys.OWNER);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static String ownerName(DisplayObject object, String fallback) {
        String name = value(object, ObjectKeys.OWNER_NAME);
        return name == null ? fallback : name;
    }

    public static List<ItemStack> contents(DisplayObject object) {
        ItemDisplay body = object.body();
        List<ItemStack> contents = new ArrayList<>();
        if (body == null) {
            return contents;
        }

        byte[] raw = body.getPersistentDataContainer().get(ObjectKeys.CONTENTS, PersistentDataType.BYTE_ARRAY);
        if (raw == null || raw.length == 0) {
            return contents;
        }

        try {
            for (ItemStack item : ItemStack.deserializeItemsFromBytes(raw)) {
                if (item != null && !item.getType().isAir()) {
                    contents.add(item);
                }
            }
        } catch (RuntimeException exception) {
            Bukkit.getLogger().warning("PumpeUtils: unreadable contents on object " + body.getUniqueId());
        }
        return contents;
    }

    public static void setContents(DisplayObject object, List<ItemStack> contents) {
        ItemDisplay body = object.body();
        if (body == null) {
            return;
        }

        PersistentDataContainer data = body.getPersistentDataContainer();
        if (contents.isEmpty()) {
            data.remove(ObjectKeys.CONTENTS);
            return;
        }
        data.set(ObjectKeys.CONTENTS, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(contents));
    }

    public static boolean hasContents(DisplayObject object) {
        ItemDisplay body = object.body();
        return body != null
            && body.getPersistentDataContainer().has(ObjectKeys.CONTENTS, PersistentDataType.BYTE_ARRAY);
    }

    private static String value(DisplayObject object, NamespacedKey key) {
        ItemDisplay body = object.body();
        return body == null ? null : body.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
