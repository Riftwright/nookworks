package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import org.jspecify.annotations.Nullable;

/**
 * Stirring Tank. Amalgamation: crushed ore or rock is stirred with mercury, which dissolves the metal in it and becomes
 * amalgam; it draws mercury from tanks on its pipe network, and the amalgam goes into the container below.
 * <p>
 * Biodiesel: with methanol in the charge slot and caustic soda in the second, it stirs {@link #OIL_PER_BATCH} mB of
 * vegetable oil - piped into its oil tank - into as much biodiesel, which it pushes out of its top into a Copper Pipe or
 * tank; the methanol's bottle goes below with the rest. So a press, this tank and a Generator make a line that runs by
 * itself, fed with seeds, methanol and caustic soda by hopper.
 * <p>
 * Mud: dirt - whatever vanilla turns to mud under a water bottle - stirred with a bottle's worth of water,
 * {@link #WATER_PER_MUD} mB piped in, becomes mud, into the container below; the Sifter gets clay and rice out of mud
 * (the user, 2026-10-03: mud made by a machine, and the Stirring Tank is the one).
 */
public class StirringTankBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    public static final int TICKS = 80;

    public static final int CATALYST = 1;
    public static final int OIL_TANK = 4_000;
    public static final int BIODIESEL_TANK = 1_000;
    public static final int OIL_PER_BATCH = 250;
    /**
     * Caustic soda is the catalyst, barely used up: one serves this many batches, a bucket of oil (the user, 2026-10-12:
     * a soda a batch spent the mercury cell's mercury on every 250 mB, a cinnabar an hour for one Diesel Generator).
     */
    public static final int BATCHES_PER_SODA = 4;
    public static final int WATER_TANK = 4_000;
    /** A water bottle's worth, as vanilla makes mud with a bottle. */
    public static final int WATER_PER_MUD = 250;

    private final FluidStacksResourceHandler oil = Tanks.tank(OIL_TANK,
            fluid -> fluid == OilFluids.SOURCE.get(), true, false, this::setChanged);
    private final FluidStacksResourceHandler biodiesel = Tanks.tank(BIODIESEL_TANK,
            fluid -> fluid == OilFluids.BIODIESEL.get(), false, true, this::setChanged);
    private final FluidStacksResourceHandler water = Tanks.tank(WATER_TANK, fluid -> fluid == Fluids.WATER,
            true, false, this::setChanged);
    private final ResourceHandler<FluidResource> fluids =
            new CombinedResourceHandler<>(this.water, this.oil, this.biodiesel);

    /**
     * Whether water or oil has come in through the top: then the top is a feed, and no biodiesel is pushed up into it.
     * The pipe bringing oil down onto the tank was filled with biodiesel the first time it ran empty, and the oil line
     * stopped (round two of r21). Forgotten once nothing that holds liquid is above.
     */
    private boolean topFeeds;
    /** {@link #topFeeds} kept with the transaction that set it: a pipe asking what would go in leaves it as it was. */
    private final SnapshotJournal<Boolean> topFeedsJournal = new SnapshotJournal<>() {
        @Override
        protected Boolean createSnapshot() {
            return StirringTankBlockEntity.this.topFeeds;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {
            StirringTankBlockEntity.this.topFeeds = snapshot;
        }

        @Override
        protected void onRootCommit(Boolean before) {
            if (!before && StirringTankBlockEntity.this.topFeeds) {
                StirringTankBlockEntity.this.setChanged();
            }
        }
    };
    /** The tanks as the side above reaches them: what comes in by it makes the top a feed. */
    private final ResourceHandler<FluidResource> fromAbove = Tanks.noting(this.fluids, transaction -> {
        if (!this.topFeeds) {
            this.topFeedsJournal.updateSnapshots(transaction);
            this.topFeeds = true;
        }
    });

    /** Batches still to be stirred with the caustic soda last taken from the slot. */
    private int catalystLeft;

    public StirringTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STIRRING_TANK.get(), pos, state, 2, 1, 10_000);
    }

    /** Water and oil in, biodiesel out, from any side (a pipe only takes what it carries). */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluids;
    }

    /** {@link #fluidHandler} as {@code side} reaches it: from above, what comes in makes the top a feed. */
    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return side == Direction.UP ? this.fromAbove : this.fluids;
    }

    /** Whether the top feeds it, so biodiesel stays in until drawn out elsewhere. */
    public boolean topFeeds() {
        return this.topFeeds;
    }

    public int oilStored() {
        return this.oil.getAmountAsInt(0);
    }

    public int biodieselStored() {
        return this.biodiesel.getAmountAsInt(0);
    }

    public int waterStored() {
        return this.water.getAmountAsInt(0);
    }

    private boolean makingBiodiesel() {
        return this.items.get(INPUT).is(ModOil.METHANOL.get());
    }

    private boolean makingMud() {
        return muddies(this.items.get(INPUT));
    }

    /** Whether it stirs this into mud: a block vanilla turns to mud under a water bottle (#minecraft:convertable_to_mud). */
    public static boolean muddies(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().is(BlockTags.CONVERTIBLE_TO_MUD);
    }

    /** Everything it stirs into mud, for the screen's hint and JEI: the items of the tag's blocks. */
    public static List<Item> muddied() {
        List<Item> items = new ArrayList<>();
        for (var block : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.CONVERTIBLE_TO_MUD)) {
            Item item = block.value().asItem();
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public Role mercuryRole() {
        return Role.CONSUMER;
    }

    /**
     * Making biodiesel or mud it has no use for mercury: a retort on the same pipes keeps its mercury for somewhere
     * that does.
     */
    @Override
    public int fillMercury(int amount, boolean simulate) {
        return this.makingBiodiesel() || this.makingMud() ? 0 : super.fillMercury(amount, simulate);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirringTankBlockEntity tank) {
        if (level instanceof ServerLevel server) {
            tank.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        this.pushBiodiesel(level, pos);
        if (this.makingBiodiesel()) {
            this.stirBiodiesel(level, pos);
            return;
        }
        if (this.makingMud()) {
            this.stirMud(level, pos);
            return;
        }
        MercuryRecipes.Amalgamation recipe = MercuryRecipes.amalgamation(this.items.get(INPUT));
        // kept at least half full - and as full as the charge in it needs, should a datapack ask more than half
        if (this.mercury.amount() < Math.max(this.mercury.capacity() / 2, recipe == null ? 0 : recipe.mercury())) {
            MercuryNetwork.pull(level, pos, this, PIPE_RATE);
        }
        MachineStatus blocked = !this.flush(level, pos) ? MachineStatus.OUTPUT_BLOCKED
                : recipe == null ? MachineStatus.IDLE
                : this.mercury.amount() < recipe.mercury() ? MachineStatus.NO_MERCURY : null;
        if (blocked != null) {
            if (blocked == MachineStatus.IDLE || recipe == null) {
                this.progress = 0; // nothing to work on, even while it waits for room
            }
            this.status = blocked;
            return;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            this.items.get(INPUT).shrink(1);
            this.mercury.drain(recipe.mercury(), false);
            this.addOutput(recipe.output());
            this.flush(level, pos);
        }
        this.setChanged();
    }


    private void stirBiodiesel(ServerLevel level, BlockPos pos) {
        MachineStatus blocked = !this.flush(level, pos) ? MachineStatus.OUTPUT_BLOCKED
                : this.catalystLeft <= 0 && !this.items.get(CATALYST).is(ModItems.CAUSTIC_SODA.get()) ? MachineStatus.NO_REAGENT
                : this.oilStored() < OIL_PER_BATCH ? MachineStatus.LOW_OIL
                : BIODIESEL_TANK - this.biodieselStored() < OIL_PER_BATCH ? MachineStatus.TANK_FULL : null;
        if (blocked != null) {
            if (blocked == MachineStatus.LOW_OIL) {
                this.progress = 0;
            }
            this.status = blocked;
            return;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            this.items.get(INPUT).shrink(1);
            if (this.catalystLeft > 0) {
                this.catalystLeft--;
            } else {
                this.items.get(CATALYST).shrink(1);
                this.catalystLeft = BATCHES_PER_SODA - 1;
            }
            Tanks.set(this.oil, OilFluids.SOURCE.get(), this.oilStored() - OIL_PER_BATCH);
            Tanks.set(this.biodiesel, OilFluids.BIODIESEL.get(), this.biodieselStored() + OIL_PER_BATCH);
            this.addOutput(new ItemStack(Items.GLASS_BOTTLE));
            this.flush(level, pos);
        }
        this.setChanged();
    }

    private void stirMud(ServerLevel level, BlockPos pos) {
        MachineStatus blocked = !this.flush(level, pos) ? MachineStatus.OUTPUT_BLOCKED
                : this.waterStored() < WATER_PER_MUD ? MachineStatus.LOW_WATER : null;
        if (blocked != null) {
            this.status = blocked;
            return;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            this.items.get(INPUT).shrink(1);
            Tanks.set(this.water, Fluids.WATER, this.waterStored() - WATER_PER_MUD);
            this.addOutput(new ItemStack(Items.MUD));
            this.flush(level, pos);
        }
        this.setChanged();
    }

    /** Out of the top, into a pipe or tank that takes biodiesel. */
    private void pushBiodiesel(ServerLevel level, BlockPos pos) {
        if (this.topFeeds && level.getBlockEntity(pos.above()) == null) {
            // the feed above is gone: the top is free to send out again (not when a Valve there is shut, or its side
            // switched off - that is still the feed, and opened again it would be filled; the round-two fix re-check)
            this.topFeeds = false;
            this.setChanged();
        }
        if (this.biodieselStored() <= 0 || this.topFeeds) {
            return;
        }
        var target = Tanks.above(level, pos);
        if (target != null && level.getBlockEntity(pos.above()) instanceof FluidPipeBlockEntity pipe && pipe.stored() <= 0
                && !this.leadsOn(level, pos, pipe)) {
            return; // an empty pipe whose line takes no biodiesel anywhere: a feed, or a line not finished yet
        }
        // not across a claim's edge into a stranger's tank, as the Water Pump and a Copper Tank ask (r21, 2026-11-06)
        if (target != null && DrawRights.mayMove(level, pos, this, pos.above(), level.getBlockEntity(pos.above()))) {
            Move.transfer(this.biodiesel, target, this.biodiesel.getResource(0), this.biodieselStored());
        }
    }

    /** {@link FluidPipeBlockEntity#leadsToTakerOf}, for biodiesel, asked once a second at most. */
    private boolean leadsOn(ServerLevel level, BlockPos pos, FluidPipeBlockEntity pipe) {
        long now = level.getGameTime();
        if (now - this.leadsAskedAt >= 20 || now < this.leadsAskedAt) {
            this.leadsAskedAt = now;
            this.leads = pipe.leadsToTakerOf(level, this.biodiesel.getResource(0), pos, 512);
        }
        return this.leads;
    }

    private long leadsAskedAt = Long.MIN_VALUE / 2;
    private boolean leads;

    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.water));
        out.accept(Tanks.held(this.oil));
        out.accept(Tanks.held(this.biodiesel));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (!Tanks.restore(this.water, fluid) && !Tanks.restore(this.oil, fluid) && !Tanks.restore(this.biodiesel, fluid)) {
            super.restoreFluid(fluid);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.water.serialize(output.child("Water"));
        this.oil.serialize(output.child("Oil"));
        this.biodiesel.serialize(output.child("Biodiesel"));
        output.putInt("CatalystLeft", this.catalystLeft);
        if (this.topFeeds) {
            output.putBoolean("top_feeds", true);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Water").ifPresent(this.water::deserialize);
        input.child("Oil").ifPresent(this.oil::deserialize);
        input.child("Biodiesel").ifPresent(this.biodiesel::deserialize);
        this.catalystLeft = Math.clamp(input.getIntOr("CatalystLeft", 0), 0, BATCHES_PER_SODA - 1);
        this.topFeeds = input.getBooleanOr("top_feeds", false);
    }

    /**
     * The soda already taken in goes with its item when it is broken, as its power and liquids do: up to three batches
     * of the four a soda serves were lost on every move (r15 late audit, N2).
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.catalystLeft > 0) {
            components.set(ModDataComponents.CATALYST_LEFT.get(), this.catalystLeft);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer left = components.get(ModDataComponents.CATALYST_LEFT.get());
        if (left != null) {
            this.catalystLeft = Math.clamp(left, 0, BATCHES_PER_SODA - 1);
        }
    }

    /** Batches the soda already taken still serves. Public for tests. */
    public int catalystLeft() {
        return this.catalystLeft;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return takes(slot, stack);
    }

    /** What slot {@code slot} takes: the machine's rule, and its screen's on the client (MachineMenu). */
    public static boolean takes(int slot, ItemStack stack) {
        if (slot == CATALYST) {
            return stack.is(ModItems.CAUSTIC_SODA.get());
        }
        return slot == INPUT && (MercuryRecipes.amalgamation(stack) != null || stack.is(ModOil.METHANOL.get()) || muddies(stack));
    }

    /** The water for mud, the biodiesel it makes, the oil it takes and the mercury for amalgam. */
    @Override
    public ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.water;
            case 1 -> this.biodiesel;
            case 2 -> this.oil;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.WATER, this.waterStored(), WATER_TANK),
                new GuiTank(GuiTank.Kind.BIODIESEL, this.biodieselStored(), BIODIESEL_TANK),
                new GuiTank(GuiTank.Kind.VEGETABLE_OIL, this.oilStored(), OIL_TANK), this.mercuryTank());
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.STIRRING_TANK;
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
        // making mud, the water it stirs in is what counts, not the mercury it has no use for
        Component held = this.makingMud() ? Component.translatable("nookworks.water.amount", this.waterStored(), WATER_TANK)
                : this.mercuryText();
        return Component.translatable("nookworks.machine.mercury_status", this.energyText(), held, MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
    }
}
