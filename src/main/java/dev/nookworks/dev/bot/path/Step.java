package dev.nookworks.dev.bot.path;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One step of a path: to the cell {@code to} (where the feet will be), standing at height {@code standY} there.
 *
 * @param breaks blocks broken before the step is taken, in order (a tunnel's cells, the head room of a jump)
 * @param place where a block is set down for the step (a bridge's floor, a pillar's block under the feet), or null
 * @param against the block clicked to set it down, and {@code face} the face of it clicked
 * @param cost the step's cost, in ticks as near as the planner can tell
 */
public record Step(BlockPos to, Move move, double standY, List<BlockPos> breaks, @Nullable BlockPos place,
        @Nullable BlockPos against, @Nullable Direction face, double cost) {

    public boolean placesBlock() {
        return this.place != null;
    }

    @Override
    public String toString() {
        return this.move + " " + this.to.toShortString() + (this.breaks.isEmpty() ? "" : " break" + this.breaks.size())
                + (this.place == null ? "" : " place " + this.place.toShortString());
    }
}
