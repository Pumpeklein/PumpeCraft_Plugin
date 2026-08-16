package de.pumpecraft.utils.objects;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/**
 * A placed object: the body display carrying the static model, one display per movable part or
 * label and an interaction entity as hitbox. Parts can be missing when their chunk is unloaded, so
 * every field is treated as optional.
 */
public record DisplayObject(
    DisplayObjectType type,
    ItemDisplay body,
    Map<String, Display> parts,
    Interaction hitbox
) {
    public static final String LABEL = "label";

    public DisplayObject {
        parts = Map.copyOf(parts);
    }

    public Display part(String name) {
        return parts.get(name);
    }

    public TextDisplay label() {
        return parts.get(LABEL) instanceof TextDisplay label ? label : null;
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
