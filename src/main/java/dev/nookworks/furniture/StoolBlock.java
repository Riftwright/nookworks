package dev.nookworks.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden stool: a chair with no back, and lower (the user, 2026-10-11: "背の低い、背もたれのない椅子とか欲しい"). Its
 * seat's top is at 7 pixels, the chair's at 9, and the rider sits as much lower; sat on and got off as a chair is.
 * It still turns to face whoever sets it down, for the grain of its seat and the way its rider gets up.
 */
public class StoolBlock extends ChairBlock {
    /** The seat's top, in pixels. */
    public static final int SEAT_TOP = 7;
    /** Where the rider sits: as far under the seat's top as on a chair. */
    public static final double SEAT_HEIGHT = SeatEntity.SEAT_HEIGHT - (CHAIR_SEAT_TOP - SEAT_TOP) / 16.0;

    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, SEAT_TOP, 13.0);

    public StoolBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public double seatHeight() {
        return SEAT_HEIGHT;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
