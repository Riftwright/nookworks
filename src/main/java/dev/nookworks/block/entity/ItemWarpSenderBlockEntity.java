package dev.nookworks.block.entity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/**
 * Item Warp Sender. Takes items dropped on top of it or pushed in by hoppers (it is a one-slot Container)
 * and warps them to the nearest Item Warp Receiver in the same column, up or down, that is not somebody else's.
 * The receiver fills the container under it, or drops the items below itself (above, if below is solid).
 * With no receiver, or a full container at the receiver, items wait here and hoppers back up.
 * <p>
 * Whoever sets a warp down owns it ({@link DrawRights#ownerOf}), and a sender sends only to receivers of its own owner -
 * or of no owner - passing over anyone else's in its column (the user, 2026-10-14: "ワープはbで…横ワープも区画を記録する
 * 方式で"): a stranger's receiver set under a claim's floor took everything a claimed sender sent (r15 fix check 8, S2),
 * and in open land one set nearer in the column would have too. A receiver of no owner was set down before owners were
 * kept (or by a machine of no known player): nobody can make one by hand now, so it is no way in for a stranger, and an
 * old column whose sender alone is set down again goes on working. A sender of no owner sends to the nearest receiver,
 * whoever's, as before.
 */
public class ItemWarpSenderBlockEntity extends BlockEntity implements Container, DrawRights.Owned {
    private final NonNullList<ItemStack> buffer = NonNullList.withSize(1, ItemStack.EMPTY);
    /**
     * With nowhere to send what it holds (no receiver, or no room at it), it looks again at a hopper's beat, not every
     * tick: the look runs up to twice the range of blocks. Anything put in looks at once.
     */
    private long retryAt;
    /** Who set it down; null for one set down before owners were kept, or by no known player. */
    private @Nullable UUID owner;
    private @Nullable DrawRights drawRights;

    public ItemWarpSenderBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_WARP_SENDER.get(), pos, state);
    }

    protected ItemWarpSenderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    /** Kept for {@link DrawRights.Owned}; a warp asks no claim, it only matches owners. */
    @Override
    public DrawRights drawRights() {
        if (this.drawRights == null) {
            this.drawRights = new DrawRights();
        }
        return this.drawRights;
    }

    /** Where this sender's items go; the vertical sender looks up and down its column. */
    protected @Nullable BlockPos receiverFor(ServerLevel level, BlockPos pos) {
        return findReceiver(level, pos, NookworksConfig.ITEM_WARP_RANGE.getAsInt(), this.owner);
    }

    /** Hands the stack to the receiver's output; returns what did not fit. */
    protected ItemStack deliverTo(ServerLevel level, BlockPos receiver, ItemStack stack) {
        if (receiver.below().equals(this.worldPosition)) {
            return stack; // the receiver sits on its own sender: delivering would put the items back in the buffer
        }
        return deliver(level, receiver, stack);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemWarpSenderBlockEntity sender) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (sender.buffer.get(0).isEmpty()) {
            sender.suckDroppedItem(serverLevel, pos);
        }
        ItemStack stack = sender.buffer.get(0);
        long now = serverLevel.getGameTime();
        if (stack.isEmpty() || now < sender.retryAt) {
            return;
        }
        BlockPos receiver = sender.receiverFor(serverLevel, pos);
        ItemStack rest = receiver == null ? stack : sender.deliverTo(serverLevel, receiver, stack);
        if (rest.getCount() != stack.getCount()) {
            sender.buffer.set(0, rest);
            sender.setChanged();
            serverLevel.sendParticles(ParticleTypes.PORTAL, receiver.getX() + 0.5, receiver.getY() + 0.5,
                    receiver.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
        } else {
            // on the next beat (on it, not a beat from now: the ground takes items only on the beat)
            sender.retryAt = now + GROUND_BEAT - now % GROUND_BEAT;
        }
    }

    private void suckDroppedItem(ServerLevel level, BlockPos pos) {
        AABB top = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, top, EntitySelector.ENTITY_STILL_ALIVE);
        if (!items.isEmpty()) {
            ItemEntity entity = items.get(0);
            this.buffer.set(0, entity.getItem().copy());
            this.retryAt = 0;
            entity.discard();
            this.setChanged();
        }
    }

    /** How often a receiver with nothing under it to take things in drops them on the ground. */
    static final int GROUND_BEAT = 8;

    /**
     * Nearest receiver in the column that can deliver, of those set down by {@code owner} or by nobody (any, for a
     * sender of no owner); below wins a tie, since farms usually send downwards. One sat right on top of the sender would
     * only hand the items back, so it is passed over for the next one along, as is one of somebody else's.
     */
    public static @Nullable BlockPos findReceiver(ServerLevel level, BlockPos from, int range, @Nullable UUID owner) {
        for (int d = 1; d <= range; d++) {
            for (BlockPos pos : new BlockPos[] {from.below(d), from.above(d)}) {
                // isLoaded as well as the height check: a tall range would otherwise pull chunks into memory
                // on every send, the same way the horizontal warp used to.
                if (!level.isOutsideBuildHeight(pos) && level.isLoaded(pos)
                        && level.getBlockState(pos).is(ModBlocks.ITEM_WARP_RECEIVER.get()) && !pos.below().equals(from)
                        && (owner == null || !(level.getBlockEntity(pos) instanceof ItemWarpReceiverBlockEntity receiver)
                                || receiver.owner() == null || owner.equals(receiver.owner()))) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** Puts the stack into the container under the receiver, or drops it. Returns what did not fit. */
    static ItemStack deliver(ServerLevel level, BlockPos receiver, ItemStack stack) {
        BlockPos below = receiver.below();
        if (!level.isLoaded(below)) {
            return stack; // wait for the chunk rather than load it
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, below, Direction.UP);
        if (handler != null) {
            return ItemUtil.insertItemReturnRemaining(handler, stack.copy(), false, null);
        }
        if (level.getGameTime() % GROUND_BEAT != 0) {
            return stack; // onto the ground only at a hopper's beat, as a Sorter throws: not an item a tick for the server to carry
        }
        boolean openBelow = level.getBlockState(below).getCollisionShape(level, below).isEmpty();
        double y = openBelow ? receiver.getY() - 0.4 : receiver.getY() + 1.1;
        ItemEntity drop = new ItemEntity(level, receiver.getX() + 0.5, y, receiver.getZ() + 0.5, stack.copy(), 0.0, 0.0, 0.0);
        level.addFreshEntity(drop);
        return ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.buffer);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.buffer.clear(); // what is read fills only the slots saved: a slot emptied by /data stayed as it was
        ContainerHelper.loadAllItems(input, this.buffer);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    // ---- Container (one slot, so hoppers can feed it) ----

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.buffer.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.buffer.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(this.buffer, slot, count);
        if (!taken.isEmpty()) {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.buffer, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.buffer.set(slot, stack);
        this.retryAt = 0;
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.buffer.clear();
    }
}
