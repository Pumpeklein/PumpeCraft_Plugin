package de.pumpecraft.utils.objects;

/**
 * Hinge of a movable part, given in blocks relative to the display entity origin.
 *
 * <p>Use {@link #fromModel} to take the values straight out of the model file: a model coordinate
 * {@code m} sits at {@code (m - 8) / 16} blocks, ground level is model height 0.
 */
public record ObjectHinge(String part, float x, float y, float z) {
    public static ObjectHinge fromModel(String part, double modelX, double modelY, double modelZ) {
        return new ObjectHinge(
            part,
            (float) ((modelX - 8.0D) / 16.0D),
            (float) ((modelY - 8.0D) / 16.0D),
            (float) ((modelZ - 8.0D) / 16.0D)
        );
    }
}
