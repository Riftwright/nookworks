package dev.nookworks.port.core;

/**
 * 26.3's {@code net.minecraft.util.Prediction}: whether the client already showed an action (an arm swing) itself.
 * 1.21.1's {@code Player.drop} and {@code Inventory.placeItemBackInInventory} take no such argument (the client swings
 * on its own there too), so tx/core.py drops it from those calls; this enum is only here for a leftover reference.
 */
public enum Prediction {
    PREDICTED,
    SERVER_ONLY
}
