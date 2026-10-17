package dev.nookworks.spawner;

import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A spawner holding itself off for a controller out of reach ({@link ControlledSpawner#nookworks$holdOff}): when to let
 * go. While its controller cannot be asked - in a chunk not loaded - it holds; it lets go once that controller is found
 * switched on, gone, or reaching it no more, or as soon as no Spawner Link Sender touches the spawner (which anyone can
 * see to from the spawner's side).
 */
public final class SpawnerHold {
    /** How often a held spawner looks whether it still should be: every second. */
    public static final int LOOK_EVERY = 20;

    /** Whether the spawner at {@code pos}, held off by the controller at {@code by}, is held off still. */
    public static boolean stillHeld(ServerLevel level, BlockPos pos, GlobalPos by) {
        boolean sender = false;
        for (Direction side : Direction.values()) {
            sender |= level.getBlockState(pos.relative(side)).is(ModBlocks.SPAWNER_LINK_SENDER.get());
        }
        if (!sender || !by.dimension().equals(level.dimension())) {
            return false; // a link works within one dimension: a hold from another is none
        }
        if (!level.isLoaded(by.pos())) {
            return true;
        }
        return level.getBlockEntity(by.pos()) instanceof SpawnerControllerBlockEntity controller && controller.holdsOff(level, pos);
    }

    /** Let go: the spawner's block entity saved without the hold. */
    public static void released(ServerLevel level, BlockPos pos) {
        BlockEntity spawner = level.getBlockEntity(pos);
        if (spawner != null) {
            changed(level, spawner);
        }
    }

    /**
     * Held off or let go: the spawner saved as it now is and sent to the players near it, whose spawner stops turning
     * and smoking while it is held off ({@link ControlledSpawner#nookworks$shownOff}).
     */
    public static void changed(Level level, BlockEntity spawner) {
        spawner.setChanged();
        level.sendBlockUpdated(spawner.getBlockPos(), spawner.getBlockState(), spawner.getBlockState(), Block.UPDATE_CLIENTS);
    }

    private SpawnerHold() {}
}
