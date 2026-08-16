package de.pumpecraft.utils.objects;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

/**
 * A placed object: the body display carrying the static model, one display per movable part and an
 * interaction entity as hitbox. All of them share one position, only their transformation differs.
 * Parts can be missing when their chunk is unloaded, so every field is treated as optional.
 */
public record DisplayObject(
    DisplayObjectType type,
    ItemDisplay body,
    Map<String, ItemDisplay> parts,
    Interaction hitbox
) {
    public DisplayObject {
        parts = Map.copyOf(parts);
    }

    public ItemDisplay part(String name) {
        return parts.get(name);
    }

    public boolean isValid() {
        return body != null && body.isValid();
    }

    /**
     * @return bottom center of the block the object stands on
     */
    public Location location() {
        if (hitbox != null) {
            return hitbox.getLocation();
        }
        return body == null ? null : body.getLocation().subtract(0.0D, 0.5D, 0.0D);
    }
}
