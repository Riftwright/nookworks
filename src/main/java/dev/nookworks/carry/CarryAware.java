package dev.nookworks.carry;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * For a block entity that wants a word in being carried ({@link Carrying}). Every method is optional; a block entity
 * that does not implement this is carried all the same, its data kept exactly.
 */
public interface CarryAware {
    /**
     * Called at take, after the block's data is saved into the value and before the block is removed: stop work that
     * holds things outside the block (chunk tickets, a quarry's frame). What is changed here is not in the value: the
     * block comes back as it was saved.
     */
    default void beforeCarried(ServerLevel level) {}

    /**
     * Called at put-back, once the data is loaded and before neighbours are told: turn anything kept in the data that
     * has a direction (a side configuration) by {@code rotation} - the block's state is already turned.
     */
    default void afterPut(ServerLevel level, Rotation rotation) {}

    /**
     * Tooltip lines saying how full it is, for a carried block shown as an item ({@link CarriedBlock#summary}): called
     * on a block entity made from the saved data, out of the world (no level). Without it, the stacks in its saved items
     * are counted.
     */
    default void summary(Consumer<Component> lines) {}
}
