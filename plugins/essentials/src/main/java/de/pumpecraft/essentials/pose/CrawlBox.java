package de.pumpecraft.essentials.pose;

import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
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
 * {@link Pose#SWIMMING}. Die Unterkante der Decke muss also unter seiner Hock- und über seiner
 * Krabbelhöhe liegen. Ein Block taugt dafür nicht: In Türen und an Treppen steht dort schon
 * einer, und auf halber Standhöhe passt nur eine sichtbare Stufe hinein. Der Client prüft aber
 * auch feste Entities, und ein Shulker ist eines davon ({@code Shulker#canBeCollidedWith}).
 *
 * <p>Frei setzen lässt er sich trotzdem nicht: {@code Shulker#setPos} rundet die Höhe auf ganze
 * Blöcke und zentriert x/z in der Blockmitte. Unter eine Blockgrenze kommt die Unterkante nur
 * über den Öffnungsgrad - nach {@link Direction#UP} angehängt wächst die Kollisionsbox nach
 * unten, und zwar um {@code 0.5 - sin((0.5 + peek) * PI) * 0.5}. Genau diese Kurve wird hier
 * umgekehrt. Ohne sie liegt die Decke immer auf einer Blockgrenze, und wer kleiner ist als ein
 * Block hoch, findet dort entweder nichts - er hockt - oder steckt in der Box fest.
 */
final class CrawlBox {
    /** Abstand zur Hockhöhe; der Client verkleinert seine Box vor der Prüfung um 1.0E-7. */
    private static final double FIT_MARGIN = 1.0E-3D;

    /**
     * Lage der Decke im Fenster zwischen Krabbel- und Hockhöhe. Nach oben kostet ein Fehler nur
     * eine Hockhaltung für ein paar Ticks, nach unten schlösse er den Spieler in der Box ein -
     * deshalb liegt sie im oberen Viertel und nicht mittig.
     */
    private static final double CEILING_BIAS = 0.75D;

    private final ServerPlayer receiver;
    private final Shulker box;
    private World world;
    private Placement placement;

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
        box.setAttachFace(Direction.UP);

        CrawlBox crawlBox = new CrawlBox(receiver, box, world);
        crawlBox.show(crawlBox.placementFor(location));
        return crawlBox;
    }

    void follow(Location destination) {
        World target = destination.getWorld();
        if (target == null) {
            return;
        }
        Placement next = placementFor(destination);
        if (target.equals(world) && next.equals(placement)) {
            return;
        }
        // Einen kleineren Öffnungsgrad fährt der Client mit 0.05 je Tick nach; die Unterkante
        // stünde solange zu tief und könnte den Spieler einschließen. Ein neu erzeugter Shulker
        // beginnt dagegen geschlossen und öffnet sich erst nach unten - diese Richtung ist
        // harmlos. Beim Weltwechsel vergisst der Client ohnehin alles, was er kannte.
        if (!target.equals(world) || next.peek() < placement.peek()) {
            hide();
            world = target;
            show(next);
            return;
        }
        apply(next);
        send(new ClientboundTeleportEntityPacket(box.getId(), PositionMoveRotation.of(box), Set.of(), false));
        // Der Client setzt den Öffnungsgrad selbst auf null, sobald der Shulker den Block
        // wechselt; die Daten müssen deshalb nach dem Teleport kommen, nicht davor.
        sendData();
    }

    void hide() {
        send(new ClientboundRemoveEntitiesPacket(box.getId()));
    }

    private void show(Placement next) {
        apply(next);
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
        sendData();
    }

    private void apply(Placement next) {
        // setPosRaw statt setPos: Das Runden und Zentrieren erledigt der Client, und setPos
        // würde serverseitig den Öffnungsgrad zurücksetzen.
        box.setPosRaw(next.x() + 0.5D, next.y(), next.z() + 0.5D);
        box.setRawPeekAmount(next.peek());
        placement = next;
    }

    /**
     * Die Unterkante liegt bei {@code blockY} minus der Öffnung. Fällt eine Blockgrenze in das
     * Fenster, bleibt der Shulker geschlossen - der Normalfall für einen ungeskalierten Spieler
     * auf ebenem Boden, der damit ohne jede Nachführung auskommt.
     */
    private Placement placementFor(Location location) {
        double feet = location.getY();
        double lowest = feet + receiver.getDimensions(Pose.SWIMMING).height();
        double highest = feet + receiver.getDimensions(Pose.CROUCHING).height() - FIT_MARGIN;
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();

        int blockY = (int) Math.floor(highest);
        if (blockY >= lowest) {
            return new Placement(blockX, blockY, blockZ, 0);
        }
        double ceiling = lowest + (highest - lowest) * CEILING_BIAS;
        return new Placement(blockX, blockY + 1, blockZ, peekFor(blockY + 1 - ceiling));
    }

    /** Umkehrung von {@code Shulker.getPhysicalPeek}. */
    private static int peekFor(double drop) {
        double opening = 0.5D - Math.asin(1.0D - 2.0D * Math.clamp(drop, 0.0D, 1.0D)) / Math.PI;
        return (int) Math.clamp(Math.round(opening * 100.0D), 0L, 100L);
    }

    private void sendData() {
        List<SynchedEntityData.DataValue<?>> data = box.getEntityData().getNonDefaultValues();
        if (data != null) {
            send(new ClientboundSetEntityDataPacket(box.getId(), data));
        }
    }

    private void send(Packet<?> packet) {
        receiver.connection.send(packet);
    }

    private record Placement(int x, int y, int z, int peek) {
    }
}
