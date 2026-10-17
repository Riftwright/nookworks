package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.experience.ExperienceBottling;
import dev.nookworks.experience.ExperienceBuffer;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Sealed Retort: heats cinnabar (HgS) in a closed vessel; the mercury vapour is condensed into its buffer and
 * the sulfur stays behind as sulfur dust. Mercury leaves through Mercury Pipes (or into an adjacent tank),
 * the dust into the container below. It also boils sea water down to crude salt, which frees no mercury at all.
 * <p>
 * The second slot holds a reagent for entries that cook with one. A kraft digester is a heated sealed vessel
 * like any other, so pulping wood chips in caustic soda belongs here rather than on a crafting table.
 * <p>
 * Like a furnace, every charge leaves a little experience behind. The Retort keeps it as Liquid Experience (up to
 * {@link #EXPERIENCE_CAPACITY} mB) and pushes it into an Experience Pipe or tank beside it - ours or another mod's -
 * which may also draw it out. Broken, it pours what it held out as orbs.
 * <p>
 * Water can come in by pipe as well as by the bucket: a Copper Pipe (or another mod's) fills a tank of
 * {@link #WATER_TANK} mB, and with nothing in the charge slot, each bucket's worth of it is boiled down just as a
 * water bucket would be.
 */
public class RetortBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 30;
    public static final int TICKS = 100;
    /** Cinnabar or wood chips go in slot 0; anything the entry cooks them with goes in slots 1 and 2. */
    public static final int FIRST_REAGENT = 1;
    public static final int REAGENT_SLOTS = 2;
    private static final int INPUTS = 1 + REAGENT_SLOTS;
    /** The by-product, plus room for an emptied container. */
    private static final int OUTPUTS = 2;

    public static final int EXPERIENCE_CAPACITY = 1_000;
    public static final int WATER_TANK = 4_000;
    private static final int BUCKET = 1_000;

    /** The experience the charges left behind: taken from, never filled from outside. */
    private final ExperienceBuffer experience =
            new ExperienceBuffer(EXPERIENCE_CAPACITY, this::setChanged);

    /** Water piped in, boiled a bucket at a time when the charge slot is empty: filled from outside, never drawn. */
    private final FluidStacksResourceHandler water =
            new OneTank(WATER_TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid() == Fluids.WATER;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount,
                TransactionContext transaction) {
            return 0; // it goes in to be boiled, not to be passed along
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            RetortBlockEntity.this.setChanged();
        }
    };

    /** What pipes see: the experience to draw out, and the water tank to fill. */
    private final ResourceHandler<FluidResource> fluids =
            new CombinedResourceHandler<>(this.experience, this.water);

    /** The fill box: an empty bucket or bottle in, filled with the experience the charges left, out. */
    public static final int FILL = INPUTS + OUTPUTS;
    public static final int FILLED = FILL + 1;
    private static final int[] SIDE_SLOTS = {0, 1, 2, FILL};
    private static final int[] DOWN_SLOTS = {INPUTS, INPUTS + 1, FILLED};

    /** Where a glass bottle from a hopper or pipe goes: set from the screen. */
    private RetortBottles bottles = RetortBottles.DEFAULT;

    public RetortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RETORT.get(), pos, state, INPUTS, OUTPUTS, 2, 10_000);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? DOWN_SLOTS : SIDE_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && (slot == INPUTS || slot == INPUTS + 1 || slot == FILLED);
    }

    /** Bottles and buckets from the fill box; filled, they go down into a container below, or wait in the box to be taken. */
    private void bottle(ServerLevel level, BlockPos pos) {
        if (ExperienceBottling.fill(this.items, FILL, FILLED, this.experience)) {
            this.setChanged();
        }
        ItemStack filled = this.items.get(FILLED);
        if (!filled.isEmpty()) {
            ItemStack rest = MachineIO.intoBelow(level, pos, filled);
            if (rest.getCount() != filled.getCount()) {
                this.items.set(FILLED, rest);
                this.setChanged();
            }
        }
    }

    @Override
    public Role mercuryRole() {
        return Role.PRODUCER;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RetortBlockEntity retort) {
        if (level instanceof ServerLevel server) {
            retort.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (this.mercury.amount() > 0) {
            MercuryNetwork.push(level, pos, this, PIPE_RATE);
        }
        this.experience.push(level, pos, this);
        this.bottle(level, pos);
        MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(this.items.get(INPUT));
        // With the charge slot empty, piped water is boiled as if it had come in a bucket.
        boolean fromTank = recipe == null && this.items.get(INPUT).isEmpty() && this.waterStored() >= BUCKET
                && (recipe = MercuryRecipes.retorting(new ItemStack(Items.WATER_BUCKET))) != null;
        // The work done belongs to what it was done on: a charge of cinnabar taken out halfway leaves none of it for the
        // tank's water to start from, nor the water's for a charge put back in.
        if (recipe != null) {
            if (this.workingOn != null && (!recipe.equals(this.workingOn) || fromTank != this.workingFromTank)) {
                this.progress = 0;
            }
            this.workingOn = recipe;
            this.workingFromTank = fromTank;
        }
        MachineStatus blocked = !this.flush(level, pos) ? MachineStatus.OUTPUT_BLOCKED
                : recipe == null ? MachineStatus.IDLE
                : this.mercury.space() < recipe.mercury() ? MachineStatus.MERCURY_FULL
                : !this.hasReagents(recipe) ? MachineStatus.NO_REAGENT : null;
        if (blocked != null) {
            // Nothing to work on - even while it waits for room - and what it had done went with what was in it;
            // stopped for room or a reagent with the charge still in it, it keeps what it had done.
            if (blocked == MachineStatus.IDLE || recipe == null) {
                this.progress = 0;
            }
            this.status = blocked;
            this.showFire(level, pos, false);
            return;
        }
        if (!this.redstoneAllows() || this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = !this.redstoneAllows() ? MachineStatus.REDSTONE : MachineStatus.NO_POWER;
            this.showFire(level, pos, false);
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        this.showFire(level, pos, true);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            if (fromTank) {
                this.water.set(0, this.water.getResource(0), this.waterStored() - BUCKET);
            } else {
                this.items.get(INPUT).shrink(1);
            }
            this.spendReagents(recipe);
            this.mercury.fill(recipe.mercury(), false);
            if (recipe.count() > 0) {
                this.addOutput(new ItemStack(recipe.byproduct(), Math.min(recipe.count(), new ItemStack(recipe.byproduct()).getMaxStackSize())));
            }
            if (!fromTank) {
                recipe.remainder().ifPresent(item -> this.addOutput(new ItemStack(item))); // no bucket came in with piped water
            }
            this.experience.keep(this.level, this.worldPosition, recipe.experience());
            this.flush(level, pos);
        }
        this.setChanged();
    }

    /**
     * How many of each reagent slot one charge takes - each reagent from the first slot that still has one to give -
     * or null when a reagent is missing.
     */
    private int @Nullable [] reagentUse(MercuryRecipes.Retorting recipe) {
        List<ItemStack> held = this.items.subList(FIRST_REAGENT, FIRST_REAGENT + REAGENT_SLOTS);
        return reagentUse(recipe, held, null);
    }

    /**
     * The reagents {@code recipe} still lacks with {@code held} in the reagent slots, as a charge takes them; empty when
     * it has them all. What the screen names under "Missing a reagent", which said nothing of which (r15 playthrough).
     */
    public static List<HolderSet<Item>> missingReagents(MercuryRecipes.Retorting recipe, List<ItemStack> held) {
        List<HolderSet<Item>> missing = new ArrayList<>();
        reagentUse(recipe, held, missing);
        return missing;
    }

    /**
     * The names of what a charge of {@code charge} still lacks with {@code held} in the reagent slots, one after another
     * ("Copper Ingot, Zinc Ingot"); null when nothing is lacking or {@code charge} is nothing the Retort takes. A
     * reagent given as a tag is named by its first item.
     */
    public static @Nullable Component missingReagentNames(ItemStack charge, List<ItemStack> held) {
        MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(charge);
        if (recipe == null) {
            return null;
        }
        MutableComponent names = null;
        for (HolderSet<Item> reagent : missingReagents(recipe, held)) {
            Component name = reagent.stream().findFirst().map(item -> new ItemStack(item).getHoverName())
                    .orElseGet(() -> Component.literal(reagent.unwrapKey().map(tag -> "#" + tag.location()).orElse("?")));
            names = names == null ? name.copy()
                    : names.append(Component.translatable("nookworks.list.separator")).append(name);
        }
        return names;
    }

    /** {@link #reagentUse(MercuryRecipes.Retorting)} over {@code held}; with {@code missing}, it lists what is lacking and goes on. */
    private static int @Nullable [] reagentUse(MercuryRecipes.Retorting recipe, List<ItemStack> held,
            @Nullable List<HolderSet<Item>> missing) {
        int[] used = new int[held.size()];
        for (var reagent : recipe.reagents()) {
            boolean found = false;
            for (int i = 0; i < held.size() && !found; i++) {
                if (held.get(i).is(reagent) && held.get(i).getCount() > used[i]) {
                    used[i]++;
                    found = true;
                }
            }
            if (!found) {
                if (missing == null) {
                    return null;
                }
                missing.add(reagent);
            }
        }
        return used;
    }

    private boolean hasReagents(MercuryRecipes.Retorting recipe) {
        return !recipe.needsReagents() || this.reagentUse(recipe) != null;
    }

    private void spendReagents(MercuryRecipes.Retorting recipe) {
        int[] used = this.reagentUse(recipe);
        for (int i = 0; used != null && i < REAGENT_SLOTS; i++) {
            this.items.get(FIRST_REAGENT + i).shrink(used[i]);
        }
    }

    /**
     * Through a face - a hopper, a pipe - a reagent goes in only for a charge already loaded, and only one that cooks
     * with it. Glass bottles sent down for the fill box (bottles are also what logs are charred with) went into a
     * reagent slot of an empty retort: no bottling started, and brass or pulp then had no slot left for its second
     * reagent. By hand a reagent may still go in first.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        boolean reagentSlot = slot >= FIRST_REAGENT && slot < FIRST_REAGENT + REAGENT_SLOTS;
        if (stack.is(Items.GLASS_BOTTLE) && this.bottles != RetortBottles.AUTO) {
            // the screen's setting: only the fill box, or only the reagents - there before the logs, as the setting asks
            boolean wanted = this.bottles == RetortBottles.EXPERIENCE ? slot == FILL : reagentSlot;
            return wanted && super.canPlaceItemThroughFace(slot, stack, side);
        }
        if (reagentSlot && MercuryRecipes.retorting(this.items.get(INPUT)) == null) {
            return false;
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    public RetortBottles bottles() {
        return this.bottles;
    }

    public void setBottles(RetortBottles bottles) {
        this.bottles = bottles;
        this.setChanged();
    }

    /** The bottle setting, for the screen's button. */
    @Override
    public int guiExtra() {
        return this.bottles.ordinal();
    }

    /**
     * A shift-clicked bottle or bucket goes into the fill box, where the screen shows a bottle - not to the reagents,
     * which take glass bottles too - unless the charge in is one that cooks with it (logs, for wood vinegar).
     */
    @Override
    public int shiftClickSlot(ItemStack stack) {
        if (!ExperienceBottling.fillable(stack)) {
            return -1;
        }
        MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(this.items.get(INPUT));
        return recipe != null && recipe.reagents().stream().anyMatch(stack::is) ? -1 : FILL;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FILL) {
            return ExperienceBottling.fillable(stack);
        }
        if (slot == FILLED) {
            return false;
        }
        if (slot == INPUT) {
            return MercuryRecipes.retorting(stack) != null;
        }
        if (slot < FIRST_REAGENT || slot >= FIRST_REAGENT + REAGENT_SLOTS || !isReagent(stack)) {
            return false;
        }
        // One kind of reagent to a slot. Copper is both the brass charge and one of its reagents, so a hopper of copper
        // would otherwise fill both reagent slots before the zinc arrived, and the zinc could never get in.
        for (int other = FIRST_REAGENT; other < FIRST_REAGENT + REAGENT_SLOTS; other++) {
            if (other != slot && this.items.get(other).is(stack.getItem())) {
                return false;
            }
        }
        // With a charge loaded, only what its entry cooks with.
        MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(this.items.get(INPUT));
        return recipe == null || recipe.reagents().stream().anyMatch(stack::is);
    }

    private static volatile @Nullable Set<Item> reagents;

    /** Anything some retorting entry cooks with, so a hopper cannot fill the slot with the wrong thing. */
    public static boolean isReagent(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var known = reagents;
        if (known == null) {
            // Worked out once per data load, not on every hopper's every try: it walks the whole item registry.
            known = BuiltInRegistries.ITEM.listElements()
                    .map(holder -> holder.getData(MercuryRecipes.RETORTING))
                    .filter(Objects::nonNull)
                    .flatMap(entry -> entry.reagents().stream().flatMap(HolderSet::stream).map(Holder::value))
                    .collect(Collectors.toUnmodifiableSet());
            reagents = known;
        }
        return known.contains(stack.getItem());
    }

    /** Forgets the reagents when the data maps are loaded again (a datapack reload). */
    public static void forgetReagents() {
        reagents = null;
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Reload {
        @SubscribeEvent
        static void onDataMaps(DataMapsUpdatedEvent event) {
            forgetReagents();
        }
    }

    /** What the progress was made on, and whether from the tank's water; not saved, taken up afresh after a load. */
    private MercuryRecipes.@Nullable Retorting workingOn;
    private boolean workingFromTank;

    /**
     * The firebox shows whether a charge is actually cooking, not merely whether the retort has power - and stays up a
     * moment after, so that one short of power does not flicker.
     */
    private void showFire(ServerLevel level, BlockPos pos, boolean lit) {
        RetortBlock.setLit(level, pos, this.getBlockState(), this.showsLit(lit));
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.RETORT;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS);
    }

    @Override
    public Component status() {
        Component base = Component.translatable("nookworks.machine.mercury_status", this.energyText(), this.mercuryText(), this.progress * 100 / MachineTuning.time(TICKS));
        return this.waterStored() <= 0 ? base
                : base.copy().append(Component.translatable("nookworks.machine.retort_water", this.waterStored(), WATER_TANK));
    }

    // ---- the experience a charge leaves behind ----

    public ResourceHandler<FluidResource> experienceHandler() {
        return this.experience;
    }

    /** The experience and the water tank together, as pipes and other mods see them. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluids;
    }

    public int waterStored() {
        return this.water.getAmountAsInt(0);
    }

    /** The experience it keeps, the water it boils and the mercury it frees. */
    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.EXPERIENCE, this.experienceStored(), EXPERIENCE_CAPACITY),
                new GuiTank(GuiTank.Kind.WATER, this.waterStored(), WATER_TANK), this.mercuryTank());
    }

    public int experienceStored() {
        return this.experience.stored();
    }

    /** Its water goes with it, as its mercury does; its experience pours out as orbs. */
    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.water));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (!Tanks.restore(this.water, fluid)) {
            super.restoreFluid(fluid);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.experience.serialize(output.child("Experience"));
        this.water.serialize(output.child("Water"));
        this.bottles.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Experience").ifPresent(this.experience::deserialize);
        input.child("Water").ifPresent(this.water::deserialize);
        this.bottles = RetortBottles.load(input);
    }

    /** Broken, it pours out the experience it held as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        this.experience.spill(this.level, pos);
    }
}
