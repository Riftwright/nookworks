package dev.nookworks.block.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.block.HorizontalItemWarpBlock;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/**
 * Horizontal Item Warp Sender: like the vertical sender, but sends straight ahead (its facing) to the nearest
 * Horizontal Item Warp Receiver on that line, at the same height, that takes from it ({@link HorizontalItemWarpReceiverBlockEntity}):
 * one of its own owner's, an old one of nobody's, or one paired with it while receivers paired. Anyone else's in the line
 * is passed over. The
 * receiver puts items into the block in front of it (its facing), or drops them out there.
 * <p>
 * Its owner is whoever set it down, as the vertical sender's (the user, 2026-10-14, in place of pairing: "あれ（ペアリング）
 * いがいとめんどくさいよね"). It keeps the stamp of its setting-down from when receivers paired: a receiver paired with
 * it before it was set down again takes nothing from it, and a new sender at the same place is a new stamp. One set down
 * before pairing came in - or now by a machine of no known player - has no owner and stamp 0, and goes on sending to the
 * old receivers in its line; one from the pairing days keeps sending to the receivers paired with it.
 */
public class HorizontalItemWarpSenderBlockEntity extends ItemWarpSenderBlockEntity {
    private long stamp;

    public HorizontalItemWarpSenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HORIZONTAL_ITEM_WARP_SENDER.get(), pos, state);
    }

    public long stamp() {
        return this.stamp;
    }

    /**
     * Set down by {@code owner} (null: by no known player), with {@code stamp} (0 only for one of no owner, which then
     * works as one from before pairing).
     */
    public void placed(@Nullable UUID owner, long stamp) {
        super.setOwner(owner);
        this.stamp = stamp;
        this.setChanged();
    }

    /** A stamp for a new setting-down: never 0, the stamp of a sender from before pairing. */
    public static long newStamp(RandomSource random) {
        long stamp;
        do {
            stamp = random.nextLong();
        } while (stamp == 0);
        return stamp;
    }

    /**
     * Given an owner where it has none - by the placing event, for a placing that did not go through
     * {@link HorizontalItemWarpBlock#setPlacedBy} - it has a stamp of its own too, so that no receiver paired with whatever stood
     * here before takes from it.
     */
    @Override
    public void setOwner(@Nullable UUID owner) {
        super.setOwner(owner);
        if (owner != null && this.stamp == 0) {
            this.stamp = newStamp(this.level != null ? this.level.getRandom() : RandomSource.create());
        }
    }

    @Override
    protected @Nullable BlockPos receiverFor(ServerLevel level, BlockPos pos) {
        return findLineReceiver(level, pos, this.getBlockState().getValue(HorizontalItemWarpBlock.FACING), NookworksConfig.ITEM_WARP_RANGE.getAsInt(),
                this.stamp, this.owner());
    }

    /**
     * The nearest receiver on the line from {@code from} going {@code dir} that takes from the sender there, of stamp
     * {@code stamp} and owner {@code owner} ({@link HorizontalItemWarpReceiverBlockEntity#takesFrom}). Others are passed over, and
     * the line goes on past them.
     */
    public static @Nullable BlockPos findLineReceiver(ServerLevel level, BlockPos from, Direction dir, int range, long stamp,
            @Nullable UUID owner) {
        for (int d = 1; d <= range; d++) {
            BlockPos pos = from.relative(dir, d);
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.HORIZONTAL_ITEM_WARP_RECEIVER.get()) && !pos.relative(state.getValue(HorizontalItemWarpBlock.FACING)).equals(from)
                    && level.getBlockEntity(pos) instanceof HorizontalItemWarpReceiverBlockEntity receiver
                    && receiver.takesFrom(level, from, stamp, owner)) {
                return pos; // one facing straight back into this sender would only hand the items back: the next one along
            }
        }
        return null;
    }

    @Override
    protected ItemStack deliverTo(ServerLevel level, BlockPos receiver, ItemStack stack) {
        Direction out = level.getBlockState(receiver).getValue(HorizontalItemWarpBlock.FACING);
        BlockPos target = receiver.relative(out);
        if (target.equals(this.worldPosition)) {
            return stack; // the receiver faces back into its own sender
        }
        if (!level.isLoaded(target)) {
            return stack;
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, target, out.getOpposite());
        if (handler != null) {
            return ItemUtil.insertItemReturnRemaining(handler, stack.copy(), false, null);
        }
        if (!level.getBlockState(target).getCollisionShape(level, target).isEmpty()) {
            return stack; // blocked and no inventory: wait
        }
        if (level.getGameTime() % ItemWarpSenderBlockEntity.GROUND_BEAT != 0) {
            return stack; // onto the ground only at a hopper's beat, not an item a tick
        }
        double x = receiver.getX() + 0.5 + out.getStepX() * 0.8;
        double z = receiver.getZ() + 0.5 + out.getStepZ() * 0.8;
        level.addFreshEntity(new ItemEntity(level, x, receiver.getY() + 0.3, z, stack.copy(), out.getStepX() * 0.1, 0.0, out.getStepZ() * 0.1));
        return ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); // its owner too, under "Owner", where it kept it before the vertical one had one
        output.putLong("Stamp", this.stamp);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.stamp = input.getLongOr("Stamp", 0L);
    }
}
