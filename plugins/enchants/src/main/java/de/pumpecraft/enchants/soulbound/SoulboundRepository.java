package de.pumpecraft.enchants.soulbound;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class SoulboundRepository {
    private final DatabaseService database;

    public SoulboundRepository(Plugin plugin) {
        this.database = Databases.require(plugin);
    }

    public void store(UUID playerId, Map<Integer, ItemStack> items, long when) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pc_soulbound_items (player_uuid, slot, item, created_at) "
                    + "VALUES (?, ?, ?, ?)"
            )) {
                for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                    statement.setString(1, playerId.toString());
                    statement.setInt(2, entry.getKey());
                    statement.setBytes(3, entry.getValue().serializeAsBytes());
                    statement.setLong(4, when);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public Map<Integer, ItemStack> load(UUID playerId) {
        return database.withConnection(connection -> {
            Map<Integer, ItemStack> items = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT slot, item FROM pc_soulbound_items WHERE player_uuid = ? ORDER BY id"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        items.put(
                            result.getInt("slot"),
                            ItemStack.deserializeBytes(result.getBytes("item")));
                    }
                }
            }
            return items;
        });
    }

    public void delete(UUID playerId) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_soulbound_items WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
