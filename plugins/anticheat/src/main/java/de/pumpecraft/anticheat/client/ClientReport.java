package de.pumpecraft.anticheat.client;

import java.util.List;

public record ClientReport(
    boolean bedrock,
    String brand,
    String loader,
    String client,
    List<String> mods,
    List<String> channels
) {
}
