package dev.nookworks.block.entity;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.TrashCanBlock;
import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Trash Can: anything put in is gone.
 * <p>
 * It reports as a one-slot container that is always empty, which is what makes a hopper keep pushing into it
 * forever: the hopper sees room, inserts, and the item never arrives. Nothing is stored, so there is nothing
 * to drop when it is broken; the only thing saved is the running count of what it has destroyed.
 * <p>
 * It only accepts anything while the block is powered. A hopper asks before it inserts, so an unpowered can
 * turns the line into a queue rather than a leak - the items sit in the hopper where they can still be taken
 * back, which is the behaviour to want from something irreversible.
 */
public class TrashCanBlockEntity extends BlockEntity implements WorldlyContainer, Readout {
    private static final int[] SLOTS = {0};

    private int swallowed;

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRASH_CAN.get(), pos, state);
    }

    /**
     * The count as a transaction sees it. Pipes from other mods try an insert, then often roll it back (a simulated
     * move); counting in {@link #swallow} alone would count those items as destroyed although they never left the
     * pipe. Through {@link #itemHandler()} the count only moves when the transaction commits.
     */
    private final SnapshotJournal<Integer> counted =
            new SnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() {
            return TrashCanBlockEntity.this.swallowed;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            TrashCanBlockEntity.this.swallowed = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            TrashCanBlockEntity.this.setChanged();
            TrashCanBlockEntity.this.heard();
        }
    };

    /** The item capability: a slot that is always empty, takes anything while the can is on, and counts on commit. */
    public ResourceHandler<ItemResource> itemHandler() {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemResource getResource(int index) {
                return ItemResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int index) {
                return 0;
            }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) {
                return TrashCanBlockEntity.this.accepting() ? 64 : 0;
            }

            @Override
            public boolean isValid(int index, ItemResource resource) {
                return TrashCanBlockEntity.this.accepting();
            }

            @Override
            public int insert(int index, ItemResource resource, int amount,
                    TransactionContext transaction) {
                if (!TrashCanBlockEntity.this.accepting() || amount <= 0) {
                    return 0;
                }
                TrashCanBlockEntity.this.counted.updateSnapshots(transaction);
                TrashCanBlockEntity.this.swallowed = (int) Math.min(Integer.MAX_VALUE, (long) TrashCanBlockEntity.this.swallowed + amount);
                return amount;
            }

            @Override
            public int extract(int index, ItemResource resource, int amount,
                    TransactionContext transaction) {
                return 0;
            }
        };
    }

    /** How many items this can has destroyed, for the status message. */
    public int swallowed() {
        return this.swallowed;
    }

    /** Destroys the stack and counts it. Returns what is left, which is always nothing. */
    public ItemStack swallow(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.swallowed = (int) Math.min(Integer.MAX_VALUE, (long) this.swallowed + stack.getCount());
            stack.setCount(0);
            this.setChanged();
            this.heard();
        }
        return ItemStack.EMPTY;
    }

    /**
     * The game time it was last heard. Not saved. Half the lowest long: the time now less Long.MIN_VALUE overflowed to
     * a negative, "just heard", and it was never heard at all (r13).
     */
    private long heardAt = Long.MIN_VALUE / 2;

    /** When it was last heard, in game time. Public for tests. */
    public long heardAt() {
        return this.heardAt;
    }

    /**
     * Something taken in: a knock of its tin, and the lava's sizzle under it - at most twice a second, so a hopper
     * feeding it is heard now and then, not as a rattle.
     */
    private void heard() {
        if (this.level == null || this.level.isClientSide() || this.level.getGameTime() - this.heardAt < 10) {
            return;
        }
        this.heardAt = this.level.getGameTime();
        float pitch = 0.9F + this.level.getRandom().nextFloat() * 0.2F;
        this.level.playSound(null, this.worldPosition, ModSounds.TRASH_CAN_SWALLOW.get(), SoundSource.BLOCKS, 1.0F, pitch);
        this.level.playSound(null, this.worldPosition, ModSounds.TRASH_CAN_BURN.get(), SoundSource.BLOCKS, 1.0F,
                1.6F + this.level.getRandom().nextFloat() * 0.3F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Swallowed", this.swallowed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.swallowed = input.getIntOr("Swallowed", 0);
    }

    @Override
    public int[] getSlotsForFace(@Nullable Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.accepting();
    }

    /** Whether it takes things now, and how many it has done away with: what an empty hand reads off it, and Jade. */
    public Component statusLine() {
        return Component.translatable(this.accepting() ? "nookworks.trash_can.on" : "nookworks.trash_can.off",
                this.swallowed);
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.statusLine());
    }

    /** Whether the can is switched on. Off, it takes nothing at all. */
    public boolean accepting() {
        return this.level != null && TrashCanBlock.isOn(this.level, this.worldPosition);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return this.accepting() ? 64 : 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (this.accepting()) {
            this.swallow(stack);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.accepting();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
    }
}
