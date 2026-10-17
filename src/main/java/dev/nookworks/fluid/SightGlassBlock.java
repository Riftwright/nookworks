package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Sight Glass (計量窓): a brass-framed glass chamber set in a Copper Pipe line, a length of the line itself. What stands
 * in it - water, oil, biodiesel - and how high, in six steps of its bucket, show through the glass, so a line can be
 * read at a glance: full and backed up, running, or dry. A comparator beside it reads the same.
 */
public class SightGlassBlock extends CopperFittingBlock {
    public SightGlassBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LineContent.PROPERTY, LineContent.EMPTY).setValue(SightGlass.LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LineContent.PROPERTY, SightGlass.LEVEL);
    }

    @Override
    protected BlockEntityType<? extends FluidPipeBlockEntity> pipeType() {
        return ModBlockEntities.SIGHT_GLASS.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SightGlassBlockEntity(pos, state);
    }

    /** Comparators reading it through a block hear that it is gone, as with vanilla's containers. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        // what the glass shows, in the comparator's 15 steps
        return state.getValue(SightGlass.LEVEL) * 15 / SightGlass.STEPS;
    }
}
