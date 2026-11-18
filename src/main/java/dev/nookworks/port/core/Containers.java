package dev.nookworks.port.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code net.minecraft.world.Containers} on 1.21.1: the same drops, plus {@link #updateNeighboursAfterDestroy},
 * which 1.21.1 folds into {@code dropContentsOnDestroy}. The mod's sources keep calling {@code Containers.x(...)}.
 */
public final class Containers {
    private Containers() {}

    public static void dropContents(Level level, BlockPos pos, Container container) {
        net.minecraft.world.Containers.dropContents(level, pos, container);
    }

    public static void dropContents(Level level, Entity entity, Container container) {
        net.minecraft.world.Containers.dropContents(level, entity, container);
    }

    public static void dropContents(Level level, BlockPos pos, NonNullList<ItemStack> list) {
        net.minecraft.world.Containers.dropContents(level, pos, list);
    }

    public static void dropItemStack(Level level, double x, double y, double z, ItemStack stack) {
        net.minecraft.world.Containers.dropItemStack(level, x, y, z, stack);
    }

    /** 26.3: a comparator reading the block is told it went (called from affectNeighborsAfterRemoval). */
    public static void updateNeighboursAfterDestroy(BlockState state, Level level, BlockPos pos) {
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }

    /** 1.21.1's own helper, kept reachable under the stand-in's name. */
    public static void dropContentsOnDestroy(BlockState state, BlockState newState, Level level, BlockPos pos) {
        net.minecraft.world.Containers.dropContentsOnDestroy(state, newState, level, pos);
    }
}
