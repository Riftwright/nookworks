package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/** Stand-in for NeoForge 26.3's {@code transfer.fluid.FluidUtil}: the part of it the mod uses. */
public final class FluidUtil {
    private FluidUtil() {}

    /** A new stack of what index {@code index} holds. */
    public static FluidStack getStack(ResourceHandler<FluidResource> handler, int index) {
        FluidResource resource = handler.getResource(index);
        return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(handler.getAmountAsInt(index));
    }

    /** What the first non-empty tank of one of these items holds (a bucket of water: 1000 of water), or empty. */
    public static FluidStack getFirstStackContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return FluidStack.EMPTY;
        }
        for (int i = 0, n = handler.size(); i < n; i++) {
            FluidStack held = getStack(handler, i);
            if (!held.isEmpty()) {
                return held;
            }
        }
        return FluidStack.EMPTY;
    }

    /** {@link #interactWithFluidHandler(Player, InteractionHand, BlockPos, ResourceHandler, TransactionContext)} with the block's own handler. */
    public static boolean interactWithFluidHandler(Player player, InteractionHand hand, Level level, BlockPos pos, @Nullable Direction side,
            @Nullable TransactionContext transaction) {
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return handler != null && interactWithFluidHandler(player, hand, pos, handler, transaction);
    }

    /**
     * A player clicking a fluid handler with the item in hand: the item is filled from the handler if it can be, else
     * emptied into it, one item at a time, with the bucket's sound. With a null transaction the move is final at once;
     * else it (and its sound) waits for the root commit.
     */
    public static boolean interactWithFluidHandler(Player player, InteractionHand hand, @Nullable BlockPos pos, ResourceHandler<FluidResource> handler,
            @Nullable TransactionContext transaction) {
        ItemAccess access = ItemAccess.forPlayerInteraction(player, hand).oneByOne();
        ResourceHandler<FluidResource> held = access.getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return false;
        }
        return moveWithSound(handler, held, player.level(), pos, player, transaction, true)
                || moveWithSound(held, handler, player.level(), pos, player, transaction, false);
    }

    private static boolean moveWithSound(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, Level level, @Nullable BlockPos pos,
            @Nullable Player player, @Nullable TransactionContext transaction, boolean pickup) {
        ResourceStack<FluidResource> moved = ResourceHandlerUtil.moveFirst(from, to, resource -> true, Integer.MAX_VALUE, transaction);
        if (moved == null) {
            return false;
        }
        Vec3 at = pos != null ? Vec3.atCenterOf(pos) : new Vec3(player.getX(), player.getY() + 0.5, player.getZ());
        if (transaction == null) {
            triggerSoundAndGameEvent(moved.resource(), level, at, player, pickup);
        } else {
            new RootCommitJournal(() -> triggerSoundAndGameEvent(moved.resource(), level, at, player, pickup)).updateSnapshots(transaction);
        }
        return true;
    }

    /** The bucket's sound for this fluid (filled when {@code pickup}, else emptied) and the matching game event. */
    public static void triggerSoundAndGameEvent(FluidResource resource, Level level, Vec3 position, @Nullable Player player, boolean pickup) {
        FluidStack stack = resource.toStack(FluidType.BUCKET_VOLUME);
        SoundEvent sound = resource.getFluidType().getSound(stack, pickup ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
        if (sound != null) {
            level.playSound(null, position.x, position.y, position.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.gameEvent(player, pickup ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, position);
    }
}
