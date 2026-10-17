package dev.nookworks.fluid;

import java.util.List;

import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Copper Pipe: carries water (from a Water Pump), vegetable oil (from an Oil Press) or biodiesel, ours or another mod's, to
 * whatever takes it. A pipe holds one of them at a time, so a line keeps to one until it runs dry. Broken, what it
 * held is lost.
 */
public class CopperPipeBlockEntity extends FluidPipeBlockEntity {
    /** A bucket, so a pipe keeps up with a pump. */
    public static final int BUFFER = 1_000;
    /** The Copper Pipe's line: the pipe itself and the fittings set in it (Sight Glass, Valve). */
    public static final Object FAMILY = new Object();

    public CopperPipeBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.COPPER_PIPE.get(), pos, state);
    }

    /** For a fitting in a copper line: its own block entity type, the pipe's buffer and fluids. */
    protected CopperPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, BUFFER);
    }

    @Override
    protected Object family() {
        return FAMILY;
    }

    @Override
    protected boolean carries(FluidResource resource) {
        return resource.getFluid() == Fluids.WATER || resource.getFluid() == OilFluids.SOURCE.get() || resource.getFluid() == OilFluids.BIODIESEL.get();
    }

    @Override
    protected List<Fluid> candidates() {
        return List.of(Fluids.WATER, OilFluids.SOURCE.get(), OilFluids.BIODIESEL.get());
    }
}
