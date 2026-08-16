package de.pumpecraft.mailbox.mail;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class DeliveryRepository {
    private final DatabaseService database;

    public DeliveryRepository(Plugin plugin) {
        this.database = Databases.require(plugin);
    }

    public long insert(Delivery delivery) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pc_mail_deliveries (recipient_uuid, sender_uuid, sender_name, items, stacks, "
                    + "item_count, cost, sent_at, arrives_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setString(1, delivery.recipient().toString());
                statement.setString(2, delivery.sender().toString());
                statement.setString(3, delivery.senderName());
                statement.setBytes(4, ItemStack.serializeItemsAsBytes(delivery.items()));
                statement.setInt(5, delivery.stacks());
                statement.setInt(6, delivery.itemCount());
                statement.setLong(7, delivery.cost());
                statement.setLong(8, delivery.sentAt());
                statement.setLong(9, delivery.arrivesAt());
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : 0L;
                }
            }
        });
    }

    public List<Delivery> loadPending() {
        return database.withConnection(connection -> {
            List<Delivery> deliveries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, recipient_uuid, sender_uuid, sender_name, items, stacks, item_count, cost, "
                    + "sent_at, arrives_at FROM pc_mail_deliveries WHERE delivered_at IS NULL ORDER BY arrives_at"
            ); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    deliveries.add(new Delivery(
                        result.getLong("id"),
                        UUID.fromString(result.getString("recipient_uuid")),
                        UUID.fromString(result.getString("sender_uuid")),
                        result.getString("sender_name"),
                        items(result.getBytes("items")),
                        result.getInt("stacks"),
                        result.getInt("item_count"),
                        result.getLong("cost"),
                        result.getLong("sent_at"),
                        result.getLong("arrives_at")
                    ));
                }
            }
            return deliveries;
        });
    }

    public void markDelivered(long id, long when) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_mail_deliveries SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL"
            )) {
                statement.setLong(1, when);
                statement.setLong(2, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static List<ItemStack> items(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(ItemStack.deserializeItemsFromBytes(raw)));
    }
}
