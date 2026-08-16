package de.pumpecraft.mailbox.box;

import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * One mailbox per player. The map is the fast path for every check in the game loop, the database
 * behind it keeps the assignment across restarts and knows positions of unloaded chunks.
 */
public final class MailboxIndex {
    private final Plugin plugin;
    private final MailboxRepository repository;
    private final Map<UUID, MailboxEntry> entries = new ConcurrentHashMap<>();

    public MailboxIndex(Plugin plugin, MailboxRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void load() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Collection<MailboxEntry> loaded = repository.loadAll();
            Bukkit.getScheduler().runTask(plugin, () -> {
                entries.clear();
                loaded.forEach(entry -> entries.put(entry.owner(), entry));
                plugin.getLogger().info("Loaded " + entries.size() + " mailboxes.");
            });
        });
    }

    public Optional<MailboxEntry> of(UUID owner) {
        return Optional.ofNullable(entries.get(owner));
    }

    public boolean has(UUID owner) {
        return entries.containsKey(owner);
    }

    public void add(MailboxEntry entry) {
        entries.put(entry.owner(), entry);
        long now = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.insert(entry, now));
    }

    public void remove(UUID owner) {
        entries.remove(owner);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.delete(owner));
    }

    public Optional<UUID> ownerOf(DisplayObject mailbox) {
        if (mailbox.body() == null) {
            return Optional.empty();
        }
        UUID bodyId = mailbox.body().getUniqueId();
        return entries.values().stream()
            .filter(entry -> entry.bodyId().equals(bodyId))
            .map(MailboxEntry::owner)
            .findFirst();
    }

    /**
     * Resolves the placed mailbox of a player, loading its chunk when needed. The callback runs on
     * the main thread and receives {@code null} when the object is gone - then the entry is stale
     * and gets dropped.
     */
    public void resolve(UUID owner, Consumer<DisplayObject> callback) {
        MailboxEntry entry = entries.get(owner);
        if (entry == null) {
            callback.accept(null);
            return;
        }

        Optional<DisplayObject> loaded = DisplayObjects.byBody(MailboxObject.TYPE, entry.bodyId());
        if (loaded.isPresent()) {
            callback.accept(loaded.get());
            return;
        }

        Location location = entry.location();
        if (location == null) {
            callback.accept(null);
            return;
        }

        CompletableFuture<org.bukkit.Chunk> chunk = location.getWorld().getChunkAtAsync(location);
        chunk.thenAccept(loadedChunk -> Bukkit.getScheduler().runTask(plugin, () -> {
            Optional<DisplayObject> object = DisplayObjects.byBody(MailboxObject.TYPE, entry.bodyId());
            if (object.isEmpty()) {
                entries.remove(owner);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.delete(owner));
            }
            callback.accept(object.orElse(null));
        }));
    }
}
