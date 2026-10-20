package dev.nookworks.recycling;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.MachineBlock;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.registry.ModRecycling;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** See {@link CompressorBlockEntity}. */
public class CompressorBlock extends MachineBlock {
    public CompressorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MachineBlockEntity.RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachineBlockEntity.RUNNING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompressorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModRecycling.COMPRESSOR_ENTITY.get(), withLamp(CompressorBlockEntity::serverTick));
    }

    @Override
    public @Nullable SoundEvent workSound(BlockState state) {
        return state.getValue(MachineBlockEntity.RUNNING) ? ModSounds.COMPRESSOR_RUN.get() : null;
    }
}
