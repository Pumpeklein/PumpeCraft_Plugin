package de.pumpecraft.utils.objects;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Describes one kind of server object: which vanilla item carries the model, which item models the
 * body and the movable parts use, and how big the hitbox is. One instance per object kind, built
 * once and kept as a constant in the owning plugin.
 */
public final class DisplayObjectType {
    private final String id;
    private final Material baseMaterial;
    private final NamespacedKey itemModel;
    private final NamespacedKey bodyModel;
    private final Map<String, NamespacedKey> parts;
    private final float width;
    private final float height;
    private final float shadowRadius;

    private DisplayObjectType(Builder builder) {
        this.id = builder.id;
        this.baseMaterial = builder.baseMaterial;
        this.itemModel = builder.itemModel;
        this.bodyModel = builder.bodyModel == null ? builder.itemModel : builder.bodyModel;
        this.parts = Map.copyOf(builder.parts);
        this.width = builder.width;
        this.height = builder.height;
        this.shadowRadius = builder.shadowRadius;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public Material baseMaterial() {
        return baseMaterial;
    }

    public NamespacedKey itemModel() {
        return itemModel;
    }

    public NamespacedKey bodyModel() {
        return bodyModel;
    }

    public Map<String, NamespacedKey> parts() {
        return parts;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float shadowRadius() {
        return shadowRadius;
    }

    public static final class Builder {
        private final String id;
        private final Map<String, NamespacedKey> parts = new LinkedHashMap<>();
        private Material baseMaterial = Material.PAPER;
        private NamespacedKey itemModel;
        private NamespacedKey bodyModel;
        private float width = 0.6F;
        private float height = 1.0F;
        private float shadowRadius;

        private Builder(String id) {
            this.id = id;
        }

        public Builder baseMaterial(Material baseMaterial) {
            this.baseMaterial = baseMaterial;
            return this;
        }

        public Builder itemModel(String key) {
            this.itemModel = key(key);
            return this;
        }

        public Builder bodyModel(String key) {
            this.bodyModel = key(key);
            return this;
        }

        public Builder part(String name, String key) {
            this.parts.put(name, key(key));
            return this;
        }

        public Builder hitbox(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder shadow(float radius) {
            this.shadowRadius = radius;
            return this;
        }

        public DisplayObjectType build() {
            Objects.requireNonNull(itemModel, "itemModel");
            return new DisplayObjectType(this);
        }

        private static NamespacedKey key(String value) {
            return Objects.requireNonNull(NamespacedKey.fromString(value), "Invalid model key: " + value);
        }
    }
}
