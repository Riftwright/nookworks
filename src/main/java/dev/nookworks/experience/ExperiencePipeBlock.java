package dev.nookworks.experience;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.FluidPipeBlock;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Experience Pipe. See {@link FluidPipeBlock} and {@link ExperiencePipeBlockEntity}. */
public class ExperiencePipeBlock extends FluidPipeBlock {
    /** Whether the glass shows the green of the experience in it: clear glass while the pipe is empty. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    /** A pulling arm: a steel flange against the block it draws from, stepping down to a bolted band. */
    public static final Identifier PULL_FLANGE = Nookworks.id("block/experience_pipe_pull");

    public ExperiencePipeBlock(Properties properties) {
        super(6.0F, properties); // its arms are drawn six wide
        this.registerDefaultState(this.defaultBlockState().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILLED);
    }

    @Override
    protected BlockEntityType<? extends FluidPipeBlockEntity> pipeType() {
        return ModBlockEntities.EXPERIENCE_PIPE.get();
    }

    @Override
    public Identifier pullFlange() {
        return PULL_FLANGE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExperiencePipeBlockEntity(pos, state);
    }
}
