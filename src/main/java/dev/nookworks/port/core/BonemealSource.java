package dev.nookworks.port.core;

/**
 * 26.3's {@code net.minecraft.world.level.block.BonemealSource}: who applies bone meal. 1.21.1's
 * {@code BonemealableBlock} has no such parameter (only a player's or a dispenser's bone meal exists there, which 26.3
 * calls {@link #INTERACTION}); tx/core.py drops it from the overrides and the calls, and a body that still reads it
 * gets {@code BonemealSource source = BonemealSource.INTERACTION}.
 */
public enum BonemealSource {
    INTERACTION,
    MOB
}
