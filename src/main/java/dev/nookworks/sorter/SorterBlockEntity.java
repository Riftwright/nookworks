package dev.nookworks.sorter;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Item Sorter logic. One input slot (hoppers feed it from any side), which it also fills itself from the container
 * above it, as a hopper draws from the block over it. Every so often - a hopper's pace, or faster with a Sorter Gear in
 * its gear slot ({@link SorterGear}) - it moves a batch to the registered or the other outlet: into the inventory on
 * that side if there is one (waiting if it is full), otherwise spat out like a dropper (waiting if the outlet is
 * blocked by a solid block). While it waits it draws nothing more in: what is still to sort stays in the chest above.
 * <p>
 * What it is never to take in stays above too (the user, 2026-10-08: "設定したアイテムは、ソーターで引かなくなる（ソーター
 * の上のチェストに残る）"): what its Keep Out row names, and what the inventory it would go to can never hold - a bundle
 * or a shulker box bound for a Storage Barrel, which takes neither (a bundle held so stopped the sorter for good). A
 * hopper cannot put either in, and one it holds already goes back up.
 */
public class SorterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    /** Room for the bigger sorter; each uses {@link SorterTier#filterSlots} of them. */
    public static final int FILTER_SLOTS = 18;
    /** The Keep Out row: what the sorter never takes in. */
    public static final int DENY_SLOTS = 8;
    /** How many of an inventory's slots are asked whether they could hold an item, before it is taken to hold none. */
    private static final int ASKED_SLOTS = 64;
    private static final int[] INPUT = {0};

    private final NonNullList<ItemStack> input = NonNullList.withSize(1, ItemStack.EMPTY);
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            SorterBlockEntity.this.setChanged();
        }
    };
    /** The Sorter Gear fitted, if any: one, in a slot of its own. */
    private final SimpleContainer gear = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof SorterGearItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SorterBlockEntity.this.setChanged();
        }
    };
    /** The Keep Out row: ghosts, as the filter's are. */
    private final SimpleContainer deny = new SimpleContainer(DENY_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            SorterBlockEntity.this.setChanged();
        }
    };
    private final SorterSettings settings = new SorterSettings();
    private int cooldown;

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SORTER.get(), pos, state);
    }

    public Container filter() {
        return this.filter;
    }

    /** The Keep Out row. */
    public Container deny() {
        return this.deny;
    }

    /**
     * Whether the Keep Out row names {@code stack}: as the filter would catch it (the same item; a Precision Sorter's
     * Exact switch asks for the same condition too).
     */
    public boolean denies(ItemStack stack) {
        for (int i = 0; i < DENY_SLOTS; i++) {
            if (this.settings.matches(stack, this.deny.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** The outlet {@code stack} would leave by. */
    private Direction outletFor(ItemStack stack) {
        BlockState state = this.getBlockState();
        return this.isRegistered(stack) ? SorterBlock.registeredSide(state) : SorterBlock.otherSide(state);
    }

    /** Whether the sorter is never to take {@code stack} in: kept out, or bound for an inventory that cannot hold it. */
    private boolean keepsOut(ServerLevel level, ItemStack stack) {
        return this.denies(stack) || refuses(level, this.worldPosition, this.outletFor(stack), stack);
    }

    /** The gear slot. */
    public Container gearSlot() {
        return this.gear;
    }

    /** How fast it works: its gear's pace, or a hopper's with none. */
    public SorterGear speed() {
        return SorterGearItem.of(this.gear.getItem(0));
    }

    public SorterSettings settings() {
        return this.settings;
    }

    public SorterTier sorterTier() {
        return this.tier();
    }

    public boolean isRegistered(ItemStack stack) {
        boolean caught = false;
        for (int i = 0; i < this.tier().filterSlots; i++) {
            if (this.settings.matches(stack, this.filter.getItem(i))) {
                caught = true;
                break;
            }
        }
        return this.settings.sortsAsRegistered(caught);
    }

    private SorterTier tier() {
        return this.getBlockState().getBlock() instanceof SorterBlock block ? block.tier : SorterTier.ITEM;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SorterBlockEntity sorter) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (state.getBlock() instanceof LegacySorterBlock legacy) {
            sorter.becomeItemSorter(server, pos, state, legacy.gear);
            return;
        }
        SorterGear gear = sorter.speed();
        if (state.getValue(SorterBlock.GEAR) != gear) { // the front shows the gear fitted
            level.setBlock(pos, state.setValue(SorterBlock.GEAR, gear), Block.UPDATE_CLIENTS);
        }
        if (--sorter.cooldown > 0) {
            return;
        }
        sorter.cooldown = gear.interval;
        ItemStack stack = sorter.input.get(0);
        if (!stack.isEmpty() && sorter.keepsOut(server, stack)) {
            // kept out since it came in, or bound for an inventory that cannot hold it: back up where it came from (an
            // older sorter held a bundle for a Storage Barrel for good), or held, waiting, with nowhere to put it
            if (!sorter.giveBack(server, pos)) {
                return;
            }
            stack = sorter.input.get(0);
        }
        if (!stack.isEmpty()) {
            Direction side = sorter.outletFor(stack);
            int batch = gear.batch;
            if (gear.interval < THROW_INTERVAL && throwsOut(server, pos, side)) {
                // Thrown out on the ground, not handed over: at a fast gear's pace that is a stream of item entities
                // (a Velarium gear, twenty stacks a second) for the server to carry. Thrown ones go at a hopper's beat,
                // as many at a time as the gear moves in it, up to a stack.
                batch = Math.min(stack.getMaxStackSize(), gear.batch * THROW_INTERVAL / gear.interval);
                sorter.cooldown = THROW_INTERVAL;
            }
            int offered = Math.min(batch, stack.getCount());
            int moved = output(server, pos, side, stack.copyWithCount(offered));
            if (moved > 0) {
                stack.shrink(moved);
                sorter.setChanged();
            }
            if (moved < offered) {
                return; // the outlet is full or blocked: it keeps what it has, and draws nothing more in until that goes
            }
        }
        // Then the next batch from the container above, at the same pace: what the gear moves in a beat of this length
        // (a thrown beat is a hopper's, and moves as many as the gear does in one).
        sorter.drawFromAbove(server, pos, gear.batch * sorter.cooldown / gear.interval);
    }

    /**
     * Draws up to {@code most} items into the input from the container above, through its bottom face, as a hopper
     * draws from the block over it: the first thing that container gives out, as many of it as fit in the slot - or,
     * with something left in the input, more of that alone. Any container with NeoForge's item capability: a chest, a
     * barrel, a Storage Barrel, a hopper, another mod's. A Sorter above gives nothing out (what it holds only ever goes
     * sideways), nor does a Trash Can, which holds nothing. Claims are not asked, as a hopper does not ask: claim mods
     * claim whole columns, so a sorter under a claimed chest stands on that claim (docs/compat.md). What it keeps out
     * ({@link #keepsOut}) it passes over, and it stays in the container.
     */
    private void drawFromAbove(ServerLevel level, BlockPos pos, int most) {
        BlockPos above = pos.above();
        if (most <= 0 || !level.isLoaded(above)) {
            return; // over the top of the world (the rest of the column is the sorter's own chunk, loaded)
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, above, Direction.DOWN);
        if (handler == null) {
            return;
        }
        ItemStack held = this.input.get(0);
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty() || !held.isEmpty() && !resource.matches(held)) {
                continue;
            }
            if (held.isEmpty() && this.keepsOut(level, resource.toStack(1))) {
                continue; // left where it is
            }
            int room = Math.min(most, resource.getMaxStackSize() - (held.isEmpty() ? 0 : held.getCount()));
            if (room <= 0) {
                return; // the input is a full stack already
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = handler.extract(resource, room, transaction);
                if (taken > 0) {
                    transaction.commit();
                    if (held.isEmpty()) {
                        this.input.set(0, resource.toStack(taken));
                    } else {
                        held.grow(taken);
                    }
                    this.setChanged();
                    return;
                }
            } // nothing given out of this one (a furnace's fuel, say): on to the next
        }
    }

    /**
     * Puts what the input holds back into the container above, as much as it takes; true once the input is empty. With
     * no container there, or one that takes nothing in from below, it stays. Only into one that holds what it takes, so
     * that it could be drawn back out: a Trash Can on top takes everything and keeps nothing, and what had come in by a
     * side - from a hopper, a pipe - went into it for good (r10, items going missing).
     */
    private boolean giveBack(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        var handler = level.isLoaded(above) ? level.getCapability(Capabilities.Item.BLOCK, above, Direction.DOWN) : null;
        if (handler == null) {
            return false;
        }
        ItemStack held = this.input.get(0);
        ItemResource resource = ItemResource.of(held);
        try (Transaction transaction = Transaction.openRoot()) {
            int put = handler.insert(resource, held.getCount(), transaction);
            if (put <= 0) {
                return false;
            }
            try (Transaction asked = Transaction.open(transaction)) {
                if (handler.extract(resource, put, asked) < put) {
                    return false; // swallowed, not held: it stays here
                }
            } // only asked: what was put in stays put
            transaction.commit();
            this.input.set(0, put < held.getCount() ? held.copyWithCount(held.getCount() - put) : ItemStack.EMPTY);
            this.setChanged();
            return put == held.getCount();
        }
    }

    /**
     * Whether the inventory on {@code side} could never hold {@code stack}: one is there, and none of its slots (of the
     * first {@link #ASKED_SLOTS}) would take that item - a Storage Barrel and a bundle or a shulker box, a shulker box
     * and another, a Sorter and what it keeps out. A full one does not refuse (it waits for room), nor does open ground,
     * a solid block or a chunk not loaded.
     */
    static boolean refuses(ServerLevel level, BlockPos pos, Direction side, ItemStack stack) {
        BlockPos target = pos.relative(side);
        if (!level.isLoaded(target)) {
            return false;
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, target, side.getOpposite());
        if (handler == null) {
            return false;
        }
        ItemResource resource = ItemResource.of(stack);
        int asked = Math.min(handler.size(), ASKED_SLOTS);
        // A sided container's own rule for that face, which the wrapper's isValid does not ask: a shulker box keeps
        // shulker boxes out only there, and a Sorter what it keeps out. Unasked, a sorter feeding one drew such an item
        // in and held it for good, sorting nothing more. A ring of sorters asking each other stops asking at its depth.
        int[] depth = DEPTH.get();
        WorldlyContainer sided = handler instanceof WorldlyContainerWrapper && depth[0] < MAX_DEPTH
                && level.getBlockEntity(target) instanceof WorldlyContainer worldly ? worldly : null;
        int[] slots = sided != null ? sided.getSlotsForFace(side.getOpposite()) : null;
        depth[0]++;
        try {
            for (int index = 0; index < asked; index++) {
                if (handler.isValid(index, resource) && (sided == null || index >= slots.length
                        || sided.canPlaceItemThroughFace(slots[index], stack, side.getOpposite()))) {
                    return false;
                }
            }
        } finally {
            depth[0]--;
        }
        return true;
    }

    /** How deep {@link #refuses} is in sorters asking the sorters they feed (one thread a level, so one count each). */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final int MAX_DEPTH = 8;

    /** An old tiered sorter: it is an Item Sorter now, with the gear its tier was, keeping its filter and its load. */
    private void becomeItemSorter(ServerLevel level, BlockPos pos, BlockState state, SorterGear was) {
        if (this.gear.getItem(0).isEmpty()) {
            this.gear.setItem(0, gearItem(was));
        }
        level.setBlock(pos, ModBlocks.ITEM_SORTER.get().withPropertiesOf(state).setValue(SorterBlock.GEAR, was), Block.UPDATE_ALL);
    }

    /** The Sorter Gear item for {@code gear} (nothing for NONE). */
    public static ItemStack gearItem(SorterGear gear) {
        return switch (gear) {
            case NONE -> ItemStack.EMPTY;
            case GOLD -> new ItemStack(ModItems.GOLD_SORTER_GEAR.get());
            case DIAMOND -> new ItemStack(ModItems.DIAMOND_SORTER_GEAR.get());
            case NETHERITE -> new ItemStack(ModItems.NETHERITE_SORTER_GEAR.get());
            case VELARIUM -> new ItemStack(ModItems.VELARIUM_SORTER_GEAR.get());
        };
    }

    /** Ticks between throws onto the ground: a hopper's beat, whatever the gear. */
    static final int THROW_INTERVAL = SorterGear.NONE.interval;

    /** Whether the outlet on {@code side} throws onto the ground: loaded, open, and no inventory there. */
    static boolean throwsOut(ServerLevel level, BlockPos pos, Direction side) {
        BlockPos target = pos.relative(side);
        return level.isLoaded(target) && level.getCapability(Capabilities.Item.BLOCK, target, side.getOpposite()) == null
                && level.getBlockState(target).getCollisionShape(level, target).isEmpty();
    }

    /**
     * Sends {@code stack} out through {@code side}: into the container there, or thrown onto open ground. Returns how
     * many went out - none while the outlet is blocked or in a chunk not loaded.
     */
    static int output(ServerLevel level, BlockPos pos, Direction side, ItemStack stack) {
        BlockPos target = pos.relative(side);
        if (!level.isLoaded(target)) {
            return 0; // the outlet is in an unloaded chunk: wait rather than load it
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, target, side.getOpposite());
        if (handler != null) {
            ItemStack rest = ItemUtil.insertItemReturnRemaining(handler, stack, false, null);
            return stack.getCount() - rest.getCount();
        }
        if (!level.getBlockState(target).getCollisionShape(level, target).isEmpty()) {
            return 0; // blocked outlet: wait
        }
        Vec3 face = Vec3.atCenterOf(pos).add(side.getStepX() * 0.7, 0.0, side.getStepZ() * 0.7);
        DefaultDispenseItemBehavior.spawnItem(level, stack, 6, side, face);
        return stack.getCount();
    }

    /** Broken, it drops what it held and its gear (the filter holds only ghosts). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropContents(this.level, pos, this.gear);
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SorterMenu(containerId, inventory, this.filter, this.gear, this.deny, this.worldPosition, this.tier(), this.settings);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(this.tier());
        buffer.writeBlockPos(this.worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.input);
        ContainerHelper.saveAllItems(output.child("Filter"), this.filter.getItems());
        ContainerHelper.saveAllItems(output.child("Gear"), this.gear.getItems());
        if (!this.deny.isEmpty()) { // a sorter with none saves as one from before the row did
            ContainerHelper.saveAllItems(output.child("Deny"), this.deny.getItems());
        }
        this.settings.save(output.child("Settings"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // loaded over what it held, not added to it (a /data merge or block data on an item: a slot not in the data kept
        // its old stack - the dupe and loss audit)
        this.input.clear();
        this.filter.getItems().clear();
        this.deny.getItems().clear();
        this.gear.getItems().clear();
        ContainerHelper.loadAllItems(input, this.input);
        input.child("Filter").ifPresent(f -> ContainerHelper.loadAllItems(f, this.filter.getItems()));
        input.child("Deny").ifPresent(d -> ContainerHelper.loadAllItems(d, this.deny.getItems()));
        input.child("Settings").ifPresent(this.settings::load);
        if (this.settings.get(SorterSettings.MATCH_TAGS)) {
            // A 0.2.0 Precision Sorter with its Tags switch on, where every entry stood for all of its tags at once: each
            // becomes the entry for the one tag it was meant for (planks for a plank), or stays its item where none is.
            for (int i = 0; i < FILTER_SLOTS; i++) {
                ItemStack entry = this.filter.getItem(i);
                var tag = entry.isEmpty() || SorterTags.of(entry) != null ? null : SorterTags.bestFor(entry);
                if (tag != null) {
                    this.filter.setItem(i, SorterTags.entry(entry, tag));
                }
            }
            this.settings.clear(SorterSettings.MATCH_TAGS);
        }
        var gear = input.child("Gear");
        if (gear.isPresent()) {
            ContainerHelper.loadAllItems(gear.get(), this.gear.getItems());
        } else if (input.child("Filter").isPresent() && this.getBlockState().is(ModBlocks.PRECISION_SORTER.get())) {
            // A Precision Sorter from 0.2.0, when it moved a stack a tick of itself: its Velarium is a gear now.
            this.gear.setItem(0, gearItem(SorterGear.VELARIUM));
        }
    }

    // ---- WorldlyContainer: the single input slot, from every side ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return INPUT;
    }

    /** Anything, from any side - but what the sorter keeps out ({@link #keepsOut}), which stays in the hopper or pipe. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return !(this.level instanceof ServerLevel server) || !this.keepsOut(server, stack);
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
        return this.input.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.input.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(this.input, slot, count);
        this.setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.input, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.input.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.input.clear();
    }
}
