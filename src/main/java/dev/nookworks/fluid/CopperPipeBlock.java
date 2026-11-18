package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/** Copper Pipe: a pipe for water and vegetable oil. See {@link FluidPipeBlock} and {@link CopperPipeBlockEntity}. */
public class CopperPipeBlock extends FluidPipeBlock {
    /** A pulling arm: a steel flange against the block it draws from, stepping down to a bolted band. */
    public static final ResourceLocation PULL_FLANGE = Nookworks.id("block/copper_pipe_pull");

    public CopperPipeBlock(Properties properties) {
        super(8.0F, properties); // drawn as thick as a Mercury Pipe
    }

    @Override
    protected BlockEntityType<? extends FluidPipeBlockEntity> pipeType() {
        return ModBlockEntities.COPPER_PIPE.get();
    }

    @Override
    protected Object family() {
        return CopperPipeBlockEntity.FAMILY;
    }

    @Override
    public ResourceLocation pullFlange() {
        return PULL_FLANGE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperPipeBlockEntity(pos, state);
    }

    // PORT: 1.21.1 asks every block for a codec (26.3 has none); only the block-list data report reads it
    @Override
    protected MapCodec<? extends CopperPipeBlock> codec() {
        return BlockCodecs.of(this);
    }
}
