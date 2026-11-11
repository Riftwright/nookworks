package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.fluid.Bottles;
import dev.nookworks.fluid.LiquidOutlet;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Vacuum Distiller: the base of a four-block tower (this block, two Distillation Column sections, a Condenser
 * on top). Under vacuum the mercury boils off the amalgam at a low temperature, rises through the column and is
 * condensed for reuse; the purified metal stays behind. Only runs while the tower is complete.
 * <p>
 * Wood vinegar distils to methanol, a liquid since 1.5 (the user, 2026-11-07: 「メタノールは液体として扱いたい」): it goes
 * into its methanol tank ({@link #METHANOL_TANK} mB), which pours out into a Copper Pipe whose line leads to a taker, a
 * Copper Tank, another mod's tank or machine beside or under it, as the Oil Press's does. Nothing is bottled: a run
 * whose methanol has no room waits ({@link MachineStatus#TANK_FULL}), a datapack entry with methanol as its result too;
 * a bottle or bucket in its screen's Liquid tab draws it.
 * <p>
 * Wood vinegar comes in as a liquid since 1.5 (the user, 2026-11-10: 「木酢液も液体として作って欲しい」; and their rule
 * for both mods, 2026-11-08: 「入ってくる瓶と汲みだす瓶を一緒にしない。瓶やバケツで投入されたものも、液体として機械にいれる」):
 * piped into its wood vinegar tank ({@link #WOOD_VINEGAR_TANK} mB), or poured there from a bottle or bucket put in its
 * charge slot, the emptied bottle or bucket going out below as its products do - never filled again with methanol. Each
 * bottle's worth of the tank distils to methanol into its methanol tank, which pours out as above; with that tank too
 * full for it, it waits ({@link MachineStatus#TANK_FULL}), as there is no bottle to put it in.
 */
public class DistillerBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 60;
    public static final int TICKS = 120;
    public static final int COLUMN_SECTIONS = 2;
    private static final int OUTPUTS = 4;
    /** Four bottles' worth: only what is on its way out by pipe waits here. */
    public static final int METHANOL_TANK = 1_000;
    /** Sixteen bottles' worth of wood vinegar, piped or poured in, waiting to be distilled. */
    public static final int WOOD_VINEGAR_TANK = 4_000;

    /** Methanol on its way out: filled by its own work only, drawn by pipes (and by a bottle or bucket at its gauge). */
    private final FluidStacksResourceHandler methanol = Tanks.tank(METHANOL_TANK, fluid -> fluid == OilFluids.METHANOL.get(),
            false, true, this::setChanged);
    /** Wood vinegar to distil: filled by pipes and poured from what is put in the charge slot, never drawn by a pipe. */
    private final FluidStacksResourceHandler woodVinegar = Tanks.tank(WOOD_VINEGAR_TANK,
            fluid -> fluid == OilFluids.WOOD_VINEGAR.get(), true, false, this::setChanged);
    /** What pipes and other mods see: the methanol to draw, the wood vinegar to fill. */
    private final ResourceHandler<FluidResource> fluids = new CombinedResourceHandler<>(this.methanol, this.woodVinegar);
    /** Where the methanol goes out: beside it or under it, not up into the column. */
    private final LiquidOutlet outlet = new LiquidOutlet(false);
    /** Whether the work in hand is on the tank's wood vinegar; not saved, taken up afresh after a load. */
    private boolean workingFromTank;

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLER.get(), pos, state, OUTPUTS, 20_000);
    }

    @Override
    public Role mercuryRole() {
        return Role.PRODUCER;
    }

    /** Whether the column sections and the condenser are stacked on top. */
    public static boolean isFormed(LevelReader level, BlockPos base) {
        for (int i = 1; i <= COLUMN_SECTIONS; i++) {
            if (!level.getBlockState(base.above(i)).is(ModBlocks.DISTILLATION_COLUMN.get())) {
                return false;
            }
        }
        return level.getBlockState(base.above(COLUMN_SECTIONS + 1)).is(ModBlocks.CONDENSER.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillerBlockEntity distiller) {
        if (level instanceof ServerLevel server) {
            distiller.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (this.mercury.amount() > 0) {
            MercuryNetwork.push(level, pos, this, PIPE_RATE);
        }
        if (this.methanolStored() > 0) {
            this.pushMethanol(level, pos);
        }
        this.pourIn();
        ItemStack charge = this.items.get(INPUT);
        boolean pouring = pours(charge);
        MercuryRecipes.Distillation recipe = pouring ? null : MercuryRecipes.distillation(charge);
        // with nothing else in the charge slot (or a bottle there waiting for room in the tank), the tank's wood vinegar
        boolean fromTank = recipe == null && (charge.isEmpty() || pouring) && this.woodVinegarStored() >= OilFluids.BOTTLE
                && (recipe = MercuryRecipes.distillation(new ItemStack(ModOil.WOOD_VINEGAR.get()))) != null;
        if (recipe != null && fromTank != this.workingFromTank) {
            this.progress = 0; // the work done belongs to what it was done on
            this.workingFromTank = fromTank;
        }
        boolean flushed = this.flush(level, pos);
        MachineStatus blocked = !isFormed(level, pos) ? MachineStatus.INCOMPLETE
                : !flushed ? MachineStatus.OUTPUT_BLOCKED
                : recipe == null ? MachineStatus.IDLE
                : this.mercury.space() < recipe.mercury() ? MachineStatus.MERCURY_FULL
                : METHANOL_TANK - this.methanolStored() < methanolMade(recipe) ? MachineStatus.TANK_FULL : null;
        if (blocked != null) {
            // Nothing to work on - even while it waits for room - and what it had done went with what was in it;
            // stopped for room or its tower with the work still in it, it keeps what it had done.
            if (blocked == MachineStatus.IDLE || recipe == null) {
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
            if (fromTank) {
                Tanks.set(this.woodVinegar, OilFluids.WOOD_VINEGAR.get(), this.woodVinegarStored() - OilFluids.BOTTLE);
            } else {
                this.items.get(INPUT).shrink(1);
            }
            this.mercury.fill(recipe.mercury(), false);
            boolean methanolResult = methanolMade(recipe) > 0;
            for (ItemStack made : products(level, pos, recipe)) {
                if (methanolResult && made.is(ModOil.METHANOL.get())) {
                    // the methanol goes into its tank as the liquid it is, whatever it was distilled from (a datapack's
                    // entry too): nothing is bottled by the machine (the user's rule, 2026-11-08); the room for it was
                    // asked before the run (TANK_FULL)
                    int room = METHANOL_TANK - this.methanolStored();
                    Tanks.set(this.methanol, OilFluids.METHANOL.get(),
                            this.methanolStored() + Math.min(room, made.getCount() * OilFluids.BOTTLE));
                    continue;
                }
                if (!this.addOutput(made)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, made);
                }
            }
            if (this.methanolStored() > 0) {
                this.pushMethanol(level, pos);
            }
            this.flush(level, pos);
        }
        this.setChanged();
    }

    static List<ItemStack> products(ServerLevel level, BlockPos pos, MercuryRecipes.Distillation recipe) {
        List<ItemStack> products = new ArrayList<>(2);
        if (recipe.result().isPresent()) {
            int count = recipe.count() + (level.getRandom().nextFloat() < recipe.bonusChance() ? recipe.bonus() : 0);
            // a stack at most, whatever a datapack asks: a bigger one is not saved, and is lost with the world closed
            products.add(new ItemStack(recipe.result().get(), Math.min(count, new ItemStack(recipe.result().get()).getMaxStackSize())));
        } else if (recipe.lootTable().isPresent()) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(recipe.lootTable().get());
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            products.addAll(table.getRandomItems(params));
        }
        // none while velarium is switched off (features.json)
        if (level.getRandom().nextFloat() < recipe.velariumChance() && dev.nookworks.feature.Features.enabled(ModItems.VELARIUM_NUGGET.get())) {
            products.add(new ItemStack(ModItems.VELARIUM_NUGGET.get()));
        }
        return products;
    }

    /** Pours its methanol into what takes it beside or under it, not across a claim's edge (LiquidOutlet). */
    private void pushMethanol(ServerLevel level, BlockPos pos) {
        this.outlet.push(level, pos, this, this.methanol);
    }

    /** The methanol one run of {@code recipe} makes, in mB (its bonus counted in, so there is always room for it). */
    private static int methanolMade(MercuryRecipes.Distillation recipe) {
        return recipe.result().filter(result -> result.value() == ModOil.METHANOL.get()).isPresent()
                ? (recipe.count() + (recipe.bonusChance() > 0 ? recipe.bonus() : 0)) * OilFluids.BOTTLE : 0;
    }

    /**
     * Whether {@code stack} is wood vinegar the Distiller pours into its tank rather than distilling as it stands: a
     * Bottle of Wood Vinegar, a Wood Vinegar Bucket.
     */
    public static boolean pours(ItemStack stack) {
        return pouredAmount(stack) > 0;
    }

    private static int pouredAmount(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.content == OilFluids.WOOD_VINEGAR.get()) {
            return 1_000;
        }
        return Bottles.fluidOf(stack) == OilFluids.WOOD_VINEGAR.get() ? OilFluids.BOTTLE : 0;
    }

    /** One bottle or bucket from the charge slot into the wood vinegar tank, the empty container into the outputs. */
    private void pourIn() {
        ItemStack charge = this.items.get(INPUT);
        int amount = pouredAmount(charge);
        if (amount <= 0 || WOOD_VINEGAR_TANK - this.woodVinegarStored() < amount) {
            return;
        }
        ItemStack empty = new ItemStack(charge.getItem() instanceof BucketItem ? Items.BUCKET : Items.GLASS_BOTTLE);
        if (!this.addOutput(empty)) {
            return; // no room for the empty one: it waits, as a product would
        }
        charge.shrink(1);
        Tanks.set(this.woodVinegar, OilFluids.WOOD_VINEGAR.get(), this.woodVinegarStored() + amount);
        this.setChanged();
    }

    public int woodVinegarStored() {
        return this.woodVinegar.getAmountAsInt(0);
    }

    public int methanolStored() {
        return this.methanol.getAmountAsInt(0);
    }

    /** Its tanks as pipes and other mods see them: the methanol drawn from, never filled; the wood vinegar filled, never drawn. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluids;
    }

    /** The methanol tank alone: drawn from, never filled. */
    public FluidStacksResourceHandler methanolTank() {
        return this.methanol;
    }

    /** The wood vinegar tank alone. */
    public FluidStacksResourceHandler woodVinegarTank() {
        return this.woodVinegar;
    }

    /** The methanol it pipes off and the wood vinegar it distils (the mercury it gives back has a gauge of its own kind). */
    @Override
    public @Nullable ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.methanol;
            case 1 -> this.woodVinegar;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.METHANOL, this.methanolStored(), METHANOL_TANK),
                new GuiTank(GuiTank.Kind.WOOD_VINEGAR, this.woodVinegarStored(), WOOD_VINEGAR_TANK), this.mercuryTank());
    }

    /** Its methanol and wood vinegar go with it, as its mercury does. */
    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.methanol));
        out.accept(Tanks.held(this.woodVinegar));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (!Tanks.restore(this.methanol, fluid) && !Tanks.restore(this.woodVinegar, fluid)) {
            super.restoreFluid(fluid);
        }
    }

    /** "methanol" and "wood_vinegar" only since 1.5: a Distiller saved before has neither, and its tanks start empty. */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.methanol.serialize(output.child("methanol"));
        this.woodVinegar.serialize(output.child("wood_vinegar"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("methanol").ifPresent(this.methanol::deserialize);
        input.child("wood_vinegar").ifPresent(this.woodVinegar::deserialize);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return takes(slot, stack);
    }

    /** What slot {@code slot} takes: the machine's rule, and its screen's on the client (MachineMenu). */
    public static boolean takes(int slot, ItemStack stack) {
        return slot == INPUT && (MercuryRecipes.distillation(stack) != null || pours(stack));
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.DISTILLER;
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
        if (this.level != null && !isFormed(this.level, this.worldPosition)) {
            return Component.translatable("nookworks.machine.distiller.incomplete");
        }
        return Component.translatable("nookworks.machine.mercury_status", this.energyText(), this.mercuryText(), MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
    }
}
