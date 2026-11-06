package dev.nookworks.fluid;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** One-fluid tanks for machines: what they hold, and whether the outside may fill them, draw from them, or both. */
public final class Tanks {
    /**
     * A tank of {@code capacity} mB for the fluids {@code holds} accepts (one at a time). With {@code fill} false nothing
     * outside can pour in; with {@code drain} false nothing can draw out - so a pipe beside it moves fluid one way only.
     */
    public static FluidStacksResourceHandler tank(int capacity, Predicate<Fluid> holds, boolean fill, boolean drain, Runnable changed) {
        return new OneTank(capacity) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return holds.test(resource.getFluid());
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return fill ? super.insert(index, resource, amount, transaction) : 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return drain ? super.extract(index, resource, amount, transaction) : 0;
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previous) {
                changed.run();
            }
        };
    }

    /**
     * {@code tank} as a side that only lets fluid out: what a pipe or machine beside it may draw, but not fill. The
     * Copper Tank's bottom is one, so what it pours down into a pipe is never handed straight back up.
     */
    public static ResourceHandler<FluidResource> drainOnly(
            ResourceHandler<FluidResource> tank) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return tank.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return tank.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return tank.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return tank.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return tank.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return tank.extract(index, resource, amount, transaction);
            }
        };
    }

    /**
     * {@code tank} as a side that only takes fluid in: what a neighbour may pour into, but not draw from. A pipe's side
     * set to pull is one, so what the pipe draws out of a block is never drawn straight back by it (a Repair Box draws
     * from whatever is beside it), and a bucket clicked on a machine only ever pours in.
     */
    public static ResourceHandler<FluidResource> fillOnly(
            ResourceHandler<FluidResource> tank) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return tank.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return tank.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return tank.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return tank.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return tank.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return tank.insert(index, resource, amount, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /**
     * {@code tank}, telling {@code took} of each transaction that puts something into it through this handler - a trial
     * one (a pipe asking what would go in) too, so what it keeps belongs in that transaction's journal.
     */
    public static ResourceHandler<FluidResource> noting(ResourceHandler<FluidResource> tank, Consumer<TransactionContext> took) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return tank.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return tank.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return tank.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return tank.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return tank.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                int in = tank.insert(index, resource, amount, transaction);
                if (in > 0) {
                    took.accept(transaction);
                }
                return in;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return tank.extract(index, resource, amount, transaction);
            }
        };
    }

    /**
     * Where a machine that sends its fluid out of its top puts it: the handler above {@code pos} - and a Copper Tank
     * stood on the machine is filled whole. A tank's bottom takes nothing in from below, so that a pipe under it does
     * not hand straight back what the tank pours down; but a machine sending its output up is no pipe, and a tank on
     * top of it is the plainest way to catch what it makes. (The tank stood there and stayed empty.)
     */
    public static @Nullable ResourceHandler<FluidResource> above(
            ServerLevel level, BlockPos pos) {
        BlockPos up = pos.above();
        if (!level.isLoaded(up)) {
            return null;
        }
        if (level.getBlockEntity(up) instanceof CopperTankBlockEntity tank) {
            return tank.fluidHandler();
        }
        return level.getCapability(Capabilities.Fluid.BLOCK, up, Direction.DOWN);
    }

    /**
     * A copy of {@code tank} that a player's own hand may fill and draw from, past its fill and drain rules but holding
     * only what it holds and no more than it holds: a bucket clicked on its gauge (GaugePouring), which writes what the
     * copy holds back with {@link #set}.
     */
    public static OneTank byHand(OneTank tank) {
        OneTank copy = (OneTank) tank(tank.capacity(), fluid -> tank.isValid(0, FluidResource.of(fluid)), true, true, () -> {});
        if (tank.getAmountAsInt(0) > 0) {
            copy.set(0, tank.getResource(0), tank.getAmountAsInt(0));
        }
        return copy;
    }

    /** What {@code tank} holds, as a stack (empty when it is dry): for keeping in the machine's item. */
    public static FluidStack held(FluidStacksResourceHandler tank) {
        return tank.getAmountAsInt(0) <= 0 ? FluidStack.EMPTY : tank.getResource(0).toStack(tank.getAmountAsInt(0));
    }

    /**
     * Pours {@code fluid} kept in an item back into {@code tank}, past the fill rules, if the tank holds that fluid; no
     * more than it has room for. False when the tank is not for that fluid.
     */
    public static boolean restore(FluidStacksResourceHandler tank, FluidStack fluid) {
        FluidResource resource = FluidResource.of(fluid);
        if (fluid.isEmpty() || !tank.isValid(0, resource)) {
            return false;
        }
        tank.set(0, resource, Math.min(fluid.getAmount(), tank.getCapacityAsInt(0, resource)));
        return true;
    }

    /** Sets what {@code tank} holds from inside the machine, past the fill and drain rules. */
    public static void set(FluidStacksResourceHandler tank, Fluid fluid, int amount) {
        tank.set(0, amount > 0 ? FluidResource.of(fluid) : FluidResource.EMPTY, Math.max(0, amount));
    }

    /**
     * Holds the item of a block that keeps liquid in it to one a stack while it does, as a filled bucket is: 64 filled
     * Copper Tanks carried a thousand buckets in one slot. Set as the block is made an item (its loot table copies it).
     */
    public static void oneToAStack(DataComponentMap.Builder components) {
        components.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    /**
     * Takes back the limit {@link #oneToAStack} set, as the item is set down. Read here, it is not kept with the block
     * for its next drop, by when the block may be empty.
     */
    public static void forgetStackLimit(DataComponentGetter components) {
        components.get(DataComponents.MAX_STACK_SIZE);
    }

    private Tanks() {}
}
