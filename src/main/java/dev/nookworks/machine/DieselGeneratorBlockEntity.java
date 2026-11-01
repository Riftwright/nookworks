package dev.nookworks.machine;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.block.DieselGeneratorBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Diesel Generator: the end of the biodiesel chain, and the power a base of machines wants (the user, 2026-10-06: "機械に
 * 対して、明らかに発電機が貧弱"). It burns biodiesel only, at 240 FE/t - six Generators - and gets twice the Generator's
 * power out of each mB: 1,280 FE, where a Generator gets 640. So a bucket runs it about four and a half minutes. The
 * fuel comes into its four-bucket tank by Copper Pipe, by a bucket or bottle clicked on it, or put in its slot, which
 * is poured into the tank as there is room (the empties put aside as a Generator's are). Stores 240,000 FE and sends up
 * to five times what it makes a tick, split evenly, as a Generator does; pauses while full.
 */
public class DieselGeneratorBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 240;
    /** What a mB of biodiesel gives at the default output: twice a Generator's 640 (16 ticks at 40 FE/t). */
    public static final int FE_PER_MB = 1_280;
    public static final int CAPACITY = 240_000;
    public static final int MAX_PUSH = 1_200;
    public static final int FUEL_TANK = 4_000;
    /**
     * mB taken from the tank at a time: 80 ticks' burning. Small, so a machine broken mid-gulp loses little, and large
     * enough that a comparator on the tank is not woken every few ticks.
     */
    public static final int GULP = 15;
    private static final int[] SLOTS = {0};

    private final FluidStacksResourceHandler fuelTank = Tanks.tank(FUEL_TANK, fluid -> fluid == OilFluids.BIODIESEL.get(),
            true, false, this::setChanged);

    /**
     * Fuel taken in from the tank and not burned yet, in FE at the default output. Kept as power rather than as ticks:
     * a mB burns for five and a third ticks, and nothing of it is rounded away.
     */
    private int burning;
    /** The game time it last burned (not saved): its lamp and smoke stay up a little after, as a Generator's fire does. */
    private static final int EMBERS = 40;
    private long lastBurned = -EMBERS - 1;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIESEL_GENERATOR.get(), pos, state, 1, CAPACITY, 0, MAX_PUSH);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DieselGeneratorBlockEntity gen) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        gen.pourSlot(server);
        // As a Generator: send first, then burn only if a whole tick's output still fits, so no fuel is thrown away.
        MachineIO.pushEnergy(server, pos, gen.energy, Math.max(MAX_PUSH, MachineTuning.generated(MAX_PUSH)));
        boolean full = gen.energyStored() + MachineTuning.generated(FE_PER_TICK) > CAPACITY;
        boolean allowed = gen.redstoneAllows();
        boolean ran = allowed && !full && gen.fuelUp();
        if (ran) {
            // the setting raises the power made a tick from the same fuel, as it does a Generator's
            gen.burning -= FE_PER_TICK;
            gen.addEnergy(MachineTuning.generated(FE_PER_TICK));
            gen.lastBurned = level.getGameTime();
        }
        boolean lit = allowed && gen.hasFuel() && level.getGameTime() - gen.lastBurned <= EMBERS;
        // Running while its lamp is lit, as a Generator is while its fire shows, so a light load does not show
        // Running and Full by turns.
        gen.status = !allowed ? MachineStatus.REDSTONE : ran || lit ? MachineStatus.RUNNING
                : full ? MachineStatus.FULL : MachineStatus.NO_FUEL;
        if ((gen.status == MachineStatus.RUNNING || gen.status == MachineStatus.FULL) && MachineIO.cutShort(server, pos)) {
            gen.status = MachineStatus.TOO_MANY_CABLES;
        }
        if (state.getValue(DieselGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(DieselGeneratorBlock.LIT, lit), 3);
        }
    }

    /** Takes a gulp from the tank when what is burning will not last the tick; whether there is a tick's fuel now. */
    private boolean fuelUp() {
        if (this.burning < FE_PER_TICK && this.fuelStored() > 0) {
            int gulp = Math.min(GULP, this.fuelStored());
            Tanks.set(this.fuelTank, OilFluids.BIODIESEL.get(), this.fuelStored() - gulp);
            this.burning += gulp * FE_PER_MB;
        }
        return this.burning >= FE_PER_TICK;
    }

    /** Fuel for a tick or more, in the tank or burning. (Less than a tick's left of a last gulp waits for more.) */
    private boolean hasFuel() {
        return this.burning >= FE_PER_TICK || this.fuelStored() > 0;
    }

    /** Ticks the fuel in it lasts, burning and in the tank, at the default output. */
    public int ticksLeft() {
        return (int) (((long) this.fuelStored() * FE_PER_MB + this.burning) / FE_PER_TICK);
    }

    /**
     * A biodiesel bucket or bottle in the slot is poured into the tank once it has room for it. The empty is put aside
     * as a Generator puts its empties: the last of a stack's stays in the slot (a hopper under it draws it out), the
     * rest go into what takes items below, or out on top.
     */
    private void pourSlot(ServerLevel level) {
        ItemStack held = this.items.get(0);
        int amount = mbIn(held);
        if (amount <= 0 || !this.pour(amount)) {
            return;
        }
        ItemStack empty = new ItemStack(held.is(ModOil.BIODIESEL_BUCKET.get()) ? Items.BUCKET : Items.GLASS_BOTTLE);
        held.shrink(1);
        if (held.isEmpty()) {
            this.items.set(0, empty);
        } else {
            this.putAside(level, empty);
        }
        this.setChanged();
    }

    /** mB of biodiesel {@code stack} pours: a bucket's or a bottle's; 0 for anything else. */
    public static int mbIn(ItemStack stack) {
        return stack.is(ModOil.BIODIESEL_BUCKET.get()) ? 1_000 : stack.is(ModOil.BIODIESEL_BOTTLE.get()) ? OilFluids.BOTTLE : 0;
    }

    /** Pours {@code amount} mB of biodiesel into the tank if it all fits; whether it did. */
    public boolean pour(int amount) {
        if (FUEL_TANK - this.fuelStored() < amount) {
            return false;
        }
        Tanks.set(this.fuelTank, OilFluids.BIODIESEL.get(), this.fuelStored() + amount);
        return true;
    }

    /** Into whatever takes items under it, or else onto its top. */
    private void putAside(ServerLevel level, ItemStack stack) {
        var below = level.getCapability(Capabilities.Item.BLOCK, this.worldPosition.below(), Direction.UP);
        if (below != null) {
            stack.shrink(Move.insert(below, ItemResource.of(stack), stack.getCount()));
        }
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0,
                    this.worldPosition.getZ() + 0.5, stack);
        }
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fuelTank;
    }

    public int fuelStored() {
        return this.fuelTank.getAmountAsInt(0);
    }

    /** A comparator reads the tank, as at a Copper Tank: the fuel is there, not in the slot. */
    public int comparatorSignal() {
        return this.fuelStored() <= 0 ? 0 : 1 + this.fuelStored() * 14 / FUEL_TANK;
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.DIESEL_GENERATOR;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.generated(FE_PER_TICK);
    }

    /**
     * The flame on the screen: full while it burns - it burns at one steady rate, and the tank beside it shows the fuel
     * going down - and out while it stands. Its tooltip says how long the fuel lasts.
     */
    @Override
    public int guiProgress() {
        return this.getBlockState().getValue(DieselGeneratorBlock.LIT) ? this.ticksLeft() : 0;
    }

    @Override
    public int guiProgressMax() {
        return this.getBlockState().getValue(DieselGeneratorBlock.LIT) ? Math.max(1, this.ticksLeft()) : 1;
    }

    @Override
    public ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.fuelTank;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.BIODIESEL, this.fuelStored(), FUEL_TANK));
    }

    @Override
    public Component status() {
        return this.getBlockState().getValue(DieselGeneratorBlock.LIT)
                ? Component.translatable("nookworks.machine.generator.burning", this.energyText(), this.ticksLeft() / 20)
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
        output.putInt("Burning", this.burning);
        this.fuelTank.serialize(output.child("Fuel"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // a save can say anything (an operator's /data): no more than a gulp, and what was left of the last, is ever burning
        this.burning = Math.clamp(input.getIntOr("Burning", 0), 0, GULP * FE_PER_MB + FE_PER_TICK);
        input.child("Fuel").ifPresent(this.fuelTank::deserialize);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    /** Biodiesel by the bucket or the bottle, nothing else. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return mbIn(stack) > 0;
    }

    /** Only the empties can be pulled out, from below. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && mbIn(stack) <= 0;
    }
}
