package dev.nookworks.machine;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import dev.nookworks.block.GeneratorBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.loot.NeoForgeLootContextParams;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Generator: burns furnace fuel into FE at 40 FE/t for the fuel's furnace burn time (one coal gives 64,000 FE, enough
 * to run two machines), stores 50,000 FE and pushes up to 200 FE/t, split evenly between everything connected.
 * Pauses while full. With nothing in its slot it burns liquid fuel piped into its tank - vegetable oil, or biodiesel
 * for twice as long - so an Oil Press piped to it runs it without a hand.
 */
public class GeneratorBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 40;
    public static final int CAPACITY = 50_000;
    public static final int MAX_PUSH = 200;
    private static final int[] SLOTS = {0};

    /** Liquid fuel, piped in: vegetable oil or biodiesel, one at a time. */
    public static final int FUEL_TANK = 4_000;
    /**
     * mB of liquid fuel taken at a time, burned for this many ticks per mB (a bucket burns as long as its item). Oil and
     * biodiesel alike: they hold about the same energy, as they do (the user, 2026-10-12) - biodiesel is made for the
     * Diesel Generator, the engine that gets more out of it, not to burn longer here.
     * <p>
     * The gulp is the Diesel Generator's, for the same reason: what is burning is not kept in the item when the
     * Generator is broken (as a furnace's fire is not), so a small one loses little - 15 mB, 240 ticks. It was 100 mB,
     * and a Generator full and banked held up to a tenth of a bucket that its item threw away (r15 late audit, N4).
     */
    public static final int GULP = 15;
    public static final int OIL_TICKS_PER_MB = 16;
    public static final int BIODIESEL_TICKS_PER_MB = 16;

    private final FluidStacksResourceHandler fuelTank = Tanks.tank(FUEL_TANK,
            fluid -> fluid == OilFluids.SOURCE.get() || fluid == OilFluids.BIODIESEL.get(),
            true, false, this::setChanged);

    private int burnTime;
    /** How long the fuel now burning lasts in all, for the flame on the screen. */
    private int burnTotal;
    /**
     * The game time it last really burned (not saved). Its fire shows only while it burns: full, with nothing drawing
     * on it, it banks the fire and looks cold. A generator on a small steady load burns every other tick or so, so the
     * flame stays up for {@link #EMBERS} ticks after the last burn rather than flickering.
     */
    private static final int EMBERS = 40;
    private long lastBurned = -EMBERS - 1;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state, 1, CAPACITY, 0, MAX_PUSH);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity gen) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // Send first, then burn only if a whole tick's output still fits: burning into a nearly full store would
        // throw the part that does not fit away, and a generator feeding a small steady load would waste half its fuel.
        // as the setting raises what it makes, so what it sends: at ten times, it would fill and stop at five
        MachineIO.pushEnergy(server, pos, gen.energy, Math.max(MAX_PUSH, MachineTuning.generated(MAX_PUSH)));
        boolean full = gen.energyStored() + MachineTuning.generated(FE_PER_TICK) > CAPACITY;
        // A generator told to stop keeps its fire banked (the fuel is not lost) but still sends out what it holds.
        boolean allowed = gen.redstoneAllows();
        if (allowed && gen.burnTime <= 0 && !full) {
            gen.startBurning(server);
        }
        if (allowed && gen.burnTime > 0 && !full) {
            gen.burnTime--;
            gen.addEnergy(MachineTuning.generated(FE_PER_TICK));
            gen.lastBurned = level.getGameTime();
        }
        boolean lit = allowed && gen.burnTime > 0 && level.getGameTime() - gen.lastBurned <= EMBERS;
        // Running while the fire shows, as the flame says: on a light load it burns every other tick, its store full in
        // between, and the screen said Running and Full by turns.
        gen.status = !allowed ? MachineStatus.REDSTONE : lit ? MachineStatus.RUNNING
                : full ? MachineStatus.FULL : MachineStatus.NO_FUEL;
        if ((gen.status == MachineStatus.RUNNING || gen.status == MachineStatus.FULL) && MachineIO.cutShort(server, pos)) {
            gen.status = MachineStatus.TOO_MANY_CABLES; // the walk just made is kept, so asking costs nothing more
        }
        if (state.getValue(GeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, lit), 3);
        }
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fuelTank;
    }

    public int fuelStored() {
        return this.fuelTank.getAmountAsInt(0);
    }

    /** Ticks one mB of the liquid fuel in the tank burns for, or 0 when it is empty. */
    private int ticksPerMb() {
        var fluid = this.fuelTank.getResource(0).getFluid();
        return this.fuelStored() <= 0 ? 0 : fluid == OilFluids.BIODIESEL.get() ? BIODIESEL_TICKS_PER_MB : OIL_TICKS_PER_MB;
    }

    private void startBurning(ServerLevel level) {
        ItemStack fuel = this.items.get(0);
        int time = this.burnTime(level, fuel);
        if (time <= 0) {
            // No fuel in the slot: a gulp from the tank instead.
            int perMb = this.ticksPerMb();
            int gulp = Math.min(GULP, this.fuelStored());
            if (perMb > 0 && gulp > 0) {
                Tanks.set(this.fuelTank, this.fuelTank.getResource(0).getFluid(), this.fuelStored() - gulp);
                this.burnTime = gulp * perMb;
                this.burnTotal = this.burnTime;
                this.setChanged();
            }
            return;
        }
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        fuel.shrink(1);
        if (fuel.isEmpty()) {
            this.items.set(0, remainder != null ? remainder.create() : ItemStack.EMPTY);
        } else if (remainder != null) {
            this.putAside(level, remainder.create()); // a stack of bottles: the empties go aside, not into thin air
        }
        this.burnTime = time;
        this.burnTotal = time;
        this.setChanged();
    }

    /** Into whatever takes items under the generator, or else onto its top. */
    private void putAside(ServerLevel level, ItemStack stack) {
        var below = level.getCapability(Capabilities.Item.BLOCK, this.worldPosition.below(),
                Direction.UP);
        if (below != null) {
            stack.shrink(Move.insert(below, ItemResource.of(stack), stack.getCount()));
        }
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0,
                    this.worldPosition.getZ() + 0.5, stack);
        }
    }

    /**
     * Ticks {@code stack} burns for here: a furnace's fuel, or a bottle of oil or biodiesel, which burns in a Generator
     * only. A furnace hands back a stack's empties only when the stack runs out - fifteen bottles of sixteen lost, the
     * last jamming its fuel slot (r7 review) - where the Generator puts each one aside (the user, 2026-10-06).
     * <p>
     * A furnace's fuel burns as long as in a furnace: its cooking_fuel, reckoned as AbstractFurnaceBlockEntity does,
     * with the Generator in the furnace's place (so not the half a smoker or a blast furnace gives).
     */
    public int burnTime(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0; // nothing to burn: no loot context built for it, every tick an idle Generator stands
        }
        if (stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get())) {
            return OIL_BOTTLE_TICKS;
        }
        if (stack.is(ModOil.BIODIESEL_BOTTLE.get())) {
            return BIODIESEL_BOTTLE_TICKS;
        }
        // the loot context BaseContainerBlockEntity#getLootContext makes for a furnace's fuel
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, this.getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.getBlockPos()))
                .withParameter(LootContextParams.CONTAINER, this)
                .withParameter(NeoForgeLootContextParams.QUERIED_STACK, stack)
                .create(LootContextParamSets.CONTAINER_PROCESS)).create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    /** Whether {@code stack} burns here, on either side (a slot asks on the client too, where no fuel is reckoned). */
    private static boolean burns(ItemStack stack) {
        // nor a chest or barrel picked up with its contents, burnt with them
        return stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get()) || stack.is(ModOil.BIODIESEL_BOTTLE.get())
                || stack.has(DataComponents.COOKING_FUEL) && !dev.nookworks.recycling.RecyclingRecipe.holdsThings(stack);
    }

    /** A bottle's burn: a quarter of its bucket's (a bucket is 16,000, oil or biodiesel, as a furnace's fuel). */
    static final int OIL_BOTTLE_TICKS = 4000;
    static final int BIODIESEL_BOTTLE_TICKS = 4000;

    @Override
    public MachineLayout layout() {
        return MachineLayout.GENERATOR;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.generated(FE_PER_TICK);
    }

    /** The flame on the screen, as on the front: only while it burns (a banked fire shows none). */
    @Override
    public int guiProgress() {
        return this.getBlockState().getValue(GeneratorBlock.LIT) ? this.burnTime : 0;
    }

    /** The liquid fuel tank: oil or biodiesel, whichever is in it. */
    @Override
    public ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.fuelTank;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        var fluid = this.fuelTank.getResource(0).getFluid();
        GuiTank.Kind kind = this.fuelStored() <= 0 ? GuiTank.Kind.LIQUID_FUEL
                : fluid == OilFluids.BIODIESEL.get() ? GuiTank.Kind.BIODIESEL : GuiTank.Kind.VEGETABLE_OIL;
        return List.of(new GuiTank(kind, this.fuelStored(), FUEL_TANK));
    }

    @Override
    public int guiProgressMax() {
        return Math.max(this.burnTotal, this.burnTime);
    }

    @Override
    public Component status() {
        return this.burnTime > 0
                ? Component.translatable("nookworks.machine.generator.burning", this.energyText(), this.burnTime / 20)
                : Component.translatable("nookworks.machine.idle", this.energyText());
    }

    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        out.accept(Tanks.held(this.fuelTank));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        Tanks.restore(this.fuelTank, fluid);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("BurnTotal", this.burnTotal);
        this.fuelTank.serialize(output.child("Fuel"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.burnTime = input.getIntOr("BurnTime", 0);
        this.burnTotal = input.getIntOr("BurnTotal", this.burnTime);
        input.child("Fuel").ifPresent(this.fuelTank::deserialize);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.level != null && burns(stack);
    }

    /** Only leftovers such as the empty bucket from lava can be pulled out, from below. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && this.level != null && !burns(stack);
    }
}
