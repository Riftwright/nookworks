package dev.nookworks.experience;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The experience a machine's work leaves behind - what a furnace would give as orbs - kept as Liquid Experience. Only
 * the machine fills it: a pipe beside it would otherwise pump fluid back and forth. Pipes and tanks beside it draw it
 * off, ours or another mod's, and the machine's fill box bottles it; what no longer fits comes out as orbs at once, and
 * what is in it when the machine is broken pours out as orbs. The Electric Furnace and the Sealed Retort keep one each.
 */
public class ExperienceBuffer extends OneTank {
    /** What it pushes into each side a tick. */
    private static final int PUSH = 100;

    private final int capacity;
    private final Runnable changed;
    /**
     * What stands on each side to take it, as NeoForge keeps it until it changes: asked afresh, all six sides were
     * looked up every tick a machine held any (the 10-19 performance audit). Made at the first push.
     */
    @SuppressWarnings("unchecked")
    private final @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>[] sides = new BlockCapabilityCache[6];

    /** {@code changed} is told of every change, to have the machine saved. */
    public ExperienceBuffer(int capacity, Runnable changed) {
        super(capacity);
        this.capacity = capacity;
        this.changed = changed;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previous) {
        this.changed.run();
    }

    public int stored() {
        return this.getAmountAsInt(0);
    }

    /** Keeps {@code points} of experience as liquid; what no longer fits comes out on top of {@code pos} as orbs. */
    public void keep(@Nullable Level level, BlockPos pos, float points) {
        int millibuckets = Math.round(points * ExperienceFluids.MB_PER_POINT);
        int add = Math.min(millibuckets, this.capacity - this.stored());
        if (level instanceof ServerLevel server) {
            ExperienceFluids.overflowAsOrbs(server, pos, millibuckets - Math.max(0, add));
        }
        if (add > 0) {
            this.set(0, FluidResource.of(ExperienceFluids.SOURCE.get()), this.stored() + add);
        }
    }

    /**
     * Into any pipe or tank beside {@code machine} (at {@code pos}) that takes experience, ours or another mod's - not
     * across a claim's edge where claims keep it from going ({@link DrawRights#mayMove}).
     */
    public void push(ServerLevel level, BlockPos pos, BlockEntity machine) {
        for (Direction side : Direction.values()) {
            if (this.stored() <= 0) {
                return;
            }
            BlockPos next = pos.relative(side);
            if (this.sides[side.ordinal()] == null) {
                // nothing in a chunk not loaded: the cache answers none there, and is told when it loads
                this.sides[side.ordinal()] = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, next, side.getOpposite(),
                        () -> !machine.isRemoved(), () -> {});
            }
            var target = this.sides[side.ordinal()].getCapability();
            if (target != null && DrawRights.mayMove(level, pos, machine, next, level.getBlockEntity(next))) {
                Move.transfer(this, target, this.getResource(0), Math.min(PUSH, this.stored()));
            }
        }
    }

    /** The machine broken at {@code pos}: what it held pours out as orbs. */
    public void spill(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            ExperienceFluids.spillAsOrbs(server, pos, this.stored());
        }
    }
}
