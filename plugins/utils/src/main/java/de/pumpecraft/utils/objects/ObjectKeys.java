package de.pumpecraft.utils.objects;

import java.util.Objects;
import org.bukkit.NamespacedKey;

/**
 * Keys live in the shared {@code pumpeutils} namespace instead of the namespace of the consuming
 * plugin: every object is then readable by every plugin, which is what lets one plugin remove or
 * inspect an object another one placed.
 */
final class ObjectKeys {
    static final NamespacedKey TYPE = key("object_type");
    static final NamespacedKey PART = key("object_part");
    static final NamespacedKey ROOT = key("object_root");
    static final NamespacedKey PARTS = key("object_parts");
    static final NamespacedKey OWNER = key("object_owner");
    static final NamespacedKey OWNER_NAME = key("object_owner_name");
    static final NamespacedKey CONTENTS = key("object_contents");

    static final String BODY = "body";
    static final String HITBOX = "hitbox";

    private ObjectKeys() {
    }

    private static NamespacedKey key(String path) {
        return Objects.requireNonNull(NamespacedKey.fromString("pumpeutils:" + path));
    }
}
