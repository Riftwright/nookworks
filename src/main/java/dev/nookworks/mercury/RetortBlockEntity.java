package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.experience.ExperienceBuffer;
import dev.nookworks.fluid.LiquidOutlet;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GaugePouring;
import dev.nookworks.machine.GuiTank;
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
import net.minecraft.world.Container;
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
 * Its three input slots are all alike (since 1.9): an entry cooks once they hold its key and its reagents between
 * them, in any slot - brass's two copper as one stack beside the zinc ({@link #charges}). A kraft digester is a heated
 * sealed vessel like any other, so pulping wood chips in caustic soda belongs here rather than on a crafting table.
 * <p>
 * Like a furnace, every charge leaves a little experience behind. The Retort keeps it as Liquid Experience (up to
 * {@link #EXPERIENCE_CAPACITY} mB) and pushes it into an Experience Pipe or tank beside it - ours or another mod's -
 * which may also draw it out. Broken, it pours what it held out as orbs.
 * <p>
 * Water can come in by pipe as well as by the bucket: a Copper Pipe (or another mod's) fills a tank of
 * {@link #WATER_TANK} mB, and with nothing to cook in its slots, each bucket's worth of it is boiled down just as a
 * water bucket would be.
 * <p>
 * Logs give their wood vinegar as a liquid since 1.5 (the user, 2026-11-10: 「木酢液も液体として作って欲しい」; and their
 * rule for both mods, 2026-11-08: 「そもそも瓶しかない液体があるのは、工業modとして扱いづらい」): no glass bottle goes in with
 * them, and what an entry gives off as a liquid ({@code liquid} in its data) is kept in a tank of {@link #LIQUID_TANK} mB
 * and pushed out into a pipe or tank beside it that takes it, as the Vacuum Distiller's methanol is (any side). With
 * the tank too full for a charge's worth it waits; nothing is bottled.
 * <p>
 * Its fill box (an empty bottle or bucket in, filled with the experience, out) and the screen's setting for where a
 * hopper's glass bottles go are gone since 1.5, under the user's rule for both mods (2026-11-08: 「入ってくる瓶と汲みだす
 * 瓶を一緒にしない」): the experience goes out by pipe, or into a bottle or bucket in its screen's Liquid tab. What an
 * old world kept in the box is put out below at the first tick ({@link #putOutLeftovers}).
 */
public class RetortBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 30;
    public static final int TICKS = 100;
    /** The three input slots, all alike: whatever goes into a charge goes in any of them ({@link #charges}). */
    public static final int INPUT_SLOTS = 3;
    private static final int INPUTS = INPUT_SLOTS;
    /** The by-product, plus room for an emptied container. */
    private static final int OUTPUTS = 2;

    public static final int EXPERIENCE_CAPACITY = 1_000;
    public static final int WATER_TANK = 4_000;
    /** Sixteen logs' wood vinegar, on its way out. */
    public static final int LIQUID_TANK = 4_000;
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

    /**
     * What the charges give off as a liquid (wood vinegar from logs), on its way out: filled by its own work only, drawn
     * by pipes (and by a bottle or bucket at its gauge). One liquid at a time.
     */
    private final FluidStacksResourceHandler liquid = Tanks.tank(LIQUID_TANK, fluid -> true, false, true, this::setChanged);

    /** What pipes see: the experience and the liquid to draw out, and the water tank to fill. */
    private final ResourceHandler<FluidResource> fluids =
            new CombinedResourceHandler<>(this.experience, this.water, this.liquid);

    /** Where the liquid goes out: any side. */
    private final LiquidOutlet outlet = new LiquidOutlet(true);

    private static final int[] SIDE_SLOTS = {0, 1, 2};
    private static final int[] DOWN_SLOTS = {INPUTS, INPUTS + 1};

    public RetortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RETORT.get(), pos, state, INPUTS, OUTPUTS, 0, 10_000);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? DOWN_SLOTS : SIDE_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && (slot == INPUTS || slot == INPUTS + 1);
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
        this.putOutLeftovers(level, pos);
        if (this.mercury.amount() > 0) {
            MercuryNetwork.push(level, pos, this, PIPE_RATE);
        }
        this.experience.push(level, pos, this);
        if (this.liquidStored() > 0) {
            this.outlet.push(level, pos, this, this.liquid);
        }
        List<Charge> charges = charges(this.inputs());
        Charge charge = charges.isEmpty() ? null : charges.getFirst();
        MercuryRecipes.Retorting recipe = charge == null ? null : charge.recipe();
        // With nothing in the slots to cook, piped water is boiled as if it had come in a bucket.
        boolean fromTank = charge == null && this.waterStored() >= BUCKET
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
        MachineStatus blocked = !this.flush(level, pos) ? this.outputWaits()
                : recipe == null ? (named(this.inputs()).isEmpty() ? MachineStatus.IDLE : MachineStatus.NO_REAGENT)
                : this.mercury.space() < recipe.mercury() ? MachineStatus.MERCURY_FULL
                : !this.roomFor(recipe) ? MachineStatus.TANK_FULL
                : !fromTank && !charge.complete() ? MachineStatus.NO_REAGENT : null;
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
                for (int s = 0; s < INPUT_SLOTS; s++) {
                    this.items.get(INPUT + s).shrink(charge.used()[s]);
                }
            }
            this.mercury.fill(recipe.mercury(), false);
            if (recipe.count() > 0) {
                this.addOutput(new ItemStack(recipe.byproduct(), Math.min(recipe.count(), new ItemStack(recipe.byproduct()).getMaxStackSize())));
            }
            if (!fromTank) {
                recipe.remainder().ifPresent(item -> this.addOutput(new ItemStack(item))); // no bucket came in with piped water
            }
            recipe.liquid().ifPresent(given -> Tanks.set(this.liquid, given.getFluid(), this.liquidStored() + given.getAmount()));
            this.experience.keep(this.level, this.worldPosition, recipe.experience());
            if (this.liquidStored() > 0) {
                this.outlet.push(level, pos, this, this.liquid);
            }
            this.flush(level, pos);
        }
        this.setChanged();
    }

    /** Whether the liquid tank has room for what a charge of {@code recipe} gives off (and holds nothing else). */
    private boolean roomFor(MercuryRecipes.Retorting recipe) {
        if (recipe.liquid().isEmpty()) {
            return true;
        }
        var given = recipe.liquid().get();
        return (this.liquidStored() == 0 || this.liquid.getResource(0).getFluid().isSame(given.getFluid()))
                && LIQUID_TANK - this.liquidStored() >= given.getAmount();
    }

    /**
     * A charge the input slots name: an entry keyed by one slot's item, what it would take from each slot - its key and
     * every reagent, each from the first slot that still has one to give - and what it still lacks.
     */
    public record Charge(MercuryRecipes.Retorting recipe, int[] used, List<HolderSet<Item>> missing) {
        public boolean complete() {
            return this.missing.isEmpty();
        }

        /** Its key and reagents: how many things go into one charge. */
        int size() {
            return 1 + this.recipe.reagents().size();
        }
    }

    /**
     * Every entry {@code held} (the three input slots) names - each slot's item read as an entry's key - the best first:
     * those the slots hold all of before the rest, among them the one of the most things (brass and pulp before a lone
     * cinnabar, so the copper is not taken for something else), and among those short, the one lacking least; then by
     * slot. The slots are all alike (the user, 2026-11-25, of a charge slot and two reagent slots with brass's copper
     * split between them: 「もう主材料・副材料みたな括りで入力欄あるの不自然じゃない？」).
     */
    public static List<Charge> charges(List<ItemStack> held) {
        List<Charge> found = new ArrayList<>();
        for (int i = 0; i < held.size(); i++) {
            MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(held.get(i));
            if (recipe == null || found.stream().anyMatch(c -> c.recipe().equals(recipe))) {
                continue;
            }
            found.add(charge(recipe, held.get(i).typeHolder(), held));
        }
        return sorted(found);
    }

    private static List<Charge> sorted(List<Charge> found) {
        found.sort(Comparator.comparing((Charge c) -> !c.complete())
                .thenComparingInt(c -> c.complete() ? -c.size() : c.missing().size()));
        return found;
    }

    /**
     * The charge of {@code recipe} keyed by {@code key}: what it takes from each slot, its key and every reagent. Each
     * need is matched to a slot so that as few as can be go short: a tag that an iron ingot and a gold one both fill, and
     * an iron ingot named after it, took the iron for the tag and found none left for the iron (r24 CX-05); the slots
     * are tried in order, so where several ways serve, it is the first.
     */
    public static Charge charge(MercuryRecipes.Retorting recipe, Holder<Item> key, List<ItemStack> held) {
        List<HolderSet<Item>> needs = new ArrayList<>();
        needs.add(HolderSet.direct(key));
        needs.addAll(recipe.reagents());
        int[] used = new int[held.size()];
        int[] best = new int[held.size()];
        boolean[] got = new boolean[needs.size()];
        boolean[] bestGot = new boolean[needs.size()];
        int[] fewest = {Integer.MAX_VALUE};
        match(needs, held, 0, used, got, 0, best, bestGot, fewest);
        List<HolderSet<Item>> missing = new ArrayList<>();
        for (int n = 0; n < needs.size(); n++) {
            if (!bestGot[n]) {
                missing.add(needs.get(n));
            }
        }
        return new Charge(recipe, best, missing);
    }

    /** {@link #charge}'s search: needs from {@code n} on matched to the slots, keeping the way with the fewest short. */
    private static void match(List<HolderSet<Item>> needs, List<ItemStack> held, int n, int[] used, boolean[] got, int short_,
            int[] best, boolean[] bestGot, int[] fewest) {
        if (short_ >= fewest[0]) {
            return;
        }
        if (n == needs.size()) {
            fewest[0] = short_;
            System.arraycopy(used, 0, best, 0, used.length);
            System.arraycopy(got, 0, bestGot, 0, got.length);
            return;
        }
        for (int s = 0; s < held.size(); s++) {
            if (held.get(s).is(needs.get(n)) && held.get(s).getCount() > used[s]) {
                used[s]++;
                got[n] = true;
                match(needs, held, n + 1, used, got, short_, best, bestGot, fewest);
                used[s]--;
                got[n] = false;
                if (fewest[0] == 0) {
                    return;
                }
            }
        }
        match(needs, held, n + 1, used, got, short_ + 1, best, bestGot, fewest);
    }

    /**
     * As {@link #charges}; and with no key in the slots, the entries what is in them would go into, each lacking its key:
     * what the screen names and shows in the empty slots when only a charge's other things are in - a zinc ingot left
     * after the copper ran out said only "Idle" (r23 self-review). Never cooked from: the tick reads {@link #charges}.
     */
    private static List<Charge> named(List<ItemStack> held) {
        List<Charge> found = charges(held);
        List<ItemStack> in = held.stream().filter(stack -> !stack.isEmpty() && isReagent(stack)).toList();
        if (!found.isEmpty() || in.isEmpty()) {
            return found;
        }
        for (var key : BuiltInRegistries.ITEM.getDataMap(MercuryRecipes.RETORTING).keySet().stream()
                .sorted(Comparator.comparing(entry -> entry.identifier().toString())).toList()) {
            Item item = BuiltInRegistries.ITEM.getValue(key);
            Holder<Item> holder = item == null ? null : item.builtInRegistryHolder();
            MercuryRecipes.Retorting recipe = holder == null ? null : MercuryRecipes.retorting(new ItemStack(holder));
            if (recipe != null && in.stream().allMatch(stack -> recipe.reagents().stream().anyMatch(stack::is))) {
                found.add(charge(recipe, holder, held));
            }
        }
        return sorted(found);
    }

    /**
     * What {@code charges} lack that needs a slot of its own: not more of a thing a slot holds already (brass's second
     * copper goes onto the first), and one slot for what two of them both want.
     */
    private static List<HolderSet<Item>> newSlots(List<Charge> charges, List<ItemStack> held) {
        List<HolderSet<Item>> wanted = new ArrayList<>();
        for (Charge charge : charges) {
            for (HolderSet<Item> need : charge.missing()) {
                if (held.stream().noneMatch(stack -> !stack.isEmpty() && stack.is(need))
                        && wanted.stream().noneMatch(other -> need.stream().anyMatch(other::contains))) {
                    wanted.add(need);
                }
            }
        }
        return wanted;
    }

    /** What the empty input slots are wanted for, in turn: the best charge's things that need a slot of their own. */
    public static List<HolderSet<Item>> slotNeeds(List<ItemStack> held) {
        List<Charge> charges = named(held);
        return charges.isEmpty() ? List.of() : newSlots(List.of(charges.getFirst()), held);
    }

    /**
     * The names of what the best charge the slots name still lacks, one after another ("Copper Ingot, Zinc Ingot"); null
     * when one is whole or nothing in them is a charge. What the screen names under its status, which said nothing of
     * which (r15 playthrough). A thing given as a tag is named by its first item.
     */
    public static @Nullable Component missingNames(List<ItemStack> held) {
        List<Charge> charges = named(held);
        if (charges.isEmpty() || charges.getFirst().complete()) {
            return null;
        }
        // the same thing twice once, counted: brass with only its zinc in lacks "Copper Ingot ×2"
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, Component> shown = new java.util.HashMap<>();
        for (HolderSet<Item> need : charges.getFirst().missing()) {
            Component name = need.stream().findFirst().map(item -> new ItemStack(item).getHoverName())
                    .orElseGet(() -> Component.literal(need.unwrapKey().map(tag -> "#" + tag.location()).orElse("?")));
            counts.merge(name.getString(), 1, Integer::sum);
            shown.putIfAbsent(name.getString(), name);
        }
        MutableComponent names = null;
        for (var entry : counts.entrySet()) {
            MutableComponent name = shown.get(entry.getKey()).copy();
            if (entry.getValue() > 1) {
                name.append(" \u00d7" + entry.getValue());
            }
            names = names == null ? name : names.append(Component.translatable("nookworks.list.separator")).append(name);
        }
        return names;
    }

    private List<ItemStack> inputs() {
        return this.items.subList(INPUT, INPUT + INPUT_SLOTS);
    }

    /**
     * Through a face - a hopper, a pipe - into an input slot only what {@link #goesWith} what the slots already hold. A
     * hopper's cinnabar after the copper waits, instead of taking the slot the zinc needs. By hand anything the Retort
     * takes goes in.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot >= INPUT && slot < INPUT + INPUT_SLOTS && !goesWith(this.inputs(), stack)) {
            return false;
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    /**
     * Whether a hopper or a pipe may put {@code stack} into input slots holding {@code held}: more of what a slot holds;
     * else a key, or a thing a charge keyed in the slots still lacks, when every charge the slots then name can still be
     * made whole in the slots left over. One entry taking all that was in (until r23) let a lone reagent in first:
     * nookTechnology's Fine Glass and pulp both take caustic soda, so a mixed hopper's soda and then limestone powder
     * held the Retort for a quartz that never came, its chips and sulfur waiting behind; and a thing no charge takes,
     * left in a slot from an old world, shut it to every hopper (r23 self-review).
     */
    public static boolean goesWith(List<ItemStack> held, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        int first = -1;
        for (int s = 0; s < held.size(); s++) {
            if (held.get(s).isEmpty()) {
                first = first < 0 ? s : first;
            } else if (held.get(s).is(stack.getItem())) {
                return true; // more of it, onto its own slot
            }
        }
        if (first < 0) {
            return false;
        }
        if (MercuryRecipes.retorting(stack) == null
                && newSlots(charges(held), held).stream().noneMatch(stack::is)) {
            return false; // no key, nor wanted by a charge begun
        }
        List<ItemStack> after = new ArrayList<>(held);
        after.set(first, stack.copyWithCount(1));
        long room = after.stream().filter(ItemStack::isEmpty).count();
        return newSlots(charges(after), after).size() <= room;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return takes(this, slot, stack);
    }

    /**
     * What slot {@code slot} of a Retort holding {@code items} takes: the machine's rule, and its screen's on the client,
     * which holds the slots the server sent (MachineMenu), so a shift-click lands there as it will on the server. Any
     * input slot takes anything some entry cooks, as its key or with it - one kind to a slot, so a hopper of copper goes
     * onto the copper there and leaves the other slots for the zinc.
     */
    public static boolean takes(Container items, int slot, ItemStack stack) {
        if (slot < INPUT || slot >= INPUT + INPUT_SLOTS || MercuryRecipes.retorting(stack) == null && !isReagent(stack)) {
            return false;
        }
        for (int other = INPUT; other < INPUT + INPUT_SLOTS; other++) {
            if (other != slot && items.getItem(other).is(stack.getItem())) {
                return false;
            }
        }
        return true;
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
        Component base = Component.translatable("nookworks.machine.mercury_status", this.energyText(), this.mercuryText(), MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
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

    /** The water tank alone, which a bucket clicked on the Retort pours into. */
    public ResourceHandler<FluidResource> waterHandler() {
        return this.water;
    }

    /** The experience it keeps, the water it boils, the liquid it gives off and the mercury it frees. */
    @Override
    public ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.experienceHandler();
            case 1 -> this.water;
            case 2 -> this.liquid;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.EXPERIENCE, this.experienceStored(), EXPERIENCE_CAPACITY),
                new GuiTank(GuiTank.Kind.WATER, this.waterStored(), WATER_TANK),
                new GuiTank(GaugePouring.kindOf(this.liquid.getResource(0).getFluid(), GuiTank.Kind.WOOD_VINEGAR), this.liquidStored(),
                        LIQUID_TANK), this.mercuryTank());
    }

    /** How much of the liquid it gives off (wood vinegar) it holds. */
    public int liquidStored() {
        return this.liquid.getAmountAsInt(0);
    }

    /**
     * Whether it gives off a liquid now or is about to: some in its tank, or a charge whose run makes one (a log's wood
     * vinegar). A Vacuum Distiller sends it no water then - water standing in its pipes would bar that liquid's way.
     */
    public boolean givesOffLiquid() {
        if (this.liquidStored() > 0) {
            return true;
        }
        // any of the three slots: logs shift-clicked in beside a cinnabar charge land in the second (r23 self-review)
        for (ItemStack in : this.inputs()) {
            MercuryRecipes.Retorting recipe = MercuryRecipes.retorting(in);
            if (recipe != null && recipe.liquid().isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** That tank alone: drawn from, never filled. */
    public FluidStacksResourceHandler liquidTank() {
        return this.liquid;
    }

    public int experienceStored() {
        return this.experience.stored();
    }

    /** Its water and wood vinegar go with it, as its mercury does; its experience pours out as orbs. */
    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.water));
        out.accept(Tanks.held(this.liquid));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        // water into the water tank, as it always went; anything else of ours (wood vinegar) into the liquid tank
        if (!Tanks.restore(this.water, fluid) && (fluid.getFluid() == MercuryFluids.SOURCE.get() || !Tanks.restore(this.liquid, fluid))) {
            super.restoreFluid(fluid);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.experience.serialize(output.child("Experience"));
        this.water.serialize(output.child("Water"));
        this.liquid.serialize(output.child("liquid")); // since 1.5: a Retort saved before has none, its tank empty
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Experience").ifPresent(this.experience::deserialize);
        input.child("Water").ifPresent(this.water::deserialize);
        input.child("liquid").ifPresent(this.liquid::deserialize);
        // its old bottle setting ("Bottles", 1.4) is no longer read: there is no fill box for it to choose
    }

    /** Broken, it pours out the experience it held as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        this.experience.spill(this.level, pos);
    }
}
