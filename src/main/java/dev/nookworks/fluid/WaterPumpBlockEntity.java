package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Water Pump: set it on water and it draws a bucket a second from the source block under it, on FE, and pushes the
 * water out of its sides and top into Copper Pipes, tanks and machines (ours or another mod's), which may also draw it.
 * <p>
 * Water that would refill itself in vanilla - a source with at least two source neighbours beside it - is never used
 * up, so a 2x2 pool or the sea runs a pump forever. A lone source is pumped dry, as a bucket would take it.
 */
public class WaterPumpBlockEntity extends MachineBlockEntity {
    public static final int TANK = 4_000;
    public static final int BUCKET = 1_000;
    public static final int FE_PER_TICK = 10;
    /** Ticks to draw a bucket. */
    public static final int TICKS = 20;
    private static final int[] NO_SLOTS = {};

    private int progress;

    /** What it has pumped: drawn from by pipes, never filled from outside. */
    private final FluidStacksResourceHandler water = new OneTank(TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid() == Fluids.WATER;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0; // only the pump fills it, or a pipe beside it would push water back and forth
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            WaterPumpBlockEntity.this.setChanged();
        }
    };

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PUMP.get(), pos, state, 0, 10_000, 200, 0);
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.water;
    }

    public int stored() {
        return this.water.getAmountAsInt(0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterPumpBlockEntity pump) {
        if (level instanceof ServerLevel server) {
            pump.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        this.push(level, pos);
        BlockPos below = pos.below();
        MachineStatus blocked = !sourceAt(level, below) ? MachineStatus.NO_WATER
                : TANK - this.stored() < BUCKET ? MachineStatus.TANK_FULL
                : !this.redstoneAllows() ? MachineStatus.REDSTONE
                : this.energyStored() < this.workCost(FE_PER_TICK) ? MachineStatus.NO_POWER
                : this.progress >= MachineTuning.time(TICKS) - 1 && refills(level, below) == null ? MachineStatus.UNLOADED : null;
        if (blocked != null) {
            this.status = blocked;
            if (blocked != MachineStatus.NO_POWER && blocked != MachineStatus.REDSTONE && blocked != MachineStatus.UNLOADED && this.progress != 0) {
                this.progress = 0;
                this.setChanged();
            }
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            if (!Boolean.TRUE.equals(refills(level, below))) {  // known by now: the status check waits on null
                level.setBlockAndUpdate(below, Blocks.AIR.defaultBlockState());
            }
            this.water.set(0, FluidResource.of(Fluids.WATER), this.stored() + BUCKET);
        }
        this.setChanged();
    }

    /** A water source block the pump can draw from (not a waterlogged block, which keeps its water). */
    private static boolean sourceAt(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource();
    }

    /**
     * Whether vanilla would make the source again at once, by its own rule: two or more sources beside it that may
     * spread one (so not when the water_source_conversion game rule is off), over something solid or more water.
     * Null when a neighbour is not loaded: the chunk next door might hold the sea, or nothing, and the pump waits
     * rather than drink a lake dry because it could not see it.
     */
    private static @Nullable Boolean refills(ServerLevel level, BlockPos pos) {
        int sources = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next)) {
                return null;
            }
            BlockState state = level.getBlockState(next);
            if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()
                    && EventHooks.canCreateFluidSource(level, next, state)) {
                sources++;
            }
        }
        if (sources < 2) {
            return false;
        }
        BlockPos under = pos.below();
        if (!level.isLoaded(under)) {
            return null;
        }
        BlockState below = level.getBlockState(under);
        return below.isSolid() || below.getFluidState().is(Fluids.WATER) && below.getFluidState().isSource();
    }

    /** Out of the top and sides, into anything that takes water: not across a claim's edge where it may not go. */
    private void push(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (this.stored() <= 0) {
                return;
            }
            BlockPos next = pos.relative(side);
            if (side == Direction.DOWN || !level.isLoaded(next)) {
                continue;
            }
            ResourceHandler<FluidResource> target = side == Direction.UP ? Tanks.above(level, pos)
                    : level.getCapability(Capabilities.Fluid.BLOCK, next, side.getOpposite());
            if (target != null && DrawRights.mayMove(level, pos, this, next, level.getBlockEntity(next))) {
                Move.transfer(this.water, target, this.water.getResource(0), this.stored());
            }
        }
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.water_pump", this.energyText(), this.stored(), TANK,
                Component.translatable(this.status.translationKey()));
    }

    /** To Jade, only what it is doing: its power and water Jade shows itself. */
    @Override
    public void jade(java.util.function.Consumer<Component> lines) {
        lines.accept(Component.translatable(this.status.translationKey()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
        this.water.serialize(output.child("Water"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
        input.child("Water").ifPresent(this.water::deserialize);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }
}
