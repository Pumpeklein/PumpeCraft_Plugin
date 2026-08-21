package de.pumpecraft.utils.clan;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public interface ClanDisplayService {
    Optional<Component> badge(UUID playerId);
}
