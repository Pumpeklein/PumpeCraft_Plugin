package de.pumpecraft.utils.objects;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Places, finds and removes server objects built from display entities. A server can not teach the
 * client new blocks, so an object is a set of vanilla items with an {@code item_model} component:
 * one display for the body, one per movable part and an interaction entity as hitbox.
 *
 * <p>All displays share the position of the body, half a block above the ground - an item display
 * renders the model box centered on the entity, so this is where model height 0 meets the ground.
 * Movable parts differ from the body only in their transformation, see {@link HingeAnimator}.
 * Parts that stand next to the object instead of inside it are attached with
 * {@link #attach(DisplayObject, String, NamespacedKey, double, double, double)}.
 */
public final class DisplayObjects {
    private static final Color LABEL_BACKGROUND = Color.fromARGB(90, 0, 0, 0);

    private DisplayObjects() {
    }

    /**
     * @param base bottom center of the block the object stands on
     */
    public static DisplayObject spawn(DisplayObjectType type, Location base, float yaw, OfflinePlayer owner) {
        Location hitboxLocation = base.clone();
        hitboxLocation.setYaw(yaw);
        hitboxLocation.setPitch(0.0F);
        Location displayLocation = hitboxLocation.clone().add(0.0D, 0.5D, 0.0D);

        World world = hitboxLocation.getWorld();
        ItemDisplay body = display(world, displayLocation, type, type.bodyModel(), ObjectKeys.BODY);
        body.setShadowRadius(type.shadowRadius());

        Map<String, Display> parts = new LinkedHashMap<>();
        type.parts().forEach((name, model) ->
            parts.put(name, display(world, displayLocation, type, model, name)));
        if (type.labelled()) {
            parts.put(DisplayObject.LABEL, label(world, hitboxLocation, type));
        }

        Interaction hitbox = world.spawn(hitboxLocation, Interaction.class, entity -> {
            entity.setInteractionWidth(type.width());
            entity.setInteractionHeight(type.height());
            entity.setResponsive(true);
            entity.setPersistent(true);
            mark(entity, type, ObjectKeys.HITBOX);
        });

        StringJoiner references = new StringJoiner(";");
        parts.forEach((name, part) -> references.add(name + "=" + part.getUniqueId()));
        references.add(ObjectKeys.HITBOX + "=" + hitbox.getUniqueId());
        body.getPersistentDataContainer().set(ObjectKeys.PARTS, PersistentDataType.STRING, references.toString());

        UUID root = body.getUniqueId();
        parts.values().forEach(part -> link(part, root));
        link(hitbox, root);

        DisplayObject object = new DisplayObject(type, body, parts, hitbox);
        if (owner != null) {
            ObjectStorage.setOwner(object, owner);
        }
        return object;
    }

    public static boolean isPart(Entity entity) {
        return entity.getPersistentDataContainer().has(ObjectKeys.TYPE, PersistentDataType.STRING);
    }

    public static boolean isPart(DisplayObjectType type, Entity entity) {
        return type.id().equals(entity.getPersistentDataContainer().get(ObjectKeys.TYPE, PersistentDataType.STRING));
    }

    public static Optional<DisplayObject> resolve(DisplayObjectType type, Entity entity) {
        ItemDisplay body = body(type, entity);
        if (body == null) {
            return Optional.empty();
        }

        Map<String, UUID> references = references(body);
        Map<String, Display> parts = new LinkedHashMap<>();
        references.forEach((name, id) -> {
            if (!ObjectKeys.HITBOX.equals(name) && entity(id) instanceof Display part) {
                parts.put(name, part);
            }
        });

        Interaction hitbox = entity(references.get(ObjectKeys.HITBOX)) instanceof Interaction found ? found : null;
        return Optional.of(new DisplayObject(type, body, parts, hitbox));
    }

    /**
     * Resolves an object from the unique id of its body, for example after reading it from a
     * database. Returns empty while the chunk is unloaded.
     */
    public static Optional<DisplayObject> byBody(DisplayObjectType type, UUID bodyId) {
        Entity body = entity(bodyId);
        return body == null ? Optional.empty() : resolve(type, body);
    }

    public static Optional<DisplayObject> nearest(DisplayObjectType type, Location location, double radius) {
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof Interaction) || !isPart(type, entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(location);
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest == null ? Optional.empty() : resolve(type, closest);
    }

    public static void remove(DisplayObject object) {
        object.parts().values().forEach(DisplayObjects::removePart);
        removePart(object.hitbox());
        removePart(object.body());
    }

    /**
     * Adds a display next to an existing object, for example a parcel standing beside a mailbox.
     * The offset is relative to the object: x to its side, z towards its front.
     */
    public static Display attach(
        DisplayObject object,
        String name,
        NamespacedKey model,
        double offsetX,
        double offsetY,
        double offsetZ
    ) {
        ItemDisplay body = object.body();
        Location base = object.location();
        if (body == null || base == null) {
            return null;
        }

        detach(object, name);

        float yaw = body.getLocation().getYaw();
        Vector offset = new Vector(offsetX, 0.0D, offsetZ).rotateAroundY(-Math.toRadians(yaw));
        Location location = base.clone().add(offset.getX(), offsetY + 0.5D, offset.getZ());
        location.setYaw(yaw);
        location.setPitch(0.0F);

        Display part = display(location.getWorld(), location, object.type(), model, name);
        link(part, body.getUniqueId());
        putReference(body, name, part.getUniqueId());
        return part;
    }

    public static void detach(DisplayObject object, String name) {
        ItemDisplay body = object.body();
        if (body == null) {
            return;
        }

        Map<String, UUID> references = references(body);
        UUID id = references.remove(name);
        if (id == null) {
            return;
        }

        Entity part = entity(id);
        if (part != null) {
            part.remove();
        }
        writeReferences(body, references);
    }

    public static void setLabel(DisplayObject object, Component text) {
        TextDisplay label = object.label();
        if (label != null) {
            label.text(text);
        }
    }

    public static ItemStack createItem(DisplayObjectType type, int amount) {
        ItemStack item = new ItemStack(type.baseMaterial(), type.stackable() ? amount : 1);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(type.itemModel());
        if (!type.stackable()) {
            meta.setMaxStackSize(1);
        }
        meta.getPersistentDataContainer().set(ObjectKeys.TYPE, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isItem(DisplayObjectType type, ItemStack item) {
        if (item == null || item.getType() != type.baseMaterial() || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ObjectKeys.TYPE, PersistentDataType.STRING);
        return type.id().equals(id);
    }

    /**
     * @return bottom center of the block, the position an object is placed on
     */
    public static Location baseOf(Block block) {
        return block.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    /**
     * @return yaw that turns the front of an object towards the player
     */
    public static float facingYaw(Player player) {
        return player.getLocation().getYaw() + 180.0F;
    }

    private static ItemDisplay display(
        World world,
        Location location,
        DisplayObjectType type,
        NamespacedKey model,
        String part
    ) {
        return world.spawn(location, ItemDisplay.class, entity -> {
            ItemStack item = new ItemStack(type.baseMaterial(), 1);
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(model);
            item.setItemMeta(meta);

            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setPersistent(true);
            mark(entity, type, part);
        });
    }

    private static TextDisplay label(World world, Location base, DisplayObjectType type) {
        Location location = base.clone().add(0.0D, type.labelHeight(), 0.0D);
        return world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setViewRange(type.labelViewRange());
            entity.setBackgroundColor(LABEL_BACKGROUND);
            entity.setSeeThrough(false);
            entity.setPersistent(true);
            mark(entity, type, DisplayObject.LABEL);
        });
    }

    private static void mark(Entity entity, DisplayObjectType type, String part) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(ObjectKeys.TYPE, PersistentDataType.STRING, type.id());
        data.set(ObjectKeys.PART, PersistentDataType.STRING, part);
    }

    private static void link(Entity part, UUID root) {
        part.getPersistentDataContainer().set(ObjectKeys.ROOT, PersistentDataType.STRING, root.toString());
    }

    private static ItemDisplay body(DisplayObjectType type, Entity entity) {
        if (!isPart(type, entity)) {
            return null;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (entity instanceof ItemDisplay display
            && ObjectKeys.BODY.equals(data.get(ObjectKeys.PART, PersistentDataType.STRING))) {
            return display;
        }
        String root = data.get(ObjectKeys.ROOT, PersistentDataType.STRING);
        return entity(uuid(root)) instanceof ItemDisplay found ? found : null;
    }

    private static Map<String, UUID> references(ItemDisplay body) {
        String raw = body.getPersistentDataContainer().get(ObjectKeys.PARTS, PersistentDataType.STRING);
        Map<String, UUID> references = new LinkedHashMap<>();
        if (raw == null) {
            return references;
        }
        for (String entry : raw.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            UUID id = uuid(entry.substring(separator + 1));
            if (id != null) {
                references.put(entry.substring(0, separator), id);
            }
        }
        return references;
    }

    private static void putReference(ItemDisplay body, String name, UUID id) {
        Map<String, UUID> references = references(body);
        references.put(name, id);
        writeReferences(body, references);
    }

    private static void writeReferences(ItemDisplay body, Map<String, UUID> references) {
        StringJoiner joiner = new StringJoiner(";");
        references.forEach((name, id) -> joiner.add(name + "=" + id));
        body.getPersistentDataContainer().set(ObjectKeys.PARTS, PersistentDataType.STRING, joiner.toString());
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Entity entity(UUID id) {
        return id == null ? null : Bukkit.getEntity(id);
    }

    private static void removePart(Entity part) {
        if (part != null) {
            part.remove();
        }
    }
}
