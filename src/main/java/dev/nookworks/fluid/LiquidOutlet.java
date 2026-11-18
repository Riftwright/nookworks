package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.FluidStacksResourceHandler;

/**
 * A machine's liquid on its way out: pushed by the machine itself into what takes it beside it - a Copper Pipe whose line
 * leads to something that takes it (an empty line not finished yet, or one that only brings something in, is left
 * alone), a Copper Tank, another mod's tank or machine - never across a claim's edge ({@link DrawRights#mayMove}). The
 * Sealed Retort's wood vinegar goes out so, and the Vacuum Distiller's water from its sides; its methanol from its
 * Condenser, into pipes only ({@link #LiquidOutlet(boolean, boolean)}).
 */
public final class LiquidOutlet {
    /** How far along a pipe line it looks for something that takes the liquid, as the Stirring Tank does for biodiesel. */
    private static final int LINE_LOOK = 512;

    private final boolean up;
    /** Whether only our Copper Pipes take it: no machine or tank set against it (a Condenser's outlet). */
    private final boolean pipesOnly;
    private final long[] askedAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2,
            Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private final boolean[] leads = new boolean[6];

    /** {@code up}: whether the block above may take it too (not a Distiller's column). */
    public LiquidOutlet(boolean up) {
        this(up, false);
    }

    /**
     * {@code pipesOnly}: only Copper Pipes take it - the user, 2026-11-12, of the Distiller's Condenser: 「冷却器の横に機械を
     * 置くのはさすがに格好悪いし…そこはパイプで運ぶ設計にしましょう」.
     */
    public LiquidOutlet(boolean up, boolean pipesOnly) {
        this.up = up;
        this.pipesOnly = pipesOnly;
    }

    /** Pours what {@code tank} holds into what takes it around {@code pos}, as much as each will take. */
    public void push(ServerLevel level, BlockPos pos, BlockEntity machine, FluidStacksResourceHandler tank) {
        this.push(level, pos, machine, tank, side -> true);
    }

    /** As {@link #push(ServerLevel, BlockPos, BlockEntity, FluidStacksResourceHandler)}, on the sides {@code may} lets. */
    public void push(ServerLevel level, BlockPos pos, BlockEntity machine, FluidStacksResourceHandler tank,
            java.util.function.Predicate<Direction> may) {
        for (Direction side : Direction.values()) {
            if (!may.test(side)) {
                continue;
            }
            int stored = tank.getAmountAsInt(0);
            if (stored <= 0) {
                return;
            }
            Fluid fluid = tank.getResource(0).getFluid();
            ResourceHandler<FluidResource> target = this.target(level, pos, machine, side, fluid, stored);
            if (target != null) {
                Move.transfer(tank, target, FluidResource.of(fluid), stored);
            }
        }
    }

    /** What on {@code side} takes {@code fluid}, of which the machine holds {@code held}, or null. */
    private @Nullable ResourceHandler<FluidResource> target(ServerLevel level, BlockPos pos, BlockEntity machine, Direction side,
            Fluid fluid, int held) {
        BlockPos next = pos.relative(side);
        if (side == Direction.UP && !this.up || !level.isLoaded(next)) {
            return null;
        }
        ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, next, side.getOpposite());
        BlockEntity there = level.getBlockEntity(next);
        if (target == null || !DrawRights.mayMove(level, pos, machine, next, there)) {
            return null;
        }
        FluidResource resource = FluidResource.of(fluid);
        if (this.pipesOnly && !(there instanceof FluidPipeBlockEntity)) {
            return null;
        }
        if (there instanceof FluidPipeBlockEntity pipe && pipe.stored() <= 0) {
            return this.leadsOn(level, pos, side, pipe, resource) ? target : null;
        }
        // a cauldron beside it takes a bucket once the machine holds one (Tanks.wouldTake)
        return Tanks.wouldTake(target, resource, Math.max(1, held)) ? target : null;
    }

    /** {@link FluidPipeBlockEntity#leadsToTakerOf}, for the empty pipe on each side, asked once a second at most. */
    private boolean leadsOn(ServerLevel level, BlockPos pos, Direction side, FluidPipeBlockEntity pipe, FluidResource resource) {
        int i = side.ordinal();
        long now = level.getGameTime();
        if (now - this.askedAt[i] >= 20 || now < this.askedAt[i]) {
            this.askedAt[i] = now;
            this.leads[i] = pipe.leadsToTakerOf(level, resource, pos, LINE_LOOK);
        }
        return this.leads[i];
    }
}
