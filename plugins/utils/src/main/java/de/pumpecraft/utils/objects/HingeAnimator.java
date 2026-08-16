package de.pumpecraft.utils.objects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns a movable part around its hinge. Parts stand at the same position as the body, so the
 * rotation is expressed as {@code p' = pivot + rotation * (p - pivot)}: the rotation goes into the
 * transformation, the remaining offset {@code pivot - rotation * pivot} into its translation.
 *
 * <p>One instance per plugin - it owns the scheduled tasks and has to be shut down in
 * {@code onDisable} through {@link #cancelAll()}.
 */
public final class HingeAnimator {
    private static final float EPSILON = 0.01F;

    private final Plugin plugin;
    private final Map<UUID, BukkitTask> movements = new HashMap<>();
    private final Map<UUID, BukkitTask> delayed = new HashMap<>();

    public HingeAnimator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param ticks number of steps; 1 or less jumps straight to the end position
     * @return whether the part had to move at all
     */
    public boolean rotate(DisplayObject object, ObjectHinge hinge, float degrees, int ticks) {
        Display display = object.part(hinge.part());
        if (display == null || !display.isValid()) {
            return false;
        }

        stop(movements, display.getUniqueId());
        float start = angle(object, hinge);
        if (Math.abs(degrees - start) < EPSILON) {
            return false;
        }

        if (ticks <= 1) {
            apply(display, degrees, hinge);
            return true;
        }

        UUID id = display.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                step++;
                if (!display.isValid()) {
                    movements.remove(id);
                    cancel();
                    return;
                }

                apply(display, start + (degrees - start) * step / ticks, hinge);
                if (step >= ticks) {
                    movements.remove(id);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        movements.put(id, task);
        return true;
    }

    public float angle(DisplayObject object, ObjectHinge hinge) {
        Display display = object.part(hinge.part());
        if (display == null) {
            return 0.0F;
        }
        Vector3f angles = display.getTransformation().getLeftRotation().getEulerAnglesXYZ(new Vector3f());
        return (float) Math.toDegrees(angles.x);
    }

    /**
     * Runs an action later, for example to close a lid again. A second call for the same part
     * replaces the pending action.
     */
    public void delay(DisplayObject object, ObjectHinge hinge, int ticks, Runnable action) {
        Display display = object.part(hinge.part());
        if (display == null) {
            return;
        }

        UUID id = display.getUniqueId();
        stop(delayed, id);
        delayed.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            delayed.remove(id);
            if (display.isValid()) {
                action.run();
            }
        }, Math.max(1, ticks)));
    }

    public void cancel(DisplayObject object, ObjectHinge hinge) {
        Display display = object.part(hinge.part());
        if (display != null) {
            stop(delayed, display.getUniqueId());
        }
    }

    public void cancelAll() {
        movements.values().forEach(BukkitTask::cancel);
        movements.clear();
        delayed.values().forEach(BukkitTask::cancel);
        delayed.clear();
    }

    private void apply(Display display, float degrees, ObjectHinge hinge) {
        Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(degrees));
        // An item display with transform FIXED renders the model turned by 180 degrees around Y and
        // the transformation applies on top of that, so X and Z of a model space hinge are mirrored
        // here. Without the mirror the hinge travels instead of staying put and the part swings off.
        Vector3f pivot = new Vector3f(-hinge.x(), hinge.y(), -hinge.z());
        Vector3f translation = new Vector3f(pivot).sub(rotation.transform(new Vector3f(pivot)));

        // The client interpolates rotation and translation separately, so a large step would spin
        // the part around the entity origin instead of swinging it on the hinge. Each server tick
        // therefore moves a small piece that the client only has to smooth over a single tick.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTransformation(new Transformation(
            translation, rotation, new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
    }

    private void stop(Map<UUID, BukkitTask> tasks, UUID id) {
        BukkitTask task = tasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }
}
