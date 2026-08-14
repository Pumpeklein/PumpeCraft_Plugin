package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.utils.Locations;
import de.pumpecraft.utils.Texts;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class MovementChecks extends AbstractCheck {
    private static final long MOVEMENT_WINDOW_MILLIS = 500L;
    private static final long TELEPORT_GRACE_MILLIS = 2_000L;
    private static final long VELOCITY_GRACE_MILLIS = 1_500L;

    public MovementChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        state(event.getPlayer()).resetMovement(event.getPlayer().getLocation());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        grantTeleportGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        grantTeleportGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerState.Movement movement = state(event.getPlayer()).movement;
        movement.velocityGraceUntil = System.currentTimeMillis() + VELOCITY_GRACE_MILLIS;
        movement.airTicks = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            state(player).movement.fallDamageObserved = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || Locations.samePosition(event.getFrom(), to)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerState state = state(player);
        PlayerState.Movement movement = state.movement;
        long now = System.currentTimeMillis();
        if (movement.lastLocation == null || movement.lastLocation.getWorld() != to.getWorld()) {
            state.resetMovement(to);
            return;
        }

        double deltaY = to.getY() - event.getFrom().getY();
        movement.recentHorizontal = Locations.horizontalDistance(event.getFrom(), to);
        boolean grounded = isGrounded(to);

        if (movementExempt(player, to, now, state)) {
            state.resetMovement(to);
            return;
        }

        checkSpeed(event, state, now);
        checkNoFall(player, state, grounded, deltaY);

        if (grounded) {
            movement.lastGround = to.clone();
        }
        movement.wasOnGround = grounded;
        movement.lastLocation = to.clone();
        movement.lastNanos = System.nanoTime();
    }

    public void tickFlyChecks() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerState state = state(player);
            PlayerState.Movement movement = state.movement;
            Location current = player.getLocation();
            if (!violations.enabled(CheckType.FLY) || movementExempt(player, current, now, state)) {
                resetFlySample(movement, current, false);
                continue;
            }

            if (isGrounded(current)) {
                resetFlySample(movement, current, true);
                violations.reward(player, CheckType.FLY, 0.05);
                continue;
            }

            Location previous = movement.lastFlySample;
            if (previous == null || previous.getWorld() != current.getWorld()) {
                resetFlySample(movement, current, false);
                continue;
            }

            double deltaY = current.getY() - previous.getY();
            if (deltaY > -0.08) {
                movement.airTicks++;
            } else {
                movement.airTicks = Math.max(0, movement.airTicks - 3);
            }
            movement.lastFlySample = current.clone();

            int maximum = (int) Math.round(
                settings.platformDecimal(player, CheckType.FLY, "max-air-ticks", 24.0)
            );
            if (movement.airTicks <= maximum
                || (movement.airTicks != maximum + 1 && (movement.airTicks - maximum) % 10 != 0)) {
                continue;
            }

            double level = violations.flag(
                player,
                CheckType.FLY,
                1.0,
                movement.airTicks + " Ticks ohne normale Fallbewegung"
            );
            if (violations.shouldCancel(player, CheckType.FLY, level)
                && movement.lastGround != null
                && movement.lastGround.getWorld() == player.getWorld()) {
                player.teleport(movement.lastGround.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                grantTeleportGrace(player);
            }
        }
    }

    private void checkSpeed(PlayerMoveEvent event, PlayerState state, long now) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.SPEED)) {
            return;
        }

        PlayerState.Movement movement = state.movement;
        double horizontal = Locations.horizontalDistance(event.getFrom(), event.getTo());
        if (horizontal > 3.0) {
            state.resetMovement(event.getTo());
            return;
        }
        movement.windowDistance += horizontal;
        long elapsed = now - movement.windowStarted;
        if (elapsed < MOVEMENT_WINDOW_MILLIS) {
            return;
        }

        double blocksPerSecond = movement.windowDistance * 1_000.0 / elapsed;
        double maximum = settings.platformDecimal(player, CheckType.SPEED, "max-blocks-per-second", 8.2);
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            maximum *= 1.0 + 0.20 * (speed.getAmplifier() + 1);
        }
        maximum += Math.min(1.5, player.getPing() / 300.0);

        if (blocksPerSecond > maximum) {
            double level = violations.flag(
                player,
                CheckType.SPEED,
                Math.min(2.0, (blocksPerSecond - maximum) / 2.0),
                Texts.decimal(blocksPerSecond) + " Blöcke/s > " + Texts.decimal(maximum)
            );
            if (violations.shouldCancel(player, CheckType.SPEED, level)) {
                event.setTo(event.getFrom());
                state.resetMovement(event.getFrom());
            }
        } else {
            violations.reward(player, CheckType.SPEED, 0.15);
        }
        movement.windowDistance = 0.0;
        movement.windowStarted = now;
    }

    private void checkNoFall(Player player, PlayerState state, boolean grounded, double deltaY) {
        PlayerState.Movement movement = state.movement;
        if (deltaY < 0.0) {
            movement.accumulatedFall += -deltaY;
        }
        if (!grounded || movement.wasOnGround) {
            return;
        }

        double fallDistance = movement.accumulatedFall;
        movement.accumulatedFall = 0.0;
        if (!violations.enabled(CheckType.NO_FALL) || hasNoFallExemption(player)) {
            return;
        }

        double minimum = settings.platformDecimal(player, CheckType.NO_FALL, "minimum-fall", 4.0);
        if (fallDistance < minimum) {
            return;
        }

        movement.fallDamageObserved = false;
        long sequence = ++movement.landingSequence;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerState current = states.find(player.getUniqueId());
            if (current == null
                || current.movement.landingSequence != sequence
                || current.movement.fallDamageObserved) {
                return;
            }
            violations.flag(
                player,
                CheckType.NO_FALL,
                1.0,
                "kein Fallschaden nach " + Texts.decimal(fallDistance) + " Blöcken"
            );
        }, 3L);
    }

    private void grantTeleportGrace(Player player) {
        PlayerState state = state(player);
        state.resetMovement(player.getLocation());
        state.movement.teleportGraceUntil = System.currentTimeMillis() + TELEPORT_GRACE_MILLIS;
    }

    private void resetFlySample(PlayerState.Movement movement, Location location, boolean grounded) {
        movement.airTicks = 0;
        movement.lastFlySample = location.clone();
        if (grounded) {
            movement.lastGround = location.clone();
        }
    }

    private boolean movementExempt(Player player, Location location, long now, PlayerState state) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE
            || mode == GameMode.SPECTATOR
            || player.hasPermission("pumpecraft.anticheat.bypass")
            || player.isFlying()
            || player.isGliding()
            || player.isSwimming()
            || player.isRiptiding()
            || player.isInsideVehicle()
            || player.getPotionEffect(PotionEffectType.LEVITATION) != null
            || player.getPotionEffect(PotionEffectType.SLOW_FALLING) != null
            || now < state.movement.teleportGraceUntil
            || now < state.movement.velocityGraceUntil
            || isLiquidOrClimbable(location);
    }

    private boolean hasNoFallExemption(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0.0, 0.2, 0.0).getBlock().getType();
        return player.isGliding()
            || player.isSwimming()
            || player.isInsideVehicle()
            || feet == Material.COBWEB
            || below == Material.SLIME_BLOCK
            || below == Material.HONEY_BLOCK
            || below == Material.HAY_BLOCK
            || below == Material.POWDER_SNOW
            || below == Material.WATER;
    }

    private boolean isGrounded(Location location) {
        double[] offsets = {-0.29, 0.29};
        for (double x : offsets) {
            for (double z : offsets) {
                Block block = location.clone().add(x, -0.08, z).getBlock();
                if (block.getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLiquidOrClimbable(Location location) {
        Material feet = location.getBlock().getType();
        Material head = location.clone().add(0.0, 1.0, 0.0).getBlock().getType();
        return feet == Material.WATER
            || feet == Material.LAVA
            || feet == Material.LADDER
            || feet == Material.VINE
            || feet == Material.SCAFFOLDING
            || feet == Material.TWISTING_VINES
            || feet == Material.WEEPING_VINES
            || head == Material.WATER;
    }
}
