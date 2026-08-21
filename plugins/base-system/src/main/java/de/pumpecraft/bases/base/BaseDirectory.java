package de.pumpecraft.bases.base;

import java.util.List;

/**
 * Namensliste für die Tab-Vervollständigung. Sie wird im Hintergrund aufgefrischt, damit ein
 * Tastendruck im Chat nie auf die Datenbank warten muss.
 */
public final class BaseDirectory {
    private volatile List<String> ownerNames = List.of();

    public List<String> ownerNames() {
        return ownerNames;
    }

    void update(List<String> names) {
        ownerNames = names;
    }
}
