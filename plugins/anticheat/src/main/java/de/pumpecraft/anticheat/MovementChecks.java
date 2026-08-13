package de.pumpecraft.anticheat;

import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class MovementChecks implements Listener {
    private static final long MOVEMENT_WINDOW_MILLIS = 500L;
    private static final long TELEPORT_GRACE_MILLIS = 2_000L;
    private static final long VELOCITY_GRACE_MILLIS = 1_500L;

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final BedrockDetector bedrockDetector;

    MovementChecks(
        PumpeAntiCheatPlugin plugin,
        PlayerStateStore states,
        ViolationService violations,
        BedrockDetector bedrockDetector
    ) {
        this.plugin = plugin;
        this.states = states;
        this.violations = violations;
        this.bedrockDetector = bedrockDetector;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        states.get(event.getPlayer()).resetMovement(event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
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
        PlayerState state = states.get(event.getPlayer());
        state.velocityGraceUntil = System.currentTimeMillis() + VELOCITY_GRACE_MILLIS;
        state.airTicks = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            states.get(player).fallDamageObserved = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || samePosition(event.getFrom(), to)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerState state = states.get(player);
        long now = System.currentTimeMillis();
        if (state.lastMovementLocation == null
            || state.lastMovementLocation.getWorld() != to.getWorld()) {
            state.resetMovement(to);
            return;
        }

        double deltaY = to.getY() - event.getFrom().getY();
        state.recentHorizontalMovement = horizontalDistance(event.getFrom(), to);
        boolean grounded = isGrounded(player, to);
        boolean exempt = movementExempt(player, to, now, state);

        if (exempt) {
            state.resetMovement(to);
            return;
        }

        checkSpeed(event, state, now);
        checkFly(event, state, grounded, deltaY);
        checkNoFall(player, state, grounded, deltaY);

        state.wasOnGround = grounded;
        state.lastMovementLocation = to.clone();
        state.lastMovementNanos = System.nanoTime();
    }

    private void checkSpeed(PlayerMoveEvent event, PlayerState state, long now) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.SPEED)) {
            return;
        }

        double horizontal = horizontalDistance(event.getFrom(), event.getTo());
        if (horizontal > 3.0) {
            state.resetMovement(event.getTo());
            return;
        }
        state.movementWindowDistance += horizontal;
        long elapsed = now - state.movementWindowStarted;
        if (elapsed < MOVEMENT_WINDOW_MILLIS) {
            return;
        }

        double blocksPerSecond = state.movementWindowDistance * 1_000.0 / elapsed;
        double maximum = threshold("speed.max-blocks-per-second", player);
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
                format(blocksPerSecond) + " Blöcke/s > " + format(maximum)
            );
            if (violations.shouldCancel(player, CheckType.SPEED, level)) {
                event.setTo(event.getFrom());
                state.resetMovement(event.getFrom());
            }
        } else {
            violations.reward(player, CheckType.SPEED, 0.15);
        }
        state.movementWindowDistance = 0.0;
        state.movementWindowStarted = now;
    }

    private void checkFly(
        PlayerMoveEvent event,
        PlayerState state,
        boolean grounded,
        double deltaY
    ) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.FLY)) {
            return;
        }

        if (grounded || deltaY < -0.08 || player.getFallDistance() > 0.5f) {
            state.airTicks = 0;
            violations.reward(player, CheckType.FLY, 0.1);
            return;
        }

        state.airTicks++;
        int maximum = (int) Math.round(threshold("fly.max-air-ticks", player));
        if (state.airTicks > maximum) {
            double level = violations.flag(
                player,
                CheckType.FLY,
                1.0,
                state.airTicks + " Luftbewegungen ohne Fallphase"
            );
            if (violations.shouldCancel(player, CheckType.FLY, level)) {
                event.setTo(event.getFrom());
                state.airTicks = maximum;
            }
        }
    }

    private void checkNoFall(Player player, PlayerState state, boolean grounded, double deltaY) {
        if (deltaY < 0.0) {
            state.accumulatedFall += -deltaY;
        }
        if (!grounded || state.wasOnGround) {
            return;
        }

        double fallDistance = state.accumulatedFall;
        state.accumulatedFall = 0.0;
        if (!violations.enabled(CheckType.NO_FALL)
            || hasNoFallExemption(player)) {
            return;
        }

        double minimum = threshold("nofall.minimum-fall", player);
        if (fallDistance < minimum) {
            return;
        }

        state.fallDamageObserved = false;
        long sequence = ++state.landingSequence;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerState current = states.find(player.getUniqueId());
            if (current == null || current.landingSequence != sequence || current.fallDamageObserved) {
                return;
            }
            violations.flag(
                player,
                CheckType.NO_FALL,
                1.0,
                "kein Fallschaden nach " + format(fallDistance) + " Blöcken"
            );
        }, 3L);
    }

    private void grantTeleportGrace(Player player) {
        PlayerState state = states.get(player);
        state.resetMovement(player.getLocation());
        state.teleportGraceUntil = System.currentTimeMillis() + TELEPORT_GRACE_MILLIS;
    }

    private boolean movementExempt(Player player, Location location, long now, PlayerState state) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE
            || mode == GameMode.SPECTATOR
            || player.getAllowFlight()
            || player.isFlying()
            || player.isGliding()
            || player.isSwimming()
            || player.isRiptiding()
            || player.isInsideVehicle()
            || player.getPotionEffect(PotionEffectType.LEVITATION) != null
            || player.getPotionEffect(PotionEffectType.SLOW_FALLING) != null
            || now < state.teleportGraceUntil
            || now < state.velocityGraceUntil
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

    private boolean isGrounded(Player player, Location location) {
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

    private double threshold(String path, Player player) {
        String suffix = bedrockDetector.isBedrock(player.getUniqueId()) ? "-bedrock" : "-java";
        return plugin.getConfig().getDouble("checks." + path + suffix);
    }

    private boolean samePosition(Location from, Location to) {
        return from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ();
    }

    private double horizontalDistance(Location from, Location to) {
        double x = to.getX() - from.getX();
        double z = to.getZ() - from.getZ();
        return Math.sqrt(x * x + z * z);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
