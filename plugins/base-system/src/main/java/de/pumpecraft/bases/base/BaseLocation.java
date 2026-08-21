package de.pumpecraft.bases.base;

import java.util.UUID;

public record BaseLocation(
    UUID worldId,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
}
