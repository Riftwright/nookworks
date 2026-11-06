package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Valve: a brass valve set in a Copper Pipe line. Open, it is a length of the line; shut, nothing gets through it
 * either way - the line before it backs up and the line after it runs dry. Its handle shows which: along the pipe
 * open, across it shut, as a real quarter-turn valve's does. It is placed open, whatever the signal where it goes, so a
 * new valve never cuts a line by surprise; from then on it is turned by a click with anything in hand, as a lever is
 * (the user, 2026-11-05: a pipe is set against it sneaking), or by redstone as a trapdoor is: a signal coming on opens
 * it and going off shuts it, so a Nested Timer can meter a line.
 */
public class ValveBlock extends CopperFittingBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ValveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(OPEN, true).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, POWERED);
    }

    @Override
    protected BlockState placementBase(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        // placed open, so a new valve does not cut a line by surprise; the signal it is placed in is where it starts
        // from, and the next change of that signal turns it
        return super.placementBase(context).setValue(POWERED, powered).setValue(OPEN, true);
    }

    @Override
    protected BlockEntityType<? extends FluidPipeBlockEntity> pipeType() {
        return ModBlockEntities.VALVE.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(OPEN);
            turn(level, pos, state.setValue(OPEN, open));
            player.sendOverlayMessage(Component.translatable(open ? "message.nookworks.valve.open" : "message.nookworks.valve.shut"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, block, orientation, moving);
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            // as a trapdoor: the signal coming on opens it, going off shuts it; a hand may turn it in between
            turn(level, pos, state.setValue(POWERED, powered).setValue(OPEN, powered));
        }
    }

    /** Sets the valve's state, with the sound of it turning, and lets the line see it change at once. */
    private static void turn(Level level, BlockPos pos, BlockState state) {
        boolean was = level.getBlockState(pos).getValue(OPEN);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (was != state.getValue(OPEN)) {
            level.playSound(null, pos, SoundEvents.COPPER_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, state.getValue(OPEN) ? 1.3F : 0.9F);
        }
        level.invalidateCapabilities(pos); // a shut valve offers the pipes beside it nothing to fill
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ValveBlockEntity valve) {
            valve.setChanged();
        }
    }
}
