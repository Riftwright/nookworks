package dev.nookworks.barter;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.MachineBlock;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * See {@link BarterTerminalBlockEntity}. A machine standing outside the room it keeps, joined to the room's fan by Ducts
 * on any side (or with the fan's back against it), so its other faces are free for hoppers and cables. Its screen
 * lights while its piglins barter.
 */
public class BarterTerminalBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BarterTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH)
                .setValue(MachineBlockEntity.RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MachineBlockEntity.RUNNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Whoever sets it down is who claims are asked about, as its piglins and things go in and out of the room - as
     * DrawRights owns what is set down: another mod's machine (a fake player) only for a player the server knows.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (DrawRights.ownerOf(placer) instanceof UUID owner && level.getBlockEntity(pos) instanceof BarterTerminalBlockEntity terminal) {
            terminal.setOwner(owner);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * A Mob Bottle clicked on it goes into its bottle slot - a piglin's to be let out into the room, an empty one to take
     * one back out - as gold clicked on it goes into its gold slot (as on any machine).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (BarterTerminalBlockEntity.takesBottle(stack) && level.getBlockEntity(pos) instanceof BarterTerminalBlockEntity terminal) {
            if (!terminal.getItem(BarterTerminalBlockEntity.BOTTLE).isEmpty()) {
                return InteractionResult.TRY_WITH_EMPTY_HAND; // one bottle at a time: the screen opens instead
            }
            if (!level.isClientSide()) {
                terminal.setItem(BarterTerminalBlockEntity.BOTTLE, player.hasInfiniteMaterials() ? stack.copyWithCount(1) : stack.split(1));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarterTerminalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBarter.BARTER_TERMINAL_ENTITY.get(), withLamp(BarterTerminalBlockEntity::serverTick));
    }

    @Override
    public @Nullable SoundEvent workSound(BlockState state) {
        return state.getValue(MachineBlockEntity.RUNNING) ? ModSounds.BARTER_TERMINAL_RUN.get() : null;
    }
}
