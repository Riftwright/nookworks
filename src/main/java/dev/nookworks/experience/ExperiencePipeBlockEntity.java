package dev.nookworks.experience;

import java.util.List;

import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Experience Pipe: a short length of glass that carries Liquid Experience one step at a time. See
 * {@link FluidPipeBlockEntity} for how it moves.
 */
public class ExperiencePipeBlockEntity extends FluidPipeBlockEntity {
    public static final int BUFFER = 400;
    /**
     * How long the glass goes on showing green after the pipe last held any experience: it moves along a line a step at
     * a time, so a pipe in use holds some only now and then, and its glass must not flicker (nor send a block update
     * every tick).
     */
    public static final int SHOW_FOR = 20;
    private static final int FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    private int showing;

    public ExperiencePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIENCE_PIPE.get(), pos, state, BUFFER);
    }

    @Override
    protected boolean carries(FluidResource resource) {
        return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
    }

    @Override
    protected List<Fluid> candidates() {
        return List.of(ExperienceFluids.SOURCE.get());
    }

    /** The glass shows its green while there is experience in it, and for {@link #SHOW_FOR} ticks after. */
    @Override
    protected void ticked(ServerLevel level) {
        this.showing = this.stored() > 0 ? SHOW_FOR : Math.max(0, this.showing - 1);
        boolean filled = this.showing > 0;
        BlockState state = this.getBlockState();
        if (state.hasProperty(ExperiencePipeBlock.FILLED) && state.getValue(ExperiencePipeBlock.FILLED) != filled) {
            level.setBlock(this.worldPosition, state.setValue(ExperiencePipeBlock.FILLED, filled), FLAGS);
        }
    }

    /** Flushed with a Wrench, it lets its experience out as orbs, as when it is broken. */
    @Override
    protected void spill(ServerLevel level, FluidStack held) {
        ExperienceFluids.spillAsOrbs(level, this.worldPosition, held.getAmount());
    }

    /** Broken, it pours what it held out as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            ExperienceFluids.spillAsOrbs(server, pos, this.stored());
        }
    }
}
