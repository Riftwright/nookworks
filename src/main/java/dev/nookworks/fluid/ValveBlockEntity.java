package dev.nookworks.fluid;

import java.util.function.Consumer;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/** The Valve's body: a length of copper line (see {@link CopperPipeBlockEntity}) that passes nothing while it is shut. */
public class ValveBlockEntity extends CopperPipeBlockEntity implements Readout {
    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE.get(), pos, state);
    }

    @Override
    protected boolean passes() {
        BlockState state = this.getBlockState();
        return !state.hasProperty(ValveBlock.OPEN) || state.getValue(ValveBlock.OPEN);
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(Component.translatable(this.passes() ? "message.nookworks.valve.open" : "message.nookworks.valve.shut"));
    }
}
