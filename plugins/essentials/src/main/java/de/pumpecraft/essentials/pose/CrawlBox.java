package de.pumpecraft.essentials.pose;

import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Shulker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Die Decke, die einen krabbelnden Spieler in seiner eigenen Spiellogik unten hält.
 *
 * <p>Die Pose des eigenen Spielers entscheidet allein der Client: Passt er stehend nicht hin,
 * probiert {@code Player#updatePlayerPose} zuerst {@link Pose#CROUCHING} und erst danach
 * {@link Pose#SWIMMING}. Es braucht also eine Decke unter der Hock- und über der Krabbelhöhe.
 * Ein Block taugt dafür nicht: In Türen und an Treppen sitzt an dieser Stelle schon einer, auf
 * halber Standhöhe passt nur eine sichtbare Stufe hin, und beides fällt genau dem Spieler auf,
 * der sie sowieso nicht sehen soll.
 *
 * <p>Der Client prüft aber nicht nur Blöcke, sondern auch feste Entities, und ein Shulker ist
 * eines davon ({@code Shulker#canBeCollidedWith}). Er wird unsichtbar, allein an diesen Spieler
 * und auf Bruchteile genau gesetzt - unabhängig von Blockraster, Blockform und Spielergröße.
 */
final class CrawlBox {
    private final ServerPlayer receiver;
    private final Shulker box;
    private World world;

    private CrawlBox(ServerPlayer receiver, Shulker box, World world) {
        this.receiver = receiver;
        this.box = box;
        this.world = world;
    }

    static CrawlBox spawn(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        ServerPlayer receiver = ((CraftPlayer) player).getHandle();
        Shulker box = new Shulker(EntityType.SHULKER, ((CraftWorld) world).getHandle());
        box.setInvisible(true);
        box.setNoAi(true);
        box.setSilent(true);
        box.setNoGravity(true);
        box.setInvulnerable(true);

        CrawlBox crawlBox = new CrawlBox(receiver, box, world);
        crawlBox.moveBox(location);
        crawlBox.add();
        return crawlBox;
    }

    void follow(Location destination) {
        World target = destination.getWorld();
        if (target == null) {
            return;
        }
        // Beim Weltwechsel wirft der Client alles weg, was er kennt; ein Teleport auf eine ihm
        // unbekannte Id verpufft, der Spieler stünde wieder auf.
        if (!target.equals(world)) {
            remove();
            world = target;
            moveBox(destination);
            add();
            return;
        }
        if (!moveBox(destination)) {
            return;
        }
        send(new ClientboundTeleportEntityPacket(box.getId(), PositionMoveRotation.of(box), Set.of(), false));
    }

    void remove() {
        send(new ClientboundRemoveEntitiesPacket(box.getId()));
    }

    private boolean moveBox(Location location) {
        double x = location.getX();
        double y = ceiling(location.getY());
        double z = location.getZ();
        if (x == box.getX() && y == box.getY() && z == box.getZ()) {
            return false;
        }
        box.setPos(x, y, z);
        return true;
    }

    /**
     * Die Unterkante des geschlossenen Shulkers ist genau seine Entity-Höhe. Sie liegt mittig
     * zwischen Krabbel- und Hockhöhe: Nach unten bleibt Platz für die Krabbelbox - berührt die
     * Decke sie, bricht der Client die Posenwahl ganz ab -, nach oben scheitert das Hocken.
     * Beide Höhen kommen aus den Maßen, die der Server für diesen Spieler führt, und tragen
     * damit seine Skalierung ebenso wie die Maße der Serverversion.
     */
    private double ceiling(double feet) {
        double crawling = receiver.getDimensions(Pose.SWIMMING).height();
        double crouching = receiver.getDimensions(Pose.CROUCHING).height();
        return feet + (crawling + crouching) / 2.0D;
    }

    private void add() {
        send(new ClientboundAddEntityPacket(
            box.getId(),
            box.getUUID(),
            box.getX(),
            box.getY(),
            box.getZ(),
            box.getXRot(),
            box.getYRot(),
            box.getType(),
            0,
            box.getDeltaMovement(),
            box.getYHeadRot()
        ));
        List<SynchedEntityData.DataValue<?>> data = box.getEntityData().getNonDefaultValues();
        if (data != null) {
            send(new ClientboundSetEntityDataPacket(box.getId(), data));
        }
    }

    private void send(Packet<?> packet) {
        receiver.connection.send(packet);
    }
}
