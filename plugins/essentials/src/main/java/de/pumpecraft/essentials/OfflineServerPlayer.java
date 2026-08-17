package de.pumpecraft.essentials;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.CraftServer;

/** Server-side player shell used only while editing an offline player's data. */
final class OfflineServerPlayer extends ServerPlayer {
    private static final Field BUKKIT_ENTITY_FIELD = findBukkitEntityField();

    private final CraftServer craftServer;
    private final CompoundTag originalData;
    private OfflineCraftPlayer bukkitPlayer;

    OfflineServerPlayer(
        MinecraftServer server,
        CraftServer craftServer,
        ServerLevel level,
        GameProfile profile,
        ClientInformation clientInformation,
        CompoundTag originalData
    ) {
        super(server, level, profile, clientInformation);
        this.craftServer = craftServer;
        this.originalData = originalData;
    }

    @Override
    public OfflineCraftPlayer getBukkitEntity() {
        if (bukkitPlayer == null) {
            bukkitPlayer = new OfflineCraftPlayer(craftServer, this, originalData);
            try {
                BUKKIT_ENTITY_FIELD.set(this, bukkitPlayer);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not bind offline Bukkit player", exception);
            }
        }
        return bukkitPlayer;
    }

    private static Field findBukkitEntityField() {
        try {
            Field field = Entity.class.getDeclaredField("bukkitEntity");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
