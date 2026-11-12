package dev.nookworks.decor;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What lies between an eye and a Standing Mirror's glass. Asked by the client when it draws the glass under a shader
 * pack (dev.nookworks.client.render.MirrorReflections); kept apart from the client so that a test can ask it too.
 */
public final class MirrorSight {
    /** The glass's face, half a pixel in front of the block's middle (as the model has it). */
    public static final float GLASS_OUT = 0.5F / 16;
    /** Where the glass is looked at: across it and up it from its middle, within its edges. */
    private static final double[] ACROSS = {-0.28, 0.0, 0.28};
    private static final double[] UP = {-0.75, -0.375, 0.0, 0.375, 0.75};

    private MirrorSight() {}

    /** Where the glass's face is: the plane it reflects in, through its middle. */
    public static Vec3 glassCentre(BlockPos lower, Direction facing) {
        return Vec3.atLowerCornerOf(lower).add(0.5 + facing.getStepX() * GLASS_OUT, 1.0, 0.5 + facing.getStepZ() * GLASS_OUT);
    }

    /**
     * Whether something see-through - a fluid, or a block {@code seeThrough} says is drawn so (stained glass, ice, a
     * window) - lies between the eye and the glass of the mirror whose lower half is at {@code lower}.
     *
     * <p>It matters under a shader pack. There the glass is painted over the pack's finished picture, and left out
     * where the world's depth says something stands before it; water and glass are in that depth too, so behind them
     * the glass was not painted, and what the pack had drawn beneath showed through them instead - the board its light
     * is measured on, dark with a pale stripe up it (the user, 2026-11-12, of a mirror seen from under water:
     * 「壊れちゃうね」; a mirror behind a window did the same). A mirror so veiled is not drawn at all: its silver
     * shows, lit and tinted by the pack as anything behind water or glass is. A solid thing before the glass needs none
     * of this - the depth hides the glass there, as it should - so the first thing on each line of sight decides.
     */
    public static boolean veiled(BlockGetter level, BlockPos lower, Direction facing, Vec3 eye, Predicate<BlockState> seeThrough) {
        Vec3 centre = glassCentre(lower, facing);
        Vec3 out = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 across = new Vec3(-out.z, 0, out.x);
        BlockPos upper = lower.above();
        for (double u : ACROSS) {
            for (double v : UP) {
                Vec3 to = centre.add(across.scale(u)).add(0, v, 0).add(out.scale(0.03));
                boolean through = BlockGetter.traverseBlocks(eye, to, level, (world, pos) -> {
                    if (pos.equals(lower) || pos.equals(upper)) {
                        return null; // the mirror's own frame
                    }
                    FluidState fluid = world.getFluidState(pos);
                    if (!fluid.isEmpty() && fluid.getShape(world, pos).clip(eye, to, pos) != null) {
                        return Boolean.TRUE;
                    }
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        return null;
                    }
                    VoxelShape shape = state.getShape(world, pos);
                    if (shape.isEmpty() || shape.clip(eye, to, pos) == null) {
                        return null; // the line passes it by
                    }
                    return seeThrough.test(state);
                }, world -> Boolean.FALSE);
                if (through) {
                    return true;
                }
            }
        }
        return false;
    }
}
