package dev.nookworks.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.OutputRetry;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Sifter: turns junk blocks into random resources.
 * <p>
 * Inputs are the items in the item tag {@code nookworks:siftable}; what one sifts into is the loot table
 * {@code nookworks:sifting/<item namespace>/<item path>} (type "minecraft:chest"). Datapacks add inputs by adding both.
 * <p>
 * Automation: hoppers feed slot 0 from any side; every {@code sifterTicks} one block is sifted and its results
 * go into the container below (or drop out below, or on top if below is solid). Results that do not fit wait in
 * slots 1-9, a buffer they stack up in as in any chest, and the sifter goes on while there is room there; a hopper
 * underneath can also pull them. A block's results that the buffer cannot take at once wait behind it, unseen and
 * kept ({@link #pending}), and the sifter stops until they have gone in - so a full buffer never changes what a block
 * gives, nor loses any of it.
 * <p>
 * Slot 10 takes a Brass Mesh. Fitted, the sifter works at half speed and rolls {@code nookworks:sifting_fine/...}
 * instead, where it exists (where it does not, as for Crude Salt, it sifts as ever, at the usual pace): fewer rolls
 * come up empty, the small things - nuggets, raw zinc, gems, seeds - turning up more often, and the coarse stuff as
 * often as before. Only the machine uses it; sifting by hand does not.
 */
public class SifterBlockEntity extends BlockEntity implements WorldlyContainer, MachineInfo {
    public static final TagKey<Item> SIFTABLE = TagKey.create(Registries.ITEM, Nookworks.id("siftable"));
    public static final int INPUT = 0;
    public static final int MESH = 10;
    private static final int[] OUTPUT_SLOTS = IntStream.rangeClosed(1, 9).toArray();
    private static final int[] INPUT_SLOTS = {INPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(11, ItemStack.EMPTY);
    /** Results of the last block that did not fit in the buffer yet: they go in before anything is sifted again. */
    private final List<ItemStack> pending = new ArrayList<>();
    private int cooldown;
    /** How long the block now being waited out took: the mesh slows only what it has a finer table for. */
    private int lastTicks;
    private RedstoneMode redstone = RedstoneMode.DEFAULT;
    private MachineStatus status = MachineStatus.IDLE;
    /** When the buffer is next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    public SifterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIFTER.get(), pos, state);
    }

    public static ResourceKey<LootTable> tableFor(ItemStack stack) {
        return tableFor(stack, false);
    }

    /** The table for {@code stack}; with the fine mesh, its {@code sifting_fine} one. */
    public static ResourceKey<LootTable> tableFor(ItemStack stack, boolean fine) {
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceKey.create(Registries.LOOT_TABLE,
                Nookworks.id((fine ? "sifting_fine/" : "sifting/") + item.getNamespace() + "/" + item.getPath()));
    }

    /** Whether a Brass Mesh is fitted. */
    public boolean fine() {
        return this.items.get(MESH).is(ModItems.BRASS_MESH.get());
    }

    /** How long one block takes: twice as long through the fine mesh. */
    public int ticksPerBlock() {
        return NookworksConfig.SIFTER_TICKS.getAsInt() * (this.fine() ? 2 : 1);
    }

    public static boolean canSift(ServerLevel level, ItemStack stack) {
        return stack.is(SIFTABLE) && level.getServer().reloadableRegistries().getLootTable(tableFor(stack)) != LootTable.EMPTY;
    }

    /** Rolls the sifting table for one block of {@code input}. */
    public static List<ItemStack> sift(ServerLevel level, BlockPos pos, ItemStack input) {
        return sift(level, pos, input, false);
    }

    /** Rolls one block of {@code input}, through the fine mesh if {@code fine} and there is a fine table for it. */
    public static List<ItemStack> sift(ServerLevel level, BlockPos pos, ItemStack input, boolean fine) {
        var tables = level.getServer().reloadableRegistries();
        LootTable table = fine ? tables.getLootTable(tableFor(input, true)) : LootTable.EMPTY;
        if (table == LootTable.EMPTY) {
            table = tables.getLootTable(tableFor(input));
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params).stream().map(SifterBlockEntity::given)
                .filter(stack -> !stack.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * What a thing a sifting table rolled comes out as: with Farmer's Delight installed, its rice or onion rather than
     * ours - one rice in the pack to plant and cook - and nothing of a part of the mod a pack has switched off. JEI's
     * Sifter page shows the tables through this too, so it never lists what the Sifter does not give.
     */
    public static ItemStack given(ItemStack rolled) {
        ItemStack stack = ModCrops.preferred(rolled);
        return Features.enabled(stack) ? stack : ItemStack.EMPTY;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SifterBlockEntity sifter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        sifter.flushOutputs(serverLevel, pos);
        if (!sifter.pending.isEmpty()) {
            sifter.placePending();
            if (!sifter.pending.isEmpty()) {
                sifter.status = MachineStatus.OUTPUT_BLOCKED;
                return;
            }
        }
        if (!sifter.redstone.allows(level.hasNeighborSignal(pos))) {
            sifter.status = MachineStatus.REDSTONE;
            return;
        }
        // counted down and sifted in the same tick, so a block takes the ticks the setting says, not one more (r7 review)
        if (sifter.cooldown > 0 && --sifter.cooldown > 0) {
            sifter.status = MachineStatus.RUNNING;
            return;
        }
        ItemStack input = sifter.items.get(INPUT);
        if (input.isEmpty() || !canSift(serverLevel, input)) {
            // Checked again here, not only when it went in: a datapack reload can take an input's table away,
            // and the sifter must then leave the blocks alone rather than eat them for nothing.
            sifter.status = MachineStatus.IDLE;
            return;
        }
        sifter.status = MachineStatus.RUNNING;
        ItemStack one = input.split(1);
        // The mesh costs time only where it sifts finer: something it has no finer table for goes through as fast as
        // without it (coarse salt through the mesh took twice as long, for nothing).
        boolean throughMesh = sifter.fine()
                && serverLevel.getServer().reloadableRegistries().getLootTable(tableFor(one, true)) != LootTable.EMPTY;
        sifter.pending.addAll(sift(serverLevel, pos, one, sifter.fine()));
        // heard now and then as it works, as by hand: what it sifts giving way, softer (a block a second, every one of
        // them, ran into a patter)
        if (serverLevel.getRandom().nextInt(3) == 0) {
            SoundEvent sound = one.getItem() instanceof BlockItem block
                    ? block.getBlock().defaultBlockState().getSoundType().getBreakSound() : SoundEvents.SAND_BREAK;
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.35F, 1.1F + serverLevel.getRandom().nextFloat() * 0.15F);
        }
        sifter.placePending();
        sifter.flushOutputs(serverLevel, pos);
        sifter.cooldown = NookworksConfig.SIFTER_TICKS.getAsInt() * (throughMesh ? 2 : 1);
        sifter.lastTicks = sifter.cooldown;
        sifter.setChanged();
    }

    /**
     * Stacks what is {@link #pending} into the buffer (slots 1-9, never the mesh) as a chest would take it: onto stacks
     * of the same thing first, then into empty slots. What does not fit stays pending.
     */
    private void placePending() {
        boolean moved = false;
        for (var it = this.pending.iterator(); it.hasNext(); ) {
            ItemStack rest = it.next();
            int before = rest.getCount();
            for (int slot : OUTPUT_SLOTS) {
                ItemStack held = this.items.get(slot);
                if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, rest)) {
                    int move = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(move);
                    rest.shrink(move);
                }
            }
            for (int slot : OUTPUT_SLOTS) {
                if (!rest.isEmpty() && this.items.get(slot).isEmpty()) {
                    this.items.set(slot, rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
                }
            }
            moved |= rest.getCount() != before;
            if (rest.isEmpty()) {
                it.remove();
            }
        }
        if (moved) { // waiting on a full buffer, every tick: nothing changed, nothing to tell the neighbours
            this.setChanged();
        }
    }

    /** Broken, it drops what it holds (vanilla's container drop) and what was still waiting to go in. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            for (ItemStack stack : this.pending) {
                Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            this.pending.clear();
        }
    }

    /**
     * Moves waiting results out - with no room below at the last try, not until a hopper's beat later
     * ({@link OutputRetry}). Returns true when nothing is left waiting.
     */
    private boolean flushOutputs(ServerLevel level, BlockPos pos) {
        boolean due = this.outputRetry.due(level);
        boolean clear = true;
        boolean moved = false;
        for (int slot : OUTPUT_SLOTS) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = due ? MachineIO.deliverBelow(level, pos, stack) : stack;
            if (rest.getCount() != stack.getCount()) {
                this.items.set(slot, rest);
                this.setChanged();
                moved = true;
            }
            clear &= rest.isEmpty();
        }
        if (due) {
            this.outputRetry.tried(level, !clear && !moved);
        }
        return clear;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Cooldown", this.cooldown);
        this.redstone.save(output);
        if (!this.pending.isEmpty()) {
            output.store("Pending", ItemStack.CODEC.listOf(), List.copyOf(this.pending));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear(); // what is read fills only the slots saved: a slot emptied by /data stayed as it was
        ContainerHelper.loadAllItems(input, this.items);
        this.cooldown = input.getIntOr("Cooldown", 0);
        this.redstone = RedstoneMode.load(input);
        this.pending.clear();
        input.read("Pending", ItemStack.CODEC.listOf()).ifPresent(this.pending::addAll);
    }

    // ---- the screen: no power, just the sifting ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.SIFTER;
    }

    @Override
    public Container slots() {
        return this;
    }

    @Override
    public int guiEnergy() {
        return 0;
    }

    @Override
    public int guiEnergyCapacity() {
        return 0;
    }

    @Override
    public int guiEnergyRate() {
        return 0;
    }

    /** How far through the block now on the screen it is: the wait after each sifting counts down. */
    @Override
    public int guiProgress() {
        return this.cooldown > 0 ? this.guiProgressMax() - this.cooldown : 0;
    }

    @Override
    public int guiProgressMax() {
        return this.cooldown > 0 && this.lastTicks > 0 ? this.lastTicks : this.ticksPerBlock();
    }

    @Override
    public MachineStatus guiStatus() {
        return this.status;
    }

    @Override
    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    // ---- WorldlyContainer: input from any side, results out of the bottom ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != INPUT && slot != MESH && side == Direction.DOWN;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == MESH) {
            return stack.is(ModItems.BRASS_MESH.get());
        }
        return slot == INPUT && this.level instanceof ServerLevel serverLevel && canSift(serverLevel, stack);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
        if (!taken.isEmpty()) {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }
}
