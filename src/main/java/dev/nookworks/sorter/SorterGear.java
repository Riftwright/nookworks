package dev.nookworks.sorter;

import net.minecraft.util.StringRepresentable;

/**
 * How fast a sorter works, by the Sorter Gear fitted in it (see {@link SorterGearItem}); {@link #NONE} with none. Without
 * one it keeps a hopper's pace, which is what feeds it most of the time; a gear is for when something faster does - a
 * pipe of another mod, a warp, a machine emptying itself. The same gear goes in either sorter, so the speed and the
 * filtering are bought apart.
 */
public enum SorterGear implements StringRepresentable {
    NONE("none", 8, 1),
    GOLD("gold", 4, 1),
    DIAMOND("diamond", 2, 1),
    NETHERITE("netherite", 1, 4),
    /** A whole stack a tick. */
    VELARIUM("velarium", 1, 64);

    private final String name;
    /** Ticks between moves. */
    public final int interval;
    /** Items moved per move. */
    public final int batch;

    SorterGear(String name, int interval, int batch) {
        this.name = name;
        this.interval = interval;
        this.batch = batch;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
