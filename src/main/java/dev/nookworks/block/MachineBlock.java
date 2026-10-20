package dev.nookworks.block;

import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.mercury.MercuryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour of Nookworks machines: right-click with an item the machine accepts to load it straight in,
 * right-click with an empty hand to open its screen (or, for one without a screen, to see its status in the
 * action bar). Gear - anything with durability, enchantments or a name - is never loaded by a click, even where the
 * machine would take it (a Generator burns a wooden bow): it opens the screen instead, where it can still be put in
 * on purpose.
 */
public abstract class MachineBlock extends BaseEntityBlock {
    /**
     * The chance a working machine sounds when the client looks at it for particles - as vanilla's furnace crackles, at
     * its rate: every few seconds beside it, now and then from across the room, not a drone.
     */
    public static final float WORK_SOUND_CHANCE = 0.1F;

    protected MachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** What the machine sounds of now and then while it works, as its block state shows; null while it stands. */
    public @Nullable SoundEvent workSound(BlockState state) {
        return null;
    }

    /**
     * Whether its sound keeps one pitch: a motor's does, as a pitch that wanders reads as a motor changing speed (the
     * user, 2026-10-11). Other machines' vary a little, as vanilla's campfire does.
     */
    protected boolean steadyPitch() {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        soundNowAndThen(level, pos, random, this.workSound(state), this.steadyPitch());
    }

    /** One of a working machine's sounds, now and then; see {@link #WORK_SOUND_CHANCE}. */
    public static void soundNowAndThen(Level level, BlockPos pos, RandomSource random, @Nullable SoundEvent sound) {
        soundNowAndThen(level, pos, random, sound, false);
    }

    /** As {@link #soundNowAndThen(Level, BlockPos, RandomSource, SoundEvent)}, at one pitch if {@code steady}. */
    public static void soundNowAndThen(Level level, BlockPos pos, RandomSource random, @Nullable SoundEvent sound, boolean steady) {
        if (sound != null && random.nextFloat() < WORK_SOUND_CHANCE) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 1.0F,
                    steady ? 1.0F : 0.9F + random.nextFloat() * 0.2F, false);
        }
    }

    /** The ticker of a machine with a status lamp: the machine's own tick, then the lamp set from how it went. */
    protected static <E extends MachineBlockEntity> BlockEntityTicker<E> withLamp(
            BlockEntityTicker<E> tick) {
        return (level, pos, state, machine) -> {
            tick.tick(level, pos, state, machine);
            machine.showLamp(level, pos);
        };
    }

    /**
     * Whether its machine holds mercury, so that setting it down or taking it away changes the mercury lines too. Every
     * machine said so, and the dimension's every mercury walk was made again for a Crusher set down anywhere (the
     * performance audit, 2026-10-19).
     */
    protected boolean holdsMercury() {
        return false;
    }

    /** A machine set down or taken away changes what the energy network reaches. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            MachineIO.networkChanged(level, pos);
            if (this.holdsMercury()) {
                MercuryNetwork.changed(level);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
        MachineIO.networkChanged(level, pos);
        if (this.holdsMercury()) {
            MercuryNetwork.changed(level);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine) || !machine.canPlaceItem(0, stack) || isGear(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ItemStack held = machine.getItem(0);
        if (!held.isEmpty() && (!ItemStack.isSameItemSameComponents(held, stack) || held.getCount() >= held.getMaxStackSize())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND; // something else is in the slot, or it is full: open the screen
        }
        if (!level.isClientSide()) {
            // in creative the hand keeps its stack, as it does feeding a composter or a furnace: a copy goes in
            boolean keep = player.hasInfiniteMaterials();
            ItemStack slot = machine.getItem(0);
            if (slot.isEmpty()) {
                machine.setItem(0, keep ? stack.copy() : stack.split(stack.getCount()));
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                if (!keep) {
                    stack.shrink(move);
                }
                machine.setChanged();
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Something a player keeps rather than feeds a machine: worn, enchanted or named. */
    public static boolean isGear(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) || stack.isEnchanted()
                || stack.has(DataComponents.STORED_ENCHANTMENTS)
                || stack.has(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof MachineInfo) && !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // no screen, only a status line: what is in hand is used, a pipe set against it
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (entity instanceof MachineInfo) {
            openScreen(player, entity);
        } else if (entity instanceof MachineBlockEntity machine) {
            player.sendOverlayMessage(machine.status());
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the screen of a block entity that has one. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void openScreen(Player player, BlockEntity entity) {
        if (entity instanceof MachineInfo) {
            MachineMenu.open(player, (BlockEntity & MachineInfo) entity);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
