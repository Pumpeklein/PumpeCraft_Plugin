package de.pumpecraft.utils.clan;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public interface ClanDisplayService {
    Optional<Component> badge(UUID playerId);

    /** The clan a player belongs to - the only way for other plugins to compare two players. */
    default OptionalLong clanId(UUID playerId) {
        return OptionalLong.empty();
    }
}
