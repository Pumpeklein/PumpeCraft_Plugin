package de.pumpecraft.essentials;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/** Bukkit wrapper that avoids changing unrelated metadata during an offline inventory save. */
@SuppressWarnings("unchecked") // Paper's CraftPlayer currently has generic bridge warnings.
final class OfflineCraftPlayer extends CraftPlayer {
    private final CompoundTag originalData;

    OfflineCraftPlayer(CraftServer server, OfflineServerPlayer entity, CompoundTag originalData) {
        super(server, entity);
        this.originalData = originalData;
    }

    @Override
    public void setExtraData(ValueOutput output) {
        super.setExtraData(output);
        if (!(output instanceof TagValueOutput tagOutput)) {
            return;
        }

        CompoundTag saved = tagOutput.buildResult();
        copyLong(originalData.getCompound("bukkit"), saved.getCompoundOrEmpty("bukkit"), "lastPlayed");
        copyLong(originalData.getCompound("Paper"), saved.getCompoundOrEmpty("Paper"), "LastSeen");
        originalData.getCompound("RootVehicle")
            .ifPresent(rootVehicle -> saved.put("RootVehicle", rootVehicle.copy()));
    }

    @Override
    public OfflineServerPlayer getHandle() {
        return (OfflineServerPlayer) this.entity;
    }

    private static void copyLong(
        Optional<CompoundTag> source,
        CompoundTag destination,
        String key
    ) {
        source.flatMap(tag -> tag.getLong(key))
            .ifPresent(value -> destination.putLong(key, value));
    }
}
