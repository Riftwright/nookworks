package dev.nookworks.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rice bale (kome-dawara): nine white rice packed in a sack of woven rice straw, lids of straw at both ends and straw
 * rope bound round it, the way rice was stored and paid. It lies on its side, as hay does - along the way it is set
 * against a block, or the way the player faces when set on the ground - its corners taken off so that it reads round
 * and bales stack into the piles of a storehouse. Straw breaks a fall as hay does.
 */
public class RiceBaleBlock extends RotatedPillarBlock {
    // Its section, as the model builds it: an octagon, the corners stepped in twice.
    private static final VoxelShape ALONG_Z = Shapes.or(Block.box(2, 0, 0, 14, 16, 16), Block.box(0, 2, 0, 16, 14, 16),
            Block.box(1, 1, 0, 15, 15, 16));
    private static final VoxelShape ALONG_X = Shapes.or(Block.box(0, 0, 2, 16, 16, 14), Block.box(0, 2, 0, 16, 14, 16),
            Block.box(0, 1, 1, 16, 15, 15));
    private static final VoxelShape ALONG_Y = Shapes.or(Block.box(2, 0, 0, 14, 16, 16), Block.box(0, 0, 2, 16, 16, 14),
            Block.box(1, 0, 1, 15, 16, 15));

    public RiceBaleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis();
        if (axis == Direction.Axis.Y) {
            axis = context.getHorizontalDirection().getAxis();
        }
        return this.defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> ALONG_X;
            case Y -> ALONG_Y;
            case Z -> ALONG_Z;
        };
    }

    /** A pile of bales is walked over as one: the corners left between them do not trip anyone. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall());
    }
}
