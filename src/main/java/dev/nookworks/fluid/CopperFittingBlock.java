package dev.nookworks.fluid;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A fitting set in a Copper Pipe line - the Sight Glass, the Valve. It is one of the line's pipes (it shares the
 * line's reach and passes fluid on as a pipe does) but it lies along one axis, as a length of pipe does, and joins only
 * at its two ends: placed against the end of a pipe, it lines up with it, as a log lines up with the face it is set on.
 */
public abstract class CopperFittingBlock extends FluidPipeBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** The body round the pipe, 12 across, and the pipe's own 8 px running out of both ends. */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Map.of(
            Direction.Axis.X, Shapes.or(Block.box(2, 2, 2, 14, 14, 14), Block.box(0, 4, 4, 16, 12, 12)),
            Direction.Axis.Y, Shapes.or(Block.box(2, 2, 2, 14, 14, 14), Block.box(4, 0, 4, 12, 16, 12)),
            Direction.Axis.Z, Shapes.or(Block.box(2, 2, 2, 14, 14, 14), Block.box(4, 4, 0, 12, 12, 16)));

    protected CopperFittingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Override
    protected Object family() {
        return CopperPipeBlockEntity.FAMILY;
    }

    /**
     * No sides switched off: a fitting joins only at its two ends, and is shut, if at all, by being a valve. Their six
     * flags would multiply its states by 64 for nothing - the Sight Glass alone had 430,080, nine tenths of the game's,
     * every one built at each start.
     */
    @Override
    protected boolean switchableSides() {
        return false;
    }

    @Override
    protected BlockState placementBase(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected boolean joinsSide(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = super.rotate(state, rotation);
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            turned = turned.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return turned;
    }
}
