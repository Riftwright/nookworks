package dev.nookworks.machine;

import java.util.function.IntSupplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The sight glass in a tank's side: how full the tank is, in the six pixels its window stands tall. The block's
 * LEVEL state holds it (0 empty, 1 the first drop in, 6 full or nearly), so the model shows the liquid standing that
 * high in the glass without anything drawn each frame - and a tank across the room says how full it is.
 */
public final class SightGlass {
    public static final int STEPS = 6;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, STEPS);

    /** Setting the level changes only how the tank looks: no neighbour, shape or placement update goes out. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private SightGlass() {}

    /** The step for an amount: empty only when nothing is in, full only when it all but is, a step up otherwise. */
    public static int level(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(STEPS, (int) Math.ceil(amount * (double) STEPS / capacity)));
    }

    /** Brings the tank's LEVEL to its contents, on the server, when it differs. */
    public static void show(BlockEntity tank, int amount, int capacity) {
        Level level = tank.getLevel();
        if (level == null || level.isClientSide() || tank.isRemoved()) {
            return;
        }
        show(tank, LEVEL, level(amount, capacity));
    }

    /**
     * Brings any such state of the block a block entity stands in - how full it looks - to {@code step}, on the
     * server, when it differs.
     */
    public static void show(BlockEntity entity, IntegerProperty property, int step) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide() || entity.isRemoved()) {
            return;
        }
        BlockState state = entity.getBlockState();
        if (state.hasProperty(property) && state.getValue(property) != step && level.getBlockState(entity.getBlockPos()) == state) {
            level.setBlock(entity.getBlockPos(), state.setValue(property, step), FLAGS);
        }
    }

    /**
     * A tank loaded from a world saved before it had a sight glass shows empty however full it is: set it right once
     * the chunk is in (not while it loads - a block is not set in a chunk that is still loading).
     */
    public static void showOnLoad(BlockEntity tank, IntSupplier amount, int capacity) {
        if (tank.getLevel() instanceof ServerLevel server) {
            server.getServer().execute(() -> show(tank, amount.getAsInt(), capacity));
        }
    }
}
