package dev.nookworks.machine;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.fluid.Tanks;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import dev.nookworks.port.core.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import dev.nookworks.port.storage.ContainerHelper;
import dev.nookworks.port.core.Containers;
import dev.nookworks.port.storage.ItemStackWithSlot;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import dev.nookworks.port.transfer.SimpleEnergyHandler;
import dev.nookworks.port.transfer.TransactionContext;
import dev.nookworks.fluid.PipePorts;
import dev.nookworks.port.core.PreRemoveSideEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import dev.nookworks.port.storage.ValueIO;

/**
 * Base for Nookworks machines: a few item slots (hopper/pipe accessible as a WorldlyContainer), an FE buffer,
 * a redstone mode, saving, and a one-line status for the player. Subclasses define the slot rules and the tick,
 * and set {@link #status} as they go so the screen can say why a machine is not running.
 */
public abstract class MachineBlockEntity extends BlockEntity implements PreRemoveSideEffects, WorldlyContainer, Readout, dev.nookworks.feature.DrawRights.Owned {
    /** Who set it down; null for one set down before owners were kept. */
    protected @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked (power, mercury, liquids). */
    protected final dev.nookworks.feature.DrawRights drawRights = new dev.nookworks.feature.DrawRights();

    protected final NonNullList<ItemStack> items;
    /**
     * What an old world kept in slots a machine no longer has (the Sealed Retort's and the Electric Furnace's fill box,
     * gone in 1.5 under the user's rule that nothing fills a container by itself): read from the save, never lost, put
     * out below by {@link #putOutLeftovers} at the machine's first tick - into the container under it, else dropped -
     * and dropped with the rest if the machine is broken first. Saved meanwhile under {@code leftovers}.
     */
    private final List<ItemStack> leftovers = new ArrayList<>();
    protected final SimpleEnergyHandler energy;
    protected RedstoneMode redstone = RedstoneMode.DEFAULT;
    /** What the last tick found; not saved, since the next tick finds it again. */
    protected MachineStatus status = MachineStatus.IDLE;
    /**
     * The status lamp some machines carry on their faces (a block state, so their lamp model lights it): on while the
     * machine runs, and for {@link #LAMP_HOLD} ticks after, so it does not blink out between one item and the next.
     */
    public static final BooleanProperty RUNNING =
            BooleanProperty.create("running");
    public static final int LAMP_HOLD = 20;
    private int lampLeft;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots,
            int capacity, int maxInsert, int maxExtract) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        // What goes in and out a tick follows the settings that raise what machines use and generators make: a machine
        // set to use six times the power took in no more than before, and stood short of it.
        // Worked out again at each call: set once, a setting raised in play took effect only after the chunk reloaded
        // (r21 round three: an Electric Furnace at energyUse 10 ran at half speed)
        int baseInsert = maxInsert;
        int baseExtract = maxExtract;
        this.energy = new SimpleEnergyHandler(capacity, insertCap(baseInsert), extractCap(baseExtract)) {
            @Override
            public int insert(int amount, TransactionContext transaction) {
                this.maxInsert = insertCap(baseInsert);
                return super.insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                this.maxExtract = extractCap(baseExtract);
                return super.extract(amount, transaction);
            }

            @Override
            protected void onEnergyChanged(int previousAmount) {
                savePower(MachineBlockEntity.this);
            }
        };
    }

    /** What a machine of base intake {@code base} takes in a call, at the settings now. */
    private static int insertCap(int base) {
        return base == 0 ? 0 : Math.max(base, Math.max(MachineTuning.energy(base), MachineTuning.workEnergy(base)));
    }

    /** What a generator of base output {@code base} gives out a call, at the settings now. */
    private static int extractCap(int base) {
        return base == 0 ? 0 : Math.max(base, MachineTuning.generated(base));
    }

    /** A machine that shows its pipes' ports, loaded from a world saved before it did: set right ({@link PipePorts}). */
    @Override
    public void onLoad() {
        super.onLoad();
        MachineAlerts.track(this);
        PipePorts.showOnLoad(this);
    }

    /**
     * Power moving in or out of {@code entity} only has to be saved. Nothing reads it with a comparator, and the
     * neighbour updates a full {@code setChanged} sends would run every tick for every machine a network feeds.
     */
    public static void savePower(BlockEntity entity) {
        if (entity.getLevel() != null) {
            entity.getLevel().blockEntityChanged(entity.getBlockPos());
        }
    }

    /** Ticks the fire or glow of a machine that shows one is kept up after it last worked. */
    private int litLeft;

    /**
     * Whether a machine's fire (or glow) shows, after a tick in which it {@code ran}: kept up for {@link #LAMP_HOLD}
     * ticks after it last ran, so that a machine short of power - running every other tick - does not flicker its
     * light on and off, and its block with it, every tick.
     */
    protected boolean showsLit(boolean ran) {
        this.litLeft = ran ? LAMP_HOLD : Math.max(0, this.litLeft - 1);
        return this.litLeft > 0;
    }

    public SimpleEnergyHandler energy() {
        return this.energy;
    }

    public int energyStored() {
        return (int) this.energy.getAmountAsLong();
    }

    /** Fractions of a FE the ticks of this machine's jobs owe, carried to the next tick (MachineTuning#due). */
    private double workOwed;

    /** What this tick of a job costs: its exact share of the job's power, with the fraction the last ones left. */
    protected int workCost(int base) {
        return this.workCost(base, 1);
    }

    /** {@link #workCost(int)} for {@code count} jobs side by side (a Motor turning several machines). */
    protected int workCost(int base, int count) {
        return MachineTuning.due(this.workOwed, MachineTuning.workEnergyExact(base) * count);
    }

    /** Pays this tick of a job, {@link #workCost(int)}, and carries the fraction on. */
    protected void payWork(int base) {
        this.payWork(base, 1);
    }

    protected void payWork(int base, int count) {
        double exact = MachineTuning.workEnergyExact(base) * count;
        int cost = MachineTuning.due(this.workOwed, exact);
        this.workOwed += exact - cost;
        this.addEnergy(-cost);
    }

    /** {@link #workCost(int)} at a rate of {@code fe} FE a tick that need not be whole (a Motor paying by the stress it gives). */
    protected int workCostFe(double fe) {
        return MachineTuning.due(this.workOwed, MachineTuning.workEnergyExact(1) * fe);
    }

    /** Pays {@link #workCostFe} and carries the fraction on. */
    protected void payWorkFe(double fe) {
        double exact = MachineTuning.workEnergyExact(1) * fe;
        int cost = MachineTuning.due(this.workOwed, exact);
        this.workOwed += exact - cost;
        this.addEnergy(-cost);
    }

    /** Adds (positive) or removes (negative) energy internally, ignoring the external insert/extract limits. */
    protected void addEnergy(int delta) {
        long next = Math.max(0, Math.min(this.energy.getCapacityAsLong(), this.energy.getAmountAsLong() + delta));
        this.energy.set((int) next);
    }

    /** Whether the redstone mode lets the machine work now. */
    protected boolean redstoneAllows() {
        return this.level != null && this.redstone.allows(this.level.hasNeighborSignal(this.worldPosition));
    }

    // ---- what the machine's screen shows (see MachineInfo) ----

    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    /** Whether the lamp is lit for this tick: the machine ran. A machine that tells it otherwise says so. */
    protected boolean lampOn() {
        return this.status == MachineStatus.RUNNING;
    }

    /** Lights or darkens the lamp after a tick, on a machine whose block has one ({@link #RUNNING}). */
    public void showLamp(Level level, BlockPos pos) {
        this.lampLeft = lamp(level, pos, this.getBlockState(), this.lampOn(), this.lampLeft);
    }

    /**
     * Sets the lamp of the block at {@code pos} after a tick in which the machine {@code ran}, {@code left} ticks of
     * light being left from before; returns the ticks left now.
     */
    public static int lamp(Level level, BlockPos pos, BlockState state, boolean ran, int left) {
        if (!state.hasProperty(RUNNING)) {
            return 0;
        }
        int next = ran ? LAMP_HOLD : Math.max(0, left - 1);
        if (state.getValue(RUNNING) != next > 0) {
            level.setBlock(pos, state.setValue(RUNNING, next > 0), Block.UPDATE_CLIENTS);
        }
        return next;
    }

    public MachineStatus guiStatus() {
        return this.status;
    }

    /**
     * Whether {@code status}, this machine's, is a stop to see to - heard once and rung for by a Machine Bell
     * ({@link MachineAlerts}) - as its tone says ({@link MachineStatus#alerts}). A machine for which one of them is as
     * meant overrides this: the Water Pump's tank full is the line drawing less than it pumps.
     */
    public boolean alerts(MachineStatus status) {
        return status.alerts();
    }

    public Container slots() {
        return this;
    }

    public int guiEnergy() {
        return this.energyStored();
    }

    public int guiEnergyCapacity() {
        return (int) this.energy.getCapacityAsLong();
    }

    /** Shown in the action bar when the machine is right-clicked with an empty hand. */
    public abstract Component status();

    /** Its status line; a machine with a screen is read from the screen's numbers instead (see {@link MachineInfo}). */
    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.status());
    }

    protected Component energyText() {
        return Component.translatable("nookworks.machine.energy", this.energy.getAmountAsLong(), this.energy.getCapacityAsLong());
    }

    // ---- the liquids it keeps in its item ----

    /**
     * The liquids this machine keeps in its item when it is broken, as a Copper Tank keeps its contents: mercury, oil,
     * fuel, water. Not experience - that pours out as orbs, as a furnace's would.
     */
    protected void heldFluids(Consumer<FluidStack> out) {}

    /** Takes back a liquid its item kept (one {@link #heldFluids} gave); the machine puts it where that liquid goes. */
    protected void restoreFluid(FluidStack fluid) {}

    /**
     * Keeps the power in {@code energy} in the item a block leaves when it is broken, as its liquids are kept (the
     * user, 2026-10-01): a machine taken up and set down elsewhere does not start from nothing.
     */
    public static void keepEnergy(SimpleEnergyHandler energy, DataComponentMap.Builder components) {
        if (energy.getAmountAsLong() > 0) {
            components.set(ModDataComponents.HELD_ENERGY.get(), (int) Math.min(Integer.MAX_VALUE, energy.getAmountAsLong()));
        }
    }

    /** Puts back the power its item kept ({@link #keepEnergy}), held to what the buffer holds. */
    public static void restoreEnergy(SimpleEnergyHandler energy, DataComponentGetter components) {
        Integer held = components.get(ModDataComponents.HELD_ENERGY.get());
        if (held != null) {
            energy.set((int) Math.min(held, energy.getCapacityAsLong()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        keepEnergy(this.energy, components);
        List<SimpleFluidContent> held = new ArrayList<>();
        this.heldFluids(fluid -> {
            if (!fluid.isEmpty()) {
                held.add(SimpleFluidContent.copyOf(fluid));
            }
        });
        if (!held.isEmpty()) {
            components.set(ModDataComponents.HELD_FLUIDS.get(), List.copyOf(held));
            Tanks.oneToAStack(components);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        DataComponentGetter components = input::get; // PORT: 1.21.1 hands its own DataComponentInput
        restoreEnergy(this.energy, components);
        Tanks.forgetStackLimit(components);
        for (var fluid : components.getOrDefault(ModDataComponents.HELD_FLUIDS.get(),
                List.<SimpleFluidContent>of())) {
            this.restoreFluid(fluid.copy());
        }
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

    @Override
    public dev.nookworks.feature.DrawRights drawRights() {
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        output.storeNullable("Owner", net.minecraft.core.UUIDUtil.CODEC, this.owner);
        ContainerHelper.saveAllItems(output, this.items);
        if (!this.leftovers.isEmpty()) {
            output.store("leftovers", ItemStack.CODEC.listOf(), List.copyOf(this.leftovers));
        }
        this.energy.serialize(output.child("Energy"));
        this.redstone.save(output);
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.owner = input.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.items.clear(); // what is read fills only the slots saved: a slot emptied by /data stayed as it was
        ContainerHelper.loadAllItems(input, this.items);
        this.leftovers.clear();
        // the slots beyond the machine's own, as an old world saved them (the indices of the rest are unchanged)
        for (ItemStackWithSlot item : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            if (item.slot() >= this.items.size() && !item.stack().isEmpty()) {
                this.leftovers.add(item.stack());
            }
        }
        input.read("leftovers", ItemStack.CODEC.listOf()).ifPresent(this.leftovers::addAll);
        readEnergy(input, this.energy);
        this.redstone = RedstoneMode.load(input);
    }

    /** Whether anything an old world kept in a slot that is gone waits to be put out ({@link #leftovers}). */
    public boolean hasLeftovers() {
        return !this.leftovers.isEmpty();
    }

    /**
     * Puts out what an old world kept in slots this machine no longer has: into the container below, the rest dropped
     * out where there is room ({@link MachineIO#deliverBelow}, {@link MachineIO#dropOut}); with every side closed it
     * waits and is tried again a second later. Called from the tick of a machine that lost slots.
     */
    protected void putOutLeftovers(ServerLevel level, BlockPos pos) {
        if (this.leftovers.isEmpty() || (level.getGameTime() + pos.hashCode()) % 20 != 0 && this.leftoversTried) {
            return;
        }
        this.leftoversTried = true;
        for (int i = 0; i < this.leftovers.size(); i++) {
            ItemStack rest = MachineIO.deliverBelow(level, pos, this.leftovers.get(i));
            if (!rest.isEmpty() && MachineIO.dropOut(level, pos, rest.copy())) {
                rest = ItemStack.EMPTY;
            }
            this.leftovers.set(i, rest);
        }
        this.leftovers.removeIf(ItemStack::isEmpty);
        this.setChanged();
    }

    private boolean leftoversTried;

    /** Broken before its first tick, it drops what an old world kept in its lost slots with the rest. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        PreRemoveSideEffects.super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            for (ItemStack stack : this.leftovers) {
                Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        this.leftovers.clear();
    }

    /**
     * Reads a buffer of FE from a save, held to what it can hold. A save may say more - a command, or a capacity lowered
     * since - and Jade, drawing the energy bar of a block holding more than it can, crashed the client.
     */
    public static void readEnergy(ValueInput input, SimpleEnergyHandler energy) {
        input.child("Energy").ifPresent(energy::deserialize);
        if (energy.getAmountAsLong() > energy.getCapacityAsLong()) {
            energy.set((int) energy.getCapacityAsLong());
        }
    }

    // ---- WorldlyContainer plumbing; subclasses decide which slots each side sees ----

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(slot, stack);
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
        ItemStack old = this.items.get(slot);
        this.items.set(slot, stack);
        if (!old.isEmpty() && !stack.isEmpty() && !ItemStack.isSameItemSameComponents(old, stack)) {
            this.swapped(slot);
        }
        this.setChanged();
    }

    /**
     * One kind of item was swapped for another in {@code slot} in a single move (a click with a stack in hand), with no
     * tick in between where the slot stood empty. A machine whose half-done work belonged to the old item starts over
     * here: otherwise an ore crushed nearly through would finish a moment after a different ore replaced it.
     */
    protected void swapped(int slot) {}

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }
}
